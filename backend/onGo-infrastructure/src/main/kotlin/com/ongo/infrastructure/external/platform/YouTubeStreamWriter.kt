package com.ongo.infrastructure.external.platform

import com.ongo.application.video.PlatformStreamWriter
import com.ongo.application.video.PlatformStreamWriterFactory
import com.ongo.application.video.PlatformUploadResult
import com.ongo.common.enums.Platform
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.infrastructure.external.youtube.YouTubeConfig
import com.ongo.infrastructure.external.youtube.dto.YouTubeUploadRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

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
) : PlatformStreamWriter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val buffer = ByteArrayOutputStream()
    private var sessionUri: String? = null

    override fun initSession(
        meta: VideoPlatformMeta,
        accessToken: String,
        platformChannelId: String?,
        fileSize: Long,
    ): String {
        val uploadRequest = YouTubeUploadRequest(
            snippet = YouTubeUploadRequest.Snippet(
                title = (meta.title ?: "Untitled").take(100),
                description = (meta.description ?: "").take(5000),
                tags = meta.tags,
            ),
            status = YouTubeUploadRequest.Status(
                privacyStatus = mapVisibility(meta.visibility.name),
            ),
        )

        val uri = fileTransferHelper.initiateYouTubeResumableUpload(
            uploadBaseUrl = youTubeConfig.getUploadBaseUrl(),
            metadata = uploadRequest,
            accessToken = accessToken,
            fileSize = fileSize,
        )
        sessionUri = uri
        log.debug("YouTube 스트리밍 세션 초기화: {}", uri.take(60))
        return uri
    }

    override fun writeChunk(chunk: ByteArray, offset: Long, totalSize: Long) {
        buffer.write(chunk)
    }

    override fun complete(): PlatformUploadResult {
        val data = buffer.toByteArray()
        val uri = sessionUri ?: throw IllegalStateException("initSession() 호출 필요")
        return try {
            val videoId = fileTransferHelper.uploadToYouTubeSession(uri, data)
            log.info("YouTube 스트리밍 업로드 완료: videoId={}", videoId)
            PlatformUploadResult(
                success = true,
                platformVideoId = videoId,
                platformUrl = "https://www.youtube.com/watch?v=$videoId",
            )
        } catch (e: Exception) {
            log.error("YouTube 스트리밍 업로드 실패", e)
            PlatformUploadResult(success = false, errorMessage = e.message)
        }
    }

    private fun mapVisibility(visibility: String) = when (visibility.uppercase()) {
        "PUBLIC" -> "public"
        "PRIVATE" -> "private"
        "UNLISTED" -> "unlisted"
        else -> "private"
    }
}
