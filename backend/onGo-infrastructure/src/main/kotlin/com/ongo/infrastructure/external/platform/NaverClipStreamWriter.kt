package com.ongo.infrastructure.external.platform

import com.ongo.application.video.PlatformStreamWriter
import com.ongo.application.video.PlatformStreamWriterFactory
import com.ongo.application.video.PlatformUploadResult
import com.ongo.common.enums.Platform
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.infrastructure.external.naverclip.NaverClipApi
import com.ongo.infrastructure.external.naverclip.dto.NaverClipUploadCompleteRequest
import com.ongo.infrastructure.external.naverclip.dto.NaverClipUploadInitRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Component
class NaverClipStreamWriterFactory(
    private val naverClipApi: NaverClipApi,
    private val fileTransferHelper: PlatformFileTransferHelper,
) : PlatformStreamWriterFactory {
    override val platform = Platform.NAVER_CLIP
    override fun createWriter(): PlatformStreamWriter = NaverClipStreamWriter(naverClipApi, fileTransferHelper)
}

class NaverClipStreamWriter(
    private val naverClipApi: NaverClipApi,
    private val fileTransferHelper: PlatformFileTransferHelper,
) : PlatformStreamWriter {

    private val log = LoggerFactory.getLogger(javaClass)
    private val buffer = TempFileChunkBuffer("naver-clip")
    private var uploadId: String? = null
    private var uploadUrl: String? = null
    private var accessTokenRef: String? = null

    companion object {
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

        accessTokenRef = accessToken
        val visibility = mapVisibility(meta.visibility.name)

        // Naver Clip 예약 게시: publish_at (ISO 8601)
        val publishAtIso = scheduledAt?.let {
            it.atZone(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        }

        if (publishAtIso != null) {
            log.info("Naver Clip 예약 게시 설정: publishAt={}", publishAtIso)
        }

        val initResponse = naverClipApi.initUpload(
            authorization = "Bearer $accessToken",
            request = NaverClipUploadInitRequest(
                title = (meta.title ?: "Untitled").take(100),
                description = (meta.description ?: "").take(1000),
                tags = meta.tags,
                fileSize = fileSize,
                visibility = visibility,
                publishAt = publishAtIso,
            ),
        )

        if (initResponse.error != null) {
            throw IllegalStateException("Naver Clip 업로드 초기화 실패: ${initResponse.error.message}")
        }

        uploadId = initResponse.uploadId
            ?: throw IllegalStateException("Naver Clip upload_id를 받지 못했습니다")
        uploadUrl = initResponse.uploadUrl
            ?: throw IllegalStateException("Naver Clip upload_url을 받지 못했습니다")

        log.debug("Naver Clip 스트리밍 세션 초기화: uploadId={}", uploadId)
        return uploadId!!
    }

    override fun writeChunk(chunk: ByteArray, offset: Long, totalSize: Long) {
        buffer.write(chunk, offset)
    }

    override fun complete(): PlatformUploadResult {
        val file = buffer.finish()
        val uid = uploadId ?: throw IllegalStateException("initSession() 호출 필요")
        val url = uploadUrl ?: throw IllegalStateException("initSession() 호출 필요")
        val token = accessTokenRef ?: throw IllegalStateException("initSession() 호출 필요")

        return try {
            fileTransferHelper.uploadToNaverClip(url, file, "Bearer $token")

            val completeResponse = naverClipApi.completeUpload(
                authorization = "Bearer $token",
                request = NaverClipUploadCompleteRequest(uploadId = uid),
            )

            if (completeResponse.error != null) {
                throw IllegalStateException("Naver Clip 업로드 완료 처리 실패: ${completeResponse.error.message}")
            }

            val clipId = completeResponse.clipId
                ?: throw IllegalStateException("Naver Clip clip_id를 받지 못했습니다")

            log.info("Naver Clip 스트리밍 업로드 완료: clipId={}", clipId)
            PlatformUploadResult(
                success = true,
                platformVideoId = clipId,
                platformUrl = completeResponse.clipUrl ?: "",
                // complete 응답이 clipId 를 주면 클립 등록이 끝난 상태다.
                published = true,
            )
        } catch (e: Exception) {
            log.error("Naver Clip 스트리밍 업로드 실패", e)
            PlatformUploadResult(success = false, errorMessage = e.message)
        } finally {
            buffer.cleanup()
        }
    }

    override fun abort() = buffer.cleanup()

    private fun mapVisibility(visibility: String) = when (visibility.uppercase()) {
        "PUBLIC" -> "PUBLIC"
        "PRIVATE" -> "PRIVATE"
        else -> "PRIVATE"
    }
}
