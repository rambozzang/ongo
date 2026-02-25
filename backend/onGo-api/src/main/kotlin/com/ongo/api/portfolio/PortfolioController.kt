package com.ongo.api.portfolio

import com.ongo.application.portfolio.PortfolioUseCase
import com.ongo.application.portfolio.dto.*
import com.ongo.common.ResData
import com.ongo.common.annotation.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "포트폴리오", description = "크리에이터 포트폴리오 관리")
@RestController
@RequestMapping("/api/v1/portfolios")
class PortfolioController(
    private val portfolioUseCase: PortfolioUseCase
) {

    @Operation(summary = "내 포트폴리오 조회")
    @GetMapping("/me")
    fun getMyPortfolio(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<PortfolioResponse?>> {
        val result = portfolioUseCase.getPortfolio(userId)
        return ResData.success(result)
    }

    @Operation(summary = "공개 포트폴리오 조회")
    @GetMapping("/public/{slug}")
    fun getPublicPortfolio(
        @PathVariable slug: String,
    ): ResponseEntity<ResData<PortfolioResponse>> {
        val result = portfolioUseCase.getPublicPortfolio(slug)
        return ResData.success(result)
    }

    @Operation(summary = "포트폴리오 생성/업데이트")
    @PostMapping
    fun createOrUpdatePortfolio(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: CreatePortfolioRequest,
    ): ResponseEntity<ResData<PortfolioResponse>> {
        val result = portfolioUseCase.createOrUpdatePortfolio(userId, request)
        return ResData.success(result, "포트폴리오가 저장되었습니다")
    }

    @Operation(summary = "포트폴리오 수정")
    @PutMapping
    fun updatePortfolio(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: UpdatePortfolioRequest,
    ): ResponseEntity<ResData<PortfolioResponse>> {
        val result = portfolioUseCase.updatePortfolio(userId, request)
        return ResData.success(result)
    }

    @Operation(summary = "포트폴리오 삭제")
    @DeleteMapping
    fun deletePortfolio(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ResData<Nothing?>> {
        portfolioUseCase.deletePortfolio(userId)
        return ResData.success(null, "포트폴리오가 삭제되었습니다")
    }
}
