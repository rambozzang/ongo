package com.ongo.infrastructure.security.oauth

import com.ongo.common.exception.UnauthorizedException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClientResponseException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 이 클래스의 존재 이유는 실패 원인을 밖에서 읽을 수 있게 하는 것이다.
 * 따라서 검증 대상은 "예외를 던지는가" 가 아니라 "어떤 사유가 메시지에 남는가" 다.
 */
class OAuth2ErrorReporterTest {

    private fun responseException(status: Int, body: String) = RestClientResponseException(
        "call failed",
        status,
        "",
        HttpHeaders.EMPTY,
        body.toByteArray(),
        Charsets.UTF_8,
    )

    @Test
    fun `성공 응답은 그대로 통과시킨다`() {
        val result = OAuth2ErrorReporter.report("Google", "토큰 발급") { "ok" }
        assertEquals("ok", result)
    }

    @Test
    fun `표준 error 필드를 사유로 남긴다`() {
        // 클라이언트 키가 틀렸을 때 구글이 돌려주는 형태다.
        val e = assertFailsWith<UnauthorizedException> {
            OAuth2ErrorReporter.report("Google", "토큰 발급") {
                throw responseException(401, """{"error":"invalid_client","error_description":"Unauthorized"}""")
            }
        }
        assertTrue(e.message.contains("invalid_client"), "실제 메시지=${e.message}")
        assertTrue(e.message.contains("Google"), "제공자가 빠졌다: ${e.message}")
    }

    @Test
    fun `인가 코드 문제와 클라이언트 키 문제를 구분할 수 있다`() {
        val e = assertFailsWith<UnauthorizedException> {
            OAuth2ErrorReporter.report("Google", "토큰 발급") {
                throw responseException(400, """{"error":"invalid_grant"}""")
            }
        }
        assertTrue(e.message.contains("invalid_grant"), "실제 메시지=${e.message}")
    }

    @Test
    fun `표준을 벗어난 카카오 사용자 조회 응답은 code 를 사유로 쓴다`() {
        val e = assertFailsWith<UnauthorizedException> {
            OAuth2ErrorReporter.report("Kakao", "사용자 조회") {
                throw responseException(401, """{"msg":"this access token does not exist","code":-401}""")
            }
        }
        assertTrue(e.message.contains("-401"), "실제 메시지=${e.message}")
    }

    @Test
    fun `JSON 이 아니거나 비어 있으면 HTTP status 로 대신한다`() {
        val notJson = assertFailsWith<UnauthorizedException> {
            OAuth2ErrorReporter.report("Google", "토큰 발급") {
                throw responseException(502, "<html>Bad Gateway</html>")
            }
        }
        assertTrue(notJson.message.contains("HTTP 502"), "실제 메시지=${notJson.message}")

        val empty = assertFailsWith<UnauthorizedException> {
            OAuth2ErrorReporter.report("Google", "토큰 발급") { throw responseException(500, "") }
        }
        assertTrue(empty.message.contains("HTTP 500"), "실제 메시지=${empty.message}")
    }
}
