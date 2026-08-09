package com.ongo.infrastructure.external.platform

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.video.PlatformStreamWriter
import com.ongo.application.video.PlatformStreamWriterFactory
import com.ongo.application.video.PlatformUploadResult
import com.ongo.common.enums.Platform
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.domain.channel.PlainToken
import com.ongo.infrastructure.external.youtube.YouTubeConfig
import com.ongo.infrastructure.external.youtube.dto.YouTubeUploadRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class YouTubeStreamWriterFactory(
    private val youTubeConfig: YouTubeConfig,
    private val fileTransferHelper: PlatformFileTransferHelper,
) : PlatformStreamWriterFactory {
    override val platform = Platform.YOUTUBE
    override fun createWriter(): PlatformStreamWriter = YouTubeStreamWriter(youTubeConfig, fileTransferHelper)
}

class YouTubeStreamWriter(
    private val youTubeConfig: YouTubeConfig,
    private val fileTransferHelper: PlatformFileTransferHelper,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) : PlatformStreamWriter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val buffer = TempFileChunkBuffer("youtube")
    private var sessionUri: String? = null

    companion object {
        private const val MAX_FILE_SIZE = 256L * 1024 * 1024 * 1024 // 256GB — YouTube 제한이지만 메모리 보호를 위해 실질 제한 적용
        private const val MAX_MEMORY_FILE_SIZE = 2L * 1024 * 1024 * 1024 // 2GB — 메모리 버퍼링 상한
    }

    override fun initSession(
        meta: VideoPlatformMeta,
        accessToken: PlainToken,
        platformChannelId: String?,
        fileSize: Long,
        scheduledAt: LocalDateTime?,
    ): String {
        if (fileSize > MAX_MEMORY_FILE_SIZE) {
            throw IllegalArgumentException(
                "스트리밍 업로드 최대 파일 크기(${MAX_MEMORY_FILE_SIZE / 1024 / 1024}MB)를 초과합니다: ${fileSize / 1024 / 1024}MB"
            )
        }

        // YouTube 예약 게시: privacyStatus=private + publishAt 설정
        // 업로드 후 publishAt 시점에 자동으로 public 전환됨
        // 주의: YouTube는 예약 게시 시 반드시 private→public 전환만 지원
        //       UNLISTED 예약은 YouTube API에서 미지원
        val isScheduled = scheduledAt != null
        val userVisibility = mapVisibility(meta.visibility.name)
        val settings = meta.customSettingsJson
            ?.let { runCatching { objectMapper.readTree(it) }.getOrNull() }
        val privacyStatus = if (isScheduled) "private" else userVisibility
        val publishAtIso = scheduledAt?.let {
            it.atZone(ZoneId.of("Asia/Seoul"))
                .withZoneSameInstant(ZoneId.of("UTC"))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }

        if (isScheduled && userVisibility != "public") {
            log.warn("YouTube 예약 게시는 public 전환만 지원합니다. 요청된 visibility={}, 예약 시간에 public으로 전환됩니다.", userVisibility)
        }

        val uploadRequest = YouTubeUploadRequest(
            snippet = YouTubeUploadRequest.Snippet(
                title = (meta.title ?: "Untitled").take(100),
                description = (meta.description ?: "").take(5000),
                tags = meta.tags,
            ),
            status = YouTubeUploadRequest.Status(
                privacyStatus = privacyStatus,
                publishAt = publishAtIso,
                selfDeclaredMadeForKids = settings?.path("selfDeclaredMadeForKids")
                    ?.takeUnless { it.isMissingNode || it.isNull }
                    ?.asBoolean() ?: false,
            ),
        )

        if (isScheduled) {
            log.info("YouTube 예약 게시 설정: publishAt={}", publishAtIso)
        }

        val uri = fileTransferHelper.initiateYouTubeResumableUpload(
            uploadBaseUrl = youTubeConfig.getUploadBaseUrl(),
            metadata = uploadRequest,
            accessToken = accessToken.value,
            fileSize = fileSize,
        )
        sessionUri = uri
        log.debug("YouTube 스트리밍 세션 초기화: {}", uri.take(60))
        return uri
    }

    override fun writeChunk(chunk: ByteArray, offset: Long, totalSize: Long) {
        buffer.write(chunk, offset)
    }

    override fun complete(): PlatformUploadResult {
        val file = buffer.finish()
        val uri = sessionUri ?: throw IllegalStateException("initSession() 호출 필요")
        return try {
            val videoId = fileTransferHelper.uploadToYouTubeSession(uri, file)
            log.info("YouTube 스트리밍 업로드 완료: videoId={}", videoId)
            PlatformUploadResult(
                success = true,
                platformVideoId = videoId,
                platformUrl = "https://www.youtube.com/watch?v=$videoId",
                // 업로드 세션이 videoId 를 돌려준 시점에 영상은 이미 시청 가능하다.
                published = true,
            )
        } catch (e: Exception) {
            log.error("YouTube 스트리밍 업로드 실패", e)
            uploadFailureResult(e)
        } finally {
            buffer.cleanup()
        }
    }

    override fun abort() = buffer.cleanup()

    private fun mapVisibility(visibility: String) = when (visibility.uppercase()) {
        "PUBLIC" -> "public"
        "PRIVATE" -> "private"
        "UNLISTED" -> "unlisted"
        else -> "private"
    }
}
