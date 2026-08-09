package com.ongo.application.publicapi

import com.ongo.application.channel.ChannelUseCase
import com.ongo.domain.channel.ChannelRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PublicOAuthUseCaseTest {
    private val channels = mockk<ChannelRepository>()
    private val channelUseCase = mockk<ChannelUseCase>()

    private fun useCase(
        stateSecret: String = "test-oauth-state-secret-that-is-at-least-32-chars",
        clientId: String = "google-client-id",
    ) = PublicOAuthUseCase(
        channelRepository = channels,
        channelUseCase = channelUseCase,
        callbackUrl = "https://ongo.example.com/public/v1/social/callback",
        successRedirect = "https://ongo.example.com/channels",
        stateSecret = stateSecret,
        googleClientId = clientId,
        tiktokClientId = "tiktok-client-id",
        instagramClientId = "instagram-client-id",
        naverClientId = "naver-client-id",
        twitterClientId = "twitter-client-id",
        facebookClientId = "facebook-client-id",
        threadsClientId = "threads-client-id",
        pinterestClientId = "pinterest-client-id",
        linkedinClientId = "linkedin-client-id",
        wordpressClientId = "wordpress-client-id",
        tumblrClientId = "tumblr-client-id",
        vimeoClientId = "vimeo-client-id",
        dailymotionClientId = "dailymotion-client-id",
    )

    @Test
    fun `authorization URL은 사용자의 state와 Twitter PKCE를 포함한다`() {
        val result = useCase().authorizationUrl(42, "twitter", null)

        assertTrue(result.url.startsWith("https://twitter.com/i/oauth2/authorize?"))
        assertTrue(result.url.contains("client_id=twitter-client-id"))
        assertTrue(result.url.contains("code_challenge_method=S256"))
        assertTrue(result.url.contains("state="))
    }

    @Test
    fun `callback은 서명된 state의 사용자와 verifier로 단 한번 연결한다`() {
        every { channelUseCase.connectChannel(any(), any(), any()) } returns mockk()
        val oauth = useCase()
        val url = oauth.authorizationUrl(42, "youtube", null).url
        val state = Regex("[?&]state=([^&]+)").find(url)!!.groupValues[1]

        val redirect = oauth.complete("one-time-code", state)

        assertTrue(redirect.contains("channel=connected"))
        assertTrue(redirect.contains("platform=youtube"))
        verify(exactly = 1) {
            channelUseCase.connectChannel(
                42,
                "YOUTUBE",
                match { it.authorizationCode == "one-time-code" && !it.codeVerifier.isNullOrBlank() },
            )
        }
    }

    @Test
    fun `state 변조는 연결을 실행하지 않는다`() {
        val oauth = useCase()
        val state = Regex("[?&]state=([^&]+)").find(oauth.authorizationUrl(42, "youtube", null).url)!!.groupValues[1]

        assertFailsWith<IllegalArgumentException> {
            oauth.complete("code", "$state-tampered")
        }
        verify(exactly = 0) { channelUseCase.connectChannel(any(), any(), any()) }
    }

    @Test
    fun `refresh는 다른 사용자의 채널을 사용할 수 없다`() {
        every { channels.findById(7) } returns mockk {
            every { userId } returns 99
            every { platform } returns com.ongo.common.enums.Platform.YOUTUBE
        }

        assertFailsWith<com.ongo.common.exception.NotFoundException> {
            useCase().authorizationUrl(42, "youtube", "7")
        }
    }

    @Test
    fun `OAuth client 미설정은 가짜 URL 대신 명시적 오류를 반환한다`() {
        assertFailsWith<com.ongo.common.exception.BusinessException> {
            useCase(clientId = "your-client-id").authorizationUrl(42, "youtube", null)
        }
    }
}
