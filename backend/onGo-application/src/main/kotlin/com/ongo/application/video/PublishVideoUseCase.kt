package com.ongo.application.video

import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.Platform
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.util.safeValueOfOrThrow
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.video.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class PublishVideoUseCase(
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val videoPlatformMetaRepository: VideoPlatformMetaRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val channelRepository: ChannelRepository,
    private val videoUploadPoller: VideoUploadPoller,
    private val userWriteGuard: UserWriteGuard,
    private val scheduleRepository: ScheduleRepository,
) {

    @Transactional
    fun publishVideo(userId: Long, videoId: Long, configs: List<PlatformUploadConfig>): PublishResult {
        userWriteGuard.requireWritable(userId)
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        // 소유자 검증
        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }

        require(configs.isNotEmpty()) { "게시할 플랫폼을 하나 이상 선택해주세요." }
        val duplicateTargets = configs.groupingBy { it.channelId ?: it.platform }.eachCount().filterValues { it > 1 }.keys
        require(duplicateTargets.isEmpty()) { "같은 게시 계정을 중복 선택할 수 없습니다: ${duplicateTargets.joinToString()}" }

        require(video.fileUrl != null) { "업로드가 완료된 미디어 파일을 찾을 수 없습니다." }
        configs.forEach { config ->
            val capability = PlatformUploadCapabilities.get(config.platform)
                ?: throw IllegalArgumentException("${video.mediaType.name.lowercase()} 게시를 지원하지 않는 플랫폼입니다: ${config.platform}")
            require(video.mediaType in capability.acceptedMediaTypes) {
                "${config.platform}은(는) ${video.mediaType.name.lowercase()} 게시를 지원하지 않습니다."
            }
            require(capability.directVideoUpload || capability.cloudVideoUpload) {
                capability.unavailableReason ?: "${config.platform} ${video.mediaType.name.lowercase()} 게시를 지원하지 않습니다."
            }
            // 예약은 플랫폼 native scheduler가 아니라 onGo durable queue가 처리한다.
            // native scheduling=false인 채널도 같은 시각에 게시할 수 있다.
            require(config.title.length <= capability.maxTitleLength) {
                "${config.platform} 제목은 ${capability.maxTitleLength}자까지 입력할 수 있습니다."
            }
            require(config.title.isNotBlank()) {
                "${config.platform} 제목을 입력해주세요."
            }
            val description = config.description.orEmpty()
            if (capability.maxDescriptionLength > 0) {
                require(description.length <= capability.maxDescriptionLength) {
                    "${config.platform} 설명은 ${capability.maxDescriptionLength}자까지 입력할 수 있습니다."
                }
            }
            require(config.tags.size <= capability.maxTagCount) {
                "${config.platform} 태그는 ${capability.maxTagCount}개까지 입력할 수 있습니다."
            }
            capability.maxCaptionLength?.let { limit ->
                val caption = PlatformCaptionRules.compose(
                    config.platform,
                    config.title,
                    description,
                    config.tags,
                ).orEmpty()
                require(caption.length <= limit) {
                    "${config.platform} 게시 문구는 ${limit}자까지 입력할 수 있습니다."
                }
            }
            require(config.scheduledAt == null || config.scheduledAt.isAfter(LocalDateTime.now().plusMinutes(5))) {
                "예약 시간은 현재보다 최소 5분 이후여야 합니다."
            }
        }

        // DRAFT 상태인지 확인
        if (video.status != UploadStatus.DRAFT) {
            throw IllegalStateException("DRAFT 상태의 영상만 게시할 수 있습니다. 현재 상태: ${video.status}")
        }

        // 게시 전 채널 토큰 검증
        configs.forEach { config ->
            val channel = if (config.channelId != null) {
                channelRepository.findById(config.channelId)
                    ?.takeIf { it.userId == userId && it.platform == config.platform }
            } else {
                channelRepository.findByUserIdAndPlatform(userId, config.platform)
            } ?: throw NotFoundException("채널", "${config.channelId ?: config.platform}")

            val tokenExpiresAt = channel.tokenExpiresAt
            if (channel.status == ChannelStatus.EXPIRED || channel.status == ChannelStatus.REVOKED ||
                (tokenExpiresAt != null && tokenExpiresAt.isBefore(LocalDateTime.now()))) {
                throw IllegalStateException(
                    "${config.platform.name} 채널의 인증 토큰이 만료되었습니다. 채널 관리에서 재연결 후 다시 시도해주세요."
                )
            }
        }

        // A public/API request identifies the exact connected account. Check its
        // durable row before claiming the source video; otherwise a duplicate
        // request can strand the video in UPLOADING before PostgreSQL rejects
        // the unique (video_id, channel_id) target.
        configs.filter { it.channelId != null }.forEach { config ->
            val existing = videoUploadRepository.findByVideoIdAndChannelId(videoId, config.channelId!!)
            require(existing == null || existing.status == UploadStatus.CANCELLED || existing.status == UploadStatus.DRAFT) {
                "${config.platform.name} 채널에는 이미 게시 작업이 있습니다. 기존 게시 결과를 확인하거나 새 영상을 사용해주세요."
            }
        }

        // The status read above is only advisory. Reserve the row atomically so
        // concurrent clicks cannot both create durable platform upload rows.
        // Validation happens first so a rejected request never strands the video
        // in UPLOADING without any durable platform rows.
        if (!videoRepository.claimForPublish(userId, videoId)) {
            throw IllegalStateException("이미 게시 준비 중인 영상입니다. 현재 게시 상태를 확인해주세요.")
        }

        // 각 플랫폼별 VideoUpload + VideoPlatformMeta 생성
        val platformConfigs = configs.map { config ->
            val existing = if (config.channelId != null) {
                videoUploadRepository.findByVideoIdAndChannelId(videoId, config.channelId)
            } else {
                videoUploadRepository.findByVideoIdAndPlatform(videoId, config.platform)
            }
            val upload = if (existing?.status == UploadStatus.CANCELLED || existing?.status == UploadStatus.DRAFT) {
                // 저장된 초안/취소된 예약을 다시 게시할 때 unique(video_id,
                // platform) 제약을 위반해 새 row를 만들지 않고 같은 durable
                // 작업을 재사용한다.
                videoUploadRepository.update(
                    existing.copy(
                        status = UploadStatus.UPLOADING,
                        platformVideoId = null,
                        platformUrl = null,
                        errorMessage = null,
                        attemptCount = 0,
                        nextRetryAt = null,
                        leaseOwner = null,
                        leaseUntil = null,
                        pollToken = null,
                        lastError = null,
                        scheduledAt = config.scheduledAt,
                        publishedAt = null,
                    )
                )
            } else {
                videoUploadRepository.save(
                    VideoUpload(
                        videoId = videoId,
                        platform = config.platform,
                        channelId = config.channelId,
                        status = UploadStatus.UPLOADING,
                        scheduledAt = config.scheduledAt,
                    )
                )
            }
            val uploadId = upload.id!!

            videoPlatformMetaRepository.save(
                VideoPlatformMeta(
                    videoUploadId = uploadId,
                    title = config.title.ifBlank { video.title },
                    description = config.description,
                    tags = config.tags,
                        visibility = config.visibility,
                        customThumbnailUrl = config.thumbnailUrl,
                        customSettingsJson = config.customSettingsJson,
                )
            )

            config.copy(videoUploadId = uploadId, channelId = upload.channelId ?: config.channelId)
        }

        val scheduledConfigs = configs.filter { it.scheduledAt != null }
        if (scheduledConfigs.isNotEmpty()) {
            val earliest = scheduledConfigs.minOf { it.scheduledAt!! }
            scheduleRepository.save(
                Schedule(
                    videoId = videoId,
                    userId = userId,
                    scheduledAt = earliest,
                    status = com.ongo.common.enums.ScheduleStatus.SCHEDULED,
                    platforms = scheduledConfigs.associate { config ->
                        val key = if (config.channelId == null) config.platform.name
                        else "${config.platform.name}#${config.channelId}"
                        key to mapOf("scheduledAt" to config.scheduledAt.toString())
                    },
                )
            )
        }

        // VideoPublishEvent 발행 (비동기 처리)
        eventPublisher.publishEvent(
            VideoPublishEvent(
                videoId = videoId,
                userId = userId,
                fileUrl = video.fileUrl,
                platformConfigs = platformConfigs,
            )
        )

        return PublishResult(
            videoId = videoId,
            uploads = platformConfigs.map { config ->
                PlatformUploadStatus(
                    platform = config.platform,
                    status = UploadStatus.UPLOADING,
                )
            },
        )
    }

    @Transactional
    fun retryUpload(userId: Long, videoId: Long, platformName: String) {
        userWriteGuard.requireWritable(userId)
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }

        val platform = safeValueOfOrThrow<com.ongo.common.enums.Platform>(platformName)
        val upload = videoUploadRepository.findByVideoIdAndPlatform(videoId, platform)
            ?: throw NotFoundException("업로드 기록", "$videoId/$platformName")

        retryUploadRow(userId, video, upload, platform)
    }

    /** Retry a specific upload row so two accounts on the same platform never collide. */
    fun retryUpload(userId: Long, videoId: Long, uploadId: Long) {
        userWriteGuard.requireWritable(userId)
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)
        if (video.userId != userId) throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        val upload = videoUploadRepository.findById(uploadId)
            ?.takeIf { it.videoId == videoId }
            ?: throw NotFoundException("업로드 기록", "$videoId/$uploadId")
        retryUploadRow(userId, video, upload, upload.platform)
    }

    private fun retryUploadRow(userId: Long, video: Video, upload: VideoUpload, platform: Platform) {

        if (upload.status != UploadStatus.FAILED && upload.status != UploadStatus.REJECTED) {
            throw IllegalStateException("실패/반려 상태만 재전송할 수 있습니다. 게시 결과 확인 필요 상태는 재확인을 사용하세요. 현재 상태: ${upload.status}")
        }

        // video.fileUrl이 null이면 스트리밍 업로드로 재시도 불가
        val fileUrl = video.fileUrl ?: throw IllegalStateException("스트리밍 방식으로 업로드된 영상은 재시도를 지원하지 않습니다. 새로 업로드해주세요.")

        // Read-then-update allows two retry clicks to enqueue two external calls.
        // Reserve the failed row atomically before creating the event.
        if (!videoUploadRepository.claimForRetry(upload.id!!)) {
            throw IllegalStateException("이미 재게시 준비 중인 업로드입니다. 현재 게시 상태를 확인해주세요.")
        }

        val uploadId = upload.id!!
        val meta = videoPlatformMetaRepository.findByVideoUploadId(uploadId)

        // 재업로드 이벤트 발행
        eventPublisher.publishEvent(
            VideoPublishEvent(
                videoId = video.id!!,
                userId = userId,
                fileUrl = fileUrl,
                platformConfigs = listOf(
                    PlatformUploadConfig(
                        platform = platform,
                        videoUploadId = uploadId,
                        title = meta?.title ?: video.title,
                        description = meta?.description,
                        tags = meta?.tags ?: emptyList(),
                        visibility = meta?.visibility ?: com.ongo.common.enums.Visibility.PUBLIC,
                        thumbnailUrl = meta?.customThumbnailUrl,
                        customSettingsJson = meta?.customSettingsJson,
                        scheduledAt = null,
                        mediaType = video.mediaType,
                    )
                ),
            )
        )
    }

    /** 확인 불가 작업에 대해 외부 상태 조회만 실행한다. 새 게시 요청은 만들지 않는다. */
    fun recheckUpload(userId: Long, videoId: Long, platformName: String) {
        val platform = safeValueOfOrThrow<com.ongo.common.enums.Platform>(platformName)
        videoUploadPoller.recheck(userId, videoId, platform)
    }

    fun recheckUpload(userId: Long, videoId: Long, uploadId: Long) {
        videoUploadPoller.recheckUpload(userId, videoId, uploadId)
    }
}

data class PublishResult(
    val videoId: Long,
    val uploads: List<PlatformUploadStatus>,
)

data class PlatformUploadStatus(
    val platform: com.ongo.common.enums.Platform,
    val status: UploadStatus,
    val errorMessage: String? = null,
)
