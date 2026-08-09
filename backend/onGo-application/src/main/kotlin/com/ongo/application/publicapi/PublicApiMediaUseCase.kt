package com.ongo.application.publicapi

import com.ongo.application.asset.AssetUseCase
import com.ongo.common.exception.FileValidationException
import com.ongo.common.util.FileValidationUtil
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * Postiz public API의 /upload 어댑터.
 *
 * 공개 API가 업로드한 파일도 일반 에셋 라이브러리에 저장한다. 따라서 이후
 * /posts 요청에서 반환된 path를 사용하거나 UI에서 같은 파일을 재사용할 수 있고,
 * 저장 실패 시 AssetUseCase가 스토리지 보상 삭제를 수행한다.
 */
@Service
class PublicApiMediaUseCase(
    private val assetUseCase: AssetUseCase,
) {
    fun upload(userId: Long, file: MultipartFile): PublicMediaUploadResponse {
        val originalFilename = file.originalFilename?.trim().orEmpty()
        val contentType = file.contentType?.trim().orEmpty()
        requireSafeFilename(originalFilename)
        if (file.size <= 0 || file.size > MAX_FILE_SIZE) {
            throw FileValidationException("업로드 파일 크기는 0보다 크고 2GB 이하여야 합니다")
        }
        if (!isSupportedContentType(contentType)) {
            throw FileValidationException("지원하지 않는 미디어 MIME 타입입니다: $contentType")
        }

        // MultipartFile은 inputStream을 다시 열 수 있으므로 저장 전에 시그니처를
        // 확인한다. 클라이언트가 보낸 Content-Type만 믿으면 실행 파일을 영상으로
        // 위장해 스토리지에 올릴 수 있다.
        file.inputStream.use { FileValidationUtil.validateAssetContent(it, contentType) }

        val filename = "${UUID.randomUUID()}_$originalFilename"
        val response = file.inputStream.use { inputStream ->
            assetUseCase.createAsset(
                userId = userId,
                filename = filename,
                originalFilename = originalFilename,
                fileType = fileType(contentType),
                fileSizeBytes = file.size,
                mimeType = contentType,
                tags = emptyList(),
                folder = PUBLIC_API_FOLDER,
                width = null,
                height = null,
                durationSeconds = null,
                inputStream = inputStream,
                storageKey = "$PUBLIC_API_FOLDER/$userId/$filename",
            )
        }

        return PublicMediaUploadResponse(
            id = response.id.toString(),
            path = response.fileUrl,
            name = response.originalFilename ?: response.filename,
            mimeType = response.mimeType,
            size = response.fileSizeBytes,
        )
    }

    private fun requireSafeFilename(filename: String) {
        require(filename.isNotBlank() && filename.length <= 255) { "파일 이름이 유효하지 않습니다" }
        require(!filename.contains("..") && !filename.contains('/') && !filename.contains('\\')) {
            "파일 이름이 유효하지 않습니다"
        }
    }

    private fun isSupportedContentType(contentType: String): Boolean =
        contentType.startsWith("image/") || contentType.startsWith("audio/") || contentType.startsWith("video/")

    private fun fileType(contentType: String): String = when {
        contentType.startsWith("image/") -> "IMAGE"
        contentType.startsWith("audio/") -> "AUDIO"
        contentType.startsWith("video/") -> "VIDEO"
        else -> "FILE"
    }

    companion object {
        private const val MAX_FILE_SIZE = 2L * 1024 * 1024 * 1024
        private const val PUBLIC_API_FOLDER = "public-api"
    }
}

data class PublicMediaUploadResponse(
    val id: String,
    val path: String,
    val name: String,
    val mimeType: String?,
    val size: Long?,
)
