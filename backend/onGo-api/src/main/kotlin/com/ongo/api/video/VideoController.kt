package com.ongo.api.video

import com.ongo.api.video.dto.*
import com.ongo.application.video.CrossPlatformOptimizationUseCase
import com.ongo.application.video.PublishVideoUseCase
import com.ongo.application.video.PlatformUploadConfig
import com.ongo.application.video.UploadVideoUseCase
import com.ongo.application.video.VideoQueryUseCase
import com.ongo.application.video.dto.OptimizationCheckRequest
import com.ongo.application.video.dto.OptimizationCheckResponse
import com.ongo.common.ResData
import com.ongo.common.annotation.RequiresPermission
import com.ongo.common.config.PageResponse
import com.ongo.common.enums.Permission
import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.common.util.safeValueOfOrThrow
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "영상 관리", description = "영상 업로드, 멀티플랫폼 게시, 조회, 수정, 삭제")
@RestController
@RequestMapping("/api/v1/videos")
class VideoController(
    private val uploadVideoUseCase: UploadVideoUseCase,
    private val publishVideoUseCase: PublishVideoUseCase,
    private val videoQueryUseCase: VideoQueryUseCase,
    private val crossPlatformOptimizationUseCase: CrossPlatformOptimizationUseCase,
) {

    @Operation(
        summary = "업로드 초기화",
        description = "영상 업로드를 위한 Tus 엔드포인트와 업로드 URL을 생성합니다. 파일 확장자 및 크기 검증이 수행됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "업로드 초기화 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (지원하지 않는 파일 형식 또는 파일 크기 초과)"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @RequiresPermission(Permission.VIDEO_CREATE)
    @PostMapping("/upload/init")
    fun initUpload(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody req: InitUploadRequest,
    ): ResponseEntity<ResData<InitUploadResponse>> {
        val result = uploadVideoUseCase.initUpload(
            userId = userId,
            filename = req.filename,
            fileSize = req.fileSize,
            contentType = req.contentType,
        )
        return ResData.success(
            InitUploadResponse(
                videoId = result.videoId,
                uploadUrl = result.uploadUrl,
                tusEndpoint = result.tusEndpoint,
            )
        )
    }

    @Operation(
        summary = "멀티플랫폼 게시",
        description = "업로드 완료된 영상을 선택한 플랫폼들에 동시 게시합니다. 각 플랫폼별 제목, 설명, 태그, 공개 설정을 개별 지정할 수 있습니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "게시 요청 성공 (각 플랫폼별 상태 포함)"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (필수 항목 누락 또는 유효성 검증 실패)"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @RequiresPermission(Permission.VIDEO_PUBLISH)
    @PostMapping("/{id}/publish")
    fun publishVideo(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "게시할 영상 ID") @PathVariable id: Long,
        @Valid @RequestBody req: PublishRequest,
    ): ResponseEntity<ResData<PublishResponse>> {
        val configs = req.platforms.map { config ->
            PlatformUploadConfig(
                platform = config.platform,
                videoUploadId = 0, // will be assigned in use case
                title = config.title ?: "",
                description = config.description,
                tags = config.tags ?: emptyList(),
                visibility = config.visibility,
                thumbnailUrl = config.thumbnailUrl,
                scheduledAt = config.scheduledAt,
            )
        }

        val result = publishVideoUseCase.publishVideo(userId, id, configs)
        return ResData.success(
            PublishResponse(
                videoId = result.videoId,
                uploads = result.uploads.map { upload ->
                    UploadStatusItem(
                        platform = upload.platform,
                        status = upload.status,
                        errorMessage = upload.errorMessage,
                    )
                },
            )
        )
    }

    @Operation(
        summary = "영상 목록 조회",
        description = "사용자의 영상 목록을 페이지네이션으로 조회합니다. 상태, 플랫폼, 검색어로 필터링할 수 있습니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "영상 목록 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @RequiresPermission(Permission.VIDEO_READ)
    @GetMapping
    fun listVideos(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지당 항목 수") @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "업로드 상태 필터 (DRAFT, UPLOADING, PROCESSING, PUBLISHED, FAILED 등)") @RequestParam(required = false) status: String?,
        @Parameter(description = "플랫폼 필터 (YOUTUBE, TIKTOK, INSTAGRAM, NAVERCLIP)") @RequestParam(required = false) platform: String?,
        @Parameter(description = "검색어 (제목 기준)") @RequestParam(required = false) search: String?,
    ): ResponseEntity<ResData<PageResponse<VideoListItem>>> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val uploadStatus = status?.let { safeValueOfOrThrow<UploadStatus>(it) }
        val platformEnum = platform?.let { safeValueOfOrThrow<Platform>(it) }

        val result = videoQueryUseCase.listVideos(userId, safePage, safeSize, uploadStatus, platformEnum, search)
        val mapped = PageResponse.of(
            content = result.content.map { item ->
                VideoListItem(
                    id = item.id,
                    title = item.title,
                    thumbnailUrl = item.thumbnailUrl,
                    status = item.status,
                    uploads = item.uploads.map { upload ->
                        PlatformStatusItem(
                            platform = upload.platform,
                            status = upload.status,
                            platformUrl = upload.platformUrl,
                        )
                    },
                    totalViews = 0,
                    createdAt = item.createdAt,
                )
            },
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
        )
        return ResData.success(mapped)
    }

    @Operation(
        summary = "영상 상세 조회",
        description = "지정된 영상의 상세 정보를 조회합니다. 각 플랫폼별 업로드 상태, 메타데이터, 파일 정보 등이 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "영상 상세 조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @GetMapping("/{id}")
    fun getVideo(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "조회할 영상 ID") @PathVariable id: Long,
    ): ResponseEntity<ResData<VideoDetailResponse>> {
        val result = videoQueryUseCase.getVideoDetail(userId, id)
        return ResData.success(result.toResponse())
    }

    @Operation(
        summary = "영상 수정",
        description = "지정된 영상의 제목, 설명, 태그, 카테고리, 썸네일 등의 메타데이터를 수정합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "영상 수정 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검증 실패)"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @RequiresPermission(Permission.VIDEO_UPDATE)
    @PutMapping("/{id}")
    fun updateVideo(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "수정할 영상 ID") @PathVariable id: Long,
        @Valid @RequestBody req: UpdateVideoRequest,
    ): ResponseEntity<ResData<VideoDetailResponse>> {
        val result = videoQueryUseCase.updateVideo(
            userId = userId,
            videoId = id,
            title = req.title,
            description = req.description,
            tags = req.tags,
            category = req.category,
            thumbnailIndex = req.thumbnailIndex,
        )
        return ResData.success(result.toResponse())
    }

    @Operation(
        summary = "영상 삭제",
        description = "지정된 영상을 삭제합니다. 관련된 모든 플랫폼 업로드 기록과 파일이 함께 삭제됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "영상 삭제 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @RequiresPermission(Permission.VIDEO_DELETE)
    @DeleteMapping("/{id}")
    fun deleteVideo(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "삭제할 영상 ID") @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        videoQueryUseCase.deleteVideo(userId, id)
        return ResData.success(null, "영상이 삭제되었습니다")
    }

    @Operation(
        summary = "업로드 재시도",
        description = "실패한 특정 플랫폼 업로드를 재시도합니다. FAILED 상태인 업로드만 재시도할 수 있습니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "재업로드 요청 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (재시도 불가능한 상태)"),
        ApiResponse(responseCode = "401", description = "인증 실패 (유효하지 않은 Access Token)"),
        ApiResponse(responseCode = "404", description = "영상 또는 플랫폼 업로드를 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @PostMapping("/{id}/retry/{platform}")
    fun retryUpload(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "재시도할 영상 ID") @PathVariable id: Long,
        @Parameter(description = "재시도할 플랫폼 (youtube, tiktok, instagram, naverclip)") @PathVariable platform: String,
    ): ResponseEntity<ResData<Nothing?>> {
        publishVideoUseCase.retryUpload(userId, id, platform)
        return ResData.success(null, "재업로드가 시작되었습니다")
    }

    @Operation(
        summary = "트랜스코딩 재시도",
        description = "실패한 특정 플랫폼의 영상 트랜스코딩을 재시도합니다. FAILED 상태인 변환본만 재시도할 수 있습니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "트랜스코딩 재시도 요청 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "404", description = "영상을 찾을 수 없음"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @PostMapping("/{id}/transcode/retry/{platform}")
    fun retryTranscode(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "영상 ID") @PathVariable id: Long,
        @Parameter(description = "플랫폼 (youtube, tiktok, instagram, naver_clip)") @PathVariable platform: String,
    ): ResponseEntity<ResData<Nothing?>> {
        videoQueryUseCase.retryTranscode(userId, id, platform)
        return ResData.success(null, "트랜스코딩 재시도가 시작되었습니다")
    }

    @Operation(
        summary = "크로스 플랫폼 최적화 검사",
        description = "영상 메타데이터를 각 플랫폼 기준에 맞게 최적화 검사합니다. 플랫폼별 점수(0-100)와 개선 제안을 반환합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "최적화 검사 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 내부 오류")
    )
    @PostMapping("/optimization-check")
    fun checkOptimization(
        @RequestBody request: OptimizationCheckRequest,
    ): ResponseEntity<ResData<OptimizationCheckResponse>> {
        return ResData.success(crossPlatformOptimizationUseCase.checkOptimization(request))
    }
}

private fun com.ongo.application.video.VideoDetailResult.toResponse() = VideoDetailResponse(
    id = id,
    title = title,
    description = description,
    tags = tags,
    category = category,
    fileUrl = fileUrl,
    fileSize = fileSize,
    duration = duration,
    resolution = resolution,
    thumbnails = thumbnails,
    status = status,
    uploads = uploads.map { upload ->
        PlatformUploadDetail(
            id = upload.id,
            platform = upload.platform,
            status = upload.status,
            platformVideoId = upload.platformVideoId,
            platformUrl = upload.platformUrl,
            errorMessage = upload.errorMessage,
            publishedAt = upload.publishedAt,
            meta = upload.meta?.let { meta ->
                PlatformMetaDetail(
                    title = meta.title,
                    description = meta.description,
                    tags = meta.tags,
                    visibility = meta.visibility,
                    customThumbnailUrl = meta.customThumbnailUrl,
                )
            },
        )
    },
    variants = variants.map { v ->
        VideoVariantItem(
            platform = v.platform,
            status = v.status,
            fileUrl = v.fileUrl,
            fileSizeBytes = v.fileSizeBytes,
            width = v.width,
            height = v.height,
            errorMessage = v.errorMessage,
        )
    },
    createdAt = createdAt,
)
