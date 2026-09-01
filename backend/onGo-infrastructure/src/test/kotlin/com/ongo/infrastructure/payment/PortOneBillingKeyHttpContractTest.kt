package com.ongo.infrastructure.payment

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.portone.PortOneBillingKeyLookupException
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `GET /billing-keys/{billingKey}` HTTP 계약.
 *
 * 이 경로는 실호출 전에는 확인할 방법이 없던 곳이다. URI 인코딩이 깨지면 조회가 조용히
 * 404 가 되어 **멀쩡한 결제 수단을 거절**하고, 404 와 5xx 를 같이 다루면 PortOne 장애 중에
 * 등록된 수단을 "없음"으로 확정한다.
 *
 * 빌링키가 경로에 들어가므로 **예외에 원인을 붙이지 않는 것**도 여기서 함께 고정한다.
 */
class PortOneBillingKeyHttpContractTest {

    private lateinit var server: MockWebServer
    private lateinit var client: PortOneClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        client = PortOneClient(
            objectMapper = ObjectMapper(),
            webhookVerifier = mockk(),
            apiSecret = "test-secret",
            apiBaseUrl = server.url("/").toString().trimEnd('/'),
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    /* ---- 응답 파싱 ---- */

    @Test
    fun `발급된 빌링키의 상태를 읽는다`() {
        server.enqueue(json("""{"status":"ISSUED","billingKey":"bk_1"}"""))

        assertEquals("ISSUED", client.findBillingKey("bk_1")?.status)
    }

    @Test
    fun `삭제된 빌링키의 상태도 그대로 읽는다`() {
        server.enqueue(json("""{"status":"DELETED"}"""))

        assertEquals("DELETED", client.findBillingKey("bk_1")?.status)
    }

    /** status 가 없으면 우리가 아는 형식이 아니다. 임의로 ISSUED 로 보면 안 된다. */
    @Test
    fun `status 가 없으면 UNKNOWN 으로 읽는다`() {
        server.enqueue(json("""{"billingKey":"bk_1"}"""))

        assertEquals("UNKNOWN", client.findBillingKey("bk_1")?.status)
    }

    @Test
    fun `결제 재조회는 PortOne 결제 응답을 읽는다`() {
        server.enqueue(json("""
            {
              "paymentId":"ongo-42",
              "status":"PAID",
              "amount":{"total":19900},
              "currency":"KRW",
              "transactionId":"tx-42"
            }
        """))

        val payment = client.findPayment("ongo-42")
        val request = server.takeRequest()

        assertEquals("GET", request.method)
        assertEquals("/payments/ongo-42", request.path)
        assertEquals("PAID", payment?.status)
        assertEquals(19_900, payment?.amount)
        assertEquals("KRW", payment?.currency)
    }

    /* ---- URI 인코딩 ---- */

    /**
     * 빌링키에 `/` 나 공백이 섞이면 경로가 쪼개져 엉뚱한 엔드포인트를 친다. RestClient 의
     * uri 변수 치환이 인코딩을 해 주지만, 문자열 연결로 바꾸는 순간 조용히 깨진다.
     */
    @Test
    fun `빌링키를 경로 변수로 인코딩해 보낸다`() {
        server.enqueue(json("""{"status":"ISSUED"}"""))

        client.findBillingKey("bk/with space+plus")

        val path = server.takeRequest().path
        // `+` 까지 %2B 로 인코딩된다. 경로에서 `+` 는 공백이 아니지만, 서버 구현에 따라
        // 다르게 읽힐 수 있으므로 인코딩되는 편이 안전하다.
        assertEquals("/billing-keys/bk%2Fwith%20space%2Bplus", path)
    }

    @Test
    fun `평범한 빌링키는 그대로 경로에 붙는다`() {
        server.enqueue(json("""{"status":"ISSUED"}"""))

        client.findBillingKey("bk_live_abc123")

        assertEquals("/billing-keys/bk_live_abc123", server.takeRequest().path)
    }

    /** API secret 이 빠지면 전부 401 이 된다. 인증 헤더 형식을 고정한다. */
    @Test
    fun `PortOne 인증 헤더를 붙여 보낸다`() {
        server.enqueue(json("""{"status":"ISSUED"}"""))

        client.findBillingKey("bk_1")

        assertEquals("PortOne test-secret", server.takeRequest().getHeader("Authorization"))
    }

    /* ---- 404 vs 5xx ---- */

    /** PortOne 이 그 키를 모른다 = 없다. 이때만 null 로 단정할 수 있다. */
    @Test
    fun `404 는 null 이다`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"not found"}"""))

        assertNull(client.findBillingKey("bk_1"))
    }

    /**
     * 5xx 를 null 로 다루면 PortOne 장애 중에 등록된 수단을 "없음"으로 확정하고,
     * 결제한 고객의 정기결제를 끊게 된다.
     */
    @Test
    fun `5xx 는 null 이 아니라 예외다`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        assertFailsWith<PortOneBillingKeyLookupException> { client.findBillingKey("bk_1") }
    }

    /** 404 외의 4xx 도 "없음"이 아니다. 권한 오류를 없음으로 읽으면 안 된다. */
    @Test
    fun `401 은 null 이 아니라 예외다`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"))

        assertFailsWith<PortOneBillingKeyLookupException> { client.findBillingKey("bk_1") }
    }

    @Test
    fun `결제 재조회 404 만 null 이고 5xx 는 예외다`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"not found"}"""))
        assertNull(client.findPayment("ongo-missing"))

        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        assertFailsWith<RestClientException> { client.findPayment("ongo-unknown") }
    }

    /* ---- 결제 확정 조회(strict) ---- */

    /**
     * **`complete()` 가 쓰는 것은 `findPayment` 가 아니라 `getPayment` 다.**
     *
     * `PortOnePaymentService.complete()` 는 `gateway.getPayment(portonePaymentId)` 로 PG 에
     * 다시 물어본 뒤 `status == PAID`·금액·통화를 대조하고 나서야 구독 기간과 크레딧을
     * 부여한다. 두 메서드의 차이는 **404 를 어떻게 다루는가** 하나뿐이다.
     *
     *   findPayment : 404 → null      (호출자가 "아직 결제 안 됨" 으로 읽는다)
     *   getPayment  : 404 → 예외 전파  (호출자가 아무 판단도 하지 못하게 막는다)
     *
     * 이 구분이 무너지면 어떻게 되는지가 이 두 테스트의 존재 이유다. 누군가 `getPayment`
     * 에도 404→null 을 넣으면 `complete()` 의 `require(verified.status == "PAID")` 가
     * null 을 다루게 되고, **PortOne 에 존재하지도 않는 결제가 완료로 처리될 여지**가 생긴다.
     * 지금은 예외가 나가 완료 트랜잭션이 통째로 롤백되므로 안전하지만, 그 안전은 계약이
     * 아니라 우연이었다 — `findPayment` 쪽만 테스트가 있었기 때문이다.
     *
     * 5xx 를 함께 고정하는 이유도 같다. PortOne 장애를 "결제 없음" 으로 확정하면 이미
     * 빠져나간 돈을 못 본 채 미결제로 닫게 된다.
     */
    @Test
    fun `결제 확정 조회는 404 를 null 로 바꾸지 않고 예외로 올린다`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"not found"}"""))

        assertFailsWith<HttpClientErrorException.NotFound> { client.getPayment("ongo-missing") }
    }

    @Test
    fun `결제 확정 조회는 5xx 도 예외로 올린다`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        assertFailsWith<RestClientException> { client.getPayment("ongo-unknown") }
    }

    /**
     * 404 를 삼키지 않는다는 것을 **반대편에서도** 고정한다.
     *
     * 위 두 테스트는 "예외가 난다" 만 본다. 그런데 어떤 값이든 돌려주기만 하면 통과하는
     * 구현(예: 빈 PortOnePayment 반환)이 있을 수 있으므로, 정상 응답에서는 실제로 파싱된
     * 값이 나온다는 것도 같은 메서드로 확인해 둔다. 그래야 "예외만 나면 된다" 로 퇴화하지
     * 않는다.
     */
    @Test
    fun `결제 확정 조회는 정상 응답의 상태와 금액을 그대로 읽는다`() {
        server.enqueue(
            json(
                """{"paymentId":"ongo-42","status":"PAID","amount":{"total":19900},"currency":"KRW"}""",
            ),
        )

        val payment = client.getPayment("ongo-42")

        assertEquals("PAID", payment.status)
        assertEquals(19_900, payment.amount)
        assertEquals("KRW", payment.currency)
    }

    /* ---- 평문 유출 금지 ---- */

    /**
     * **이 테스트가 이 파일의 핵심이다.**
     *
     * RestClient 예외 메시지는 요청 URI 를 담는다. 빌링키가 경로에 있으므로 cause 를
     * 그대로 올리면 스택트레이스가 찍히는 모든 로그에 평문이 남고, 그 값 하나로 고객에게
     * 반복 청구가 가능하다.
     */
    @Test
    fun `조회 실패 예외에 빌링키가 남지 않는다`() {
        val secret = "bk_live_super_secret_value"
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val e = assertFailsWith<PortOneBillingKeyLookupException> { client.findBillingKey(secret) }

        assertTrue(secret !in (e.message ?: ""), "예외 메시지에 빌링키가 새어 나왔다")
        // cause 사슬 전체를 훑는다. 어디에 붙어 있어도 스택트레이스로 찍힌다.
        var cause = e.cause
        while (cause != null) {
            assertTrue(secret !in (cause.message ?: ""), "cause 메시지에 빌링키가 새어 나왔다: ${cause.message}")
            cause = cause.cause
        }
        // 애초에 cause 를 달 수 없어야 한다.
        assertNull(e.cause, "cause 가 붙으면 스택트레이스로 URI 가 새어 나간다")
    }

    /** 연결 자체가 끊긴 경우도 같다 — ResourceAccessException 메시지에 URI 가 들어 있다. */
    @Test
    fun `연결 실패 예외에도 빌링키가 남지 않는다`() {
        val secret = "bk_live_another_secret"
        server.shutdown()

        val e = assertFailsWith<PortOneBillingKeyLookupException> { client.findBillingKey(secret) }

        assertTrue(secret !in (e.message ?: ""))
        assertNull(e.cause)
    }

    /** 자동 청구는 명시된 상점·채널로 라우팅되어야 기본 토큰 설정에 의존하지 않는다. */
    @Test
    fun `빌링키 청구 본문에 상점과 채널을 명시한다`() {
        server.enqueue(json("""{}"""))
        server.enqueue(json("""{
            "paymentId":"ongo-42",
            "status":"PAID",
            "amount":{"total":19900},
            "currency":"KRW"
        }"""))

        client.payWithBillingKey(
            com.ongo.application.portone.PortOneBillingChargeRequest(
                paymentId = "ongo-42",
                billingKey = "bk_live_test",
                orderName = "구독 갱신",
                customerId = "7",
                amount = 19_900,
                storeId = "store-test",
                channelKey = "channel-test",
            ),
        )

        val body = ObjectMapper().readTree(server.takeRequest().body.readUtf8())
        assertEquals("store-test", body.path("storeId").asText())
        assertEquals("channel-test", body.path("channelKey").asText())
        assertEquals("bk_live_test", body.path("billingKey").asText())
    }

    /** 청구 실패도 HTTP cause 를 남기지 않아 빌링키가 예외·로그로 유출되지 않는다. */
    @Test
    fun `빌링키 청구 실패 예외에 빌링키가 남지 않는다`() {
        val secret = "bk_live_charge_secret"
        server.enqueue(MockResponse().setResponseCode(500).setBody(secret))

        val e = assertFailsWith<com.ongo.application.portone.PortOneBillingChargeException> {
            client.payWithBillingKey(
                com.ongo.application.portone.PortOneBillingChargeRequest(
                    paymentId = "ongo-43",
                    billingKey = secret,
                    orderName = "구독 갱신",
                    customerId = "7",
                    amount = 19_900,
                ),
            )
        }

        assertTrue(secret !in (e.message ?: ""))
        assertNull(e.cause)
    }
}
