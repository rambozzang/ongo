package com.ongo.infrastructure.external.platform

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.video.PlatformStreamWriter
import com.ongo.application.video.PlatformStreamWriterFactory
import com.ongo.application.video.PlatformUploadResult
import com.ongo.application.video.indeterminateUploadFailure
import com.ongo.common.enums.Platform
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.domain.channel.PlainToken
import com.ongo.infrastructure.external.twitter.TwitterApi
import com.ongo.infrastructure.external.twitter.TwitterConfig
import com.ongo.infrastructure.external.twitter.TwitterMediaApi
import com.ongo.infrastructure.external.twitter.dto.TwitterCreateTweetRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class TwitterStreamWriterFactory(
    private val twitterApi: TwitterApi,
    private val twitterMediaApi: TwitterMediaApi,
    private val twitterConfig: TwitterConfig,
    private val fileTransferHelper: PlatformFileTransferHelper,
) : PlatformStreamWriterFactory {
    override val platform = Platform.TWITTER
    override fun createWriter(): PlatformStreamWriter =
        TwitterStreamWriter(twitterApi, twitterMediaApi, twitterConfig, fileTransferHelper)
}

/**
 * Twitter 스트리밍 업로드: INIT → 버퍼 수집 → APPEND(5MB 청크) → FINALIZE → STATUS 폴링 → 트윗 생성.
 * Twitter API는 예약 게시를 지원하지 않으므로 scheduledAt은 무시됨.
 */
