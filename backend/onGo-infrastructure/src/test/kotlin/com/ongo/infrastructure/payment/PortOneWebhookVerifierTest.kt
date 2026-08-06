package com.ongo.infrastructure.payment

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 포트원 웹훅 서명 검증 테스트.
 *
 * 검증 규격은 Standard Webhooks(https://www.standardwebhooks.com/)이며,
 * 테스트 케이스는 포트원 공식 server-sdk-js 의 tests/webhook.test.ts 를 이식한 것이다.
 *
 * 서명 알고리즘은 Python 으로 독립 산출한 값과 대조해 확인했다.
 *   secret         = pzQGE83cSIRKM4/WH5QY+g==  (base64)
 *   signed content = dummy-webhook-id.1700000000.{"test":"test payload"}
 *   signature      = x9F4zp5ZADOWUiDoPKsK0/eBoHr9Nr6QnunRw+F/MHI=
 */
class PortOneWebhookVerifierTest {

    private val secret = "pzQGE83cSIRKM4/WH5QY+g=="
    private val webhookId = "dummy-webhook-id"
    private val payload = """{"test":"test payload"}"""

    private fun now() = System.currentTimeMillis() / 1000

    /** 테스트용 서명 생성 — Python 레퍼런스와 동일한 `{id}.{timestamp}.{payload}` HMAC-SHA256/base64. */
    private fun sign(timestamp: Long, body: String = payload, id: String = webhookId): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal("$id.$timestamp.$body".toByteArray()))
    }

    @Test
    @DisplayName("유효한 서명이면 통과한다")
    fun validSignaturePasses() {
        val ts = now()
        val verifier = PortOneWebhookVerifier(secret)

        assertTrue(verifier.verify(payload, webhookId, "v1,${sign(ts)}", ts.toString()))
    }

    @Test
    @DisplayName("whsec_ 접두사가 붙은 시크릿도 동일하게 통과한다")
    fun secretWithPrefixPasses() {
        val ts = now()
        val verifier = PortOneWebhookVerifier("whsec_$secret")

        assertTrue(verifier.verify(payload, webhookId, "v1,${sign(ts)}", ts.toString()))
    }

    @Test
    @DisplayName("공백으로 구분된 여러 서명 중 하나만 유효해도 통과한다")
    fun multipleSignaturesPassIfAnyMatches() {
        val ts = now()
        val verifier = PortOneWebhookVerifier(secret)
        val header = listOf(
            "v1,Ceo5qEr07ixe2NLpvHk3FH9bwy/WavXrAFQ/9tdO6mc=",
            "v2,Ceo5qEr07ixe2NLpvHk3FH9bwy/WavXrAFQ/9tdO6mc=",
            "v1,${sign(ts)}",
        ).joinToString(" ")

        assertTrue(verifier.verify(payload, webhookId, header, ts.toString()))
    }

    @Test
    @DisplayName("본문이 변조되면 거부한다")
    fun tamperedBodyRejected() {
        val ts = now()
        val verifier = PortOneWebhookVerifier(secret)

        assertFalse(verifier.verify("""{"test":"tampered"}""", webhookId, "v1,${sign(ts)}", ts.toString()))
    }

    @Test
    @DisplayName("webhook-id가 다르면 거부한다")
    fun differentWebhookIdRejected() {
        val ts = now()
        val verifier = PortOneWebhookVerifier(secret)

        assertFalse(verifier.verify(payload, "other-id", "v1,${sign(ts)}", ts.toString()))
    }

    @Test
    @DisplayName("서명 값이 틀리면 거부한다")
    fun invalidSignatureRejected() {
        val ts = now()
        val verifier = PortOneWebhookVerifier(secret)

        assertFalse(verifier.verify(payload, webhookId, "v1,Ceo5qEr07ixe2NLpvHk3FH9bwy/WavXrAFQ/9tdO6mc=", ts.toString()))
    }

    @Test
    @DisplayName("base64가 아닌 서명이면 예외 없이 거부한다")
    fun malformedBase64SignatureRejected() {
        val ts = now()
        val verifier = PortOneWebhookVerifier(secret)

        assertFalse(verifier.verify(payload, webhookId, "v1,dawfeoifkpqwoekfpqoekf!!!", ts.toString()))
        assertFalse(verifier.verify(payload, webhookId, "v1,", ts.toString()))
        assertFalse(verifier.verify(payload, webhookId, "v1", ts.toString()))
    }

    @Test
    @DisplayName("v1이 아닌 버전은 거부한다")
    fun unsupportedVersionRejected() {
        val ts = now()
        val verifier = PortOneWebhookVerifier(secret)

        assertFalse(verifier.verify(payload, webhookId, "v2,${sign(ts)}", ts.toString()))
    }

    @Test
    @DisplayName("필수 헤더가 없으면 거부한다")
    fun missingHeadersRejected() {
        val ts = now()
        val verifier = PortOneWebhookVerifier(secret)
        val sig = "v1,${sign(ts)}"

        assertFalse(verifier.verify(payload, null, sig, ts.toString()))
        assertFalse(verifier.verify(payload, webhookId, null, ts.toString()))
        assertFalse(verifier.verify(payload, webhookId, sig, null))
    }

    @Test
    @DisplayName("타임스탬프가 숫자가 아니면 거부한다")
    fun nonNumericTimestampRejected() {
        val verifier = PortOneWebhookVerifier(secret)

        assertFalse(verifier.verify(payload, webhookId, "v1,${sign(now())}", "hello"))
    }

    @Test
    @DisplayName("허용 오차(5분)보다 오래된 타임스탬프는 거부한다 — 재전송 공격 방지")
    fun tooOldTimestampRejected() {
        val ts = now() - 301
        val verifier = PortOneWebhookVerifier(secret)

        assertFalse(verifier.verify(payload, webhookId, "v1,${sign(ts)}", ts.toString()))
    }

    @Test
    @DisplayName("허용 오차(5분)보다 미래인 타임스탬프는 거부한다")
    fun tooNewTimestampRejected() {
        val ts = now() + 301
        val verifier = PortOneWebhookVerifier(secret)

        assertFalse(verifier.verify(payload, webhookId, "v1,${sign(ts)}", ts.toString()))
    }

    @Test
    @DisplayName("허용 오차 경계 안(4분 전)이면 통과한다")
    fun timestampWithinTolerancePasses() {
        val ts = now() - 240
        val verifier = PortOneWebhookVerifier(secret)

        assertTrue(verifier.verify(payload, webhookId, "v1,${sign(ts)}", ts.toString()))
    }

    @Test
    @DisplayName("시크릿이 비어 있으면 항상 거부한다 — 미설정 환경에서 검증이 통과되면 안 된다")
    fun blankSecretAlwaysRejects() {
        val ts = now()
        val verifier = PortOneWebhookVerifier("")

        assertFalse(verifier.verify(payload, webhookId, "v1,${sign(ts)}", ts.toString()))
    }

    @Test
    @DisplayName("시크릿이 유효한 base64가 아니면 항상 거부한다")
    fun malformedSecretAlwaysRejects() {
        val ts = now()
        val verifier = PortOneWebhookVerifier("not-a-valid-base64!!!")

        assertFalse(verifier.verify(payload, webhookId, "v1,${sign(ts)}", ts.toString()))
    }
}
