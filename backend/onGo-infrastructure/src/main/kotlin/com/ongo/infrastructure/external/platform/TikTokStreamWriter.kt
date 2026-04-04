package com.ongo.infrastructure.external.platform

import com.ongo.application.video.PlatformStreamWriter
import com.ongo.application.video.PlatformStreamWriterFactory
import com.ongo.application.video.PlatformUploadResult
import com.ongo.common.enums.Platform
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.infrastructure.external.tiktok.TikTokApi
import com.ongo.infrastructure.external.tiktok.dto.TikTokInitUploadRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class TikTokStreamWriterFactory(
    private val tikTokApi: TikTokApi,
    private val fileTransferHelper: PlatformFileTransferHelper,
) : PlatformStreamWriterFactory {
    override val platform = Platform.TIKTOK
    override fun createWriter(): PlatformStreamWriter = TikTokStreamWriter(tikTokApi, fileTransferHelper)
}

class TikTokStreamWriter(
    private val tikTokApi: TikTokApi,
    private val fileTransferHelper: PlatformFileTransferHelper,
) : PlatformStreamWriter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val buffer = ByteArrayOutputStream()
    private var publishId: String? = null
    private var uploadUrl: String? = null

    companion object {
        private const val CHUNK_SIZE = 10_000_000L // 10MB
        private const val MAX_FILE_SIZE = 4L * 1024 * 1024 * 1024 // 4GB — TikTok 제한이지만 메모리 보호를 위해 실질 제한 적용
        private const val MAX_MEMORY_FILE_SIZE = 2L * 1024 * 1024 * 1024 // 2GB — 메모리 버퍼링 상한
    }

    override fun initSession(
        meta: VideoPlatformMeta,
        accessToken: String,
        platformChannelId: String?,
        fileSize: Long,
        scheduledAt: LocalDateTime?,
    ): String {
        if (fileSize > MAX_MEMORY_FILE_SIZE) {
            throw IllegalArgumentException(
                "스트리밍 업로드 최대 파일 크기(${MAX_MEMORY_FILE_SIZE / 1024 / 1024}MB)를 초과합니다: ${fileSize / 1024 / 1024}MB"
            )
        }

        val privacyLevel = mapVisibility(meta.visibility.name)
        val totalChunkCount = maxOf(1, ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt())

        // TikTok 예약 게시: schedule_time (UTC Unix timestamp, 초 단위)
        val scheduleTimeEpoch = scheduledAt?.let {
            it.atZone(ZoneId.of("Asia/Seoul"))
                .withZoneSameInstant(ZoneId.of("UTC"))
                .toEpochSecond()
        }

        if (scheduleTimeEpoch != null) {
            log.info("TikTok 예약 게시 설정: scheduleTime={} (epoch={})", scheduledAt, scheduleTimeEpoch)
        }

        val initRequest = TikTokInitUploadRequest(
            postInfo = TikTokInitUploadRequest.PostInfo(
                title = (meta.title ?: "Untitled").take(150),
                privacyLevel = privacyLevel,
                postMode = if (scheduleTimeEpoch != null) "SCHEDULE_VIDEO" else "DIRECT_POST",
                scheduleTime = scheduleTimeEpoch,
            ),
            sourceInfo = TikTokInitUploadRequest.SourceInfo(
                source = "FILE_UPLOAD",
                videoSize = fileSize,
                chunkSize = CHUNK_SIZE,
                totalChunkCount = totalChunkCount,
            ),
        )

        val response = tikTokApi.initVideoUpload(
            authorization = "Bearer $accessToken",
            request = initRequest,
        )

        if (response.error != null) {
            throw IllegalStateException("TikTok 업로드 초기화 실패: ${response.error.message}")
        }

        publishId = response.data?.publishId
            ?: throw IllegalStateException("TikTok publish_id를 받지 못했습니다")
        uploadUrl = response.data.uploadUrl
            ?: throw IllegalStateException("TikTok upload_url을 받지 못했습니다")

        log.debug("TikTok 스트리밍 세션 초기화: publishId={}", publishId)
        return publishId!!
    }

    override fun writeChunk(chunk: ByteArray, offset: Long, totalSize: Long) {
        buffer.write(chunk)
    }

    override fun complete(): PlatformUploadResult {
        val data = buffer.toByteArray()
        val url = uploadUrl ?: throw IllegalStateException("initSession() 호출 필요")
        val pid = publishId ?: throw IllegalStateException("initSession() 호출 필요")
        return try {
            fileTransferHelper.uploadChunkedToTikTok(url, data, CHUNK_SIZE)
            log.info("TikTok 스트리밍 업로드 완료: publishId={}", pid)
            PlatformUploadResult(
                success = true,
                platformVideoId = pid,
                platformUrl = "",
            )
        } catch (e: Exception) {
            log.error("TikTok 스트리밍 업로드 실패", e)
            PlatformUploadResult(success = false, errorMessage = e.message)
        }
    }

    private fun mapVisibility(visibility: String) = when (visibility.uppercase()) {
        "PUBLIC" -> "PUBLIC_TO_EVERYONE"
        "PRIVATE" -> "SELF_ONLY"
        "UNLISTED" -> "MUTUAL_FOLLOW_FRIENDS"
        else -> "SELF_ONLY"
    }
}
