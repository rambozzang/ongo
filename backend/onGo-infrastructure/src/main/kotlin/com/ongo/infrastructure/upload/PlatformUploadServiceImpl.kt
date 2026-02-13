package com.ongo.infrastructure.upload

import com.ongo.application.video.PlatformUploadConfig
import com.ongo.application.video.PlatformUploadResult
import com.ongo.application.video.PlatformUploadService
import com.ongo.common.enums.Platform
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.channel.ChannelRepository
import com.ongo.infrastructure.external.platform.PlatformClientFactory
import com.ongo.infrastructure.external.platform.PlatformUploadRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PlatformUploadServiceImpl(
    private val platformClientFactory: PlatformClientFactory,
    private val channelRepository: ChannelRepository,
) : PlatformUploadService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(platform: Platform): Boolean {
        return try {
            platformClientFactory.getClient(platform)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    override fun upload(config: PlatformUploadConfig, fileUrl: String, userId: Long): PlatformUploadResult {
        val channel = channelRepository.findByUserIdAndPlatform(userId, config.platform)
            ?: throw NotFoundException("채널", "${config.platform} (userId=$userId)")

        val client = platformClientFactory.getClient(config.platform)

        return try {
            val result = client.uploadVideo(
                PlatformUploadRequest(
                    fileUrl = fileUrl,
                    title = config.title,
                    description = config.description ?: "",
                    tags = config.tags,
                    visibility = config.visibility.name,
                    thumbnailUrl = config.thumbnailUrl,
                    accessToken = channel.accessToken,
                    platformChannelId = channel.platformChannelId,
                    fileSize = config.fileSize,
                    scheduledAt = config.scheduledAt,
                )
            )

            PlatformUploadResult(
                success = true,
                platformVideoId = result.platformVideoId,
                platformUrl = result.platformUrl,
            )
        } catch (e: Exception) {
            log.error("플랫폼 {} 업로드 실패: userId={}", config.platform, userId, e)
            PlatformUploadResult(
                success = false,
                errorMessage = e.message ?: "알 수 없는 오류가 발생했습니다",
            )
        }
    }
}