class TwitterStreamWriter(
    private val twitterApi: TwitterApi,
    private val twitterMediaApi: TwitterMediaApi,
    private val twitterConfig: TwitterConfig,
    private val fileTransferHelper: PlatformFileTransferHelper,
) : PlatformStreamWriter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val buffer = TempFileChunkBuffer("twitter")

    private var mediaId: String? = null
    private var accessTokenRef: String? = null
    private var tweetText: String = ""
    private var replySettings: String? = null
    private var communityId: String? = null
    private var madeWithAi: Boolean? = null
    private var threadTexts: List<String> = emptyList()

    companion object {
        private const val APPEND_CHUNK_SIZE = 5 * 1024 * 1024 // 5MB — Twitter 권장 청크 크기
        private const val MAX_PROCESSING_ATTEMPTS = 60
        private const val MAX_FILE_SIZE = 512L * 1024 * 1024 // 512MB — Twitter 비디오 제한
    }

    override fun initSession(
        meta: VideoPlatformMeta,
        accessToken: PlainToken,
        platformChannelId: String?,
        fileSize: Long,
        scheduledAt: LocalDateTime?,
    ): String {
        if (fileSize > MAX_FILE_SIZE) {
            throw IllegalArgumentException(
                "Twitter 최대 파일 크기(${MAX_FILE_SIZE / 1024 / 1024}MB)를 초과합니다: ${fileSize / 1024 / 1024}MB"
            )
        }

        if (scheduledAt != null) {
            log.warn("Twitter는 API를 통한 예약 게시를 지원하지 않습니다. scheduledAt={} 무시됨", scheduledAt)
        }

        accessTokenRef = accessToken.value
        tweetText = buildTweetText(meta)
        val settings = meta.customSettingsJson
            ?.let { runCatching { jacksonObjectMapper().readTree(it) }.getOrNull() }
        replySettings = settings?.path("who_can_reply_post")?.asText(null)
            ?.takeIf { it in setOf("everyone", "mentionedUsers", "following", "subscribers", "verified") }
        communityId = settings?.path("community")?.asText(null)?.takeIf(String::isNotBlank)
        madeWithAi = settings?.path("made_with_ai")?.takeIf { it.isBoolean }?.asBoolean()
        threadTexts = settings?.path("__postiz_thread")
            ?.takeIf { it.isArray }
            ?.mapNotNull { it.path("content").asText(null)?.trim()?.takeIf(String::isNotBlank) }
            .orEmpty()

        val initResponse = twitterMediaApi.initUpload(
            authorization = "Bearer ${accessToken.value}",
            command = "INIT",
            totalBytes = fileSize,
            mediaType = "video/mp4",
            mediaCategory = "tweet_video",
        )

        mediaId = initResponse.mediaIdString
        log.debug("Twitter 미디어 업로드 세션 초기화: mediaId={}", mediaId)
        return initResponse.mediaIdString
    }

    override fun writeChunk(chunk: ByteArray, offset: Long, totalSize: Long) {
        buffer.write(chunk, offset)
    }

    override fun complete(): PlatformUploadResult {
        val currentMediaId = mediaId ?: throw IllegalStateException("initSession() 호출 필요")
        val token = accessTokenRef ?: throw IllegalStateException("initSession() 호출 필요")
        val file = buffer.finish()
        var publishedTweetId: String? = null

        return try {
            // APPEND: 5MB 청크로 분할 전송
            var segmentIndex = 0
            var offset = 0L
            file.inputStream().buffered().use { input ->
              while (offset < file.length()) {
                val chunk = input.readNBytes(minOf(APPEND_CHUNK_SIZE.toLong(), file.length() - offset).toInt())

                fileTransferHelper.appendToTwitterMedia(
                    uploadUrl = "${twitterConfig.getUploadBaseUrl()}/1.1/media/upload.json",
                    accessToken = token,
                    mediaId = currentMediaId,
                    segmentIndex = segmentIndex,
                    chunkData = chunk,
                )

                log.debug("Twitter APPEND 완료: segment={}, bytes={}", segmentIndex, chunk.size)
                segmentIndex++
                offset += chunk.size
              }
            }

            // FINALIZE
            val finalizeResponse = twitterMediaApi.finalizeUpload(
                authorization = "Bearer $token",
                command = "FINALIZE",
                mediaId = currentMediaId,
            )

            // STATUS 폴링 (처리 대기)
            if (finalizeResponse.processingInfo?.state == "pending" ||
                finalizeResponse.processingInfo?.state == "in_progress"
            ) {
                waitForProcessing(currentMediaId, token)
            }

            // 트윗 생성
            val tweetRequest = TwitterCreateTweetRequest(
                text = tweetText,
                media = TwitterCreateTweetRequest.MediaPayload(
                    mediaIds = listOf(currentMediaId),
                ),
                replySettings = replySettings,
                communityId = communityId,
                madeWithAi = madeWithAi,
            )

            val tweetResponse = twitterApi.createTweet(
                authorization = "Bearer $token",
                request = tweetRequest,
            )

            val tweetId = tweetResponse.data?.id
                ?: throw IllegalStateException("Twitter 트윗 생성 응답에 ID가 없습니다")
            publishedTweetId = tweetId

            // Postiz represents an X thread as the main value followed by
            // text-only values. Reply sequentially so every child points to
            // the previously created tweet. A failure after the parent is
            // visible is intentionally returned as UNKNOWN by the common
            // failure mapper; automatic retry must never duplicate the thread.
            var previousTweetId = tweetId
            threadTexts.forEachIndexed { index, text ->
                val replyResponse = twitterApi.createTweet(
                    authorization = "Bearer $token",
                    request = TwitterCreateTweetRequest(
                        text = text,
                        reply = TwitterCreateTweetRequest.ReplyPayload(previousTweetId),
                    ),
                )
                previousTweetId = replyResponse.data?.id
                    ?: throw IllegalStateException("Twitter 스레드 ${index + 1}번째 답글 응답에 ID가 없습니다")
            }

            log.info("Twitter 스트리밍 업로드 완료: tweetId={}", tweetId)
            PlatformUploadResult(
                success = true,
                platformVideoId = tweetId,
                platformUrl = "https://twitter.com/i/status/$tweetId",
                // 트윗이 생성된 시점에 이미 타임라인에 노출된다.
                published = true,
            )
        } catch (e: Exception) {
            log.error("Twitter 스트리밍 업로드 실패", e)
            val failure = uploadFailureResult(e)
            publishedTweetId?.let { id ->
                indeterminateUploadFailure(
                    "X 본문은 게시되었지만 스레드 후속 댓글을 모두 확인하지 못했습니다. 중복 게시 방지를 위해 자동 재시도하지 않습니다.",
                ).copy(
                    platformVideoId = id,
                    platformUrl = "https://twitter.com/i/status/$id",
                    httpStatus = failure.httpStatus,
                )
            } ?: failure
        } finally {
            buffer.cleanup()
        }
    }

    override fun abort() = buffer.cleanup()

    private fun waitForProcessing(mediaId: String, accessToken: String) {
        var attempts = 0
        while (attempts < MAX_PROCESSING_ATTEMPTS) {
            val status = twitterMediaApi.checkStatus(
                authorization = "Bearer $accessToken",
                command = "STATUS",
                mediaId = mediaId,
            )

            when (status.processingInfo?.state) {
                "succeeded" -> return
                "failed" -> throw IllegalStateException(
                    "Twitter 미디어 처리 실패: ${status.processingInfo.error?.message ?: "알 수 없는 오류"}"
                )
                else -> {
                    val waitSeconds = status.processingInfo?.checkAfterSecs ?: 5
                    Thread.sleep(waitSeconds * 1000L)
                    attempts++
                }
            }
        }
        throw IllegalStateException("Twitter 미디어 처리 시간 초과 (${MAX_PROCESSING_ATTEMPTS}회 폴링)")
    }

    private fun buildTweetText(meta: VideoPlatformMeta): String {
        val body = listOf(meta.title.orEmpty().trim(), meta.description.orEmpty().trim())
            .filter(String::isNotBlank)
            .joinToString("\n\n")
        val tags = meta.tags
            .filter { it.isNotBlank() }
            .joinToString(" ") { if (it.startsWith("#")) it else "#$it" }
        val combined = listOf(body, tags).filter(String::isNotBlank).joinToString("\n\n")
        return combined.take(280)
    }
}
