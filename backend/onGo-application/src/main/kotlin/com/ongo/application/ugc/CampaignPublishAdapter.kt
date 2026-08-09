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

        val targets = platforms.map { parseCampaignPublishTarget(it) }
        val configs = targets.map { target ->
            PlatformUploadConfig(
                platform = target.platform,
                channelId = target.channelId,
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

        return result.uploads.mapIndexed { index, uploadStatus ->
            val target = targets[index]
            val upload = if (target.channelId != null) {
                videoUploadRepository.findByVideoIdAndChannelId(videoId, target.channelId)
            } else {
                videoUploadRepository.findByVideoIdAndPlatform(videoId, uploadStatus.platform)
            }
            PlatformPublishOutcome(
                platform = target.rawKey,
                videoUploadId = upload?.id,
                status = uploadStatus.status.name,
                errorMessage = uploadStatus.errorMessage,
            )
        }
    }
}

private data class CampaignPublishTarget(
    val rawKey: String,
    val platform: Platform,
    val channelId: Long?,
)

private fun parseCampaignPublishTarget(rawKey: String): CampaignPublishTarget {
    val parts = rawKey.split('#', limit = 2)
    val platform = runCatching { Platform.valueOf(parts[0].trim().uppercase()) }
        .getOrElse { throw IllegalArgumentException("지원하지 않는 플랫폼입니다: $rawKey") }
    val channelId = parts.getOrNull(1)?.trim()?.let {
        it.toLongOrNull()?.takeIf { id -> id > 0 }
            ?: throw IllegalArgumentException("잘못된 캠페인 게시 계정입니다: $rawKey")
    }
    return CampaignPublishTarget(rawKey, platform, channelId)
}
