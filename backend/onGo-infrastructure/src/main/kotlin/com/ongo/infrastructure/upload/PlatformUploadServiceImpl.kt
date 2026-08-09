package com.ongo.infrastructure.upload

import com.ongo.application.video.PlatformUploadConfig
import com.ongo.application.video.PlatformUploadResult
import com.ongo.application.video.PlatformUploadService
import com.ongo.application.video.PlatformStreamWriterFactory
import com.ongo.application.video.PlatformUploadCapabilities
import com.ongo.application.video.PublishConfirmation
import com.ongo.application.video.isIndeterminateUploadFailure
import com.ongo.common.enums.Platform
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.infrastructure.external.platform.PlatformClientFactory
import com.ongo.infrastructure.external.platform.PlatformUploadRequest
import com.ongo.infrastructure.external.platform.PlatformVideoStatus
import com.ongo.infrastructure.external.platform.downloadFileToTemp
import com.ongo.domain.video.VideoPlatformMeta
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.Duration
import org.springframework.web.client.HttpStatusCodeException

@Service
class PlatformUploadServiceImpl(
    private val platformClientFactory: PlatformClientFactory,
    private val channelRepository: ChannelRepository,
    private val tokenEncryptionPort: TokenEncryptionPort,
    private val streamWriterFactories: List<PlatformStreamWriterFactory>,
) : PlatformUploadService {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 1000L
        private val PUBLISHED_STATUSES = setOf(
            "PUBLISHED", "PUBLISH_COMPLETE", "FINISHED", "FINISH", "PROCESSED", "UPLOADED",
            "READY", "LIVE", "SUCCEEDED", "SUCCESS", "COMPLETED", "COMPLETE", "POSTED",
            "PUBLISH", "AVAILABLE", "ACTIVE",
        )
        private val FAILED_STATUSES = setOf(
            "FAILED", "FAILURE", "REJECTED", "ERROR", "NOT_FOUND", "EXPIRED", "CANCELLED",
        )
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
        var channel = resolveChannel(config.channelId, userId, config.platform)
            ?: throw NotFoundException("채널", "${config.platform} (userId=$userId)")
        // Channel.accessToken은 저장 시 AES-GCM으로 암호화된다. 외부 플랫폼 경계에서만
        // 평문으로 만들고, DB/애플리케이션 객체에는 복호화된 값을 다시 저장하지 않는다.
        var accessToken = tokenEncryptionPort.decrypt(channel.accessToken).value
        var refreshedAfterUnauthorized = false

        var lastException: Exception? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                val directFactory = streamWriterFactories.find { it.platform == config.platform }
                    ?.takeIf { PlatformUploadCapabilities.get(config.platform)?.directVideoUpload == true }
                val result = if (directFactory != null) {
                    uploadFromCloudUrl(directFactory, config, fileUrl, PlainToken(accessToken), channel.platformChannelId)
                } else {
                    val client = platformClientFactory.getClient(config.platform)
                    val clientResult = client.uploadVideo(
                    PlatformUploadRequest(
                        fileUrl = fileUrl,
                        title = config.title,
                        description = config.description ?: "",
                        tags = config.tags,
                        visibility = config.visibility.name,
                        thumbnailUrl = config.thumbnailUrl,
                        accessToken = accessToken,
                        platformChannelId = channel.platformChannelId,
                        fileSize = config.fileSize,
                        scheduledAt = config.scheduledAt,
                    )
                    )
                    PlatformUploadResult(
                        success = true,
                        platformVideoId = clientResult.platformVideoId,
                        platformUrl = clientResult.platformUrl.ifBlank { null },
                    published = clientResult.status.trim().uppercase() in PUBLISHED_STATUSES &&
                        clientResult.platformUrl.isUsablePlatformUrl(),
                    )
                }

                // StreamWriter implementations intentionally convert provider
                // exceptions into a result so that a background worker can persist
                // the final state. Preserve the one safe refresh case here: a 401
                // returned before a provider upload session is created. Writers
                // that already sent bytes mark the result UNKNOWN, so this branch
                // cannot duplicate an external post.
                if (!refreshedAfterUnauthorized &&
                    result.httpStatus == 401 &&
                    result.confirmation == PublishConfirmation.CONFIRMED &&
                    channel.refreshToken != null
                ) {
                    try {
                        val refreshed = platformClientFactory.getClient(config.platform)
                            .refreshToken(tokenEncryptionPort.decrypt(channel.refreshToken!!).value)
                        channel = channelRepository.update(
                            channel.copy(
                                accessToken = tokenEncryptionPort.encrypt(com.ongo.domain.channel.PlainToken(refreshed.accessToken)),
                                refreshToken = refreshed.refreshToken?.let {
                                    tokenEncryptionPort.encrypt(com.ongo.domain.channel.PlainToken(it))
                                } ?: channel.refreshToken,
                                tokenExpiresAt = LocalDateTime.now().plusSeconds(refreshed.expiresIn),
                            )
                        )
                        accessToken = refreshed.accessToken
                        refreshedAfterUnauthorized = true
                        log.info("플랫폼 {} access token을 갱신하고 writer 업로드를 한 번 재시도합니다", config.platform)
                        continue
                    } catch (refreshException: Exception) {
                        lastException = refreshException
                        log.warn("플랫폼 {} writer access token 갱신 실패", config.platform, refreshException)
                        return result
                    }
                }

                return result
            } catch (e: Exception) {
                lastException = e
                if (!refreshedAfterUnauthorized && isUnauthorized(e) && channel.refreshToken != null) {
                    try {
                        val refreshed = platformClientFactory.getClient(config.platform)
                            .refreshToken(tokenEncryptionPort.decrypt(channel.refreshToken!!).value)
                        channel = channelRepository.update(
                            channel.copy(
                                accessToken = tokenEncryptionPort.encrypt(com.ongo.domain.channel.PlainToken(refreshed.accessToken)),
                                refreshToken = refreshed.refreshToken?.let {
                                    tokenEncryptionPort.encrypt(com.ongo.domain.channel.PlainToken(it))
                                } ?: channel.refreshToken,
                                tokenExpiresAt = LocalDateTime.now().plusSeconds(refreshed.expiresIn),
                            )
                        )
                        accessToken = refreshed.accessToken
                        refreshedAfterUnauthorized = true
                        log.info("플랫폼 {} access token을 갱신하고 업로드를 한 번 재시도합니다", config.platform)
                        continue
                    } catch (refreshException: Exception) {
                        lastException = refreshException
                        log.warn("플랫폼 {} access token 갱신 실패", config.platform, refreshException)
                    }
                }
                if (!isTransient(e) || attempt == MAX_RETRIES - 1) {
                    break
                }
                val delay = retryDelayMillis(e, attempt)
                log.warn(
                    "플랫폼 {} 업로드 일시 오류 (시도 {}/{}), {}ms 후 재시도: {}",
                    config.platform, attempt + 1, MAX_RETRIES, delay, e.message
                )
                Thread.sleep(delay)
            }
        }

        log.error("플랫폼 {} 업로드 실패: userId={}", config.platform, userId, lastException)
        val confirmation = if (lastException?.isIndeterminateUploadFailure() == true) {
            PublishConfirmation.UNKNOWN
        } else {
            PublishConfirmation.CONFIRMED
        }
        val durableRetryable = confirmation == PublishConfirmation.CONFIRMED &&
            lastException?.let(::isTransient) == true
        return PlatformUploadResult(
            success = false,
            published = false,
            errorMessage = lastException?.message ?: "알 수 없는 오류가 발생했습니다",
            confirmation = confirmation,
            retryable = durableRetryable,
            retryAfter = if (durableRetryable) {
                Duration.ofMillis(retryDelayMillis(lastException!!, MAX_RETRIES - 1))
            } else {
                null
            },
        )
    }

    override fun poll(
        platform: Platform,
        pollToken: String,
        userId: Long,
        knownPlatformUrl: String?,
        channelId: Long?,
    ): PlatformUploadResult {
        var channel = resolveChannel(channelId, userId, platform)
            ?: throw NotFoundException("채널", "$platform (userId=$userId)")
        var accessToken = tokenEncryptionPort.decrypt(channel.accessToken).value
        var refreshedAfterUnauthorized = false

        fun query(): PlatformVideoStatus = platformClientFactory.getClient(platform)
            .getVideoStatus(pollToken, accessToken)

        val status = try {
            query()
        } catch (e: Exception) {
            if (!isUnauthorized(e) || channel.refreshToken == null || refreshedAfterUnauthorized) throw e
            val refreshed = platformClientFactory.getClient(platform)
                .refreshToken(tokenEncryptionPort.decrypt(channel.refreshToken!!).value)
            channel = channelRepository.update(
                channel.copy(
                    accessToken = tokenEncryptionPort.encrypt(com.ongo.domain.channel.PlainToken(refreshed.accessToken)),
                    refreshToken = refreshed.refreshToken?.let {
                        tokenEncryptionPort.encrypt(com.ongo.domain.channel.PlainToken(it))
                    } ?: channel.refreshToken,
                    tokenExpiresAt = LocalDateTime.now().plusSeconds(refreshed.expiresIn),
                )
            )
            accessToken = refreshed.accessToken
            refreshedAfterUnauthorized = true
            query()
        }

        val normalized = status.status.trim().uppercase()
        return when {
            normalized in PUBLISHED_STATUSES -> {
                val videoId = status.platformVideoId.ifBlank { pollToken }
                val url = status.platformUrl?.takeIf { it.isUsablePlatformUrl() }
                    ?: knownPlatformUrl?.takeIf { it.isUsablePlatformUrl() }
                    ?: platformUrl(
                        platform,
                        videoId,
                        channel.platformChannelId,
                        isPublicId = videoId != pollToken,
                    )
                if (url == null) {
                    return PlatformUploadResult(
                        success = false,
                        platformVideoId = videoId,
                        published = false,
                        errorMessage = "플랫폼 게시가 완료되었지만 열 수 있는 게시물 URL을 확인하지 못했습니다.",
                        confirmation = PublishConfirmation.UNKNOWN,
                    )
                }
                PlatformUploadResult(
                    success = true,
                    platformVideoId = videoId,
                    platformUrl = url,
                    published = url.isNotBlank(),
                )
            }
            normalized in FAILED_STATUSES -> PlatformUploadResult(
                success = false,
                published = false,
                platformVideoId = status.platformVideoId.ifBlank { pollToken },
                errorMessage = status.errorMessage ?: "플랫폼 게시 처리가 거부되었습니다."
            )
            else -> PlatformUploadResult(
                success = true,
                platformVideoId = status.platformVideoId.ifBlank { pollToken },
                pollToken = pollToken,
                published = false,
            )
        }
    }

    private fun platformUrl(
        platform: Platform,
        videoId: String,
        platformChannelId: String? = null,
        isPublicId: Boolean = true,
    ): String? = when (platform) {
        Platform.YOUTUBE -> "https://www.youtube.com/watch?v=$videoId"
        // TikTok's publish_id is not a public video id. Only construct a URL
        // after the status response contains the public id and the connected
        // creator username is available. A generic /video/{publish_id} URL can
        // look valid while pointing to nothing, which would falsely complete a
        // publish in the UI.
        Platform.TIKTOK -> platformChannelId
            ?.takeIf { isPublicId }
            ?.takeIf { it.isNotBlank() }
            ?.let { "https://www.tiktok.com/@$it/video/$videoId" }
        // Instagram/Threads 상태 응답은 permalink를 반드시 반환해야 한다.
        // ID만으로 URL을 조립하면 실제 게시 링크인지 검증할 수 없다.
        Platform.INSTAGRAM, Platform.THREADS -> null
        Platform.TWITTER -> "https://twitter.com/i/status/$videoId"
        Platform.NAVER_CLIP -> "https://tv.naver.com/v/$videoId"
        Platform.FACEBOOK -> "https://www.facebook.com/watch/?v=$videoId"
        Platform.PINTEREST -> "https://www.pinterest.com/pin/$videoId/"
        Platform.LINKEDIN -> "https://www.linkedin.com/feed/update/$videoId"
        Platform.VIMEO -> "https://vimeo.com/$videoId"
        Platform.DAILYMOTION -> "https://www.dailymotion.com/video/$videoId"
        Platform.WORDPRESS, Platform.TUMBLR -> null
    }

    private fun resolveChannel(channelId: Long?, userId: Long, platform: Platform) =
        if (channelId != null) {
            channelRepository.findById(channelId)?.takeIf { it.userId == userId && it.platform == platform }
        } else {
            channelRepository.findByUserIdAndPlatform(userId, platform)
        }

    private fun uploadFromCloudUrl(
        factory: PlatformStreamWriterFactory,
        config: PlatformUploadConfig,
        fileUrl: String,
        accessToken: PlainToken,
        platformChannelId: String?,
    ): PlatformUploadResult {
        val sourceFile = downloadFileToTemp(fileUrl)
        val writer = factory.createWriter()
        try {
          writer.initSession(
            meta = VideoPlatformMeta(
                videoUploadId = config.videoUploadId,
                title = config.title,
                description = config.description,
                tags = config.tags,
                visibility = config.visibility,
                customThumbnailUrl = config.thumbnailUrl,
                customSettingsJson = config.customSettingsJson,
            ),
            accessToken = accessToken,
            platformChannelId = platformChannelId,
            fileSize = sourceFile.length(),
            scheduledAt = config.scheduledAt,
        )
          val chunkSize = 256 * 1024
          var offset = 0L
          sourceFile.inputStream().buffered().use { input ->
            while (true) {
              val chunk = input.readNBytes(chunkSize)
              if (chunk.isEmpty()) break
              writer.writeChunk(chunk, offset, sourceFile.length())
              offset += chunk.size
            }
          }
          return writer.complete()
        } finally {
          writer.abort()
          java.nio.file.Files.deleteIfExists(sourceFile.toPath())
        }
    }

    private fun isTransient(e: Exception): Boolean {
        val status = httpStatus(e)
        // Only an explicit 429 is safe to replay: timeout/connection/5xx may
        // have arrived after the provider accepted the bytes.
        return status == 429
    }

    private fun isUnauthorized(e: Exception): Boolean = httpStatus(e) == 401 ||
        generateSequence(e as Throwable?) { it.cause }
            .any { it.message?.contains("401") == true || it.message?.contains("Unauthorized", ignoreCase = true) == true }

    private fun httpStatus(e: Exception): Int? =
        generateSequence(e as Throwable?) { it.cause }
            .filterIsInstance<HttpStatusCodeException>()
            .map { it.statusCode.value() }
            .firstOrNull()

    private fun retryDelayMillis(e: Exception, attempt: Int): Long {
        val retryAfterSeconds = generateSequence(e as Throwable?) { it.cause }
            .filterIsInstance<HttpStatusCodeException>()
            .mapNotNull { it.responseHeaders?.getFirst("Retry-After")?.toLongOrNull() }
            .firstOrNull()
        return ((retryAfterSeconds?.times(1000L)) ?: (INITIAL_DELAY_MS * (1L shl attempt)))
            .coerceIn(INITIAL_DELAY_MS, 60_000L)
    }
}

private fun String?.isUsablePlatformUrl(): Boolean =
    this?.trim()?.let { it.startsWith("https://") || it.startsWith("http://") } == true
