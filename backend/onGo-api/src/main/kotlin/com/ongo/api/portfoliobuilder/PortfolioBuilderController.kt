package com.ongo.api.portfoliobuilder

import com.ongo.application.portfoliobuilder.PortfolioBuilderUseCase
import com.ongo.application.portfoliobuilder.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "포트폴리오 빌더", description = "크리에이터 포트폴리오 자동 생성")
@RestController
@RequestMapping("/api/v1/portfolio-builder")
class PortfolioBuilderController(
    private val portfolioBuilderUseCase: PortfolioBuilderUseCase
) {

    @Operation(summary = "포트폴리오 목록 조회")
    @GetMapping
    fun getPortfolios(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<List<PortfolioResponse>>> {
        val result = portfolioBuilderUseCase.getPortfolios(userId)
        return ResData.success(result)
    }

    @Operation(summary = "포트폴리오 생성")
    @PostMapping
    fun create(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreatePortfolioRequest,
    ): ResponseEntity<ResData<PortfolioResponse>> {
        val result = portfolioBuilderUseCase.create(userId, request)
        return ResData.success(result, "포트폴리오가 생성되었습니다")
    }

    @Operation(summary = "포트폴리오 공개")
    @PostMapping("/{id}/publish")
    fun publish(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ResData<PortfolioResponse>> {
        val result = portfolioBuilderUseCase.publish(userId, id)
        return ResData.success(result, "포트폴리오가 공개되었습니다")
    }

    @Operation(summary = "포트폴리오 빌더 요약")
    @GetMapping("/summary")
    fun getSummary(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<PortfolioBuilderSummaryResponse>> {
        val result = portfolioBuilderUseCase.getSummary(userId)
        return ResData.success(result)
    }
}
