package com.ongo.application.publicapi

import com.ongo.application.channel.ChannelUseCase
import com.ongo.common.enums.Platform
import com.ongo.domain.channel.ChannelRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.net.URLDecoder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
        every { channelUseCase.connectChannelFromTrustedAuthorization(any(), any(), any()) } returns mockk()
        val oauth = useCase()
        val url = oauth.authorizationUrl(42, "youtube", null).url
        val state = Regex("[?&]state=([^&]+)").find(url)!!.groupValues[1]

        val redirect = oauth.complete("one-time-code", state)

        assertTrue(redirect.contains("channel=connected"))
        assertTrue(redirect.contains("platform=youtube"))
        verify(exactly = 1) {
            channelUseCase.connectChannelFromTrustedAuthorization(
                42,
                "YOUTUBE",
                match { it.authorizationCode == "one-time-code" && !it.codeVerifier.isNullOrBlank() },
            )
        }

        assertFailsWith<IllegalArgumentException> {
            oauth.complete("one-time-code", state)
        }
        verify(exactly = 1) { channelUseCase.connectChannelFromTrustedAuthorization(any(), any(), any()) }
    }

    @Test
    fun `공개 YouTube OAuth도 수익 scope와 재동의를 요청한다`() {
        val url = useCase().authorizationUrl(42, "youtube", null).url

        assertTrue(url.contains("yt-analytics-monetary.readonly"), url)
        assertTrue(url.contains("prompt=consent"), url)
        assertTrue(url.contains("access_type=offline"), url)
    }

    @Test
    fun `state 변조는 연결을 실행하지 않는다`() {
        val oauth = useCase()
        val state = Regex("[?&]state=([^&]+)").find(oauth.authorizationUrl(42, "youtube", null).url)!!.groupValues[1]

        assertFailsWith<IllegalArgumentException> {
            oauth.complete("code", "$state-tampered")
        }
        verify(exactly = 0) { channelUseCase.connectChannelFromTrustedAuthorization(any(), any(), any()) }
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

    @Test
    fun `Naver Clip OAuth는 가짜 로그인 URL을 만들지 않는다`() {
        val error = assertFailsWith<com.ongo.common.exception.BusinessException> {
            useCase().authorizationUrl(42, "naver_clip", null)
        }

        assertTrue(error.message?.contains("공개 업로드 API") == true)
    }

    @Test
    fun `TikTok과 Instagram은 실제 게시 scope를 요청한다`() {
        val tiktokUrl = useCase().authorizationUrl(42, "tiktok", null).url
        val instagramUrl = useCase().authorizationUrl(42, "instagram", null).url

        assertTrue(tiktokUrl.contains("video.publish"))
        assertTrue(instagramUrl.contains("instagram_business_content_publish"))
    }

    /** 인가 URL 의 쿼리를 `이름 to 값` 으로 편다. */
    private fun queryOf(url: String): Map<String, String> =
        url.substringAfter('?').split("&").associate { pair ->
            val (name, value) = pair.split("=", limit = 2)
            name to URLDecoder.decode(value, "UTF-8")
        }

    /**
     * TikTok Login Kit for Web 은 `client_key` 를 요구한다. `client_id` 가 같이 남아 있으면
     * 안 된다 — TikTok 은 모르는 파라미터를 무시하므로 둘 다 보내면 통과하겠지만, 그러면
     * 어느 쪽이 실제로 쓰이는지 알 수 없는 상태가 그대로 굳는다.
     */
    @Test
    fun `공개 API TikTok 인가 URL은 client_key만 싣는다`() {
        val query = queryOf(useCase().authorizationUrl(42, "tiktok", null).url)

        assertEquals("tiktok-client-id", query["client_key"], "client_key가 없다: $query")
        assertFalse(query.containsKey("client_id"), "TikTok에 client_id가 남아 있다: $query")
    }

    /** TikTok 분기가 다른 제공자까지 끌고 가지 않았는지 — 여기가 깨지면 전체 연동이 죽는다. */
    @Test
    fun `공개 API의 TikTok 외 플랫폼은 client_id를 유지한다`() {
        val others = Platform.entries - Platform.TIKTOK - Platform.NAVER_CLIP

        others.forEach { platform ->
            val query = queryOf(useCase().authorizationUrl(42, platform.name, null).url)
            assertTrue(query.containsKey("client_id"), "$platform 에 client_id가 없다: $query")
            assertFalse(query.containsKey("client_key"), "$platform 에 client_key가 잘못 붙었다: $query")
        }
    }

    /**
     * 파라미터 이름만 고치고 나머지 조립을 흘리지 않았는지. 이 빌더는 `parameters` 맵에서
     * 키로 값을 꺼내 쓰기 때문에, 키 이름을 바꾸면 값이 통째로 `null` 이 되는 실수를 하기
     * 쉽다.
     */
    @Test
    fun `TikTok 인가 URL의 나머지 파라미터는 그대로다`() {
        val query = queryOf(useCase().authorizationUrl(42, "tiktok", null).url)

        assertEquals("code", query["response_type"], query.toString())
        assertEquals(
            "https://ongo.example.com/public/v1/social/callback",
            query["redirect_uri"],
            query.toString(),
        )
        assertTrue(query["state"]?.isNotBlank() == true, query.toString())
        assertEquals("video.publish,video.upload,video.list", query["scope"], query.toString())
    }
}
