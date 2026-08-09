package com.ongo.infrastructure.security.oauth

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.common.exception.UnauthorizedException
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClientResponseException

/**
 * OAuth 제공자가 거절한 이유를 호출자에게 남긴다.
 *
 * RestClient 는 4xx/5xx 응답에 RestClientResponseException 을 던진다. 이 예외에는
 * GlobalExceptionHandler 의 전용 핸들러가 없어 마지막 Exception 핸들러까지 흘러가고,
 * 응답이 "서버 오류가 발생했습니다" 하나로 뭉개진다. 그러면 운영에서
 *
 *   - 클라이언트 키가 틀린 것인지        (invalid_client)
 *   - 인가 코드가 만료·재사용된 것인지    (invalid_grant)
 *   - 리디렉션 URI 가 등록값과 다른 것인지
 *
 * 를 구분할 수 없다. 서버 로그를 직접 열기 전에는 진단이 불가능해진다.
 *
 * 응답 본문 전체는 로그에만 남기고 밖으로는 표준 error 코드만 내보낸다.
 * invalid_client 같은 값은 비밀이 아니고, 서버에 들어가지 않고도 원인을 좁히는 데 필요하다.
 * 본문을 그대로 흘리지 않는 이유는 제공자가 진단과 무관한 내용을 함께 담을 수 있기 때문이다.
 */
internal object OAuth2ErrorReporter {

    private val log = LoggerFactory.getLogger(OAuth2ErrorReporter::class.java)
    private val objectMapper = ObjectMapper()

    fun <T> report(provider: String, step: String, call: () -> T): T =
        try {
            call()
        } catch (e: RestClientResponseException) {
            val body = e.responseBodyAsString
            log.error("{} {} 실패: status={} body={}", provider, step, e.statusCode.value(), body)
            throw UnauthorizedException("$provider $step 실패 (${reasonOf(body, e.statusCode.value())})")
        }

    /**
     * Google 과 Kakao 의 토큰 엔드포인트는 OAuth2 표준대로 `error` 를 쓴다.
     * 카카오 사용자 조회는 표준을 벗어나 `code`/`msg` 를 쓰므로 그때는 code 를 본다.
     * 어느 쪽도 읽지 못하면 HTTP status 로 대신한다.
     */
    private fun reasonOf(body: String, status: Int): String {
        if (body.isBlank()) return "HTTP $status"
        return try {
            val node = objectMapper.readTree(body)
            val error = node.path("error").takeIf { it.isTextual }?.asText()
            val code = node.path("code").takeIf { !it.isMissingNode && !it.isNull }?.asText()
            error ?: code ?: "HTTP $status"
        } catch (_: Exception) {
            "HTTP $status"
        }
    }
}
