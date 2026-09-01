package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.EncryptedToken
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 구독자 합계가 **재지 않은 채널을 0 명으로 세지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * `channels.sumOf { it.subscriberCount }` 는 모든 채널을 더했다. `ThreadsClient.kt:205`
 * 와 `LinkedInClient.kt:264` 는 팔로워 수를 **묻지도 않고** `subscriberCount = 0` 을
 * 박아 넣으므로, 그 두 플랫폼만 연동한 크리에이터의 합계는 항상 `0` 이었다. 그 `0` 이
 * 유료 LLM 프롬프트에 "총 구독자 수: 0" 으로 들어갔다.
 *
 * **실측 0 은 보존한다** — 조회하는 플랫폼의 채널이 실제로 0 명인 것은 관측이다.
 */
class ChannelSubscriberTotalTest {

    private fun channel(platform: Platform, subscribers: Long) = Channel(
        userId = 1L,
        platform = platform,
        platformChannelId = "ch-${platform.name}",
        channelName = platform.name,
        subscriberCount = subscribers,
        accessToken = EncryptedToken("token"),
    )

    // ── 재지 않은 채널 ───────────────────────────────────────────────────────

    /** **이 케이스가 "구독자 0 명" 을 지어내던 자리다.** */
    @Test
    @DisplayName("Threads·LinkedIn 만 연동했으면 합계가 null 이다")
    fun unmeasuredOnlyChannelsProduceNull() {
        val total = ChannelSubscriberTotal.measuredTotal(
            listOf(channel(Platform.THREADS, 0), channel(Platform.LINKEDIN, 0)),
        )

        assertNull(total, "재지 않은 채널을 0 명으로 셌다")
    }

    @Test
    @DisplayName("연동 채널이 없으면 합계가 null 이다")
    fun noChannelsProduceNull() {
        assertNull(ChannelSubscriberTotal.measuredTotal(emptyList()))
    }

    /** Naver Clip 은 `getChannelInfo` 가 예외를 던져 값을 만들 수 없다. */
    @Test
    @DisplayName("Naver Clip 은 구독자 수를 조회하지 않는 것으로 본다")
    fun naverClipDoesNotReport() {
        assertFalse(ChannelSubscriberTotal.reports(Platform.NAVER_CLIP))
    }

    // ── 실측 보존 ────────────────────────────────────────────────────────────

    /** **조회하는 플랫폼의 0 은 관측이다.** 갓 만든 채널의 구독자 0 명. */
    @Test
    @DisplayName("조회하는 플랫폼의 실측 0 은 0 으로 남긴다")
    fun measuredZeroIsPreserved() {
        val total = ChannelSubscriberTotal.measuredTotal(listOf(channel(Platform.YOUTUBE, 0)))

        assertEquals(0L, total, "실측 0 을 미측정으로 감췄다")
    }

    @Test
    @DisplayName("조회하는 채널만 더한다")
    fun onlyReportingChannelsAreSummed() {
        val total = ChannelSubscriberTotal.measuredTotal(
            listOf(
                channel(Platform.YOUTUBE, 1_000),
                channel(Platform.INSTAGRAM, 500),
                // 아래 둘은 잰 적이 없다 — 0 을 더해 합계를 낮추면 안 된다.
                channel(Platform.THREADS, 0),
                channel(Platform.LINKEDIN, 0),
            ),
        )

        assertEquals(1_500L, total)
    }

    /**
     * 재지 않는 채널이 섞여도 **잰 채널의 합계는 그대로**다. 미측정을 이유로 전체를
     * 버리면 실제로 관측한 구독자 수까지 사라진다.
     */
    @Test
    @DisplayName("미측정 채널이 섞여도 측정된 합계를 버리지 않는다")
    fun mixedChannelsKeepTheMeasuredSum() {
        val total = ChannelSubscriberTotal.measuredTotal(
            listOf(channel(Platform.THREADS, 0), channel(Platform.YOUTUBE, 42)),
        )

        assertEquals(42L, total)
    }

    // ── 채널 하나 (목록·관리자 화면이 쓰는 경로) ─────────────────────────────

