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
import java.io.ByteArrayOutputStream

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
    private val buffer = ByteArrayOutputStream()
    private var uploadId: String? = null
    private var uploadUrl: String? = null
    private var accessTokenRef: String? = null

    override fun initSession(
        meta: VideoPlatformMeta,
        accessToken: String,
        platformChannelId: String?,
        fileSize: Long,
    ): String {
        accessTokenRef = accessToken
        val visibility = mapVisibility(meta.visibility.name)

        val initResponse = naverClipApi.initUpload(
            authorization = "Bearer $accessToken",
            request = NaverClipUploadInitRequest(
                title = (meta.title ?: "Untitled").take(100),
                description = (meta.description ?: "").take(1000),
                tags = meta.tags,
                fileSize = fileSize,
                visibility = visibility,
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
        buffer.write(chunk)
    }

    override fun complete(): PlatformUploadResult {
        val data = buffer.toByteArray()
        val uid = uploadId ?: throw IllegalStateException("initSession() 호출 필요")
        val url = uploadUrl ?: throw IllegalStateException("initSession() 호출 필요")
        val token = accessTokenRef ?: throw IllegalStateException("initSession() 호출 필요")

        return try {
            fileTransferHelper.uploadToNaverClip(url, data, "Bearer $token")

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
            )
        } catch (e: Exception) {
            log.error("Naver Clip 스트리밍 업로드 실패", e)
            PlatformUploadResult(success = false, errorMessage = e.message)
        }
    }

    private fun mapVisibility(visibility: String) = when (visibility.uppercase()) {
        "PUBLIC" -> "PUBLIC"
        "PRIVATE" -> "PRIVATE"
        else -> "PRIVATE"
    }
}
