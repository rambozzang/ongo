package com.ongo.application.channel

import com.ongo.application.admin.AdminUseCase
import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.Platform
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channel.EncryptedToken
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.PlatformOAuth2Port
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.domain.workspace.WorkspaceRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 채널 응답의 **구독자 수**가 재지 않은 값을 0 명으로 내보내지 않는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * `channels.subscriber_count` 는 `Long` non-null 이고 기본값이 `0` 이다. 그런데
 * `ThreadsClient.kt:205` 와 `LinkedInClient.kt:264` 는 팔로워 수를 **묻지도 않고**
 * `subscriberCount = 0` 을 박아 넣고, `NaverClipClient.kt:40` 은 채널 조회 자체가
 * 예외를 던진다.
 *
 * 그래서 그 채널들은 저장된 `0` 을 그대로 응답에 실었고, 채널 목록 화면이 **"구독자 0명"**
 * 을, 관리자 화면이 **"구독자: 0"** 을 측정 결과로 그렸다. 사용자는 그것을 채널에 문제가
 * 있다는 신호로 읽는다.
 *
 * **조회하는 플랫폼의 0 은 보존한다** — 갓 만든 채널의 구독자 0 명은 관측이다.
 */
class ChannelSubscriberResponseTest {

    private val channels = mockk<ChannelRepository>()
    private val users = mockk<UserRepository>()

    private val channelUseCase = ChannelUseCase(
        channelRepository = channels,
        userRepository = users,
        platformOAuth2Port = mockk<PlatformOAuth2Port>(relaxed = true),
        platformClientPort = mockk<PlatformClientPort>(relaxed = true),
        tokenEncryptionPort = mockk<TokenEncryptionPort>(relaxed = true),
        videoUploadRepository = mockk<VideoUploadRepository>(relaxed = true),
        workspaceRepository = mockk<WorkspaceRepository>(relaxed = true),
        channelOAuthStateManager = ChannelOAuthStateManager("test-oauth-state-secret-that-is-at-least-32-chars"),
    )

    private val adminUseCase = AdminUseCase(
        userRepository = users,
        subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true),
        videoRepository = mockk<VideoRepository>(relaxed = true),
        videoUploadRepository = mockk<VideoUploadRepository>(relaxed = true),
        channelRepository = channels,
        storageQuotaUseCase = mockk<StorageQuotaUseCase>(relaxed = true),
    )

    private val userId = 7L

    private fun channel(platform: Platform, subscribers: Long) = Channel(
        id = 9L,
        userId = userId,
        platform = platform,
        platformChannelId = "ch-${platform.name}",
        channelName = platform.name,
        subscriberCount = subscribers,
        accessToken = EncryptedToken("token"),
    )

    private fun given(platform: Platform, subscribers: Long) {
        every { users.findById(userId) } returns User(
            id = userId,
            email = "creator@example.com",
            name = "creator",
            provider = AuthProvider.GOOGLE,
            providerId = "google-7",
        )
        every { channels.findByUserId(userId) } returns listOf(channel(platform, subscribers))
    }

    private fun listedSubscribers(platform: Platform, subscribers: Long): Long? {
        given(platform, subscribers)
        return channelUseCase.listChannels(userId).channels.single().subscriberCount
    }

    private fun adminSubscribers(platform: Platform, subscribers: Long): Long? {
        given(platform, subscribers)
        return adminUseCase.getUserChannels(userId).single().subscriberCount
    }

    // ── 채널 목록 응답 ───────────────────────────────────────────────────────

    /** **이 케이스가 화면에 "구독자 0명" 을 그리던 자리다.** */
    @Test
    @DisplayName("구독자 수를 조회하지 않는 플랫폼은 null 로 응답한다")
    fun unmeasuredPlatformsRespondWithNull() {
        for (platform in listOf(Platform.THREADS, Platform.LINKEDIN, Platform.NAVER_CLIP)) {
            assertNull(listedSubscribers(platform, 0), "$platform 의 미측정 0 을 그대로 내보냈다")
        }
    }

    /** **조회하는 플랫폼의 0 은 관측이다.** */
    @Test
    @DisplayName("조회하는 플랫폼의 실측 0 은 0 으로 응답한다")
    fun measuredZeroIsPreserved() {
        assertEquals(0L, listedSubscribers(Platform.YOUTUBE, 0), "실측 0 을 미측정으로 감췄다")
    }

    @Test
    @DisplayName("조회하는 플랫폼의 측정값은 그대로 응답한다")
    fun measuredCountIsPreserved() {
        assertEquals(8_000L, listedSubscribers(Platform.YOUTUBE, 8_000))
        assertEquals(1_200L, listedSubscribers(Platform.INSTAGRAM, 1_200))
    }

    // ── 관리자 채널 목록 ─────────────────────────────────────────────────────
    //
    // 운영자가 사용자 채널을 확인하는 화면이라, 재지 않은 값이 "구독자: 0" 으로 보이면
    // 실제 문제가 있는 채널과 구분되지 않는다.

    @Test
    @DisplayName("관리자 화면도 미측정 플랫폼은 null 로 응답한다")
    fun adminRespondsWithNullForUnmeasuredPlatforms() {
        for (platform in listOf(Platform.THREADS, Platform.LINKEDIN)) {
            assertNull(adminSubscribers(platform, 0), "$platform 의 미측정 0 을 그대로 내보냈다")
        }
    }

    @Test
    @DisplayName("관리자 화면도 실측 0 과 측정값을 보존한다")
    fun adminPreservesMeasuredValues() {
        assertEquals(0L, adminSubscribers(Platform.YOUTUBE, 0))
        assertEquals(8_000L, adminSubscribers(Platform.YOUTUBE, 8_000))
    }
}
