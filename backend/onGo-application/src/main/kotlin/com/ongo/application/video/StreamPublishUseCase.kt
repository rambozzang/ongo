package com.ongo.application.video

import com.ongo.application.config.ExecutorConfig
import com.ongo.common.enums.MediaType
import com.ongo.common.enums.Platform
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Visibility
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.common.util.FileValidationUtil
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.TokenEncryptionPort
import org.springframework.context.ApplicationEventPublisher
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
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class StreamPublishUseCase(
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val videoPlatformMetaRepository: VideoPlatformMetaRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val channelRepository: ChannelRepository,
    private val tokenEncryptionPort: TokenEncryptionPort,
    private val streamWriterFactories: List<PlatformStreamWriterFactory>,
    private val scheduleRepository: ScheduleRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val storageService: StorageService,
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
        // 즉시 게시만 로컬 임시 파일로 흘려보낸다. 예약 게시도 프로세스가 재시작되어도
        // 살아 있어야 하므로 영구 오브젝트 URL을 먼저 확보한다.

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
                    scheduledAt = platformReq.scheduledAt,
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
                accessToken = tokenEncryptionPort.decrypt(channel.accessToken),
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
                    status = ScheduleStatus.SCHEDULED,
                    platforms = platformMap,
                )
            )
            log.info("예약 게시 스케줄 생성: videoId={}, scheduledAt={}", videoId, earliestScheduledAt)
        }

        // 5. 예약 게시가 하나라도 있으면 durable object URL을 저장한다. 이 URL은
        // 프로세스 재시작/다중 인스턴스에서도 dispatcher가 다시 읽을 수 있는 유일한
        // 입력이다. 즉시 게시에는 저장 비용을 만들지 않는다.
        val durableFileUrl = if (hasSchedule) {
            val safeFilename = file.originalFilename.orEmpty()
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .takeLast(180)
                .ifBlank { "upload.mp4" }
            try {
                file.inputStream.use { input ->
                    storageService.uploadFile(
                        key = "videos/$videoId/$safeFilename",
                        inputStream = input,
                        contentType = file.contentType ?: "video/mp4",
                        size = fileSize,
                    ).also { url ->
                        videoRepository.update(video.copy(fileUrl = url))
                    }
                }
            } catch (e: Exception) {
                runCatching { storageService.deleteFile(videoId) }
                throw e
            }
        } else {
            null
        }

        // 6. 트랜잭션 커밋 후 게시를 시작한다. 예약은 durable event/dispatcher가
        // 담당하고, 즉시는 기존의 메모리 효율적인 streaming writer를 사용한다.
        // (afterCommit: DB 레코드가 확실히 커밋된 후 읽기 가능)
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                if (hasSchedule) {
                    eventPublisher.publishEvent(
                        VideoPublishEvent(
                            videoId = videoId,
                            userId = userId,
                            fileUrl = durableFileUrl,
                            platformConfigs = platformConfigs.map { ctx ->
                                PlatformUploadConfig(
                                    platform = ctx.platform,
                                    videoUploadId = ctx.videoUploadId,
                                    title = ctx.meta.title ?: "",
                                    description = ctx.meta.description,
                                    tags = ctx.meta.tags,
                                    visibility = ctx.meta.visibility,
                                    thumbnailUrl = ctx.meta.customThumbnailUrl,
                                    fileSize = fileSize,
                                    scheduledAt = ctx.scheduledAt,
                                )
                            },
                        )
                    )
                } else {
                    val tempFile = runCatching {
                        Files.createTempFile("ongo-stream-", "-${file.originalFilename ?: "upload"}").also { path ->
                            try {
                                file.transferTo(path.toFile())
                            } catch (error: Exception) {
                                Files.deleteIfExists(path)
                                throw error
                            }
                        }
                    }.getOrElse { error ->
                        platformConfigs.forEach { ctx ->
                            updateUploadStatus(ctx.videoUploadId, UploadStatus.FAILED, "임시 파일 저장 실패: ${error.message}")
                        }
                        updateOverallVideoStatus(videoId)
                        return
                    }
                    Thread.ofVirtual().name("stream-publish-$videoId").start {
                        ExecutorConfig.uploadSemaphore.acquire()
                        try {
                            runStreamingUpload(videoId, userId, tempFile.toFile(), fileSize, platformConfigs)
                        } finally {
                            ExecutorConfig.uploadSemaphore.release()
                        }
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
            require(platformRequest.scheduledAt == null || capability.cloudVideoUpload) {
                "${platformRequest.platform}은(는) durable 예약 게시에 필요한 클라우드 업로드를 지원하지 않습니다."
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
        userId: Long,
        tempFile: java.io.File,
        fileSize: Long,
        platformContexts: List<StreamPlatformContext>,
    ) {
        val openedWriters = mutableListOf<PlatformStreamWriter>()
        val leaseOwners = ConcurrentHashMap<Long, String>()
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
                        val owner = "stream:$videoId:${ctx.platform}:${UUID.randomUUID()}"
                        val claimed = videoUploadRepository.claim(
                            id = ctx.videoUploadId,
                            owner = owner,
                            now = LocalDateTime.now(),
                            leaseUntil = LocalDateTime.now().plusMinutes(30),
                        ) ?: throw IllegalStateException("업로드 lease를 획득하지 못했습니다.")
                        leaseOwners[ctx.videoUploadId] = owner
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
                        updateUploadStatus(
                            ctx.videoUploadId,
                            UploadStatus.FAILED,
                            "세션 초기화 실패: ${e.cause?.message ?: e.message}",
                            leaseOwner = leaseOwners[ctx.videoUploadId],
                        )
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
                            val owner = leaseOwners[ctx.videoUploadId]
                            when (val outcome = result.toPublishOutcome()) {
                                is PublishOutcome.Published -> {
                                    updateUploadStatus(
                                        ctx.videoUploadId,
                                        UploadStatus.PUBLISHED,
                                        platformVideoId = outcome.platformVideoId,
                                        platformUrl = outcome.platformUrl,
                                        leaseOwner = owner,
                                    )
                                    log.info("플랫폼 {} 업로드 완료: videoId={}, platformUrl={}", ctx.platform, videoId, outcome.platformUrl)
                                    fireCompletedEvent(videoId, userId, ctx.platform, true, platformUrl = outcome.platformUrl)
                                }
                                is PublishOutcome.Accepted -> {
                                    updateUploadStatus(
                                        ctx.videoUploadId,
                                        UploadStatus.PROCESSING,
                                        platformVideoId = outcome.platformVideoId,
                                        platformUrl = result.platformUrl,
                                        pollToken = outcome.pollToken,
                                        nextRetryAt = LocalDateTime.now().plus(outcome.retryAfter),
                                        leaseOwner = owner,
                                    )
                                    log.info("플랫폼 {} 업로드 수락: videoId={}, pollToken={}", ctx.platform, videoId, outcome.pollToken)
                                    fireCompletedEvent(videoId, userId, ctx.platform, true, platformUrl = result.platformUrl)
                                }
                                is PublishOutcome.Failed -> {
                                    updateUploadStatus(
                                        ctx.videoUploadId,
                                        UploadStatus.FAILED,
                                        outcome.message,
                                        leaseOwner = owner,
                                    )
                                    log.warn("플랫폼 {} 업로드 실패: videoId={}, error={}", ctx.platform, videoId, outcome.message)
                                    fireCompletedEvent(videoId, userId, ctx.platform, false, errorMessage = outcome.message)
                                }
                            }
                        } catch (e: Exception) {
                            log.error("플랫폼 {} 업로드 완료 처리 중 예외: videoId={}", ctx.platform, videoId, e)
                            updateUploadStatus(
                                ctx.videoUploadId,
                                UploadStatus.UNCONFIRMED,
                                e.message,
                                leaseOwner = leaseOwners[ctx.videoUploadId],
                            )
                            fireCompletedEvent(videoId, userId, ctx.platform, false, errorMessage = e.message)
                        }
                    }
                }
                completeFutures.forEach { it.get() }
            }
        } catch (e: Exception) {
            log.error("스트리밍 업로드 전체 실패: videoId={}", videoId, e)
            platformContexts.forEach { ctx ->
                updateUploadStatus(
                    ctx.videoUploadId,
                    UploadStatus.UNCONFIRMED,
                    "게시 결과 확인 필요: ${e.message}",
                    leaseOwner = leaseOwners[ctx.videoUploadId],
                )
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

    /**
     * 플랫폼별 게시 결과를 알린다(알림 저장 + WebSocket 푸시).
     *
     * 이 경로는 사용자가 게시 버튼을 누른 직후 화면을 떠나기 때문에, 이벤트를 쏘지 않으면
     * 4개 중 1개가 실패해도 사용자가 알 방법이 없다. 실제로 이벤트를 발행하는 곳이
     * VideoPublishEventListener 한 곳뿐이라 스트리밍 경로는 결과 알림이 전혀 없었다.
     *
     * 알림 실패가 게시 자체를 되돌리게 두지 않는다.
     */
    private fun fireCompletedEvent(
        videoId: Long,
        userId: Long,
        platform: Platform,
        success: Boolean,
        platformUrl: String? = null,
        errorMessage: String? = null,
    ) {
        try {
            eventPublisher.publishEvent(
                UploadCompletedEvent(
                    videoId = videoId,
                    userId = userId,
                    platform = platform,
                    success = success,
                    platformUrl = platformUrl,
                    errorMessage = errorMessage,
                )
            )
        } catch (e: Exception) {
            log.error("게시 결과 알림 발행 실패 [videoId={}, platform={}]", videoId, platform, e)
        }
    }

    private fun updateUploadStatus(
        uploadId: Long,
        status: UploadStatus,
        errorMessage: String? = null,
        platformVideoId: String? = null,
        platformUrl: String? = null,
        pollToken: String? = null,
        nextRetryAt: LocalDateTime? = null,
        leaseOwner: String? = null,
    ) {
        val upload = videoUploadRepository.findById(uploadId) ?: return
        val updated = upload.copy(
            status = status,
            errorMessage = errorMessage,
            platformVideoId = platformVideoId ?: upload.platformVideoId,
            platformUrl = platformUrl ?: upload.platformUrl,
            pollToken = pollToken ?: upload.pollToken,
            nextRetryAt = when {
                nextRetryAt != null -> nextRetryAt
                status in setOf(UploadStatus.PUBLISHED, UploadStatus.FAILED, UploadStatus.REJECTED) -> null
                else -> upload.nextRetryAt
            },
            lastError = errorMessage,
            leaseOwner = null,
            leaseUntil = null,
            publishedAt = if (status == UploadStatus.PUBLISHED) LocalDateTime.now() else upload.publishedAt,
        )
        if (leaseOwner == null) {
            videoUploadRepository.update(updated)
        } else if (!videoUploadRepository.updateOwned(updated, leaseOwner)) {
            log.warn("lease를 잃은 스트리밍 작업자의 결과 반영을 차단했습니다: uploadId={}, owner={}", uploadId, leaseOwner)
        }
    }

    private fun updateOverallVideoStatus(videoId: Long) {
        val uploads = videoUploadRepository.findByVideoId(videoId)
        if (uploads.isEmpty()) return

        val video = videoRepository.findById(videoId) ?: return

        val overallStatus = when {
            uploads.all { it.status == UploadStatus.PUBLISHED } -> UploadStatus.PUBLISHED
            uploads.any { it.status == UploadStatus.PUBLISHED } && uploads.any {
                it.status == UploadStatus.FAILED || it.status == UploadStatus.REJECTED ||
                    it.status == UploadStatus.UNCONFIRMED
            } -> UploadStatus.PARTIALLY_PUBLISHED
            uploads.all { it.status == UploadStatus.FAILED || it.status == UploadStatus.REJECTED } -> UploadStatus.FAILED
            uploads.all { it.status == UploadStatus.UNCONFIRMED } -> UploadStatus.UNCONFIRMED
            uploads.any { it.status == UploadStatus.PROCESSING || it.status == UploadStatus.REVIEW } -> UploadStatus.PROCESSING
            else -> UploadStatus.UPLOADING
        }

        videoRepository.update(video.copy(status = overallStatus))
    }

    /**
     * 레거시 Schedule 레코드(업로드 row가 없는 예약)를 durable queue로 복구한다.
     *
     * 실제 외부 호출은 여기서 하지 않는다. 이 메서드는 VideoUpload row와
     * VideoPublishEvent만 만들고, lease/재시도/확인 불가 처리는 공통 listener가 맡는다.
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

        val fileSize = video.fileSizeBytes ?: 0L

        val platforms = schedule.platforms.keys.mapNotNull { platformStr ->
            try { Platform.valueOf(platformStr) } catch (_: Exception) { null }
        }

        if (platforms.isEmpty()) {
            log.error("예약 업로드 실패 — 유효한 플랫폼 없음 [scheduleId={}]", schedule.id)
            scheduleRepository.update(schedule.copy(status = ScheduleStatus.FAILED))
            return
        }

        val platformConfigs = platforms.mapNotNull { platform ->
            val existing = videoUploadRepository.findByVideoIdAndPlatform(schedule.videoId, platform)
            if (existing != null && existing.status !in setOf(UploadStatus.FAILED, UploadStatus.REJECTED, UploadStatus.UNCONFIRMED)) {
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

            PlatformUploadConfig(
                platform = platform,
                videoUploadId = uploadId,
                title = meta.title ?: "",
                description = meta.description,
                tags = meta.tags,
                visibility = meta.visibility,
                thumbnailUrl = meta.customThumbnailUrl,
                fileSize = fileSize,
                scheduledAt = null,
            )
        }

        if (platformConfigs.isEmpty()) {
            log.info("예약 업로드가 이미 큐에 있거나 완료됨 [scheduleId={}]", schedule.id)
            return
        }

        scheduleRepository.update(schedule.copy(status = ScheduleStatus.PROCESSING))

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                eventPublisher.publishEvent(
                    VideoPublishEvent(
                        videoId = schedule.videoId,
                        userId = video.userId,
                        fileUrl = fileUrl,
                        platformConfigs = platformConfigs,
                    )
                )
            }
        })
    }
}

/** 스트리밍 업로드 중 플랫폼별 컨텍스트 (트랜잭션 외부에서 사용) */
private data class StreamPlatformContext(
    val platform: Platform,
    val videoUploadId: Long,
    val meta: VideoPlatformMeta,
    val accessToken: com.ongo.domain.channel.PlainToken,
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
