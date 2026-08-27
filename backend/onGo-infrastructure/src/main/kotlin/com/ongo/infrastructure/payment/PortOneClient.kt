package com.ongo.infrastructure.payment

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.portone.PortOneBillingChargeException
import com.ongo.application.portone.PortOneBillingChargeRequest
import com.ongo.application.portone.PortOneBillingKey
import com.ongo.application.portone.PortOneBillingKeyLookupException
import com.ongo.application.portone.PortOnePayment
import com.ongo.application.portone.PortOnePaymentGateway
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.http.client.SimpleClientHttpRequestFactory
import java.time.Duration

@Component
class PortOneClient(
    private val objectMapper: ObjectMapper,
    private val webhookVerifier: PortOneWebhookVerifier,
    @Value("\${payment.portone.api-secret:}") apiSecret: String,
    /**
     * PortOne API 주소. 기본값이 운영 주소이며 설정하지 않으면 그대로 쓴다.
     *
     * 설정으로 뺀 이유는 **HTTP 계약을 실제로 테스트하기 위해서**다. 하드코딩이면
     * URI 인코딩·응답 파싱·404 처리를 mock 서버로 확인할 방법이 없고, 그 경로들은
     * 첫 실호출에서야 드러난다.
     */
    @Value("\${payment.portone.api-base-url:https://api.portone.io}") apiBaseUrl: String,
) : PortOnePaymentGateway {
    private val restClient = RestClient.builder()
        .baseUrl(apiBaseUrl)
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(10))
            setReadTimeout(Duration.ofSeconds(30))
        })
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne $apiSecret")
        .build()

    override fun getPayment(paymentId: String): PortOnePayment {
        val body = restClient.get()
            .uri("/payments/{paymentId}", paymentId)
            .retrieve()
            .body(String::class.java)
            ?: throw IllegalStateException("포트원 결제 조회 응답이 없습니다")
        val json = objectMapper.readTree(body)
        return PortOnePayment(
            paymentId = json.text("paymentId") ?: paymentId,
            status = json.text("status") ?: "UNKNOWN",
            amount = json.path("amount").path("total").asInt(-1),
            currency = json.text("currency") ?: "",
            transactionId = json.text("transactionId")
                ?: json.path("transaction").text("id"),
            paymentMethod = json.path("method").text("type")
                ?: json.path("method").text("name"),
            receiptUrl = json.text("receiptUrl")
                ?: json.path("receipt").text("url"),
        )
    }

    /**
     * `GET /payments/{paymentId}` 이되 404 만 null 로 바꾼다.
     *
     * 그 밖의 실패(네트워크·5xx)는 **그대로 던진다.** 여기서 삼켜 null 로 만들면 호출자가
     * "결제된 적 없음"으로 읽고, 이미 빠져나간 돈을 못 본 채 다시 청구하게 된다.
     */
    override fun findPayment(paymentId: String): PortOnePayment? =
        try {
            getPayment(paymentId)
        } catch (_: HttpClientErrorException.NotFound) {
            null
        }

    /**
     * PortOne V2 `POST /payments/{paymentId}/billing-key`.
     *
     * 응답 해석을 [getPayment] 과 같은 방식으로 맞춘다. PortOne 은 이 호출의 성공 응답에
     * 결제 상세를 전부 싣지 않으므로, **청구가 받아들여진 뒤 같은 paymentId 를 다시
     * 조회해** 금액·상태를 확인한다 — 웹훅에서 이미 쓰는 것과 같은 원칙이다. 요청 본문의
     * 값을 그대로 결과로 믿으면 우리가 보낸 숫자를 우리가 검증하는 꼴이 된다.
     *
     * 예외 메시지에 빌링키를 넣지 않는다. 로그로 새면 그 값 하나로 반복 청구가 가능하다.
     */
    override fun payWithBillingKey(request: PortOneBillingChargeRequest): PortOnePayment {
        try {
            restClient.post()
                .uri("/payments/{paymentId}/billing-key", request.paymentId)
                .body(
                    mapOf(
                        "billingKey" to request.billingKey,
                        "orderName" to request.orderName,
                        "customer" to mapOf("id" to request.customerId),
                        "amount" to mapOf("total" to request.amount),
                        "currency" to request.currency,
                    ),
                )
                .retrieve()
                .body(String::class.java)
        } catch (e: RestClientException) {
            throw PortOneBillingChargeException("포트원 빌링키 결제 요청이 실패했습니다", e)
        }

        // 청구 결과는 요청 응답이 아니라 재조회로 확정한다.
        return runCatching { getPayment(request.paymentId) }
            .getOrElse { throw PortOneBillingChargeException("포트원 빌링키 결제 후 조회에 실패했습니다", it) }
    }

    /**
     * PortOne V2 `GET /billing-keys/{billingKey}`.
     *
     * 빌링키는 **경로에 들어가므로 반드시 인코딩한다.** RestClient 의 uri 변수 치환이
     * 인코딩을 해 주지만, 문자열 연결로 바꾸는 순간 조용히 깨진다.
     *
     * 예외 메시지에 빌링키를 넣지 않는다. 로그로 새면 그 값 하나로 반복 청구가 가능하다.
     */
    override fun findBillingKey(billingKey: String): PortOneBillingKey? {
        val body = try {
            restClient.get()
                .uri("/billing-keys/{billingKey}", billingKey)
                .retrieve()
                .body(String::class.java)
        } catch (_: HttpClientErrorException.NotFound) {
            return null
        } catch (_: RestClientException) {
            /*
             * **cause 를 붙이지 않는다.**
             *
             * RestClient 예외 메시지는 요청 URI 를 담는다(예: ResourceAccessException 의
             * `I/O error on GET request for "…/billing-keys/…"`). 빌링키가 경로에 있으므로
             * cause 를 그대로 올리면 스택트레이스가 찍히는 모든 로그에 평문이 남고,
             * 그 값 하나로 고객에게 반복 청구가 가능하다.
             *
             * 원인 진단은 이 클래스가 남기는 로그와 PortOne 콘솔로 한다. 진단 편의보다
             * 결제 수단 유출을 막는 쪽이 우선이다.
             */
            throw PortOneBillingKeyLookupException("포트원 빌링키 조회에 실패했습니다")
        } ?: throw PortOneBillingKeyLookupException("포트원 빌링키 조회 응답이 없습니다")

        val json = objectMapper.readTree(body)
        return PortOneBillingKey(
            // status 가 없으면 우리가 아는 형식이 아니다. UNKNOWN 은 아래 검증에서 거절된다.
            status = json.text("status") ?: "UNKNOWN",
        )
    }

    override fun verifyWebhookSignature(
        rawBody: String,
        webhookId: String?,
        webhookSignature: String?,
        webhookTimestamp: String?,
    ): Boolean = webhookVerifier.verify(rawBody, webhookId, webhookSignature, webhookTimestamp)

    private fun JsonNode.text(name: String): String? = path(name).takeUnless { it.isMissingNode || it.isNull }?.asText()
}
