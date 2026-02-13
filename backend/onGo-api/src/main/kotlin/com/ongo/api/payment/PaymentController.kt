package com.ongo.api.payment

import com.ongo.api.config.CurrentUser
import com.ongo.application.payment.dto.*
import com.ongo.application.payment.PaymentService
import com.ongo.common.ResData
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@Tag(name = "결제", description = "결제 내역 조회, PG사 웹훅 처리")
@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentService: PaymentService
) {

    @Operation(
        summary = "결제 내역 조회",
        description = "페이지네이션된 결제 기록을 조회합니다. 구독 결제 및 크레딧 구매 내역이 포함됩니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (페이지 파라미터 오류)"),
        ApiResponse(responseCode = "401", description = "인증 실패")
    )
    @GetMapping("/history")
    fun getPaymentHistory(
        @Parameter(hidden = true) @CurrentUser userId: Long,
        @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ResData<PaymentHistoryResponse>> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 100)
        return ResData.success(paymentService.getHistory(userId, safePage, safeSize))
    }

    @Operation(
        summary = "결제 웹훅 처리",
        description = "Toss Payments에서 전송하는 결제 결과 웹훅을 수신하고 처리합니다. PG사에서 직접 호출하는 엔드포인트입니다."
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "웹훅 처리 성공"),
        ApiResponse(responseCode = "400", description = "잘못된 요청 (유효하지 않은 페이로드)"),
        ApiResponse(responseCode = "500", description = "서버 오류 (웹훅 처리 실패)")
    )
    @PostMapping("/webhook")
    fun handleWebhook(
        @RequestHeader("Toss-Signature", required = false) signature: String?,
        @RequestBody payload: TossWebhookPayload,
    ): ResponseEntity<ResData<Nothing?>> {
        paymentService.handleWebhook(payload, signature)
        return ResData.success(null)
    }
}
