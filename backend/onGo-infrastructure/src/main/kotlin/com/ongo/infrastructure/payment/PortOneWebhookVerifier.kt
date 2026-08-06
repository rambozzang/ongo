package com.ongo.infrastructure.payment

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 포트원 V2 웹훅 서명 검증기.
 *
 * 포트원은 Standard Webhooks(https://www.standardwebhooks.com/) 규격을 따르며,
 * 본 구현은 공식 JVM SDK 의 `io.portone.sdk.server.webhook.WebhookVerifier` 를 이식한 것이다.
 * HMAC-SHA256 대칭 서명(`v1`)만 지원한다.
 *
 * 시크릿은 관리자 콘솔 → 결제 연동 → 연동 관리 → 결제알림(Webhook) 관리에서 발급하며,
 * API 시크릿(`payment.portone.api-secret`)과는 다른 값이다.
 */
@Component
class PortOneWebhookVerifier(
    @Value("\${payment.portone.webhook-secret:}") secret: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 시크릿 미설정이거나 base64 디코딩에 실패하면 null — 이 경우 모든 검증을 거부한다. */
    private val secretKey: SecretKeySpec? = runCatching {
        secret.takeIf { it.isNotBlank() }
            ?.removePrefix(SECRET_PREFIX)
            ?.let { SecretKeySpec(Base64.getDecoder().decode(it), HMAC_SHA256) }
    }.getOrNull()

    /**
     * 웹훅 서명을 검증한다. 검증에 실패하면 요청을 신뢰해서는 안 된다.
     *
     * @param rawBody 요청 본문 원문. 역직렬화 후 재직렬화한 문자열을 넘기면 서명이 어긋난다.
     * @param webhookId `webhook-id` 헤더
     * @param webhookSignature `webhook-signature` 헤더. 공백으로 구분된 `v1,<base64>` 목록
     * @param webhookTimestamp `webhook-timestamp` 헤더. 초 단위 유닉스 타임스탬프
     */
    fun verify(
        rawBody: String,
        webhookId: String?,
        webhookSignature: String?,
        webhookTimestamp: String?,
    ): Boolean {
        val key = secretKey ?: run {
            log.error("포트원 웹훅 시크릿이 설정되지 않아 서명을 검증할 수 없습니다")
            return false
        }
        if (webhookId == null || webhookSignature == null || webhookTimestamp == null) {
            log.warn("포트원 웹훅 필수 헤더가 누락되었습니다")
            return false
        }

        val timestamp = webhookTimestamp.toLongOrNull() ?: return false
        val now = System.currentTimeMillis() / 1000
        if (timestamp < now - TOLERANCE_SECONDS || timestamp > now + TOLERANCE_SECONDS) {
            log.warn("포트원 웹훅 타임스탬프가 허용 범위를 벗어났습니다: ts=$timestamp, now=$now")
            return false
        }

        val expected = sign(key, webhookId, timestamp, rawBody)
        return webhookSignature.splitToSequence(' ').any { versioned ->
            val parts = versioned.split(',', limit = 3)
            if (parts.size < 2 || parts[0] != SUPPORTED_VERSION) return@any false
            val actual = runCatching { Base64.getDecoder().decode(parts[1]) }.getOrNull() ?: return@any false
            // 타이밍 공격 방지를 위해 상수 시간 비교
            MessageDigest.isEqual(actual, expected)
        }
    }

    private fun sign(key: SecretKeySpec, webhookId: String, timestamp: Long, payload: String): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(key)
        return mac.doFinal("$webhookId.$timestamp.$payload".toByteArray())
    }

    companion object {
        const val HEADER_ID = "webhook-id"
        const val HEADER_SIGNATURE = "webhook-signature"
        const val HEADER_TIMESTAMP = "webhook-timestamp"

        private const val SECRET_PREFIX = "whsec_"
        private const val HMAC_SHA256 = "HmacSHA256"
        private const val SUPPORTED_VERSION = "v1"
        private const val TOLERANCE_SECONDS = 5 * 60L
    }
}
