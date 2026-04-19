package com.ongo.api.keyword

import com.ongo.application.keyword.KeywordResearchUseCase
import com.ongo.application.keyword.dto.KeywordResearchHistoryResponse
import com.ongo.application.keyword.dto.KeywordResearchRequest
import com.ongo.application.keyword.dto.KeywordResearchResponse
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "키워드 리서치", description = "크로스 플랫폼 해시태그 & 키워드 리서치")
@RestController
@RequestMapping("/api/v1/keywords")
class KeywordResearchController(
    private val keywordResearchUseCase: KeywordResearchUseCase,
) {

    @Operation(summary = "키워드 분석", description = "AI가 크로스 플랫폼 키워드/해시태그를 분석합니다. 크레딧 3개 소모.")
    @PostMapping("/research")
    fun research(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: KeywordResearchRequest,
    ): ResponseEntity<ResData<KeywordResearchResponse>> {
        val result = keywordResearchUseCase.research(userId, request)
        return ResData.success(result, "키워드 분석이 완료되었습니다")
    }

    @Operation(summary = "키워드 분석 이력 조회", description = "과거 키워드 리서치 이력을 조회합니다.")
    @GetMapping("/history")
    fun getHistory(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<ResData<KeywordResearchHistoryResponse>> {
        val result = keywordResearchUseCase.getHistory(userId, page, size)
        return ResData.success(result)
    }
}
