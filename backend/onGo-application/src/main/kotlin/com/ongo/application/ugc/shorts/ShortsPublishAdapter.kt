package com.ongo.application.ugc.shorts

import com.ongo.application.video.PlatformUploadConfig
import com.ongo.application.video.PublishVideoUseCase
import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.publishing.PlatformPublishOutcome
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** 렌더된 쇼츠 영상을 기존 멀티 플랫폼 게시 흐름에 위임하는 어댑터. */
@Service
class ShortsPublishAdapter(
    private val publishVideoUseCase: PublishVideoUseCase,
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
) {

    fun publish(
        userId: Long,
        videoId: Long,
        platformName: String,
        title: String?,
        caption: String?,
        scheduledAt: Instant,
    ): PlatformPublishOutcome = publishAll(
        userId = userId,
        videoId = videoId,
        requests = listOf(ShortsPublishRequest(platformName, title, caption, scheduledAt)),
    ).single()

    fun publishAll(
        userId: Long,
        videoId: Long,
        requests: List<ShortsPublishRequest>,
    ): List<PlatformPublishOutcome> {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)
        require(requests.isNotEmpty()) { "게시할 플랫폼을 하나 이상 선택해주세요." }

        val configs = requests.map { request ->
            val platform = runCatching { Platform.valueOf(request.platformName) }
                .getOrElse { throw IllegalArgumentException("지원하지 않는 플랫폼입니다: ${request.platformName}") }
            PlatformUploadConfig(
                platform = platform,
                videoUploadId = 0,
                title = request.title?.ifBlank { video.title } ?: video.title,
                description = request.caption,
                tags = emptyList(),
                visibility = Visibility.PUBLIC,
                thumbnailUrl = null,
                scheduledAt = LocalDateTime.ofInstant(request.scheduledAt, ZoneOffset.UTC),
            )
        }
        val result = publishVideoUseCase.publishVideo(userId, videoId, configs)
        return result.uploads.map { uploadStatus ->
            val upload = videoUploadRepository.findByVideoIdAndPlatform(videoId, uploadStatus.platform)
            PlatformPublishOutcome(
                platform = uploadStatus.platform.name,
                videoUploadId = upload?.id,
                status = uploadStatus.status.name,
                errorMessage = uploadStatus.errorMessage,
            )
        }
    }
}

data class ShortsPublishRequest(
    val platformName: String,
    val title: String?,
    val caption: String?,
    val scheduledAt: Instant,
)
