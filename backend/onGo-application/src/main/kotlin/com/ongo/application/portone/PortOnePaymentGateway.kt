package com.ongo.application.portone

/** PortOne 결제 상태 조회와 웹훅 서명 검증을 위한 애플리케이션 포트. */
interface PortOnePaymentGateway {
    fun getPayment(paymentId: String): PortOnePayment

    /**
     * 결제를 조회하되 **없으면 null** 을 돌려준다.
     *
     * [getPayment] 만으로는 부족하다. 그쪽은 "없음"과 "조회 실패"를 모두 예외로 뭉개는데,
     * 정기 청구 복구에서는 이 둘이 정반대의 결론이다.
     *
     * - 없음 → 청구가 PG 에 닿지 못했다. **돈이 움직이지 않았다**고 단정할 수 있다.
     * - 조회 실패(네트워크·5xx) → 결제됐는지 아닌지 **모른다.** 여기서 "없음"으로 다루면
     *   이미 빠져나간 돈을 못 본 채 다음 주기에 다시 청구하게 된다.
     *
     * @return 결제 정보. PG 가 해당 id 를 모르면 null.
     * @throws Exception 조회 자체가 실패했을 때. 호출자는 **결과 불명**으로 다뤄야 한다.
     */
    fun findPayment(paymentId: String): PortOnePayment?

    /**
     * Standard Webhooks 규격에 따라 웹훅 서명을 검증한다.
     *
     * @param rawBody 요청 본문 원문 (재직렬화하면 서명이 어긋난다)
     * @param webhookId `webhook-id` 헤더
     * @param webhookSignature `webhook-signature` 헤더
     * @param webhookTimestamp `webhook-timestamp` 헤더
     */
    fun verifyWebhookSignature(
        rawBody: String,
        webhookId: String?,
        webhookSignature: String?,
        webhookTimestamp: String?,
    ): Boolean

    /**
     * 저장된 빌링키로 즉시 청구한다. PortOne V2 `POST /payments/{paymentId}/billing-key`.
     *
     * 결제창 없이 서버가 단독으로 돈을 움직이는 유일한 경로다. 그래서 [paymentId] 는
     * **호출자가 정하고**, 같은 주기에 같은 값을 쓰도록 강제한다 — 재시도가 두 번째 청구가
     * 되지 않게 하려면 PG 쪽에도 같은 신원이 필요하다.
     *
     * @param paymentId 우리가 정하는 결제 식별자. 주기당 하나로 고정한다.
     * @throws PortOneBillingChargeException 청구가 거절되거나 응답을 해석할 수 없을 때.
     *   호출자는 이 예외를 "이번 주기 청구 실패"로 다루고 구독을 PAST_DUE 로 내린다.
     */
    fun payWithBillingKey(request: PortOneBillingChargeRequest): PortOnePayment

    /**
     * 빌링키 상태를 조회한다. PortOne V2 `GET /billing-keys/{billingKey}`.
     *
     * 브라우저가 보내 온 문자열을 그대로 믿지 않기 위해 있다. 인증된 사용자라도 임의의
     * 문자열을 보낼 수 있고, 그걸 저장하면 정기 청구 때가 되어서야 실패한다 — 그때는
     * 이미 고객이 구독료를 낸 뒤다.
     *
     * @return 조회된 빌링키 정보. PortOne 이 그 키를 모르면 null.
     * @throws Exception 조회 자체가 실패했을 때. 저장을 진행하면 안 된다.
     */
    fun findBillingKey(billingKey: String): PortOneBillingKey?
}

/**
 * 빌링키 조회 결과.
 *
 * **빌링키 값 자체를 담지 않는다.** 이 객체가 로그·응답으로 흘러도 결제 수단이 새지
 * 않아야 한다. 호출자는 이미 자기가 조회한 키를 알고 있으므로 되돌려 받을 이유가 없다.
 */
data class PortOneBillingKey(
    /** `ISSUED` 여야 청구에 쓸 수 있다. 그 밖(`DELETED` 등)은 저장하면 안 된다. */
    val status: String,
)

/**
 * 빌링키 청구 요청.
 *
 * 고객 식별은 내부 userId 만 넘긴다. 이름·이메일·전화번호를 실어 보내지 않는다 —
 * 청구에 필요하지 않고, 보내는 순간 PG 로그가 개인정보 사본이 된다.
 */
data class PortOneBillingChargeRequest(
    val paymentId: String,
    val billingKey: String,
    val orderName: String,
    val customerId: String,
    val amount: Int,
    val currency: String = "KRW",
)

/** 빌링키 청구 실패. 사유 문자열에 빌링키를 넣지 않는다. */
class PortOneBillingChargeException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * 빌링키 조회 실패.
 *
 * **cause 를 받지 않는다.** 빌링키가 요청 경로에 들어가므로, HTTP 예외를 cause 로 달면
 * 그 메시지에 담긴 URI 를 통해 평문이 스택트레이스로 새어 나간다. 생성자에 자리를 두지
 * 않아 나중에 실수로 붙일 수도 없게 한다.
 */
class PortOneBillingKeyLookupException(message: String) : RuntimeException(message)

data class PortOnePayment(
    val paymentId: String,
    val status: String,
    val amount: Int,
    val currency: String,
    val transactionId: String?,
    val paymentMethod: String?,
    val receiptUrl: String?,
)
