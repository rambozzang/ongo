package com.ongo.application.video

import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.util.safeValueOfOrThrow
import com.ongo.domain.video.*
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PublishVideoUseCase(
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
    private val videoPlatformMetaRepository: VideoPlatformMetaRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun publishVideo(userId: Long, videoId: Long, configs: List<PlatformUploadConfig>): PublishResult {
        val video = videoRepository.findById(videoId)
            ?: throw NotFoundException("영상", videoId)

        // 소유자 검증
        if (video.userId != userId) {
            throw ForbiddenException("해당 영상에 대한 접근 권한이 없습니다")
        }

        // DRAFT 상태인지 확인
        if (video.status != UploadStatus.DRAFT) {
            throw IllegalStateException("DRAFT 상태의 영상만 게시할 수 있습니다. 현재 상태: ${video.status}")
        }

        // 파일 업로드 완료 확인
        val fileUrl = video.fileUrl
        if (fileUrl.isNullOrBlank()) {
            throw IllegalStateException("파일 업로드가 완료되지 않은 영상입니다")
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
                fileUrl = fileUrl,
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

        val uploadId = upload.id!!
        val meta = videoPlatformMetaRepository.findByVideoUploadId(uploadId)

        // 재업로드 이벤트 발행
        eventPublisher.publishEvent(
            VideoPublishEvent(
                videoId = videoId,
                userId = userId,
                fileUrl = video.fileUrl!!,
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
