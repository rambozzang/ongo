package com.ongo.application.ai

import com.ongo.application.ai.dto.AiBatchOperation
import com.ongo.application.ai.dto.AiBatchRequest
import com.ongo.application.ai.dto.AiBatchResponse
import com.ongo.application.ai.dto.BatchStatus
import com.ongo.application.ai.dto.ItemStatus
import com.ongo.application.config.ExecutorConfig
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.video.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Credit-consuming batch AI operations.
 *
 * Batch state is durable. The executor and its semaphore only control local
 * concurrency; they are not the source of truth and can be recreated after a
 * deployment or process restart.
 */
@Service
class AiBatchProcessingUseCase(
    private val generateMetaUseCase: GenerateMetaUseCase,
    private val generateHashtagsUseCase: GenerateHashtagsUseCase,
    private val sttUseCase: SttUseCase,
    private val creditService: CreditService,
    private val videoRepository: VideoRepository,
    private val batchRepository: AiBatchRepository,
) {

    private val log = LoggerFactory.getLogger(AiBatchProcessingUseCase::class.java)
    private val runningBatchIds = ConcurrentHashMap.newKeySet<String>()

    fun startBatch(userId: Long, request: AiBatchRequest): AiBatchResponse {
        if (request.videoIds.isEmpty()) {
            throw BusinessException("EMPTY_VIDEO_LIST", "영상 목록이 비어있습니다.")
        }
        if (request.videoIds.size != request.videoIds.distinct().size) {
            throw BusinessException("DUPLICATE_VIDEO_IDS", "배치 영상 목록에 중복 영상이 있습니다.")
        }

        // Validate ownership before charging or creating a durable job. The old
        // in-memory path only checked this inside the worker, which exposed a
        // misleading accepted batch and made authorization failures asynchronous.
        val videos = request.videoIds.map { videoId ->
            val video = videoRepository.findById(videoId)
                ?: throw BusinessException("VIDEO_NOT_FOUND", "영상을 찾을 수 없습니다: $videoId")
            if (video.userId != userId) {
                throw ForbiddenException("해당 영상에 접근 권한이 없습니다")
            }
            video
        }

        val perItemCost = getPerItemCost(request.operation)
        val totalCost = perItemCost * request.videoIds.size
        val balance = creditService.getBalance(userId)
        if (balance.totalBalance < totalCost) {
            throw BusinessException(
                "INSUFFICIENT_CREDITS",
                "크레딧이 부족합니다. 필요: $totalCost, 잔액: ${balance.totalBalance}",
            )
        }

        val items = videos.map { video ->
            com.ongo.application.ai.dto.AiBatchItemStatus(
                videoId = requireNotNull(video.id),
                videoTitle = video.title,
                status = ItemStatus.PENDING,
            )
        }
        val response = AiBatchResponse(
            batchId = UUID.randomUUID().toString(),
            userId = userId,
            totalItems = items.size,
            status = BatchStatus.PROCESSING,
            items = items,
        )

        // Persist as PENDING so the atomic claim can start it immediately. The
        // client still receives PROCESSING as the optimistic response; a fresh
        // status read is backed by the claimed DB state.
        batchRepository.save(response.copy(status = BatchStatus.PENDING), request)
        launch(response.batchId)
        return response
    }

    fun getBatchStatus(userId: Long, batchId: String): AiBatchResponse {
        val batch = batchRepository.findById(batchId)?.response
            ?: throw BusinessException("BATCH_NOT_FOUND", "배치 작업을 찾을 수 없습니다: $batchId")
        if (batch.userId != userId) {
            throw ForbiddenException("해당 배치 작업에 대한 접근 권한이 없습니다")
        }
        return batch
    }

    @Scheduled(
        fixedDelayString = "\${ai.batch.recovery-delay-ms:15000}",
        initialDelayString = "\${ai.batch.recovery-initial-delay-ms:5000}",
    )
    fun recoverActiveBatches() {
        batchRepository.findActive(50).forEach { launch(it.response.batchId) }
    }

    private fun launch(batchId: String) {
        if (!runningBatchIds.add(batchId)) return

        Thread.ofVirtual().name("ai-batch-$batchId").start {
            try {
                val now = LocalDateTime.now()
                val batch = batchRepository.claimForExecution(
                    batchId = batchId,
                    now = now,
                    staleBefore = now.minusMinutes(30),
                ) ?: return@start
                processBatch(batch)
            } catch (e: Exception) {
                log.error("AI batch worker failed: batchId={}", batchId, e)
            } finally {
                runningBatchIds.remove(batchId)
            }
        }
    }

    private fun processBatch(batch: PersistedAiBatch) {
        val response = batch.response

        try {
            ExecutorConfig.newVirtualExecutor().use { executor ->
                val futures = batch.videoIds.mapIndexed { index, videoId ->
                    executor.submit<Unit> {
                        ExecutorConfig.aiBatchSemaphore.acquire()
                        try {
                            updateItemStatus(batch.response.batchId, index, ItemStatus.PROCESSING)
                            val result = processVideoItem(
                                userId = response.userId,
                                videoId = videoId,
                                operation = batch.operation,
                                platform = batch.platform,
                            )
                            updateItemStatus(
                                batch.response.batchId,
                                index,
                                ItemStatus.COMPLETED,
                                result = result,
                            )
                        } catch (e: Exception) {
                            log.error("배치 아이템 처리 실패: videoId={}, operation={}", videoId, batch.operation, e)
                            updateItemStatus(
                                batch.response.batchId,
                                index,
                                ItemStatus.FAILED,
                                error = e.message ?: "AI 작업에 실패했습니다.",
                            )
                        } finally {
                            ExecutorConfig.aiBatchSemaphore.release()
                        }
                    }
                }
                futures.forEach { future ->
                    runCatching { future.get() }
                        .onFailure { log.error("배치 Future 처리 실패", it) }
                }
            }

            val finalBatch = batchRepository.findById(response.batchId) ?: return
            val finalStatus = if (finalBatch.response.items.any { it.status == ItemStatus.FAILED }) {
                BatchStatus.PARTIALLY_FAILED
            } else {
                BatchStatus.COMPLETED
            }
            batchRepository.update(finalBatch.response.copy(status = finalStatus))
        } catch (e: Exception) {
            log.error("AI batch 실행 실패: batchId={}", response.batchId, e)
            runCatching {
                batchRepository.update(response.copy(status = BatchStatus.PARTIALLY_FAILED))
            }
        }
    }

    private fun processVideoItem(
        userId: Long,
        videoId: Long,
        operation: AiBatchOperation,
        platform: Platform?,
    ): Any? {
        val video = videoRepository.findById(videoId)
            ?: throw BusinessException("VIDEO_NOT_FOUND", "영상을 찾을 수 없습니다: $videoId")
        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 접근 권한이 없습니다")
        }

        val targetPlatforms = if (platform != null) listOf(platform) else listOf(Platform.YOUTUBE)
        return when (operation) {
            AiBatchOperation.GENERATE_META -> generateMetaUseCase.execute(
                userId = userId,
                script = video.description ?: video.title,
                targetPlatforms = targetPlatforms,
                tone = "professional",
                category = video.category ?: "general",
            )

            AiBatchOperation.GENERATE_HASHTAGS -> generateHashtagsUseCase.execute(
                userId = userId,
                title = video.title,
                category = video.category ?: "general",
                targetPlatforms = targetPlatforms,
            )

            AiBatchOperation.STT -> sttUseCase.execute(userId = userId, videoId = videoId)

            AiBatchOperation.ALL -> mapOf(
                "meta" to generateMetaUseCase.execute(
                    userId = userId,
                    script = video.description ?: video.title,
                    targetPlatforms = targetPlatforms,
                    tone = "professional",
                    category = video.category ?: "general",
                ),
                "hashtags" to generateHashtagsUseCase.execute(
                    userId = userId,
                    title = video.title,
                    category = video.category ?: "general",
                    targetPlatforms = targetPlatforms,
                ),
                "stt" to sttUseCase.execute(userId = userId, videoId = videoId),
            )
        }
    }

    private fun updateItemStatus(
        batchId: String,
        index: Int,
        status: ItemStatus,
        result: Any? = null,
        error: String? = null,
    ) {
        batchRepository.updateItem(batchId, index, status, result, error)
    }

    private fun getPerItemCost(operation: AiBatchOperation): Int = when (operation) {
        AiBatchOperation.GENERATE_META -> AiFeature.META_GENERATION.creditCost
        AiBatchOperation.GENERATE_HASHTAGS -> AiFeature.HASHTAG_RECOMMENDATION.creditCost
        AiBatchOperation.STT -> AiFeature.STT.creditCost
        AiBatchOperation.ALL -> AiFeature.META_GENERATION.creditCost +
            AiFeature.HASHTAG_RECOMMENDATION.creditCost +
            AiFeature.STT.creditCost
    }
}
