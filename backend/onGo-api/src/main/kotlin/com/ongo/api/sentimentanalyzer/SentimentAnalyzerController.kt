package com.ongo.api.sentimentanalyzer

import com.ongo.application.sentimentanalyzer.SentimentAnalyzerUseCase
import com.ongo.application.sentimentanalyzer.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "감정 분석기", description = "콘텐츠 댓글 감정 분석 및 인사이트")
@RestController
@RequestMapping("/api/v1/sentiment-analyzer")
class SentimentAnalyzerController(
    private val useCase: SentimentAnalyzerUseCase
) {

    @Operation(summary = "분석 결과 목록 조회")
    @GetMapping
    fun getResults(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<SentimentResultResponse>>> {
        val result = useCase.getResults(userId)
        return ResData.success(result)
    }

    @Operation(summary = "감정 분석 실행")
    @PostMapping("/analyze")
    fun analyze(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: AnalyzeSentimentRequest,
    ): ResponseEntity<ResData<SentimentResultResponse>> {
        val result = useCase.analyze(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "댓글 감정 목록 조회")
    @GetMapping("/{resultId}/comments")
    fun getComments(
        @PathVariable resultId: Long,
        @RequestParam(required = false) sentiment: String?,
    ): ResponseEntity<ResData<List<CommentSentimentResponse>>> {
        val result = useCase.getComments(resultId, sentiment)
        return ResData.success(result)
    }

    @Operation(summary = "감정 분석 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<SentimentAnalyzerSummaryResponse>> {
        val result = useCase.getSummary(userId)
        return ResData.success(result)
    }
}
