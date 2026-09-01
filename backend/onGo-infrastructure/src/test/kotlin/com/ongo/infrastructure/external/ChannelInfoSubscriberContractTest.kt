package com.ongo.infrastructure.external

import com.ongo.infrastructure.external.linkedin.LinkedInApi
import com.ongo.infrastructure.external.linkedin.LinkedInClient
import com.ongo.infrastructure.external.linkedin.LinkedInConfig
import com.ongo.infrastructure.external.linkedin.LinkedInVideosApi
import com.ongo.infrastructure.external.linkedin.LinkedInOAuthApi
import com.ongo.infrastructure.external.linkedin.dto.LinkedInProfileResponse
import com.ongo.infrastructure.external.threads.ThreadsApi
import com.ongo.infrastructure.external.threads.ThreadsClient
import com.ongo.infrastructure.external.threads.ThreadsConfig
import com.ongo.infrastructure.external.threads.ThreadsOAuthApi
import com.ongo.infrastructure.external.threads.dto.ThreadsUserResponse
import com.ongo.infrastructure.external.tiktok.TikTokApi
import com.ongo.infrastructure.external.tiktok.TikTokClient
import com.ongo.infrastructure.external.tiktok.TikTokConfig
import com.ongo.infrastructure.external.tiktok.TikTokOAuthApi
import com.ongo.infrastructure.external.tiktok.dto.TikTokCreatorInfoResponse
import com.ongo.infrastructure.external.youtube.GoogleOAuthApi
import com.ongo.infrastructure.external.youtube.YouTubeAnalyticsApi
import com.ongo.infrastructure.external.youtube.YouTubeApi
import com.ongo.infrastructure.external.youtube.YouTubeClient
import com.ongo.infrastructure.external.youtube.YouTubeConfig
import com.ongo.infrastructure.external.youtube.dto.YouTubeChannelListResponse
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `getChannelInfo` 가 **구독자 수를 재지 않은 자리에 0 을 넣지 않는지** 플랫폼별로 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 어댑터마다 `?: 0` 이 붙어 있었고 Threads·LinkedIn 은 `subscriberCount = 0` 을 그대로
 * 박아 넣었다. 그 `0` 은 `channels.subscriber_count` 에 저장돼 화면·프롬프트로 흘러가
 * **"구독자 0명"** 이라는 관측처럼 읽혔다 — 실제로는 물어본 적이 없을 뿐이다.
 *
 * ## 왜 응답 DTO 를 직접 넣는가
 *
 * 외부 API 를 흉내 내 **없는 수치를 지어내지 않는다.** 여기서 검증하는 것은 "응답에 그
 * 필드가 없을 때 무엇이 되는가" 라는 매핑 계약이므로, 필드가 비어 있는 응답과 실제로
 * `0` 을 준 응답을 구분해 넣는다. 그 둘의 결과가 달라야 한다는 것이 이 테스트의 전부다.
 */
class ChannelInfoSubscriberContractTest {

    // ── 팔로워 수를 아예 요청하지 않는 플랫폼 ────────────────────────────────

    /** `ThreadsUserResponse` 에는 팔로워 필드 자체가 없다. */
    @Test
    @DisplayName("Threads 는 팔로워 수를 조회하지 않으므로 null 이다")
    fun threadsReportsNull() {
        val api = mockk<ThreadsApi>()
        every { api.getUser(any(), any()) } returns ThreadsUserResponse(
            id = "th-1",
            username = "creator",
            name = "크리에이터",
            profilePictureUrl = null,
            biography = null,
        )
        val client = ThreadsClient(api, mockk<ThreadsOAuthApi>(relaxed = true), mockk<ThreadsConfig>(relaxed = true))

        assertNull(client.getChannelInfo("token").subscriberCount, "묻지 않은 값을 0 으로 냈다")
    }

    /** `LinkedInProfileResponse` 에도 팔로워 필드가 없다 — 개인 프로필만 조회한다. */
    @Test
    @DisplayName("LinkedIn 은 팔로워 수를 조회하지 않으므로 null 이다")
    fun linkedInReportsNull() {
        val api = mockk<LinkedInApi>()
        every { api.getProfile(any(), any()) } returns LinkedInProfileResponse(
            id = "li-1",
            localizedFirstName = "길동",
            localizedLastName = "홍",
            vanityName = "gildong",
            profilePicture = null,
        )
        val client = LinkedInClient(
            api,
            mockk<LinkedInVideosApi>(relaxed = true),
            mockk<LinkedInOAuthApi>(relaxed = true),
            mockk<LinkedInConfig>(relaxed = true),
        )

        assertNull(client.getChannelInfo("token").subscriberCount, "묻지 않은 값을 0 으로 냈다")
    }

