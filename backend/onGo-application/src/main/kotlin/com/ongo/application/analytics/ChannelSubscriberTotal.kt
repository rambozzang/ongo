package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.domain.channel.Channel

/**
 * 연동 채널의 **구독자(팔로워) 합계**. 재지 않은 채널을 0 명으로 세지 않는다.
 *
 * ## 무엇이 거짓이었나
 *
 * `channels.sumOf { it.subscriberCount }` 는 모든 채널을 더한다. 그런데
 * [com.ongo.domain.channel.Channel.subscriberCount] 의 기본값은 `0` 이고,
 * **어댑터 두 곳이 그 자리에 0 을 그냥 박아 넣는다.**
 *
 * - `ThreadsClient.kt:205` — `subscriberCount = 0`. 같은 함수가 요청하는 필드는
 *   `id,username,name,threads_profile_picture_url`(`:196`) 로 **팔로워 수를 아예 묻지
 *   않는다.**
 * - `LinkedInClient.kt:264` — `subscriberCount = 0`. 개인 프로필(이름·vanityName)만
 *   조회하고 팔로워 필드는 요청 목록에 없다.
 * - `NaverClipClient.kt:40` — `getChannelInfo` 가 값을 돌려주지 않고 예외를 던진다.
 *
 * 나머지 열 개 플랫폼은 실제 응답 필드를 읽는다 — YouTube `statistics.subscriberCount`,
 * TikTok `followerCount`, Instagram `followersCount`, Twitter `publicMetrics.followersCount`,
 * Facebook `followersCount`, Pinterest `followerCount`, WordPress `subscribers_count`,
 * Tumblr `followers`, Vimeo `metadata.connections.followers.total`,
 * Dailymotion `followersTotal`.
 *
 * 그래서 Threads·LinkedIn 만 연동한 크리에이터는 합계가 `0` 이 되고, 그 `0` 이
 * **유료 LLM 프롬프트에 "총 구독자 수: 0" 으로 들어갔다.** 모델은 그것을 측정값으로 읽고
 * "구독자가 없으니 우선 채널을 알리라" 는 식의 진단을 지어낸다 — 실제로는 구독자 수를
 * 물어본 적이 없을 뿐이다.
 *
 * ## 왜 [PlatformMetricAvailability] 에 넣지 않았나
 *
 * 그 계약은 `analytics_daily` 의 **일자별 지표 컬럼**을 다룬다. 구독자 총계는 채널 프로필
 * 조회(`getChannelInfo`)가 `channels.subscriber_count` 에 쓰는 값으로 출처가 다르다.
 * 같은 맵에 섞으면 `platformsReporting` 을 쓰는 합산 SQL 까지 오염된다.
 * 이름이 비슷한 [PlatformMetricAvailability.SUBSCRIBER_GAINED] 는 **일자별 증가분**이라
 * 여기서 말하는 누적 총계와 다른 값이다.
 */
object ChannelSubscriberTotal {

    /**
     * 구독자 수를 **실제로 조회하는** 플랫폼.
     *
     * 열거하지 않은 플랫폼은 재지 않는 것으로 본다. 반대로(=못 재는 쪽을) 열거하면
     * `Platform` 에 새 값이 생겼을 때 아무도 손대지 않아도 "잰다" 가 되어 버린다.
     * 어댑터가 생기기 전에는 재지 않는 것이 사실이므로 이 방향이 안전하다.
     */
    private val REPORTING_PLATFORMS = setOf(
        Platform.YOUTUBE,
        Platform.TIKTOK,
        Platform.INSTAGRAM,
        Platform.TWITTER,
        Platform.FACEBOOK,
        Platform.PINTEREST,
        Platform.WORDPRESS,
        Platform.TUMBLR,
        Platform.VIMEO,
        Platform.DAILYMOTION,
    )

    /** 이 플랫폼의 채널이 구독자 수를 들고 있는가. */
    fun reports(platform: Platform): Boolean = platform in REPORTING_PLATFORMS

    /**
     * 채널 하나의 구독자 수. **조회하지 않는 플랫폼이면 `null`.**
     *
     * 채널 목록·관리자 화면이 채널을 하나씩 그릴 때 쓴다.
     *
     * ## 어댑터가 이미 null 을 주는데 왜 플랫폼도 보는가
     *
     * 이제 [com.ongo.domain.channel.Channel.subscriberCount] 자체가 nullable 이고 어댑터는
     * 재지 않은 자리에 null 을 넣는다. 그래도 **이 변경 이전에 저장된 행에는 `0` 이 그대로
     * 남아 있다** — Threads·LinkedIn 채널이 그렇다. 운영 DB 를 고쳐 쓰지 않기로 했으므로,
     * 그 행들을 측정값으로 읽지 않으려면 플랫폼 판정이 함께 필요하다.
     *
     * 조회하는 플랫폼의 `0` 은 그대로 `0` 이다 — 갓 만든 채널의 구독자 0 명은 관측이다.
     */
    fun measured(channel: Channel): Long? =
        if (reports(channel.platform)) channel.subscriberCount else null

    /**
     * 구독자 수를 조회하는 채널만 더한 합계.
     *
     * @return 그런 채널이 하나도 없으면 **`null`** — "구독자가 0 명" 이 아니라
     *   "구독자 수를 잰 채널이 없다" 는 뜻이다. 호출자는 이 값을 0 으로 채우지 말고
     *   [com.ongo.domain.analytics.MetricChange.describeCount] 로 문장을 만들어야 한다.
     *
     *   조회하는 채널이 있고 그 값이 실제로 0 이면 **`0` 을 그대로 돌려준다.** 갓 만든
     *   채널의 구독자 0 명은 관측된 사실이다.
     */
    fun measuredTotal(channels: List<Channel>): Long? {
        // [measured] 를 그대로 쓴다 — 합계와 개별 표시가 서로 다른 기준을 쓰면
        // 화면에서 "구독자 미측정" 인 채널이 합계에는 들어가는 모순이 난다.
        val measured = channels.mapNotNull { measured(it) }
        return if (measured.isEmpty()) null else measured.sum()
    }
}
