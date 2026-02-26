package com.ongo.api.subtitletranslation

import com.ongo.application.subtitletranslation.SubtitleTranslationUseCase
import com.ongo.application.subtitletranslation.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "자막 번역", description = "AI 자동 자막 번역 및 편집")
@RestController
@RequestMapping("/api/v1/subtitle-translations")
class SubtitleTranslationController(
    private val useCase: SubtitleTranslationUseCase
) {

    @Operation(summary = "번역 목록 조회")
    @GetMapping
    fun getTranslations(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(required = false) status: String?,
    ): ResponseEntity<ResData<List<SubtitleTranslationResponse>>> {
        val result = useCase.getTranslations(userId, status)
        return ResData.success(result)
    }

    @Operation(summary = "번역 상세 조회")
    @GetMapping("/{id}")
    fun getTranslation(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<SubtitleTranslationResponse>> {
        val result = useCase.getTranslation(id) ?: throw IllegalArgumentException("번역을 찾을 수 없습니다")
        return ResData.success(result)
    }

    @Operation(summary = "번역 요청 생성")
    @PostMapping
    fun createTranslation(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreateTranslationRequest,
    ): ResponseEntity<ResData<SubtitleTranslationResponse>> {
        val result = useCase.createTranslation(userId, request)
        return ResData.success(result, "번역이 요청되었습니다")
    }

    @Operation(summary = "번역 라인 목록 조회")
    @GetMapping("/{translationId}/lines")
    fun getLines(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable translationId: Long,
    ): ResponseEntity<ResData<List<TranslationLineResponse>>> {
        val result = useCase.getLines(translationId)
        return ResData.success(result)
    }

    @Operation(summary = "번역 라인 수정")
    @PatchMapping("/{translationId}/lines/{lineId}")
    fun updateLine(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable translationId: Long,
        @PathVariable lineId: Long,
        @RequestBody request: UpdateTranslationLineRequest,
    ): ResponseEntity<ResData<TranslationLineResponse>> {
        val result = useCase.updateLine(translationId, lineId, request)
        return ResData.success(result, "번역이 수정되었습니다")
    }

    @Operation(summary = "지원 언어 목록 조회")
    @GetMapping("/languages")
    fun getSupportedLanguages(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<SupportedLanguageResponse>>> {
        val result = useCase.getSupportedLanguages()
        return ResData.success(result)
    }

    @Operation(summary = "번역 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<SubtitleTranslationSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
