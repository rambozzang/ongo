package com.ongo.api.subtitleeditor

import com.ongo.application.subtitleeditor.SubtitleEditorUseCase
import com.ongo.application.subtitleeditor.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "자막 편집기", description = "자막 트랙 생성, 수정, 삭제 관리")
@RestController
@RequestMapping("/api/v1/subtitle-editor")
class SubtitleEditorController(
    private val subtitleEditorUseCase: SubtitleEditorUseCase
) {

    @Operation(summary = "자막 트랙 목록 조회")
    @GetMapping("/tracks")
    fun listSubtitleTracks(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<SubtitleTrackResponse>>> {
        val result = subtitleEditorUseCase.listSubtitleTracks(userId)
        return ResData.success(result)
    }

    @Operation(summary = "영상별 자막 트랙 조회")
    @GetMapping("/tracks/by-video")
    fun listSubtitleTracksByVideo(
        @RequestParam videoId: Long,
    ): ResponseEntity<ResData<List<SubtitleTrackResponse>>> {
        val result = subtitleEditorUseCase.listSubtitleTracksByVideo(videoId)
        return ResData.success(result)
    }

    @Operation(summary = "자막 트랙 생성")
    @PostMapping("/tracks")
    fun createSubtitleTrack(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateSubtitleTrackRequest,
    ): ResponseEntity<ResData<SubtitleTrackResponse>> {
        val result = subtitleEditorUseCase.createSubtitleTrack(userId, request)
        return ResData.success(result, "자막 트랙이 생성되었습니다")
    }

    @Operation(summary = "자막 트랙 수정")
    @PutMapping("/tracks/{id}")
    fun updateSubtitleTrack(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
        @RequestBody request: UpdateSubtitleTrackRequest,
    ): ResponseEntity<ResData<SubtitleTrackResponse>> {
        val result = subtitleEditorUseCase.updateSubtitleTrack(userId, id, request)
        return ResData.success(result, "자막 트랙이 수정되었습니다")
    }

    @Operation(summary = "자막 트랙 삭제")
    @DeleteMapping("/tracks/{id}")
    fun deleteSubtitleTrack(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        subtitleEditorUseCase.deleteSubtitleTrack(userId, id)
        return ResData.success(null, "자막 트랙이 삭제되었습니다")
    }
}