    // ── 응답 필드를 읽는 플랫폼: 없음 / 실측 0 / 실측값 ──────────────────────

    private fun tikTokSubscribers(followerCount: Long?): Long? {
        val api = mockk<TikTokApi>()
        every { api.getCreatorInfo(any()) } returns TikTokCreatorInfoResponse(
            data = TikTokCreatorInfoResponse.CreatorData(
                creatorAvatarUrl = null,
                creatorUsername = "creator",
                creatorNickname = "크리에이터",
                followerCount = followerCount,
            ),
            error = null,
        )
        val client = TikTokClient(api, mockk<TikTokOAuthApi>(relaxed = true), mockk<TikTokConfig>(relaxed = true))
        return client.getChannelInfo("token").subscriberCount
    }

    /** **이 케이스가 `?: 0` 으로 없는 값을 관측처럼 만들던 자리다.** */
    @Test
    @DisplayName("TikTok 응답에 팔로워 필드가 없으면 null 이다")
    fun tikTokMissingFieldBecomesNull() {
        assertNull(tikTokSubscribers(null), "응답에 없는 값을 0 으로 냈다")
    }

    /** **응답이 실제로 0 을 주면 그것은 관측이다.** */
    @Test
    @DisplayName("TikTok 이 0 을 주면 0 으로 남긴다")
    fun tikTokMeasuredZeroIsPreserved() {
        assertEquals(0L, tikTokSubscribers(0), "실측 0 을 미측정으로 감췄다")
    }

    @Test
    @DisplayName("TikTok 이 준 값은 그대로 낸다")
    fun tikTokMeasuredCountIsPreserved() {
        assertEquals(8_000L, tikTokSubscribers(8_000))
    }

    private fun youTubeSubscribers(statistics: YouTubeChannelListResponse.ChannelStatistics?): Long? {
        val api = mockk<YouTubeApi>()
        every { api.listChannels(any(), any(), any()) } returns YouTubeChannelListResponse(
            items = listOf(
                YouTubeChannelListResponse.YouTubeChannelItem(
                    id = "yt-1",
                    snippet = null,
                    statistics = statistics,
                ),
            ),
        )
        val client = YouTubeClient(
            api,
            mockk<YouTubeAnalyticsApi>(relaxed = true),
            mockk<GoogleOAuthApi>(relaxed = true),
            mockk<YouTubeConfig>(relaxed = true),
        )
        return client.getChannelInfo("token").subscriberCount
    }

    /** `statistics` 를 통째로 주지 않는 응답(비공개 채널 등). */
    @Test
    @DisplayName("YouTube 응답에 statistics 가 없으면 null 이다")
    fun youTubeMissingStatisticsBecomesNull() {
        assertNull(youTubeSubscribers(null), "응답에 없는 값을 0 으로 냈다")
    }

    /** 구독자 수를 숨긴 채널은 `subscriberCount` 필드가 비어 온다. */
    @Test
    @DisplayName("YouTube 가 구독자 수를 주지 않으면 null 이다")
    fun youTubeHiddenSubscriberCountBecomesNull() {
        val stats = YouTubeChannelListResponse.ChannelStatistics(
            subscriberCount = null,
            viewCount = "1000",
            videoCount = null,
        )

        assertNull(youTubeSubscribers(stats), "숨긴 구독자 수를 0 으로 냈다")
    }

    /** **응답이 실제로 "0" 을 주면 그것은 관측이다.** */
    @Test
    @DisplayName("YouTube 가 0 을 주면 0 으로 남긴다")
    fun youTubeMeasuredZeroIsPreserved() {
        val stats = YouTubeChannelListResponse.ChannelStatistics(
            subscriberCount = "0",
            viewCount = "0",
            videoCount = null,
        )

        assertEquals(0L, youTubeSubscribers(stats), "실측 0 을 미측정으로 감췄다")
    }

    @Test
    @DisplayName("YouTube 가 준 값은 그대로 낸다")
    fun youTubeMeasuredCountIsPreserved() {
        val stats = YouTubeChannelListResponse.ChannelStatistics(
            subscriberCount = "8000",
            viewCount = "1000",
            videoCount = null,
        )

        assertEquals(8_000L, youTubeSubscribers(stats))
    }
}
