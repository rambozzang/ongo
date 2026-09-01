package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Platform
import com.ongo.domain.channel.PlatformOAuthScopes
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLDecoder
import kotlin.test.assertTrue

/**
 * YouTube 인가 URL 이 **수익 조회 권한까지** 요청하는지 고정한다.
 *
 * `yt-analytics-monetary.readonly` 가 빠지면 `estimatedRevenue` 는 403 이고, 사용자는
 * 재연동을 해도 수익을 볼 수 없다. 그런데 화면상으로는 연동이 성공한 것처럼 보인다 —
 * 여기서 잡지 않으면 운영에서만 드러난다.
 */
class PlatformOAuthScopeTest {

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

    private fun scopeOf(platform: Platform): String {
        val url = URI.create(
            adapter().buildAuthorizationUrl(
                platform,
                "https://ongo.example.com/channels/callback/${platform.name.lowercase()}",
                "state-123",
                "challenge",
            ),
        )
        val query = URLDecoder.decode(url.rawQuery, "UTF-8")
        return query.substringAfter("scope=").substringBefore("&")
    }

    @Test
    @DisplayName("YouTube 인가 URL 이 금전 분석 scope 를 포함한다")
    fun youtubeRequestsMonetaryScope() {
        val scope = scopeOf(Platform.YOUTUBE)

        assertTrue(
            scope.contains("https://www.googleapis.com/auth/yt-analytics-monetary.readonly"),
            "수익 조회 scope 가 없다: $scope",
        )
        // 기존 업로드·관리 권한을 잃으면 안 된다.
        assertTrue(scope.contains("https://www.googleapis.com/auth/youtube"), "기본 scope 가 없다: $scope")
    }

    /**
     * 새 권한은 기존 refresh token 에 소급되지 않는다. 그래서 사용자가 다시 동의 화면을
     * 보도록 `prompt=consent` 가 반드시 남아 있어야 한다 — 없으면 Google 이 기존 동의를
     * 재사용해 수익 권한 없이 조용히 연결된다.
     */
    @Test
    @DisplayName("재동의를 강제하는 prompt=consent 가 유지된다")
    fun youtubeForcesReconsent() {
        val query = URLDecoder.decode(
            URI.create(
                adapter().buildAuthorizationUrl(
                    Platform.YOUTUBE,
                    "https://ongo.example.com/channels/callback/youtube",
                    "state-123",
                    null,
                ),
            ).rawQuery,
            "UTF-8",
        )

        assertTrue(query.contains("prompt=consent"), query)
        assertTrue(query.contains("access_type=offline"), query)
    }

    /** 두 인가 경로가 같은 상수를 쓰는지 — 갈라지면 일부 사용자만 수익 권한을 얻는다. */
    @Test
    @DisplayName("인가 어댑터가 공용 scope 상수를 쓴다")
    fun adapterUsesSharedConstant() {
        assertTrue(scopeOf(Platform.YOUTUBE) == PlatformOAuthScopes.YOUTUBE)
    }

    /** YouTube 외 플랫폼의 scope 는 건드리지 않았다. */
    @Test
    @DisplayName("다른 플랫폼 scope 는 그대로다")
    fun otherPlatformScopesUnchanged() {
        assertTrue(scopeOf(Platform.TIKTOK) == "video.publish,video.upload,video.list")
        assertTrue(scopeOf(Platform.TWITTER) == "tweet.read tweet.write users.read offline.access")
    }
}
