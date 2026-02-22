package com.ongo.api.translation

import com.ongo.application.translation.TranslationUseCase
import com.ongo.application.translation.dto.*
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "다국어 번역", description = "영상 메타데이터 AI 번역")
@RestController
@RequestMapping("/api/v1/videos/{videoId}/translations")
class TranslationController(
    private val translationUseCase: TranslationUseCase,
) {

    @Operation(summary = "번역 목록 조회")
    @GetMapping
    fun getTranslations(@PathVariable videoId: Long): ResponseEntity<ResData<List<TranslationResponse>>> =
        ResData.success(translationUseCase.getTranslations(videoId))

    @Operation(summary = "번역 요청", description = "선택한 언어로 AI 번역을 시작합니다. (언어당 3 크레딧)")
    @PostMapping
    fun requestTranslation(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable videoId: Long,
        @RequestBody request: TranslateRequest,
    ): ResponseEntity<ResData<List<TranslationResponse>>> =
        ResData.success(translationUseCase.requestTranslation(userId, videoId, request))

    @Operation(summary = "번역 수정")
    @PutMapping("/{translationId}")
    fun updateTranslation(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable videoId: Long,
        @PathVariable translationId: Long,
        @RequestBody request: UpdateTranslationRequest,
    ): ResponseEntity<ResData<TranslationResponse>> =
        ResData.success(translationUseCase.updateTranslation(userId, translationId, request))

    @Operation(summary = "번역 삭제")
    @DeleteMapping("/{translationId}")
    fun deleteTranslation(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable videoId: Long,
        @PathVariable translationId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        translationUseCase.deleteTranslation(userId, translationId)
        return ResData.success(null, "번역이 삭제되었습니다")
    }
}
