package com.ongo.api.contentstudio

import com.ongo.application.contentstudio.ContentStudioUseCase
import com.ongo.application.contentstudio.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "콘텐츠 스튜디오", description = "AI 콘텐츠 클립, 자막, 썸네일 관리")
@RestController
@RequestMapping("/api/v1/content-studio")
class ContentStudioController(
    private val contentStudioUseCase: ContentStudioUseCase
) {

    @Operation(summary = "영상 목록 조회 (콘텐츠 스튜디오용)")
    @GetMapping("/videos")
    fun listVideos(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<VideoSummaryResponse>>> {
        val result = contentStudioUseCase.listVideos(userId)
        return ResData.success(result)
    }

    @Operation(summary = "히스토리 조회")
    @GetMapping("/history")
    fun listHistory(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<ContentStudioHistoryResponse>>> {
        val result = contentStudioUseCase.listHistory(userId)
        return ResData.success(result)
    }

    @Operation(summary = "클립 목록 조회")
    @GetMapping("/clips")
    fun listClips(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<ContentClipResponse>>> {
        val result = contentStudioUseCase.listClips(userId)
        return ResData.success(result)
    }

    @Operation(summary = "클립 생성")
    @PostMapping("/clips")
    fun createClip(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateClipRequest,
    ): ResponseEntity<ResData<ContentClipResponse>> {
        val result = contentStudioUseCase.createClip(userId, request)
        return ResData.success(result, "클립이 생성되었습니다")
    }

    @Operation(summary = "클립 삭제")
    @DeleteMapping("/clips/{id}")
    fun deleteClip(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        contentStudioUseCase.deleteClip(userId, id)
        return ResData.success(null, "클립이 삭제되었습니다")
    }

    @Operation(summary = "자막 목록 조회")
    @GetMapping("/captions")
    fun listCaptions(
        @RequestParam videoId: Long,
    ): ResponseEntity<ResData<List<VideoCaptionResponse>>> {
        val result = contentStudioUseCase.listCaptions(videoId)
        return ResData.success(result)
    }

    @Operation(summary = "자막 생성")
    @PostMapping("/captions")
    fun createCaption(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateCaptionRequest,
    ): ResponseEntity<ResData<VideoCaptionResponse>> {
        val result = contentStudioUseCase.createCaption(userId, request)
        return ResData.success(result, "자막이 생성되었습니다")
    }

    @Operation(summary = "AI 자막 자동 생성")
    @PostMapping("/captions/generate")
    fun generateCaption(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: GenerateCaptionRequest,
    ): ResponseEntity<ResData<VideoCaptionResponse>> {
        val result = contentStudioUseCase.generateCaption(userId, request)
        return ResData.success(result, "자막이 생성되었습니다")
    }

    @Operation(summary = "자막 수정")
    @PutMapping("/captions/{captionId}")
    fun updateCaption(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable captionId: Long,
        @RequestBody request: UpdateCaptionRequest,
    ): ResponseEntity<ResData<VideoCaptionResponse>> {
        val result = contentStudioUseCase.updateCaption(userId, captionId, request)
        return ResData.success(result, "자막이 수정되었습니다")
    }

    @Operation(summary = "자막 삭제")
    @DeleteMapping("/captions/{id}")
    fun deleteCaption(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        contentStudioUseCase.deleteCaption(userId, id)
        return ResData.success(null, "자막이 삭제되었습니다")
    }

    @Operation(summary = "썸네일 목록 조회")
    @GetMapping("/thumbnails")
    fun listThumbnails(
        @RequestParam videoId: Long,
    ): ResponseEntity<ResData<List<AiThumbnailResponse>>> {
        val result = contentStudioUseCase.listThumbnails(videoId)
        return ResData.success(result)
    }

    @Operation(summary = "AI 썸네일 생성")
    @PostMapping("/thumbnails")
    fun generateThumbnail(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: GenerateThumbnailRequest,
    ): ResponseEntity<ResData<AiThumbnailResponse>> {
        val result = contentStudioUseCase.generateThumbnail(userId, request)
        return ResData.success(result, "썸네일이 생성되었습니다")
    }

    @Operation(summary = "썸네일 삭제")
    @DeleteMapping("/thumbnails/{id}")
    fun deleteThumbnail(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        contentStudioUseCase.deleteThumbnail(userId, id)
        return ResData.success(null, "썸네일이 삭제되었습니다")
    }
}
