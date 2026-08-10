package com.ongo.api.asset

import com.ongo.api.config.CurrentUser
import com.ongo.application.asset.AssetUseCase
import com.ongo.application.asset.dto.AssetListResponse
import com.ongo.application.asset.dto.AssetResponse
import com.ongo.application.asset.dto.UpdateAssetRequest
import com.ongo.common.ResData
import com.ongo.common.exception.FileValidationException
import com.ongo.common.util.FileValidationUtil
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Tag(name = "에셋 관리", description = "이미지, 오디오, 템플릿 등 에셋 파일 관리")
@RestController
@RequestMapping("/api/v1/assets")
class AssetController(
    private val assetUseCase: AssetUseCase,
) {

    @Operation(summary = "에셋 목록 조회", description = "사용자의 에셋을 필터링하여 조회합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "에셋 목록 조회 성공"),
    )
    @GetMapping
    fun listAssets(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) fileType: String?,
        @RequestParam(required = false) folder: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<AssetListResponse>> {
        val result = assetUseCase.listAssets(userId, fileType, folder, page, size)
        return ResData.success(result)
    }

    @Operation(summary = "에셋 업로드", description = "새로운 에셋 파일을 업로드합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "에셋 업로드 성공"),
    )
    @PostMapping
    fun uploadAsset(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam file: MultipartFile,
        @RequestParam(defaultValue = "default") folder: String,
        @RequestParam(required = false) tags: List<String>?,
    ): ResponseEntity<ResData<AssetResponse>> {
        val originalFilename = file.originalFilename?.trim().orEmpty()
        val contentType = file.contentType
            ?.lowercase()
            ?.substringBefore(';')
            ?.trim()
            .orEmpty()
        if (originalFilename.isBlank() || originalFilename.contains("..") ||
            originalFilename.contains('/') || originalFilename.contains('\\')) {
            throw FileValidationException("파일 이름이 유효하지 않습니다")
        }
        if (file.size <= 0 || file.size > 2L * 1024 * 1024 * 1024) {
            throw FileValidationException("에셋 파일 크기는 2GB 이하여야 합니다")
        }
        val allowedContentType = contentType.startsWith("image/") ||
            contentType.startsWith("audio/") ||
            contentType.startsWith("video/") ||
            contentType in setOf(
                "application/json",
                "application/pdf",
                "application/zip",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            )
        if (!allowedContentType) {
            throw FileValidationException("지원하지 않는 에셋 MIME 타입입니다: $contentType")
        }
        file.inputStream.use { FileValidationUtil.validateAssetContent(it, contentType) }

        val filename = "${UUID.randomUUID()}_$originalFilename"
        val fileType = resolveFileType(contentType)
        val storageKey = "assets/$userId/$filename"

        val result = assetUseCase.createAsset(
            userId = userId,
            filename = filename,
            originalFilename = originalFilename,
            fileType = fileType,
            fileSizeBytes = file.size,
            mimeType = contentType,
            tags = tags ?: emptyList(),
            folder = folder,
            width = null,
            height = null,
            durationSeconds = null,
            inputStream = file.inputStream,
            storageKey = storageKey,
        )
        return ResData.success(result, "에셋이 업로드되었습니다")
    }

    @Operation(summary = "에셋 수정", description = "에셋의 태그 또는 폴더를 수정합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "에셋 수정 성공"),
        ApiResponse(responseCode = "404", description = "에셋을 찾을 수 없음"),
    )
    @PutMapping("/{id}")
    fun updateAsset(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateAssetRequest,
    ): ResponseEntity<ResData<AssetResponse>> {
        val result = assetUseCase.updateAsset(userId, id, request)
        return ResData.success(result, "에셋이 수정되었습니다")
    }

    @Operation(summary = "에셋 삭제", description = "지정된 에셋을 삭제합니다.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "에셋 삭제 성공"),
        ApiResponse(responseCode = "404", description = "에셋을 찾을 수 없음"),
    )
    @DeleteMapping("/{id}")
    fun deleteAsset(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        assetUseCase.deleteAsset(userId, id)
        return ResData.success(null, "에셋이 삭제되었습니다")
    }

    private fun resolveFileType(contentType: String?): String = when {
        contentType?.startsWith("video/") == true -> "VIDEO"
        contentType?.startsWith("image/") == true -> "IMAGE"
        contentType?.startsWith("audio/") == true -> "AUDIO"
        else -> "TEMPLATE"
    }
}
