package com.ongo.api.contenttranslator

import com.ongo.application.contenttranslator.ContentTranslatorUseCase
import com.ongo.application.contenttranslator.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "콘텐츠 번역기", description = "AI 기반 다국어 콘텐츠 번역")
@RestController
@RequestMapping("/api/v1/content-translator")
class ContentTranslatorController(
    private val contentTranslatorUseCase: ContentTranslatorUseCase
) {

    @Operation(summary = "번역 작업 목록 조회")
    @GetMapping
    fun getJobs(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<TranslationJobResponse>>> {
        val result = contentTranslatorUseCase.getJobs(userId)
        return ResData.success(result)
    }

    @Operation(summary = "번역 요청")
    @PostMapping("/translate")
    fun translate(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: TranslateRequest,
    ): ResponseEntity<ResData<TranslationJobResponse>> {
        val result = contentTranslatorUseCase.translate(userId, request)
        return ResData.success(result, "번역이 시작되었습니다")
    }

    @Operation(summary = "용어집 조회")
    @GetMapping("/glossary")
    fun getGlossary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<TranslationGlossaryResponse>>> {
        val result = contentTranslatorUseCase.getGlossary(userId)
        return ResData.success(result)
    }

    @Operation(summary = "용어집 추가")
    @PostMapping("/glossary")
    fun addGlossaryTerm(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: AddGlossaryTermRequest,
    ): ResponseEntity<ResData<TranslationGlossaryResponse>> {
        val result = contentTranslatorUseCase.addGlossaryTerm(userId, request)
        return ResData.success(result, "용어가 추가되었습니다")
    }

    @Operation(summary = "번역기 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<ContentTranslatorSummaryResponse>> {
        val result = contentTranslatorUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
