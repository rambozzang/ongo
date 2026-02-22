package com.ongo.api.video

import com.ongo.api.video.dto.*
import com.ongo.application.video.CaptionUseCase
import com.ongo.application.video.CrossPlatformOptimizationUseCase
import com.ongo.application.video.PublishVideoUseCase
import com.ongo.application.video.PlatformUploadConfig
import com.ongo.application.video.ThumbnailUseCase
import com.ongo.application.video.UploadVideoUseCase
import com.ongo.application.video.VideoProcessingProgressUseCase
import com.ongo.application.video.VideoQueryUseCase
import com.ongo.application.video.VideoResizeUseCase
import com.ongo.application.video.dto.OptimizationCheckRequest
import com.ongo.application.video.dto.OptimizationCheckResponse
import com.ongo.application.video.dto.ResizeRequest
import com.ongo.application.video.dto.VideoResizeResponse
import com.ongo.domain.video.VideoMediaInfoRepository
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
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@Tag(name = "영상 관리", description = "영상 업로드, 멀티플랫폼 게시, 조회, 수정, 삭제")
@RestController
@RequestMapping("/api/v1/videos")
class VideoController(
    private val uploadVideoUseCase: UploadVideoUseCase,
    private val publishVideoUseCase: PublishVideoUseCase,
    private val videoQueryUseCase: VideoQueryUseCase,
    private val crossPlatformOptimizationUseCase: CrossPlatformOptimizationUseCase,
    private val thumbnailUseCase: ThumbnailUseCase,
    private val captionUseCase: CaptionUseCase,
    private val progressUseCase: VideoProcessingProgressUseCase,
    private val mediaInfoRepository: VideoMediaInfoRepository,
    private val videoResizeUseCase: VideoResizeUseCase,
) {

    @Operation(
        summary = "콘텐츠 생성",
        description = "메타데이터만으로 콘텐츠 레코드를 생성합니다. 이미지 업로드 또는 AI 파이프라인 등 파일 업로드 없이 레코드가 필요한 경우에 사용합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "콘텐츠 생성 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
    )
    @RequiresPermission(Permission.VIDEO_CREATE)
    @PostMapping
    fun createVideo(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody req: CreateVideoRequest,
    ): ResponseEntity<ResData<VideoDetailResponse>> {
        val video = uploadVideoUseCase.createVideo(
            userId = userId,
            title = req.title,
            description = req.description,
            tags = req.tags ?: emptyList(),
            category = req.category,
            thumbnailUrl = req.thumbnailUrl,
            mediaType = req.mediaType,
        )
        val detail = videoQueryUseCase.getVideoDetail(userId, video.id!!)
        return ResData.success(detail.toResponse())
    }

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
            mediaType = req.mediaType,
        )
        return ResData.success(
            InitUploadResponse(
                videoId = result.videoId,
                uploadUrl = result.uploadUrl,
                tusEndpoint = result.tusEndpoint,
                mediaType = result.mediaType,
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
                    mediaType = item.mediaType,
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

    @Operation(summary = "미디어 분석 정보", description = "FFprobe 기반 미디어 분석 정보를 조회합니다.")
    @GetMapping("/{id}/media-info")
    fun getMediaInfo(
        @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Any?>> {
        videoQueryUseCase.validateOwnership(userId, id)
        val info = mediaInfoRepository.findByVideoId(id)
        return ResData.success(info)
    }

    @Operation(summary = "자동 생성 썸네일 목록", description = "FFmpeg 씬 감지로 자동 생성된 썸네일 목록을 조회합니다.")
    @GetMapping("/{id}/thumbnails")
    fun getThumbnails(
        @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Any>> {
        val result = thumbnailUseCase.getThumbnails(userId, id)
        return ResData.success(result)
    }

    @Operation(summary = "대표 썸네일 선택", description = "자동 생성된 썸네일 중 대표 이미지를 선택합니다.")
    @PostMapping("/{id}/thumbnails/select")
    fun selectThumbnail(
        @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
        @RequestBody body: Map<String, Int>,
    ): ResponseEntity<ResData<Nothing?>> {
        val index = body["index"] ?: throw IllegalArgumentException("index 필수")
        thumbnailUseCase.selectThumbnail(userId, id, index)
        return ResData.success(null, "썸네일이 선택되었습니다")
    }

    @Operation(summary = "커스텀 썸네일 업로드", description = "사용자가 직접 썸네일 이미지를 업로드합니다.")
    @PostMapping("/{id}/thumbnails/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadCustomThumbnail(
        @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<ResData<Map<String, String>>> {
        val url = thumbnailUseCase.uploadCustomThumbnail(
            userId, id, file.inputStream, file.contentType ?: "image/jpeg", file.size,
        )
        return ResData.success(mapOf("url" to url))
    }

    @Operation(summary = "자막 조회", description = "영상의 자동 생성된 자막 또는 편집된 자막을 조회합니다.")
    @GetMapping("/{id}/captions")
    fun getCaptions(
        @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Any>> {
        val captions = captionUseCase.getCaptions(userId, id)
        return ResData.success(captions)
    }

    @Operation(summary = "자막 생성", description = "AI(Whisper)를 사용하여 자막을 자동 생성합니다. AI 크레딧이 차감됩니다.")
    @PostMapping("/{id}/captions/generate")
    fun generateCaption(
        @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<ResData<Nothing?>> {
        val language = body["language"] ?: "ko"
        captionUseCase.generateCaption(userId, id, language)
        return ResData.success(null, "자막 생성이 시작되었습니다")
    }

    @Operation(summary = "자막 수정", description = "자동 생성된 자막을 직접 편집합니다.")
    @PutMapping("/{id}/captions")
    fun updateCaption(
        @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<ResData<Any>> {
        val language = body["language"] ?: "ko"
        val content = body["content"] ?: throw IllegalArgumentException("content 필수")
        val result = captionUseCase.updateCaption(userId, id, language, content)
        return ResData.success(result)
    }

    @Operation(summary = "처리 진행률 SSE 스트림", description = "영상 처리(분석/트랜스코딩/썸네일/자막) 진행률을 SSE로 실시간 스트리밍합니다.")
    @GetMapping("/{id}/progress", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamProgress(
        @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
    ): SseEmitter {
        progressUseCase.validateAccess(userId, id)

        val emitter = SseEmitter(300_000L) // 5 min timeout

        Thread.startVirtualThread {
            try {
                while (true) {
                    val progressList = progressUseCase.fetchProgress(id)
                    emitter.send(
                        SseEmitter.event()
                            .name("progress")
                            .data(progressList)
                    )

                    val allDone = progressList.isNotEmpty() && progressList.all { it.progressPercent >= 100 }
                    if (allDone) {
                        emitter.complete()
                        break
                    }

                    Thread.sleep(1000)
                }
            } catch (_: Exception) {
                emitter.completeWithError(Exception("SSE stream ended"))
            }
        }

        return emitter
    }

    @Operation(summary = "이미지 업로드", description = "콘텐츠 이미지를 업로드합니다 (다중 파일 지원).")
    @RequiresPermission(Permission.VIDEO_CREATE)
    @PostMapping("/{id}/images", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadImages(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "영상/콘텐츠 ID") @PathVariable id: Long,
        @RequestParam("files") files: List<MultipartFile>,
    ): ResponseEntity<ResData<List<ContentImageItem>>> {
        val images = videoQueryUseCase.uploadContentImages(userId, id, files)
        return ResData.success(images.map { it.toItem() })
    }

    @Operation(summary = "이미지 목록 조회", description = "콘텐츠에 첨부된 이미지 목록을 조회합니다.")
    @GetMapping("/{id}/images")
    fun getImages(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "영상/콘텐츠 ID") @PathVariable id: Long,
    ): ResponseEntity<ResData<List<ContentImageItem>>> {
        val images = videoQueryUseCase.getContentImages(userId, id)
        return ResData.success(images.map { it.toItem() })
    }

    @Operation(summary = "이미지 순서 변경", description = "콘텐츠 이미지의 표시 순서를 변경합니다.")
    @PutMapping("/{id}/images/reorder")
    fun reorderImages(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "영상/콘텐츠 ID") @PathVariable id: Long,
        @RequestBody body: Map<String, List<Long>>,
    ): ResponseEntity<ResData<Nothing?>> {
        val imageIds = body["imageIds"] ?: throw IllegalArgumentException("imageIds 필수")
        videoQueryUseCase.reorderContentImages(userId, id, imageIds)
        return ResData.success(null, "이미지 순서가 변경되었습니다")
    }

    @Operation(summary = "영상 리사이즈 요청", description = "원본 영상을 선택한 비율(9:16, 1:1, 4:5, 16:9)로 리사이즈합니다. AI 크레딧이 차감됩니다.")
    @PostMapping("/{id}/resize")
    fun requestResize(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
        @RequestBody request: ResizeRequest,
    ): ResponseEntity<ResData<List<VideoResizeResponse>>> =
        ResData.success(videoResizeUseCase.requestResize(userId, id, request))

    @Operation(summary = "리사이즈 결과 조회", description = "영상의 리사이즈 결과 목록을 조회합니다.")
    @GetMapping("/{id}/resizes")
    fun getResizes(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<List<VideoResizeResponse>>> =
        ResData.success(videoResizeUseCase.getResizes(userId, id))

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
    autoThumbnails = autoThumbnails,
    selectedThumbnailIndex = selectedThumbnailIndex,
    mediaType = mediaType,
    status = status,
    contentImages = contentImages.map { img ->
        ContentImageItem(
            id = img.id,
            imageUrl = img.imageUrl,
            displayOrder = img.displayOrder,
            width = img.width,
            height = img.height,
            fileSizeBytes = img.fileSizeBytes,
            originalFilename = img.originalFilename,
        )
    },
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
    createdAt = createdAt,
)

private fun com.ongo.application.video.ContentImageResult.toItem() = ContentImageItem(
    id = id,
    imageUrl = imageUrl,
    displayOrder = displayOrder,
    width = width,
    height = height,
    fileSizeBytes = fileSizeBytes,
    originalFilename = originalFilename,
)
