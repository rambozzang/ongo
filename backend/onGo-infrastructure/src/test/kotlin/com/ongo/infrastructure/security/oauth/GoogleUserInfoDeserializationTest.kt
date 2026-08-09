package com.ongo.infrastructure.security.oauth

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals

/**
 * GoogleOAuth2Service 가 쓰는 것과 같은 경로(RestClient.create() 의 기본 메시지 컨버터)로
 * 구글 userinfo 응답을 역직렬화한다.
 *
 * 이 매핑이 조용히 실패하면 모든 필드가 기본값으로 채워진 채 가입이 성공해버린다.
 * 운영에서 실제로 email/name/provider_id 가 빈 문자열인 사용자가 만들어졌고,
 * provider_id 가 비면 findByProviderAndProviderId 가 모든 구글 사용자를 같은 행에
 * 매칭시켜 서로의 계정으로 로그인된다. 그래서 이 지점은 테스트로 고정한다.
 */
class GoogleUserInfoDeserializationTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `구글 userinfo 응답의 모든 필드가 채워진다`() {
        // https://www.googleapis.com/oauth2/v2/userinfo 의 실제 응답 형태.
        // verified_email, given_name, family_name 처럼 DTO 에 없는 필드도 함께 온다.
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "id": "104729384756102938475",
                      "email": "rambo.zzang@gmail.com",
                      "verified_email": true,
                      "name": "홍길동",
                      "given_name": "길동",
                      "family_name": "홍",
                      "picture": "https://lh3.googleusercontent.com/a/abc123"
                    }
                    """.trimIndent(),
                ),
        )

        val info = RestClient.create()
            .get()
            .uri(server.url("/oauth2/v2/userinfo").toString())
            .retrieve()
            .body(GoogleOAuth2Service.GoogleUserInfo::class.java)!!

        assertEquals("104729384756102938475", info.id, "id 가 비면 provider_id 가 빈 값으로 저장된다")
        assertEquals("rambo.zzang@gmail.com", info.email, "email 이 비면 users_email_key UNIQUE 가 빈 문자열로 점유된다")
        assertEquals("홍길동", info.name)
        assertEquals("https://lh3.googleusercontent.com/a/abc123", info.picture)
    }
}
