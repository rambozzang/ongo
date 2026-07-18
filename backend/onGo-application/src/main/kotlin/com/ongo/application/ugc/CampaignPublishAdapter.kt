package com.ongo.application.ugc

import com.ongo.application.video.PlatformUploadConfig
import com.ongo.application.video.PublishVideoUseCase
import com.ongo.common.enums.Platform
import com.ongo.common.enums.Visibility
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.ugc.publishing.CampaignPublishPort
import com.ongo.domain.ugc.publishing.PlatformPublishOutcome
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import org.springframework.stereotype.Service

/**
 * 캠페인 게시를 기존 멀티 SNS 게시 흐름([PublishVideoUseCase])에 위임하는 어댑터.
 * 게시는 크리에이터 소유 영상·채널로 실행되며, 캠페인 post 연결을 위해 플랫폼별 videoUploadId를 채워 반환한다.
 */
@Service
class CampaignPublishAdapter(
    private val publishVideoUseCase: PublishVideoUseCase,
    private val videoRepository: VideoRepository,
    private val videoUploadRepository: VideoUploadRepository,
) : CampaignPublishPort {

    override fun publish(creatorId: Long, videoId: Long, platforms: List<String>): List<PlatformPublishOutcome> {
        val video = videoRepository.findById(videoId) ?: throw NotFoundException("영상", videoId)

        val configs = platforms.map { platformName ->
            PlatformUploadConfig(
                platform = Platform.valueOf(platformName),
                videoUploadId = 0,
                title = video.title,
                description = null,
                tags = emptyList(),
                visibility = Visibility.PUBLIC,
                thumbnailUrl = null,
                scheduledAt = null,
            )
        }

        val result = publishVideoUseCase.publishVideo(creatorId, videoId, configs)

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
