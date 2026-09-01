package com.ongo.api.portone

import com.ongo.api.config.CurrentUser
import com.ongo.application.portone.PortOneCheckoutIntent
import com.ongo.application.portone.PortOnePaymentResult
import com.ongo.application.portone.PortOnePaymentService
import com.ongo.application.subscription.SubscriptionBillingKeyUseCase
import com.ongo.application.portone.PortOneWebhookFormatException
import com.ongo.common.ResData
import com.ongo.common.exception.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/portone")
class PortOneController(
    private val service: PortOnePaymentService,
    private val billingKeyUseCase: SubscriptionBillingKeyUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/checkout/subscription")
    fun createSubscriptionCheckout(
        @CurrentUser userId: Long,
        @RequestBody request: SubscriptionCheckoutRequest,
    ): ResData<PortOneCheckoutIntent> = ResData(data = service.createSubscriptionCheckout(userId, request.planType, request.billingCycle))

    @PostMapping("/checkout/credit")
    fun createCreditCheckout(
        @CurrentUser userId: Long,
        @RequestBody request: CreditCheckoutRequest,
    ): ResData<PortOneCheckoutIntent> = ResData(data = service.createCreditCheckout(userId, request.packageName))

    /**
     * 브라우저가 발급받은 정기결제 수단을 등록한다.
     *
     * ## 왜 POST 본문인가
     *
     * 빌링키가 경로나 쿼리에 들어가면 접근 로그·리퍼러·브라우저 기록에 평문으로 남는다.
     * 이 값 하나로 고객에게 반복 청구가 가능하므로 본문으로만 받는다.
     *
     * ## 어느 구독을 고치는가
     *
     * `@CurrentUser` 의 구독뿐이다. 요청에 subscriptionId 나 userId 를 받지 않아,
     * 남의 구독을 지정할 경로 자체가 없다.
     *
     * 응답은 비어 있다. 방금 보낸 값을 되돌려 줄 이유가 없고, 그러면 응답 로그에 남는다.
     */
    @PostMapping("/billing-key")
    fun registerBillingKey(
        @CurrentUser userId: Long,
        @RequestBody request: RegisterBillingKeyRequest,
    ): ResData<Unit> {
        billingKeyUseCase.register(userId, request.billingKey)
        return ResData(data = Unit)
    }

    @PostMapping("/payments/{paymentId}/complete")
    fun complete(
        @CurrentUser userId: Long,
        @PathVariable paymentId: String,
    ): ResData<PortOnePaymentResult> = ResData(data = service.complete(userId, paymentId))

    /**
     * 결제창 이탈·실패 뒤 PG 상태를 재조회해 미확정 원장을 정리한다.
     *
     * 브라우저 오류만으로 FAILED 를 기록하지 않고 PortOne 상태가 확정적으로 실패한 경우에만
     * 닫는다. 실제 PAID 면 일반 완료 경로로 돌려 크레딧·구독 권한을 놓치지 않는다.
     */
    @PostMapping("/payments/{paymentId}/reconcile")
    fun reconcile(
        @CurrentUser userId: Long,
        @PathVariable paymentId: String,
    ): ResData<PortOnePaymentResult> = ResData(data = service.reconcileCheckout(userId, paymentId))

    /**
     * 포트원 웹훅 수신 엔드포인트.
     *
     * Standard Webhooks 서명을 먼저 검증하고, 본문은 신뢰하지 않고 paymentId로 PortOne API를
     * 재조회해 결제 상태·금액을 확인한다. 포트원은 2xx가 아니면 최대 5회 재전송하므로,
     * 검증 실패만 400을 반환하고 처리 대상이 아닌 이벤트는 200으로 응답한다.
     */
    @PostMapping("/webhook")
    fun webhook(
        @RequestBody rawBody: String,
        @RequestHeader(name = PortOneWebhookHeaders.ID, required = false) webhookId: String?,
        @RequestHeader(name = PortOneWebhookHeaders.SIGNATURE, required = false) webhookSignature: String?,
        @RequestHeader(name = PortOneWebhookHeaders.TIMESTAMP, required = false) webhookTimestamp: String?,
    ): ResponseEntity<ResData<Nothing?>> = try {
        service.handleWebhook(rawBody, webhookId, webhookSignature, webhookTimestamp)
        ResponseEntity.ok(ResData(success = true, data = null))
    } catch (e: UnauthorizedException) {
        // 서명 검증 실패 — 재전송해도 결과가 같으므로 400으로 끊는다
        log.warn("포트원 웹훅 서명 검증 실패: {}", e.message)
        ResponseEntity.badRequest().body(ResData(success = false, error = e.message))
    } catch (e: PortOneWebhookFormatException) {
        // 본문 형식 오류 — 역시 재전송으로 해결되지 않는다.
        // 반드시 이 전용 예외로만 좁혀야 한다. IllegalArgumentException 전체를 400으로 묶으면
        // complete()의 결제 상태·금액·통화 검증 실패(require)까지 400이 되어,
        // PG가 아직 PAID를 반영하지 않은 일시 상태에서 재전송이 끊기고 결제가 영구 누락된다.
        log.warn("포트원 웹훅 본문 오류: {}", e.message)
        ResponseEntity.badRequest().body(ResData(success = false, error = e.message))
    } catch (e: Exception) {
        // PG 상태 미반영·금액 불일치·게이트웨이/DB 장애 등 — 트랜잭션은 이미 롤백됐다.
        // 5xx를 돌려줘야 포트원이 재전송(최대 5회)해 복구할 수 있다.
        log.error("포트원 웹훅 처리 실패 — 재전송 유도를 위해 5xx 반환", e)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ResData(success = false, error = e.message))
    }
}

/** Standard Webhooks 규격 헤더명. */
object PortOneWebhookHeaders {
    const val ID = "webhook-id"
    const val SIGNATURE = "webhook-signature"
    const val TIMESTAMP = "webhook-timestamp"
}

data class SubscriptionCheckoutRequest(val planType: String, val billingCycle: String = "MONTHLY")

/**
 * 정기결제 수단 등록 요청.
 *
 * 빌링키 하나만 받는다. userId·subscriptionId 를 받지 않는 것이 이 DTO 의 계약이다 —
 * 받는 순간 남의 구독을 지정할 수 있게 된다. 대상은 언제나 인증된 사용자의 구독이다.
 */
data class RegisterBillingKeyRequest(val billingKey: String)
data class CreditCheckoutRequest(val packageName: String)
