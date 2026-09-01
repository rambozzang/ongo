package com.ongo.api.asset

import com.ongo.api.config.CurrentUser
import com.ongo.application.asset.AssetUseCase
import com.ongo.application.asset.dto.AssetListResponse
import com.ongo.application.asset.dto.AssetResponse
import com.ongo.application.asset.dto.UpdateAssetRequest
import com.ongo.domain.asset.AssetQuery
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
        @Parameter(description = "파일명·태그 부분 일치") @RequestParam(required = false) search: String?,
        @Parameter(description = "정확히 일치하는 태그 하나") @RequestParam(required = false) tag: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<AssetListResponse>> {
        val result = assetUseCase.listAssets(
            userId = userId,
            query = AssetQuery(fileType = fileType, folder = folder, search = search, tag = tag),
            page = page.coerceAtLeast(0),
            size = size.coerceIn(MIN_PAGE_SIZE, MAX_PAGE_SIZE),
        )
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

        // 저장 이름은 ASCII 로 정제한다. 원본 이름은 아래 originalFilename 으로 그대로 남는다.
        val filename = "${UUID.randomUUID()}_${safeObjectName(originalFilename)}"
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

    /**
     * 스토리지 키에 쓸 **ASCII 파일명**.
     *
     * 원본명을 그대로 키에 넣으면 두 가지가 깨진다.
     *
     *  - **길이.** 오브젝트 키의 한글은 URL 에서 퍼센트 인코딩돼 글자당 9 자가 된다.
     *    서명 쿼리(~314 자)와 호스트·UUID(~127 자)만으로 이미 441 자라, 한글 파일명
     *    하나면 presigned URL 이 500 자를 넘긴다.
     *  - **키 검증.** 스토리지 어댑터가 키를 `^[a-zA-Z0-9\-_./]+$` 로 검사한다
     *    (`S3StorageClient.validateStorageKey`). 한글·공백이 섞이면 URL 발급에서 터진다.
     *
     * 영상 업로드가 쓰는 것과 같은 규칙이다(`VideoStorageService.safeFilename`).
     * 사람이 읽을 이름은 `original_filename` 에 원본 그대로 남으므로 표시에는 영향이 없다.
     */
    private fun safeObjectName(filename: String): String =
        filename.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .takeLast(MAX_OBJECT_NAME_LENGTH)
            .ifBlank { DEFAULT_OBJECT_NAME }

    private fun resolveFileType(contentType: String?): String = when {
        contentType?.startsWith("video/") == true -> "VIDEO"
        contentType?.startsWith("image/") == true -> "IMAGE"
        contentType?.startsWith("audio/") == true -> "AUDIO"
        else -> "TEMPLATE"
    }

    private companion object {
        /**
         * 한 번에 가져갈 수 있는 최대 건수.
         *
         * 상한이 없으면 `size=1000000` 한 번으로 전 행을 읽고, 행마다 다운로드 URL 을
         * 새로 서명하는 비용까지 함께 든다. 다른 목록 질의도 같은 이유로 상한을 둔다
         * (`ShortsRunJooqRepository.findByStatus` 는 `coerceIn(1, 200)`).
         */
        const val MAX_PAGE_SIZE = 100
        const val MIN_PAGE_SIZE = 1
        const val MAX_OBJECT_NAME_LENGTH = 180
        const val DEFAULT_OBJECT_NAME = "upload.bin"
    }
}
