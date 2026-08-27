package com.ongo.infrastructure.payment

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.portone.PortOneBillingKeyLookupException
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
}
