package com.ongo.infrastructure.external.platform

import com.ongo.application.channel.ChannelUseCase
import com.ongo.application.publicapi.PublicOAuthUseCase
import com.ongo.common.enums.Platform
import com.ongo.domain.channel.ChannelRepository
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLDecoder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 인가 URL 이 클라이언트 식별자를 **제공자가 읽는 이름으로** 싣는지 고정한다.
 *
 * TikTok Login Kit for Web 만 `client_key` 를 요구한다. 토큰 교환·갱신(`TikTokClient`)은
 * 처음부터 `client_key` 를 썼는데 인가 URL 만 `client_id` 로 나가고 있었다. 이 어긋남은
 * 사용자가 동의 화면에 닿기도 전에 TikTok 쪽에서 끝나기 때문에 우리 로그에는 남지
 * 않는다 — 여기서 잡지 않으면 "TikTok 연결이 안 된다" 는 제보로만 드러난다.
 */
class PlatformOAuthClientParamTest {

    private fun adapter() = PlatformOAuthAuthorizationAdapter(
        googleClientId = "google-client-id",
        tiktokClientId = "tiktok-client-key",
        instagramClientId = "instagram-app-id",
        twitterClientId = "twitter-client-id",
        facebookClientId = "facebook-app-id",
        threadsClientId = "threads-app-id",
        pinterestClientId = "pinterest-app-id",
        linkedinClientId = "linkedin-client-id",
        wordpressClientId = "wordpress-client-id",
        tumblrClientId = "tumblr-consumer-key",
        vimeoClientId = "vimeo-client-id",
        dailymotionClientId = "dailymotion-api-key",
    )

    /** 인가 URL 의 쿼리를 `이름 to 값` 으로 편다. */
    private fun parseQuery(url: String): Map<String, String> =
        URI.create(url).rawQuery.split("&").associate { pair ->
            val (name, value) = pair.split("=", limit = 2)
            name to URLDecoder.decode(value, "UTF-8")
        }

    private fun queryOf(platform: Platform): Map<String, String> = parseQuery(
        adapter().buildAuthorizationUrl(
            platform,
            "https://ongo.example.com/channels/callback/${platform.name.lowercase()}",
            "state-123",
            "challenge",
        ),
    )

    /**
     * **핵심.** `client_id` 가 같이 남아 있으면 안 된다. TikTok 은 모르는 파라미터를
     * 무시하므로 둘 다 보내면 통과하겠지만, 그러면 어느 쪽이 실제로 쓰이는지 알 수 없는
     * 상태가 그대로 굳는다.
     */
    @Test
    @DisplayName("TikTok 인가 URL 은 client_key 만 싣는다")
    fun tiktokUsesClientKey() {
        val query = queryOf(Platform.TIKTOK)

        assertEquals("tiktok-client-key", query["client_key"], "client_key 가 없다: $query")
        assertFalse(query.containsKey("client_id"), "TikTok 에 client_id 가 남아 있다: $query")
    }

    /** TikTok 분기가 다른 제공자까지 끌고 가지 않았는지 — 여기가 깨지면 전체 연동이 죽는다. */
    @Test
    @DisplayName("TikTok 외 플랫폼은 client_id 를 유지한다")
    fun otherPlatformsKeepClientId() {
        val others = Platform.entries - Platform.TIKTOK - Platform.NAVER_CLIP

        others.forEach { platform ->
            val query = queryOf(platform)
            assertTrue(
                query.containsKey("client_id"),
                "$platform 에 client_id 가 없다: $query",
            )
            assertFalse(
                query.containsKey("client_key"),
                "$platform 에 client_key 가 잘못 붙었다: $query",
            )
        }
    }

    /**
     * 파라미터 이름만 고치고 나머지 조립을 흘리지 않았는지. `access_type`/`prompt`(YouTube)와
     * PKCE(X)는 각각 refresh token 과 토큰 교환의 전제라 하나만 빠져도 연동이 조용히
     * 반쪽이 된다.
     */
    @Test
    @DisplayName("플랫폼별 추가 파라미터가 그대로 남는다")
    fun platformSpecificParametersSurvive() {
        val youtube = queryOf(Platform.YOUTUBE)
        assertEquals("offline", youtube["access_type"], youtube.toString())
        assertEquals("consent", youtube["prompt"], youtube.toString())

        val twitter = queryOf(Platform.TWITTER)
        assertEquals("challenge", twitter["code_challenge"], twitter.toString())
        assertEquals("S256", twitter["code_challenge_method"], twitter.toString())

        // 공통 파라미터는 TikTok 에서도 빠지지 않는다.
        val tiktok = queryOf(Platform.TIKTOK)
        assertEquals("code", tiktok["response_type"], tiktok.toString())
        assertEquals("state-123", tiktok["state"], tiktok.toString())
        assertEquals(
            "https://ongo.example.com/channels/callback/tiktok",
            tiktok["redirect_uri"],
            tiktok.toString(),
        )
    }

    // ────────────────────────────────────────────────────────────────────────
    // 두 인가 경로의 드리프트 방지
    // ────────────────────────────────────────────────────────────────────────

