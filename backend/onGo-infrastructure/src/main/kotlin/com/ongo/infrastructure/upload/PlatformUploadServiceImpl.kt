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
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

@Service
class PlatformUploadServiceImpl(
    private val platformClientFactory: PlatformClientFactory,
    private val channelRepository: ChannelRepository,
) : PlatformUploadService {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 1000L
    }

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

        var lastException: Exception? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
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

                return PlatformUploadResult(
                    success = true,
                    platformVideoId = result.platformVideoId,
                    platformUrl = result.platformUrl,
                )
            } catch (e: Exception) {
                lastException = e
                if (!isTransient(e) || attempt == MAX_RETRIES - 1) {
                    break
                }
                val delay = INITIAL_DELAY_MS * (1L shl attempt)
                log.warn(
                    "플랫폼 {} 업로드 일시 오류 (시도 {}/{}), {}ms 후 재시도: {}",
                    config.platform, attempt + 1, MAX_RETRIES, delay, e.message
                )
                Thread.sleep(delay)
            }
        }

        log.error("플랫폼 {} 업로드 실패: userId={}", config.platform, userId, lastException)
        return PlatformUploadResult(
            success = false,
            errorMessage = lastException?.message ?: "알 수 없는 오류가 발생했습니다",
        )
    }

    private fun isTransient(e: Exception): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is FileNotFoundException) return false
            if (cause is SocketTimeoutException || cause is ConnectException || cause is IOException) return true
            cause = cause.cause
        }
        return false
    }
}
