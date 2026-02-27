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

    @Operation(summary = "내 포트폴리오 조회 (루트)")
    @GetMapping
    fun getMyPortfolioRoot(
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

    @Operation(summary = "포트폴리오 프로필 수정")
    @PutMapping("/profile")
    fun updateProfile(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: UpdateProfileRequest,
    ): ResponseEntity<ResData<PortfolioResponse>> {
        val result = portfolioUseCase.updateProfile(userId, request)
        return ResData.success(result, "프로필이 수정되었습니다")
    }

    @Operation(summary = "쇼케이스 순서 변경")
    @PutMapping("/showcase/order")
    fun updateShowcaseOrder(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: ShowcaseOrderRequest,
    ): ResponseEntity<ResData<PortfolioResponse>> {
        val result = portfolioUseCase.updateShowcaseOrder(userId, request.contentIds)
        return ResData.success(result, "쇼케이스 순서가 변경되었습니다")
    }

    @Operation(summary = "쇼케이스 콘텐츠 추가")
    @PostMapping("/showcase")
    fun addShowcaseContent(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: AddShowcaseRequest,
    ): ResponseEntity<ResData<ShowcaseItemResponse>> {
        val result = portfolioUseCase.addShowcase(userId, request)
        return ResData.success(result, "쇼케이스 콘텐츠가 추가되었습니다")
    }

    @Operation(summary = "쇼케이스 콘텐츠 삭제")
    @DeleteMapping("/showcase/{contentId}")
    fun removeShowcaseContent(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable contentId: Long,
    ): ResponseEntity<ResData<PortfolioResponse>> {
        val result = portfolioUseCase.removeShowcase(userId, contentId)
        return ResData.success(result, "쇼케이스 콘텐츠가 삭제되었습니다")
    }

    @Operation(summary = "브랜드 협업 추가")
    @PostMapping("/collaborations")
    fun addBrandCollaboration(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: AddCollaborationRequest,
    ): ResponseEntity<ResData<CollaborationResponse>> {
        val result = portfolioUseCase.addCollaboration(userId, request)
        return ResData.success(result, "브랜드 협업이 추가되었습니다")
    }

    @Operation(summary = "브랜드 협업 삭제")
    @DeleteMapping("/collaborations/{collabId}")
    fun removeBrandCollaboration(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @PathVariable collabId: Long,
    ): ResponseEntity<ResData<PortfolioResponse>> {
        val result = portfolioUseCase.removeCollaboration(userId, collabId)
        return ResData.success(result, "브랜드 협업이 삭제되었습니다")
    }

    @Operation(summary = "포트폴리오 설정 수정")
    @PutMapping("/settings")
    fun updateSettings(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @RequestBody request: UpdateSettingsRequest,
    ): ResponseEntity<ResData<PortfolioResponse>> {
        val result = portfolioUseCase.updateSettings(userId, request)
        return ResData.success(result, "포트폴리오 설정이 수정되었습니다")
    }

    @Operation(summary = "포트폴리오 PDF 내보내기")
    @GetMapping("/export/pdf")
    fun exportPdf(
        @Parameter(hidden = true) @CurrentUser userId: Long,
    ): ResponseEntity<ByteArray> {
        val pdfBytes = portfolioUseCase.exportPdf(userId)
        return ResponseEntity.ok()
            .header("Content-Type", "application/pdf")
            .header("Content-Disposition", "attachment; filename=portfolio.pdf")
            .body(pdfBytes)
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
