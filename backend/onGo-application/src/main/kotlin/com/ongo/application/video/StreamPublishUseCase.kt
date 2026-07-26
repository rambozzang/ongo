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
import com.ongo.common.util.FileValidationUtil
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
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
import java.net.URI
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

        validateRequest(file, request)

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
            if (channel.status == ChannelStatus.EXPIRED || channel.status == ChannelStatus.REVOKED ||
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

    fun getCapabilities(): List<PlatformUploadCapability> = PlatformUploadCapabilities.all()

    private fun validateRequest(file: MultipartFile, request: StreamPublishRequest) {
        val filename = file.originalFilename?.trim().orEmpty()
        val contentType = file.contentType?.trim().orEmpty()
        FileValidationUtil.validate(filename, contentType, file.size)

        require(request.title.isNotBlank()) { "제목을 입력해주세요." }
        require(request.platforms.isNotEmpty()) { "게시할 플랫폼을 하나 이상 선택해주세요." }

        val duplicates = request.platforms.groupingBy { it.platform }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "같은 플랫폼을 중복 선택할 수 없습니다: ${duplicates.joinToString()}" }

        val extension = filename.substringAfterLast('.', "").lowercase()
        request.platforms.forEach { platformRequest ->
            val capability = PlatformUploadCapabilities.get(platformRequest.platform)
                ?: throw IllegalArgumentException("영상 직접 업로드를 지원하지 않는 플랫폼입니다: ${platformRequest.platform}")
            require(capability.directVideoUpload) {
                capability.unavailableReason ?: "${platformRequest.platform} 영상 직접 업로드는 현재 지원하지 않습니다."
            }
            require(streamWriterFactories.any { it.platform == platformRequest.platform }) {
                "${platformRequest.platform} 업로드 모듈이 활성화되지 않았습니다."
            }
            require(file.size <= capability.maxFileSizeBytes) {
                "${platformRequest.platform} 최대 파일 크기(${capability.maxFileSizeBytes / (1024 * 1024)}MB)를 초과합니다."
            }
            require(extension in capability.acceptedExtensions) {
                "${platformRequest.platform}은(는) .$extension 파일을 지원하지 않습니다. 지원 형식: ${capability.acceptedExtensions.joinToString { ".$it" }}"
            }
            require(platformRequest.scheduledAt == null || capability.scheduling) {
                "${platformRequest.platform}은(는) API 예약 게시를 지원하지 않습니다. 즉시 게시를 선택해주세요."
            }
            require(platformRequest.scheduledAt == null || platformRequest.scheduledAt.isAfter(LocalDateTime.now().plusMinutes(5))) {
                "예약 시간은 현재보다 최소 5분 이후여야 합니다."
            }

            val title = platformRequest.title ?: request.title
            val description = platformRequest.description ?: request.description.orEmpty()
            val tags = platformRequest.tags ?: request.tags
            require(title.isNotBlank()) { "${platformRequest.platform} 제목을 입력해주세요." }
            require(title.length <= capability.maxTitleLength) {
                "${platformRequest.platform} 제목은 ${capability.maxTitleLength}자까지 입력할 수 있습니다."
            }
            if (capability.maxDescriptionLength == 0) {
                // 별도 설명 필드가 없는 플랫폼은 writer가 제목/태그만으로 게시 문구를 구성한다.
            } else {
                require(description.length <= capability.maxDescriptionLength) {
                    "${platformRequest.platform} 설명은 ${capability.maxDescriptionLength}자까지 입력할 수 있습니다."
                }
            }
            require(tags.size <= capability.maxTagCount) {
                "${platformRequest.platform} 태그는 ${capability.maxTagCount}개까지 입력할 수 있습니다."
            }
        }
    }

    private fun runStreamingUpload(
        videoId: Long,
        tempFile: java.io.File,
        fileSize: Long,
        platformContexts: List<StreamPlatformContext>,
    ) {
        val openedWriters = mutableListOf<PlatformStreamWriter>()
        try {
            // 각 플랫폼별 writer 생성
            val writerMap: Map<StreamPlatformContext, PlatformStreamWriter> = platformContexts.mapNotNull { ctx ->
                val factory = streamWriterFactories.find { it.platform == ctx.platform }
                if (factory == null) {
                    log.error("플랫폼 {} 에 대한 StreamWriterFactory를 찾을 수 없습니다", ctx.platform)
                    updateUploadStatus(ctx.videoUploadId, UploadStatus.FAILED, "지원되지 않는 플랫폼: ${ctx.platform}")
                    null
                } else {
                    val writer = factory.createWriter()
                    openedWriters += writer
                    ctx to writer
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
                                // 플랫폼이 게시를 확정했으면 PUBLISHED 로 끝낸다. 확정 신호가 없는
                                // 플랫폼(예: TikTok 은 publish_id 만 주고 비동기 처리)만 PROCESSING 에 남는다.
                                updateUploadStatus(
                                    ctx.videoUploadId,
                                    if (result.published) UploadStatus.PUBLISHED else UploadStatus.PROCESSING,
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
            openedWriters.forEach { writer -> runCatching { writer.abort() } }
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

    /**
     * 예약된 영상을 플랫폼에 업로드합니다.
     * ScheduleUseCase.createSchedule()으로 생성된 SCHEDULED 상태의 예약을 처리합니다.
     */
    @Transactional
    fun executeScheduledUpload(schedule: Schedule) {
        val video = videoRepository.findById(schedule.videoId) ?: run {
            log.error("예약 업로드 실패 — 영상 없음 [scheduleId={}, videoId={}]", schedule.id, schedule.videoId)
            scheduleRepository.update(schedule.copy(status = ScheduleStatus.FAILED))
            return
        }

        val fileUrl = video.fileUrl
        if (fileUrl.isNullOrBlank()) {
            log.error("예약 업로드 실패 — fileUrl 없음 [scheduleId={}, videoId={}]", schedule.id, schedule.videoId)
            scheduleRepository.update(schedule.copy(status = ScheduleStatus.FAILED))
            return
        }

        val fileSize = video.fileSizeBytes ?: run {
            log.error("예약 업로드 실패 — fileSize 없음 [scheduleId={}, videoId={}]", schedule.id, schedule.videoId)
            scheduleRepository.update(schedule.copy(status = ScheduleStatus.FAILED))
            return
        }

        val platforms = schedule.platforms.keys.mapNotNull { platformStr ->
            try { Platform.valueOf(platformStr) } catch (_: Exception) { null }
        }

        if (platforms.isEmpty()) {
            log.error("예약 업로드 실패 — 유효한 플랫폼 없음 [scheduleId={}]", schedule.id)
            scheduleRepository.update(schedule.copy(status = ScheduleStatus.FAILED))
            return
        }

        val platformContexts = platforms.mapNotNull { platform ->
            val channel = channelRepository.findByUserIdAndPlatform(schedule.userId, platform)
            if (channel == null) {
                log.warn("예약 업로드 — 채널 없음 [platform={}, scheduleId={}]", platform, schedule.id)
                return@mapNotNull null
            }
            if (channel.status != ChannelStatus.ACTIVE) {
                log.warn("예약 업로드 — 채널 비활성 [platform={}, scheduleId={}]", platform, schedule.id)
                return@mapNotNull null
            }

            val upload = videoUploadRepository.save(
                VideoUpload(
                    videoId = schedule.videoId,
                    platform = platform,
                    status = UploadStatus.UPLOADING,
                )
            )
            val uploadId = upload.id!!

            val meta = videoPlatformMetaRepository.save(
                VideoPlatformMeta(
                    videoUploadId = uploadId,
                    title = video.title,
                    description = video.description,
                    tags = video.tags,
                    visibility = Visibility.PUBLIC,
                    customThumbnailUrl = video.thumbnailUrls.firstOrNull(),
                )
            )

            StreamPlatformContext(
                platform = platform,
                videoUploadId = uploadId,
                meta = meta,
                accessToken = channel.accessToken,
                platformChannelId = channel.platformChannelId,
                scheduledAt = null,
            )
        }

        if (platformContexts.isEmpty()) {
            log.error("예약 업로드 실패 — 유효한 플랫폼 컨텍스트 없음 [scheduleId={}]", schedule.id)
            scheduleRepository.update(schedule.copy(status = ScheduleStatus.FAILED))
            return
        }

        scheduleRepository.update(schedule.copy(status = ScheduleStatus.PROCESSING))

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                Thread.ofVirtual().name("scheduled-publish-${schedule.id}").start {
                    ExecutorConfig.uploadSemaphore.acquire()
                    try {
                        downloadAndStreamUpload(schedule.videoId, fileUrl, fileSize, platformContexts)
                    } finally {
                        ExecutorConfig.uploadSemaphore.release()
                    }
                }
            }
        })
    }

    private fun downloadAndStreamUpload(
        videoId: Long,
        fileUrl: String,
        fileSize: Long,
        platformContexts: List<StreamPlatformContext>,
    ) {
        val tempFile = Files.createTempFile("ongo-scheduled-", "-upload").toFile()
        try {
            val url = URI.create(fileUrl).toURL()
            url.openStream().use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            log.info("예약 업로드 파일 다운로드 완료 [videoId={}, size={}]", videoId, fileSize)
            runStreamingUpload(videoId, tempFile, fileSize, platformContexts)
        } catch (e: Exception) {
            log.error("예약 업로드 실패 [videoId={}]: {}", videoId, e.message, e)
            platformContexts.forEach { ctx ->
                updateUploadStatus(ctx.videoUploadId, UploadStatus.FAILED, "예약 업로드 실패: ${e.message}")
            }
            updateOverallVideoStatus(videoId)
        } finally {
            try {
                Files.deleteIfExists(tempFile.toPath())
            } catch (e: Exception) {
                log.warn("임시 파일 삭제 실패: {}", tempFile.absolutePath, e)
            }
        }
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
