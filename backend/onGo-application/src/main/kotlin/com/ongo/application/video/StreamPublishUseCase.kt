package com.ongo.application.video

import com.ongo.application.config.ExecutorConfig
import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.enums.MediaType
import com.ongo.common.enums.Platform
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Visibility
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.domain.video.VideoPlatformMetaRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.YearMonth

@Service
class StreamPublishUseCase(
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val videoPlatformMetaRepository: VideoPlatformMetaRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val channelRepository: ChannelRepository,
    private val storageQuotaUseCase: StorageQuotaUseCase,
    private val streamWriterFactories: List<PlatformStreamWriterFactory>,
    private val scheduleRepository: ScheduleRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CHUNK_SIZE = 256 * 1024 // 256KB
    }

    @Transactional
    fun initiate(userId: Long, file: MultipartFile, request: StreamPublishRequest): StreamPublishResponse {
        val fileSize = file.size

        // 1. 플랜 한도 확인 (월간 업로드 횟수 + 스토리지 쿼터)
        val subscription = subscriptionRepository.findByUserId(userId)
        val planType = subscription?.planType ?: PlanType.FREE
        val monthlyCount = videoRepository.countByUserIdAndMonth(userId, YearMonth.now())
        if (monthlyCount >= planType.monthlyUploads) {
            throw PlanLimitExceededException("월간 업로드", planType.monthlyUploads)
        }
        storageQuotaUseCase.checkQuota(userId, fileSize)

        // 2. Video 레코드 생성 (fileUrl = null, status = UPLOADING)
        val video = videoRepository.save(
            Video(
                userId = userId,
                title = request.title,
                description = request.description,
                tags = request.tags,
                category = request.category,
                thumbnailUrls = if (request.thumbnailUrl != null) listOf(request.thumbnailUrl) else emptyList(),
                fileSizeBytes = fileSize,
                originalFilename = file.originalFilename,
                mediaType = MediaType.VIDEO,
                status = UploadStatus.UPLOADING,
            )
        )
        val videoId = video.id!!

        // 3. 채널 토큰 검증 + 각 플랫폼별 VideoUpload + VideoPlatformMeta 생성
        val platformConfigs = request.platforms.map { platformReq ->
            val channel = channelRepository.findByUserIdAndPlatform(userId, platformReq.platform)
                ?: throw IllegalStateException("${platformReq.platform.name} 채널이 연동되어 있지 않습니다.")

            val tokenExpiresAt = channel.tokenExpiresAt
            if (channel.status == "EXPIRED" || channel.status == "DISCONNECTED" ||
                (tokenExpiresAt != null && tokenExpiresAt.isBefore(LocalDateTime.now()))
            ) {
                throw IllegalStateException(
                    "${platformReq.platform.name} 채널의 인증 토큰이 만료되었습니다. 채널 관리에서 재연결 후 다시 시도해주세요."
                )
            }

            val upload = videoUploadRepository.save(
                VideoUpload(
                    videoId = videoId,
                    platform = platformReq.platform,
                    status = UploadStatus.UPLOADING,
                )
            )
            val uploadId = upload.id!!

            val meta = videoPlatformMetaRepository.save(
                VideoPlatformMeta(
                    videoUploadId = uploadId,
                    title = platformReq.title ?: request.title,
                    description = platformReq.description ?: request.description,
                    tags = platformReq.tags ?: request.tags,
                    visibility = platformReq.visibility,
                    customThumbnailUrl = request.thumbnailUrl,
                )
            )

            StreamPlatformContext(
                platform = platformReq.platform,
                videoUploadId = uploadId,
                meta = meta,
                accessToken = channel.accessToken,
                platformChannelId = channel.platformChannelId,
                scheduledAt = platformReq.scheduledAt,
            )
        }

        // 4. 예약 게시인 경우 Schedule 레코드 생성 (상태 추적용)
        val hasSchedule = platformConfigs.any { it.scheduledAt != null }
        if (hasSchedule) {
            val earliestScheduledAt = platformConfigs.mapNotNull { it.scheduledAt }.min()
            val platformMap = platformConfigs.filter { it.scheduledAt != null }
                .associate { it.platform.name to mapOf("scheduledAt" to it.scheduledAt.toString()) }
            scheduleRepository.save(
                Schedule(
                    videoId = videoId,
                    userId = userId,
                    scheduledAt = earliestScheduledAt,
                    status = ScheduleStatus.PROCESSING,
                    platforms = platformMap,
                )
            )
            log.info("예약 게시 스케줄 생성: videoId={}, scheduledAt={}", videoId, earliestScheduledAt)
        }

        // 5. 파일을 임시 파일로 저장
        val tempFile = Files.createTempFile("ongo-stream-", "-${file.originalFilename ?: "upload"}")
        try {
            file.transferTo(tempFile.toFile())
        } catch (e: Exception) {
            Files.deleteIfExists(tempFile)
            throw e
        }

        // 6. 트랜잭션 커밋 후 Virtual Thread에서 비동기 스트리밍 시작
        // (afterCommit: DB 레코드가 확실히 커밋된 후 읽기 가능)
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                Thread.ofVirtual().name("stream-publish-$videoId").start {
                    ExecutorConfig.uploadSemaphore.acquire()
                    try {
                        runStreamingUpload(videoId, tempFile.toFile(), fileSize, platformConfigs)
                    } finally {
                        ExecutorConfig.uploadSemaphore.release()
                    }
                }
            }
        })

        return StreamPublishResponse(videoId = videoId)
    }

    private fun runStreamingUpload(
        videoId: Long,
        tempFile: java.io.File,
        fileSize: Long,
        platformContexts: List<StreamPlatformContext>,
    ) {
        try {
            // 각 플랫폼별 writer 생성
            val writerMap: Map<StreamPlatformContext, PlatformStreamWriter> = platformContexts.mapNotNull { ctx ->
                val factory = streamWriterFactories.find { it.platform == ctx.platform }
                if (factory == null) {
                    log.error("플랫폼 {} 에 대한 StreamWriterFactory를 찾을 수 없습니다", ctx.platform)
                    updateUploadStatus(ctx.videoUploadId, UploadStatus.FAILED, "지원되지 않는 플랫폼: ${ctx.platform}")
                    null
                } else {
                    ctx to factory.createWriter()
                }
            }.toMap()

            if (writerMap.isEmpty()) {
                log.warn("영상 {} — 유효한 플랫폼 writer가 없습니다", videoId)
                updateOverallVideoStatus(videoId)
                return
            }

            // 병렬로 initSession() 호출 — 실패한 플랫폼은 FAILED 처리 후 제외, 나머지는 계속
            val activeWriterMap: MutableMap<StreamPlatformContext, PlatformStreamWriter> = mutableMapOf()
            ExecutorConfig.newVirtualExecutor().use { executor ->
                val initFutures = writerMap.map { (ctx, writer) ->
                    ctx to executor.submit<Unit> {
                        val sessionId = writer.initSession(ctx.meta, ctx.accessToken, ctx.platformChannelId, fileSize, ctx.scheduledAt)
                        val scheduleInfo = if (ctx.scheduledAt != null) ", scheduledAt=${ctx.scheduledAt}" else ""
                        log.info("플랫폼 {} 업로드 세션 초기화: videoId={}, sessionId={}{}",
                            ctx.platform, videoId, sessionId, scheduleInfo)
                    }
                }
                initFutures.forEach { (ctx, future) ->
                    try {
                        future.get()
                        activeWriterMap[ctx] = writerMap[ctx]!!
                    } catch (e: Exception) {
                        log.error("플랫폼 {} 세션 초기화 실패: videoId={}", ctx.platform, videoId, e)
                        updateUploadStatus(ctx.videoUploadId, UploadStatus.FAILED, "세션 초기화 실패: ${e.cause?.message ?: e.message}")
                    }
                }
            }

            if (activeWriterMap.isEmpty()) {
                log.warn("영상 {} — 세션 초기화 성공한 플랫폼이 없습니다", videoId)
                return
            }

            // 청크 스트리밍: 256KB 버퍼로 읽어 각 writer에 순차 전달 (writeChunk는 메모리 append라 I/O 없음)
            tempFile.inputStream().buffered().use { input ->
                val buffer = ByteArray(CHUNK_SIZE)
                var offset = 0L
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    val chunk = if (bytesRead < CHUNK_SIZE) buffer.copyOf(bytesRead) else buffer.copyOf()
                    val currentOffset = offset

                    activeWriterMap.values.forEach { writer ->
                        writer.writeChunk(chunk, currentOffset, fileSize)
                    }

                    offset += bytesRead
                }
            }

            // 완료 후 각 writer complete() 호출 및 상태 업데이트
            ExecutorConfig.newVirtualExecutor().use { executor ->
                val completeFutures = activeWriterMap.map { (ctx, writer) ->
                    executor.submit<Unit> {
                        try {
                            val result = writer.complete()
                            if (result.success) {
                                updateUploadStatus(
                                    ctx.videoUploadId,
                                    UploadStatus.PROCESSING,
                                    platformVideoId = result.platformVideoId,
                                    platformUrl = result.platformUrl,
                                )
                                log.info("플랫폼 {} 업로드 완료: videoId={}, platformUrl={}", ctx.platform, videoId, result.platformUrl)
                            } else {
                                updateUploadStatus(ctx.videoUploadId, UploadStatus.FAILED, result.errorMessage)
                                log.warn("플랫폼 {} 업로드 실패: videoId={}, error={}", ctx.platform, videoId, result.errorMessage)
                            }
                        } catch (e: Exception) {
                            log.error("플랫폼 {} 업로드 완료 처리 중 예외: videoId={}", ctx.platform, videoId, e)
                            updateUploadStatus(ctx.videoUploadId, UploadStatus.FAILED, e.message)
                        }
                    }
                }
                completeFutures.forEach { it.get() }
            }
        } catch (e: Exception) {
            log.error("스트리밍 업로드 전체 실패: videoId={}", videoId, e)
            platformContexts.forEach { ctx ->
                updateUploadStatus(ctx.videoUploadId, UploadStatus.FAILED, "스트리밍 업로드 실패: ${e.message}")
            }
        } finally {
            // 임시 파일 삭제
            try {
                Files.deleteIfExists(tempFile.toPath())
                log.debug("임시 파일 삭제 완료: {}", tempFile.absolutePath)
            } catch (e: Exception) {
                log.warn("임시 파일 삭제 실패: {}", tempFile.absolutePath, e)
            }

            // Video 전체 상태 업데이트
            updateOverallVideoStatus(videoId)
        }
    }

    private fun updateUploadStatus(
        uploadId: Long,
        status: UploadStatus,
        errorMessage: String? = null,
        platformVideoId: String? = null,
        platformUrl: String? = null,
    ) {
        val upload = videoUploadRepository.findById(uploadId) ?: return
        videoUploadRepository.update(
            upload.copy(
                status = status,
                errorMessage = errorMessage,
                platformVideoId = platformVideoId ?: upload.platformVideoId,
                platformUrl = platformUrl ?: upload.platformUrl,
                publishedAt = if (status == UploadStatus.PUBLISHED) LocalDateTime.now() else upload.publishedAt,
            )
        )
    }

    private fun updateOverallVideoStatus(videoId: Long) {
        val uploads = videoUploadRepository.findByVideoId(videoId)
        if (uploads.isEmpty()) return

        val video = videoRepository.findById(videoId) ?: return

        val overallStatus = when {
            uploads.all { it.status == UploadStatus.PUBLISHED } -> UploadStatus.PUBLISHED
            uploads.all { it.status == UploadStatus.FAILED || it.status == UploadStatus.REJECTED } -> UploadStatus.FAILED
            uploads.any { it.status == UploadStatus.PROCESSING || it.status == UploadStatus.REVIEW } -> UploadStatus.PROCESSING
            else -> UploadStatus.UPLOADING
        }

        videoRepository.update(video.copy(status = overallStatus))
    }
}

/** 스트리밍 업로드 중 플랫폼별 컨텍스트 (트랜잭션 외부에서 사용) */
private data class StreamPlatformContext(
    val platform: Platform,
    val videoUploadId: Long,
    val meta: VideoPlatformMeta,
    val accessToken: String,
    val platformChannelId: String?,
    val scheduledAt: LocalDateTime?,
)

data class StreamPublishRequest(
    val title: String,
    val description: String?,
    val tags: List<String>,
    val category: String?,
    val thumbnailUrl: String?,
    val platforms: List<PlatformPublishRequest>,
)

data class PlatformPublishRequest(
    val platform: Platform,
    val title: String?,
    val description: String?,
    val tags: List<String>?,
    val visibility: Visibility,
    val scheduledAt: LocalDateTime?,
)

data class StreamPublishResponse(
    val videoId: Long,
)