    /**
     * 플랫폼마다 다른 sentinel 자격 증명. 두 경로에 **같은 값**을 넣어 두면, 그 값을 실은
     * 파라미터의 **이름**을 URL 에서 거꾸로 찾아낼 수 있다. 이름을 미리 알고 찾는 게 아니라
     * 값으로 찾기 때문에, 이 비교는 "무엇이 옳은 이름인가"를 다시 가정하지 않는다.
     */
    private fun credential(platform: Platform) = "cred-${platform.name.lowercase()}"

    private fun sentinelAdapter() = PlatformOAuthAuthorizationAdapter(
        googleClientId = credential(Platform.YOUTUBE),
        tiktokClientId = credential(Platform.TIKTOK),
        instagramClientId = credential(Platform.INSTAGRAM),
        twitterClientId = credential(Platform.TWITTER),
        facebookClientId = credential(Platform.FACEBOOK),
        threadsClientId = credential(Platform.THREADS),
        pinterestClientId = credential(Platform.PINTEREST),
        linkedinClientId = credential(Platform.LINKEDIN),
        wordpressClientId = credential(Platform.WORDPRESS),
        tumblrClientId = credential(Platform.TUMBLR),
        vimeoClientId = credential(Platform.VIMEO),
        dailymotionClientId = credential(Platform.DAILYMOTION),
    )

    /**
     * 공개 API 경로. `authorizationUrl(.., refresh = null)` 은 저장소를 건드리지 않으므로
     * 스텁 없는 mock 으로 충분하다 — 호출이 생기면 mockk 가 바로 실패시킨다.
     */
    private fun sentinelPublicUseCase() = PublicOAuthUseCase(
        channelRepository = mockk<ChannelRepository>(),
        channelUseCase = mockk<ChannelUseCase>(),
        callbackUrl = "https://ongo.example.com/public/v1/social/callback",
        successRedirect = "https://ongo.example.com/channels",
        stateSecret = "test-oauth-state-secret-that-is-at-least-32-chars",
        googleClientId = credential(Platform.YOUTUBE),
        tiktokClientId = credential(Platform.TIKTOK),
        instagramClientId = credential(Platform.INSTAGRAM),
        twitterClientId = credential(Platform.TWITTER),
        facebookClientId = credential(Platform.FACEBOOK),
        threadsClientId = credential(Platform.THREADS),
        pinterestClientId = credential(Platform.PINTEREST),
        linkedinClientId = credential(Platform.LINKEDIN),
        wordpressClientId = credential(Platform.WORDPRESS),
        tumblrClientId = credential(Platform.TUMBLR),
        vimeoClientId = credential(Platform.VIMEO),
        dailymotionClientId = credential(Platform.DAILYMOTION),
    )

    /** 자격 증명을 실은 파라미터의 이름. 못 찾거나 여러 개면 조용히 넘기지 않고 실패한다. */
    private fun clientParamNameIn(query: Map<String, String>, platform: Platform): String {
        val matches = query.entries.filter { it.value == credential(platform) }
        assertEquals(1, matches.size, "$platform 자격 증명을 실은 파라미터가 하나가 아니다: $query")
        return matches.single().key
    }

    /**
     * **핵심.** 인증 UI 경로(`PlatformOAuthAuthorizationAdapter`)와 공개 API
     * 경로(`PublicOAuthUseCase`)가 같은 플랫폼에 같은 파라미터 이름을 쓰는지 본다.
     *
     * 두 파일은 인가 URL·scope·client id 검증을 통째로 복제하고 있다. 그래서 한쪽만 고치면
     * 나머지 한쪽은 조용히 낡는다 — 실제로 TikTok `client_key` 수정이 어댑터에만 들어가
     * 공개 API 경로는 계속 깨져 있었다. 여기서 **양쪽 URL 을 실제로 만들어** 비교하므로
     * 한쪽만 바꾸면 이 테스트가 깨진다.
     *
     * 다만 이 테스트는 "두 경로가 같은가"만 본다. "그 이름이 옳은가"는 각 경로의 개별
     * 테스트(여기 [tiktokUsesClientKey], 공개 API 쪽 `PublicOAuthUseCaseTest`)가 지킨다.
     * 둘을 함께 두어야 양쪽을 똑같이 틀리게 바꾸는 것도 막힌다.
     */
    @Test
    @DisplayName("인증 UI 경로와 공개 API 경로가 같은 client 파라미터 이름을 쓴다")
    fun bothAuthorizationPathsAgreeOnClientParamName() {
        val adapter = sentinelAdapter()
        val publicApi = sentinelPublicUseCase()

        // Naver Clip 은 양쪽 모두 인가 URL 자체를 만들지 않는다.
        (Platform.entries - Platform.NAVER_CLIP).forEach { platform ->
            val fromUi = parseQuery(
                adapter.buildAuthorizationUrl(
                    platform,
                    "https://ongo.example.com/channels/callback/${platform.name.lowercase()}",
                    "state-123",
                    "challenge",
                ),
            )
            val fromPublicApi = parseQuery(publicApi.authorizationUrl(42, platform.name, null).url)

            assertEquals(
                clientParamNameIn(fromUi, platform),
                clientParamNameIn(fromPublicApi, platform),
                "$platform 의 client 파라미터 이름이 두 인가 경로에서 갈렸다",
            )
        }
    }
}
