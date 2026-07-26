package com.ongo.application.video

import com.ongo.common.enums.UploadStatus
import com.ongo.common.enums.MediaType
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.util.safeValueOfOrThrow
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.ChannelStatus
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
) {

    @Transactional
    fun publishVideo(userId: Long, videoId: Long, configs: List<PlatformUploadConfig>): PublishResult {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        // 소유자 검증
        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }

        require(configs.isNotEmpty()) { "게시할 플랫폼을 하나 이상 선택해주세요." }
        val duplicatePlatforms = configs.groupingBy { it.platform }.eachCount().filterValues { it > 1 }.keys
        require(duplicatePlatforms.isEmpty()) { "같은 플랫폼을 중복 선택할 수 없습니다: ${duplicatePlatforms.joinToString()}" }

        if (video.mediaType == MediaType.VIDEO) {
            require(video.fileUrl != null) { "업로드가 완료된 영상 파일을 찾을 수 없습니다." }
            configs.forEach { config ->
                val capability = PlatformUploadCapabilities.get(config.platform)
                    ?: throw IllegalArgumentException("영상 게시를 지원하지 않는 플랫폼입니다: ${config.platform}")
                require(capability.directVideoUpload || capability.cloudVideoUpload) {
                    capability.unavailableReason ?: "${config.platform} 영상 게시를 지원하지 않습니다."
                }
                require(config.scheduledAt == null || capability.scheduling) {
                    "${config.platform}은(는) API 예약 게시를 지원하지 않습니다."
                }
                require(config.title.length <= capability.maxTitleLength) {
                    "${config.platform} 제목은 ${capability.maxTitleLength}자까지 입력할 수 있습니다."
                }
                require(config.tags.size <= capability.maxTagCount) {
                    "${config.platform} 태그는 ${capability.maxTagCount}개까지 입력할 수 있습니다."
                }
            }
        }

        // DRAFT 상태인지 확인
        if (video.status != UploadStatus.DRAFT) {
            throw IllegalStateException("DRAFT 상태의 영상만 게시할 수 있습니다. 현재 상태: ${video.status}")
        }

        // 게시 전 채널 토큰 검증
        configs.forEach { config ->
            val channel = channelRepository.findByUserIdAndPlatform(userId, config.platform)
                ?: throw NotFoundException("채널", "${config.platform}")

            val tokenExpiresAt = channel.tokenExpiresAt
            if (channel.status == ChannelStatus.EXPIRED || channel.status == ChannelStatus.REVOKED ||
                (tokenExpiresAt != null && tokenExpiresAt.isBefore(LocalDateTime.now()))) {
                throw IllegalStateException(
                    "${config.platform.name} 채널의 인증 토큰이 만료되었습니다. 채널 관리에서 재연결 후 다시 시도해주세요."
                )
            }
        }

        // 영상 상태를 UPLOADING으로 변경
        videoRepository.update(video.copy(status = UploadStatus.UPLOADING))

        // 각 플랫폼별 VideoUpload + VideoPlatformMeta 생성
        val platformConfigs = configs.map { config ->
            val upload = videoUploadRepository.save(
                VideoUpload(
                    videoId = videoId,
                    platform = config.platform,
                    status = UploadStatus.UPLOADING,
                )
            )
            val uploadId = upload.id!!

            videoPlatformMetaRepository.save(
                VideoPlatformMeta(
                    videoUploadId = uploadId,
                    title = config.title.ifBlank { video.title },
                    description = config.description,
                    tags = config.tags,
                    visibility = config.visibility,
                    customThumbnailUrl = config.thumbnailUrl,
                )
            )

            config.copy(videoUploadId = uploadId)
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
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }

        val platform = safeValueOfOrThrow<com.ongo.common.enums.Platform>(platformName)
        val upload = videoUploadRepository.findByVideoIdAndPlatform(videoId, platform)
            ?: throw NotFoundException("업로드 기록", "$videoId/$platformName")

        if (upload.status != UploadStatus.FAILED && upload.status != UploadStatus.REJECTED) {
            throw IllegalStateException("실패/반려 상태의 업로드만 재시도할 수 있습니다. 현재 상태: ${upload.status}")
        }

        // 상태를 UPLOADING으로 리셋
        videoUploadRepository.update(
            upload.copy(
                status = UploadStatus.UPLOADING,
                errorMessage = null,
            )
        )

        // video.fileUrl이 null이면 스트리밍 업로드로 재시도 불가
        val fileUrl = video.fileUrl ?: throw IllegalStateException("스트리밍 방식으로 업로드된 영상은 재시도를 지원하지 않습니다. 새로 업로드해주세요.")

        val uploadId = upload.id!!
        val meta = videoPlatformMetaRepository.findByVideoUploadId(uploadId)

        // 재업로드 이벤트 발행
        eventPublisher.publishEvent(
            VideoPublishEvent(
                videoId = videoId,
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
                        scheduledAt = null,
                    )
                ),
            )
        )
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
