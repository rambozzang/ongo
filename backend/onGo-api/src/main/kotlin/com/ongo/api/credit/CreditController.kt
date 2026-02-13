package com.ongo.api.credit

import com.ongo.api.credit.dto.*
import com.ongo.application.credit.CreditPurchaseUseCase
import com.ongo.application.credit.CreditQueryUseCase
import com.ongo.common.ResData
import com.ongo.common.config.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "크레딧", description = "AI 크레딧 잔액 조회, 사용 내역, 충전")
@RestController
@RequestMapping("/api/v1/credits")
class CreditController(
    private val creditQueryUseCase: CreditQueryUseCase,
    private val creditPurchaseUseCase: CreditPurchaseUseCase,
) {

    @Operation(
        summary = "크레딧 잔액 조회",
        description = "사용자의 AI 크레딧 잔액을 조회합니다. 무료 크레딧과 구매 크레딧이 구분되어 표시되며, 무료 크레딧 리셋일도 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "401", description = "인증 실패")
    )
    @GetMapping
    fun getBalance(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
    ): ResponseEntity<ResData<CreditBalanceResponse>> {
        val result = creditQueryUseCase.getBalance(userId)
        return ResData.success(
            CreditBalanceResponse(
                totalBalance = result.totalBalance,
                freeRemaining = result.freeRemaining,
                freeMonthly = result.freeMonthly,
                purchasedBalance = result.purchasedBalance,
                freeResetDate = result.freeResetDate,
            )
        )
    }

    @Operation(
        summary = "크레딧 사용 내역",
        description = "페이지네이션된 크레딧 사용/충전 내역을 조회합니다. 각 내역에는 유형, 금액, 잔액, 사용 기능이 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (페이지 파라미터 오류)"),
        ApiResponse(responseCode = "401", description = "인증 실패")
    )
    @GetMapping("/transactions")
    fun getTransactions(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ResData<PageResponse<CreditTransactionResponse>>> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        val result = creditQueryUseCase.getTransactions(userId, safePage, safeSize)
        val mapped = PageResponse.of(
            content = result.content.map { tx ->
                CreditTransactionResponse(
                    id = tx.id,
                    type = tx.type,
                    amount = tx.amount,
                    balanceAfter = tx.balanceAfter,
                    feature = tx.feature,
                    createdAt = tx.createdAt,
                )
            },
            page = result.page,
            size = result.size,
            totalElements = result.totalElements,
        )
        return ResData.success(mapped)
    }

    @Operation(
        summary = "크레딧 충전",
        description = "크레딧 패키지를 선택하여 크레딧을 구매합니다. 결제 수단과 패키지 유형을 지정해야 합니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "크레딧 충전 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (패키지 또는 결제 수단 오류)"),
        ApiResponse(responseCode = "401", description = "인증 실패"),
        ApiResponse(responseCode = "500", description = "서버 오류 (결제 처리 실패)")
    )
    @PostMapping("/purchase")
    fun purchaseCredits(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Valid @RequestBody req: PurchaseCreditRequest,
    ): ResponseEntity<ResData<PurchaseResultResponse>> {
        val result = creditPurchaseUseCase.purchaseCredits(userId, req.packageType, req.paymentMethod)
        return ResData.success(
            PurchaseResultResponse(
                transactionId = result.transactionId,
                creditsAdded = result.creditsAdded,
                newBalance = result.newBalance,
                expiresAt = result.expiresAt,
            ),
            "크레딧 충전이 완료되었습니다",
        )
    }

    @Operation(
        summary = "크레딧 패키지 목록",
        description = "구매 가능한 크레딧 패키지 목록을 조회합니다. 패키지별 크레딧 수량, 가격, 유효기간, 크레딧당 단가가 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공")
    )
    @GetMapping("/packages")
    fun getPackages(): ResponseEntity<ResData<List<CreditPackageResponse>>> {
        val packages = creditPurchaseUseCase.getPackages()
        return ResData.success(
            packages.map { pkg ->
                CreditPackageResponse(
                    name = pkg.name,
                    displayName = pkg.displayName,
                    credits = pkg.credits,
                    price = pkg.price,
                    validDays = pkg.validDays,
                    pricePerCredit = pkg.pricePerCredit,
                )
            }
        )
    }
}
