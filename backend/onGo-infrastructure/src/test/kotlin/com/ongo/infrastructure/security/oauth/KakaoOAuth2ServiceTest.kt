package com.ongo.infrastructure.security.oauth

import com.ongo.common.exception.UnauthorizedException
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import org.springframework.test.util.ReflectionTestUtils
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KakaoOAuth2ServiceTest {

    private lateinit var restClient: RestClient
    private lateinit var service: KakaoOAuth2Service

    @BeforeEach
    fun setUp() {
        restClient = mockk()
        mockkStatic(RestClient::class)
        every { RestClient.create() } returns restClient
        service = KakaoOAuth2Service("client-id", "client-secret")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(RestClient::class)
    }

    @Test
    fun `카카오 사용자 응답에서 email 이 누락되면 UnauthorizedException 을 던진다`() {
        stubUserInfo(
            mapOf(
                "id" to "kakao-user-1",
                "kakao_account" to emptyMap<String, Any>(),
            ),
        )

        val exception = assertFailsWith<UnauthorizedException> {
            invokeFetchUserInfo()
        }

        assertEquals("Kakao 계정 이메일을 받지 못했습니다", exception.message)
    }

    @Test
    fun `카카오 사용자 응답에서 id 가 누락되면 UnauthorizedException 을 던진다`() {
        stubUserInfo(
            mapOf(
                "kakao_account" to mapOf("email" to "creator@example.com"),
            ),
        )

        val exception = assertFailsWith<UnauthorizedException> {
            invokeFetchUserInfo()
        }

        assertEquals("Kakao 사용자 식별자를 받지 못했습니다", exception.message)
    }

    @Test
    fun `정상 카카오 사용자 응답은 기존 프로필 정보를 그대로 반환한다`() {
        stubUserInfo(
            mapOf(
                "id" to "kakao-user-1",
                "kakao_account" to mapOf(
                    "email" to "creator@example.com",
                    "profile" to mapOf(
                        "nickname" to "크리에이터",
                        "profile_image_url" to "https://example.com/profile.jpg",
                    ),
                ),
            ),
        )

        val result = invokeFetchUserInfo()

        assertEquals(
            OAuth2UserInfo(
                providerId = "kakao-user-1",
                email = "creator@example.com",
                name = "크리에이터",
                profileImageUrl = "https://example.com/profile.jpg",
            ),
            result,
        )
    }

    private fun stubUserInfo(body: Map<String, Any>) {
        val requestSpec = mockk<RestClient.RequestHeadersUriSpec<*>>()
        val headersSpec = mockk<RestClient.RequestHeadersSpec<*>>()
        val responseSpec = mockk<RestClient.ResponseSpec>()

        every { restClient.get() } returns requestSpec
        every { requestSpec.uri(any<String>()) } returns headersSpec
        every { headersSpec.header(any<String>(), any<String>()) } returns headersSpec
        every { headersSpec.retrieve() } returns responseSpec
        every { responseSpec.body(Map::class.java) } returns body
    }

    private fun invokeFetchUserInfo(): OAuth2UserInfo =
        ReflectionTestUtils.invokeMethod(
            service,
            "fetchUserInfo",
            KakaoOAuth2Service.KakaoTokenResponse(accessToken = "access-token"),
        )!!
}