    /** **이 케이스가 화면에 "구독자 0명" 을 그리던 자리다.** */
    @Test
    @DisplayName("조회하지 않는 플랫폼의 채널 하나는 null 이다")
    fun measuredIsNullForUnmeasuredPlatform() {
        for (platform in listOf(Platform.THREADS, Platform.LINKEDIN, Platform.NAVER_CLIP)) {
            assertNull(ChannelSubscriberTotal.measured(channel(platform, 0)), "$platform 을 측정으로 봤다")
        }
    }

    @Test
    @DisplayName("조회하는 플랫폼의 채널 하나는 실측 0 을 보존한다")
    fun measuredKeepsZeroForReportingPlatform() {
        assertEquals(0L, ChannelSubscriberTotal.measured(channel(Platform.YOUTUBE, 0)))
        assertEquals(8_000L, ChannelSubscriberTotal.measured(channel(Platform.YOUTUBE, 8_000)))
    }

    /** 합계와 개별 표시가 **같은 기준**을 써야 한다 — 화면과 합계가 어긋나면 모순이 난다. */
    @Test
    @DisplayName("합계는 개별 판정과 같은 기준을 쓴다")
    fun totalAgreesWithPerChannelJudgement() {
        val channels = listOf(
            channel(Platform.YOUTUBE, 1_000),
            channel(Platform.THREADS, 0),
            channel(Platform.INSTAGRAM, 500),
        )

        assertEquals(
            channels.mapNotNull { ChannelSubscriberTotal.measured(it) }.sum(),
            ChannelSubscriberTotal.measuredTotal(channels),
        )
    }

    // ── 플랫폼 분류가 어댑터와 일치하는가 ────────────────────────────────────

    /**
     * 어댑터가 **실제 응답 필드를 읽는** 플랫폼 목록. 각 근거는
     * [ChannelSubscriberTotal] 의 KDoc 에 파일·줄과 함께 적혀 있다.
     */
    @Test
    @DisplayName("응답 필드를 읽는 플랫폼은 조회하는 것으로 분류한다")
    fun adaptersReadingARealFieldAreClassifiedAsReporting() {
        val reporting = listOf(
            Platform.YOUTUBE, Platform.TIKTOK, Platform.INSTAGRAM, Platform.TWITTER,
            Platform.FACEBOOK, Platform.PINTEREST, Platform.WORDPRESS, Platform.TUMBLR,
            Platform.VIMEO, Platform.DAILYMOTION,
        )

        for (platform in reporting) {
            assertTrue(ChannelSubscriberTotal.reports(platform), "$platform 을 미측정으로 분류했다")
        }
    }

    /** `subscriberCount = 0` 을 하드코딩하거나 조회 자체가 없는 플랫폼. */
    @Test
    @DisplayName("0 을 박아 넣는 플랫폼은 미측정으로 분류한다")
    fun adaptersHardcodingZeroAreClassifiedAsUnmeasured() {
        for (platform in listOf(Platform.THREADS, Platform.LINKEDIN, Platform.NAVER_CLIP)) {
            assertFalse(ChannelSubscriberTotal.reports(platform), "$platform 을 측정으로 분류했다")
        }
    }

    /**
     * **새 플랫폼은 기본이 미측정이어야 한다.**
     *
     * 이 테스트는 `Platform` enum 에 값이 늘었을 때 알린다. 어댑터가 구독자 수를 실제로
     * 조회하는지 확인하고 분류를 정하라는 뜻이다 — 확인 전까지 재지 않는 것이 사실이다.
     */
    @Test
    @DisplayName("분류를 정한 플랫폼이 enum 전체를 덮는다")
    fun everyPlatformIsClassified() {
        val classified = setOf(
            Platform.YOUTUBE, Platform.TIKTOK, Platform.INSTAGRAM, Platform.TWITTER,
            Platform.FACEBOOK, Platform.PINTEREST, Platform.WORDPRESS, Platform.TUMBLR,
            Platform.VIMEO, Platform.DAILYMOTION,
            Platform.THREADS, Platform.LINKEDIN, Platform.NAVER_CLIP,
        )

        assertEquals(
            Platform.entries.toSet(),
            classified,
            "새 플랫폼이 생겼다. 어댑터가 구독자 수를 조회하는지 확인하고 분류하라",
        )
    }
}
