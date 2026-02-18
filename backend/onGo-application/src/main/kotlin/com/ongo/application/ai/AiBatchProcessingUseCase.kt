package com.ongo.application.ai

import com.ongo.application.ai.dto.*
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.Platform
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.video.VideoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

@Service
class AiBatchProcessingUseCase(
    private val generateMetaUseCase: GenerateMetaUseCase,
    private val generateHashtagsUseCase: GenerateHashtagsUseCase,
    private val sttUseCase: SttUseCase,
    private val creditService: CreditService,
    private val videoRepository: VideoRepository,
) {

    private val log = LoggerFactory.getLogger(AiBatchProcessingUseCase::class.java)
    private val batchStore = ConcurrentHashMap<String, AiBatchResponse>()
    private val concurrencyLimit = Semaphore(3)

    fun startBatch(userId: Long, request: AiBatchRequest): AiBatchResponse {
        if (request.videoIds.isEmpty()) {
            throw BusinessException("EMPTY_VIDEO_LIST", "영상 목록이 비어있습니다.")
        }

        // Calculate total credit cost
        val perItemCost = getPerItemCost(request.operation)
        val totalCost = perItemCost * request.videoIds.size

        // Pre-validate credits
        val balance = creditService.getBalance(userId)
        if (balance.totalBalance < totalCost) {
            throw BusinessException(
                "INSUFFICIENT_CREDITS",
                "크레딧이 부족합니다. 필요: ${totalCost}, 잔액: ${balance.totalBalance}"
            )
        }

        val batchId = UUID.randomUUID().toString()

        // Build initial item statuses
        val items = request.videoIds.map { videoId ->
            val video = videoRepository.findById(videoId)
            AiBatchItemStatus(
                videoId = videoId,
                videoTitle = video?.title,
                status = ItemStatus.PENDING,
            )
        }

        val batchResponse = AiBatchResponse(
            batchId = batchId,
            userId = userId,
            totalItems = items.size,
            status = BatchStatus.PROCESSING,
            items = items,
        )

        batchStore[batchId] = batchResponse

        // Process asynchronously using virtual threads
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        executor.submit {
            processBatch(userId, batchId, request)
        }

        return batchResponse
    }

    fun getBatchStatus(userId: Long, batchId: String): AiBatchResponse {
        val batch = batchStore[batchId]
            ?: throw BusinessException("BATCH_NOT_FOUND", "배치 작업을 찾을 수 없습니다: $batchId")
        if (batch.userId != userId) {
            throw ForbiddenException("해당 배치 작업에 대한 접근 권한이 없습니다")
        }
        return batch
    }

    private fun processBatch(userId: Long, batchId: String, request: AiBatchRequest) {
        val currentBatch = batchStore[batchId] ?: return
        val updatedItems = currentBatch.items.toMutableList()

        val executor = Executors.newVirtualThreadPerTaskExecutor()
        val futures = request.videoIds.mapIndexed { index, videoId ->
            executor.submit<Unit> {
                concurrencyLimit.acquire()
                try {
                    // Update status to PROCESSING
                    updateItemStatus(batchId, index, ItemStatus.PROCESSING)

                    processVideoItem(userId, videoId, request.operation, request.platform)

                    // Update status to COMPLETED
                    updateItemStatus(batchId, index, ItemStatus.COMPLETED)
                } catch (e: Exception) {
                    log.error("배치 아이템 처리 실패: videoId={}, operation={}", videoId, request.operation, e)
                    updateItemStatus(batchId, index, ItemStatus.FAILED, error = e.message)
                } finally {
                    concurrencyLimit.release()
                }
            }
        }

        // Wait for all to complete
        futures.forEach { future ->
            try {
                future.get()
            } catch (e: Exception) {
                log.error("배치 Future 처리 실패", e)
            }
        }

        // Determine final batch status
        val finalBatch = batchStore[batchId] ?: return
        val hasFailures = finalBatch.items.any { it.status == ItemStatus.FAILED }
        val allFailed = finalBatch.items.all { it.status == ItemStatus.FAILED }

        val finalStatus = when {
            allFailed -> BatchStatus.PARTIALLY_FAILED
            hasFailures -> BatchStatus.PARTIALLY_FAILED
            else -> BatchStatus.COMPLETED
        }

        batchStore[batchId] = finalBatch.copy(status = finalStatus)
    }

    private fun processVideoItem(userId: Long, videoId: Long, operation: AiBatchOperation, platform: Platform?) {
        val video = videoRepository.findById(videoId)
            ?: throw BusinessException("VIDEO_NOT_FOUND", "영상을 찾을 수 없습니다: $videoId")

        val targetPlatforms = if (platform != null) listOf(platform) else listOf(Platform.YOUTUBE)

        when (operation) {
            AiBatchOperation.GENERATE_META -> {
                val script = video.description ?: video.title
                generateMetaUseCase.execute(
                    userId = userId,
                    script = script,
                    targetPlatforms = targetPlatforms,
                    tone = "professional",
                    category = video.category ?: "general",
                )
            }

            AiBatchOperation.GENERATE_HASHTAGS -> {
                generateHashtagsUseCase.execute(
                    userId = userId,
                    title = video.title,
                    category = video.category ?: "general",
                    targetPlatforms = targetPlatforms,
                )
            }

            AiBatchOperation.STT -> {
                sttUseCase.execute(userId = userId, videoId = videoId)
            }

            AiBatchOperation.ALL -> {
                // Execute all operations sequentially
                val script = video.description ?: video.title
                generateMetaUseCase.execute(
                    userId = userId,
                    script = script,
                    targetPlatforms = targetPlatforms,
                    tone = "professional",
                    category = video.category ?: "general",
                )
                generateHashtagsUseCase.execute(
                    userId = userId,
                    title = video.title,
                    category = video.category ?: "general",
                    targetPlatforms = targetPlatforms,
                )
                sttUseCase.execute(userId = userId, videoId = videoId)
            }
        }
    }

    private fun updateItemStatus(batchId: String, index: Int, status: ItemStatus, error: String? = null) {
        val batch = batchStore[batchId] ?: return
        val items = batch.items.toMutableList()
        if (index < items.size) {
            items[index] = items[index].copy(status = status, error = error)
            batchStore[batchId] = batch.copy(items = items)
        }
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
