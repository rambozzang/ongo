package com.ongo.application.analytics

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 지표 수집 계약의 **방향**을 고정한다.
 *
 * `byPlatform` 은 플랫폼 → **수집하지 않는** 지표 집합이다(`commonUnavailableMetrics`).
 * 그래서 "이 지표를 수집하는 플랫폼"은 값 집합에 지표가 **없는** 쪽이다. 이 방향을
 * 뒤집으면 수익을 지원하는 YouTube 가 빠지고 지원하지 않는 11개가 대신 들어와,
 * 합산 SQL 이 정확히 반대 플랫폼을 더하게 된다.
 */
class PlatformMetricAvailabilityTest {

    @Test
    @DisplayName("수익을 수집하는 플랫폼은 YouTube 뿐이다")
    fun onlyYouTubeReportsRevenue() {
        assertEquals(
            setOf("YOUTUBE"),
            PlatformMetricAvailability.platformsReporting(PlatformMetricAvailability.REVENUE_MICRO),
        )
    }

    /** `platformsReporting` 과 `isAvailable` 이 갈라지면 판정과 합산이 어긋난다. */
    @Test
    @DisplayName("platformsReporting 은 isAvailable 과 항상 일치한다")
    fun reportingAgreesWithIsAvailable() {
        val metrics = listOf(
            PlatformMetricAvailability.VIEWS,
            PlatformMetricAvailability.LIKES,
            PlatformMetricAvailability.COMMENTS,
            PlatformMetricAvailability.SHARES,
            PlatformMetricAvailability.WATCH_TIME_SECONDS,
            PlatformMetricAvailability.AVG_VIEW_DURATION,
            PlatformMetricAvailability.REVENUE_MICRO,
        )
        val platforms = listOf(
            "YOUTUBE", "FACEBOOK", "INSTAGRAM", "TIKTOK", "THREADS", "TWITTER",
            "PINTEREST", "LINKEDIN", "WORDPRESS", "TUMBLR", "VIMEO", "DAILYMOTION",
        )

        metrics.forEach { metric ->
            val reporting = PlatformMetricAvailability.platformsReporting(metric)
            platforms.forEach { platform ->
                assertEquals(
                    PlatformMetricAvailability.isAvailable(platform, metric),
                    platform in reporting,
                    "$platform / $metric 판정이 갈라졌다",
                )
            }
        }
    }

    /** 수익 합산은 절대 비어 있으면 안 된다 — 비면 모든 수익이 0으로 사라진다. */
    @Test
    @DisplayName("수익 수집 플랫폼 집합은 비어 있지 않다")
    fun revenuePlatformsAreNotEmpty() {
        assertTrue(
            PlatformMetricAvailability.platformsReporting(PlatformMetricAvailability.REVENUE_MICRO)
                .isNotEmpty(),
        )
    }

    @Test
    @DisplayName("수익 미수집 플랫폼은 집합에 없다")
    fun unsupportedPlatformsAreExcluded() {
        val reporting = PlatformMetricAvailability.platformsReporting(PlatformMetricAvailability.REVENUE_MICRO)

        listOf("TIKTOK", "INSTAGRAM", "FACEBOOK", "TWITTER", "THREADS", "PINTEREST").forEach {
            assertFalse(it in reporting, "$it 은 수익을 수집하지 않는다")
        }
    }

    /** 등록되지 않은 플랫폼(NAVER_CLIP 등)은 어느 지표도 지원한다고 보지 않는다. */
    @Test
    @DisplayName("알 수 없는 플랫폼은 집합에 들어오지 않는다")
    fun unknownPlatformsAreNeverReported() {
        val reporting = PlatformMetricAvailability.platformsReporting(PlatformMetricAvailability.VIEWS)

        assertFalse("NAVER_CLIP" in reporting)
        assertFalse("UNKNOWN" in reporting)
        assertFalse(PlatformMetricAvailability.isAvailable("NAVER_CLIP", PlatformMetricAvailability.VIEWS))
    }

    // ── 알 수 없는 플랫폼과 Naver Clip ───────────────────────────────────────

    /**
     * **`forPlatform` 과 `isAvailable` 이 서로 반대를 말하고 있었다.**
     *
     * `forPlatform` 은 알 수 없는 플랫폼에 `emptySet()` 을 돌려줬다. 호출부는
     * `metric !in unavailable` 로 읽으므로 그 빈 집합은 **"모든 지표를 수집한다"** 가 된다.
     * `isAvailable` 은 같은 상황에서 fail-closed 였다.
     */
    @Test
    @DisplayName("알 수 없는 플랫폼은 어떤 지표도 수집하지 않는 것으로 본다")
    fun unknownPlatformReportsNothing() {
        val metrics = listOf(
            PlatformMetricAvailability.VIEWS,
            PlatformMetricAvailability.LIKES,
            PlatformMetricAvailability.COMMENTS,
            PlatformMetricAvailability.SHARES,
        )
        val unavailable = PlatformMetricAvailability.forPlatform("SOME_NEW_PLATFORM")

        metrics.forEach { metric ->
            assertTrue(metric in unavailable, "$metric 을 수집한다고 추정했다")
            assertFalse(PlatformMetricAvailability.isAvailable("SOME_NEW_PLATFORM", metric))
        }
    }

    /**
     * **`Platform` enum 은 13개인데 이 맵은 12개였다.** 빠진 하나가 `NAVER_CLIP` 이고,
     * 그래서 그 플랫폼만 조용히 fail-open 이었다.
     *
     * 근거: `NaverClipClient.getVideoAnalytics` 는 값을 돌려주지 않고
     * `PlatformApiException("Naver Clip은 공개 업로드·관리 API를 제공하지 않습니다")` 를 던진다.
     * 수집이 없으므로 그 행의 숫자는 전부 컬럼 기본값 0 이다.
     */
    @Test
    @DisplayName("Naver Clip 은 분석 API 가 없어 어떤 지표도 수집하지 않는다")
    fun naverClipReportsNothing() {
        listOf(
            PlatformMetricAvailability.VIEWS,
            PlatformMetricAvailability.LIKES,
            PlatformMetricAvailability.COMMENTS,
            PlatformMetricAvailability.SHARES,
            PlatformMetricAvailability.WATCH_TIME_SECONDS,
            PlatformMetricAvailability.SUBSCRIBER_GAINED,
        ).forEach { metric ->
            assertFalse(
                PlatformMetricAvailability.isAvailable("NAVER_CLIP", metric),
                "$metric 을 수집한다고 선언했다",
            )
            assertTrue(metric in PlatformMetricAvailability.forPlatform("NAVER_CLIP"))
        }
    }

    /** enum 에 새 플랫폼이 생기면 이 계약에도 항목을 더해야 한다. */
    @Test
    @DisplayName("Platform enum 의 모든 값이 계약에 등록돼 있다")
    fun everyPlatformEnumValueIsDeclared() {
        com.ongo.common.enums.Platform.entries.forEach { platform ->
            assertTrue(
                PlatformMetricAvailability.forPlatform(platform.name).isNotEmpty() ||
                    PlatformMetricAvailability.isAvailable(platform.name, PlatformMetricAvailability.VIEWS),
                "${platform.name} 이 계약에 없다",
            )
        }
    }

    /**
     * 조회수를 **수집하지 않는** 플랫폼을 이름으로 못 박는다. 개수만 세면 어느 플랫폼이
     * 빠졌는지 알 수 없고, 하나가 빠지고 하나가 들어와도 통과한다.
     *
     * - `NAVER_CLIP`: `NaverClipClient.getVideoAnalytics` 가 예외를 던진다.
     * - `TUMBLR`: `TumblrClient.kt:141` 이 `views = total_notes` 로, 조회수가 아니라
     *   **노트 총합(좋아요+리블로그+답글)** 을 넣는다.
     */
    @Test
    @DisplayName("조회수를 수집하지 않는 플랫폼은 Naver Clip 과 Tumblr 뿐이다")
    fun onlyNaverClipAndTumblrDoNotReportViews() {
        val notReporting = com.ongo.common.enums.Platform.entries
            .map { it.name }
            .filterNot { PlatformMetricAvailability.isAvailable(it, PlatformMetricAvailability.VIEWS) }
            .toSet()

        assertEquals(setOf("NAVER_CLIP", "TUMBLR"), notReporting)
    }

    /**
     * 공유를 **수집하지 않는** 플랫폼. 하드코딩 0 인 곳과 **다른 이름의 지표를 공유로
     * 매핑한 곳**이 함께 들어간다.
     *
     * - 하드코딩 0: `FACEBOOK`·`WORDPRESS`·`VIMEO`
     * - 이름 불일치: `PINTEREST`(PIN_CLICK = 클릭 수), `DAILYMOTION`(bookmarks_total = 북마크)
     * - 분석 API 없음: `NAVER_CLIP`
     */
    @Test
    @DisplayName("공유를 수집하지 않는 플랫폼을 이름으로 고정한다")
    fun platformsThatDoNotReportSharesAreFixed() {
        val notReporting = com.ongo.common.enums.Platform.entries
            .map { it.name }
            .filterNot { PlatformMetricAvailability.isAvailable(it, PlatformMetricAvailability.SHARES) }
            .toSet()

        assertEquals(
            setOf("FACEBOOK", "WORDPRESS", "VIMEO", "PINTEREST", "DAILYMOTION", "NAVER_CLIP"),
            notReporting,
        )
    }

    // ── 좋아요를 수집하지 않는 플랫폼 ───────────────────────────────────────

    /**
     * **Pinterest 에는 좋아요 지표가 없다.**
     *
     * `PinterestClient.kt:150` 이 요청하는 metricTypes 는
     * `IMPRESSION,PIN_CLICK,SAVE,VIDEO_START` — 좋아요가 목록에 없다. `:158` 은
     * `likes = metrics["SAVE"]` 로 **저장(Save) 수**를 좋아요 자리에 넣는다.
     *
     * `totalLikes` 는 `DashboardKpi`·`getTopVideos`·`getVideoComparison` 에서 **플랫폼을
     * 가로질러 합산**되므로, 라벨만 바꿔서는 합계가 여전히 서로 다른 행위를 더한다.
     */
    @Test
    @DisplayName("좋아요를 수집하지 않는 플랫폼을 이름으로 고정한다")
    fun platformsThatDoNotReportLikesAreFixed() {
        val notReporting = com.ongo.common.enums.Platform.entries
            .map { it.name }
            .filterNot { PlatformMetricAvailability.isAvailable(it, PlatformMetricAvailability.LIKES) }
            .toSet()

        assertEquals(setOf("PINTEREST", "NAVER_CLIP"), notReporting)
    }

    // ── LinkedIn: 채널 종류에 따라 분석 가능 여부가 갈린다 ───────────────────

    /**
     * **이 계약은 플랫폼 단위이므로 LinkedIn 의 채널 종류 차이를 표현하지 못한다.**
     *
     * `LinkedInClient.getVideoAnalytics` 는 두 갈래다.
     *
     * - `urn:li:ugcPost:` 로 시작하는 **개인 게시물**: 조회 자체를 하지 않고
     *   `PlatformApiException("개인 LinkedIn UGC 게시물은 현재 공개 분석 API가 제공되지
     *   않습니다")` 를 던진다 → `analytics_daily` 행이 생기지 않는다.
     * - **조직 게시물**: `getShareStatistics(q = "organizationalEntity")` 로
     *   `impressionCount`·`likeCount`·`commentCount`·`shareCount` 를 받는다.
     *
     * 그래서 `byPlatform` 의 LinkedIn 항목은 **조직 게시물 기준**이다. 개인 게시물은
     * 값이 0 으로 저장되는 것이 아니라 **행 자체가 없으므로**, 미수집 0 이 섞이는 문제는
     * 생기지 않는다. 채널 종류별 계약이 필요해지면 이 맵이 아니라 채널 단위 판정이
     * 있어야 한다 — 여기서 추정으로 막지 않는다.
     */
    @Test
    @DisplayName("LinkedIn 계약은 조직 게시물 기준이며 네 지표를 수집한다")
    fun linkedInContractReflectsOrganizationEndpoint() {
        listOf(
            PlatformMetricAvailability.VIEWS,
            PlatformMetricAvailability.LIKES,
            PlatformMetricAvailability.COMMENTS,
            PlatformMetricAvailability.SHARES,
        ).forEach { metric ->
            assertTrue(
                PlatformMetricAvailability.isAvailable("LINKEDIN", metric),
                "$metric 을 미수집으로 선언했다 — 조직 게시물 통계는 실제로 조회한다",
            )
        }

        // 시청 시간·구독 증가는 이 엔드포인트가 주지 않는다.
        assertFalse(PlatformMetricAvailability.isAvailable("LINKEDIN", PlatformMetricAvailability.WATCH_TIME_SECONDS))
        assertFalse(PlatformMetricAvailability.isAvailable("LINKEDIN", PlatformMetricAvailability.SUBSCRIBER_GAINED))
    }

    // ── 어댑터 재감사: 같은 유형이 더 있는지 ────────────────────────────────

    /**
     * **13개 어댑터를 다시 훑은 결과를 계약에 고정한다.**
     *
     * 이름이 다른 지표를 매핑한 곳은 이미 막은 셋뿐이다.
     *
     * | 어댑터 | 필드 | 실제 값 | 조치 |
     * |---|---|---|---|
     * | Pinterest `:160` | shares | `PIN_CLICK`(클릭) | 미수집 |
     * | Pinterest `:158` | likes | `SAVE`(저장) | 미수집 |
     * | Dailymotion `:121` | shares | `bookmarks_total`(북마크) | 미수집 |
     * | Tumblr `:141` | views | `total_notes`(노트 총합) | 미수집 |
     *
     * 나머지는 의미가 일치한다 — Threads 의 `replies`→댓글·`reposts`→공유,
     * Vimeo 의 `stats.plays`→조회, Instagram 의 `plays`→조회, WordPress 직접 매핑,
     * YouTube Analytics `views,likes,comments,shares` 1:1.
     *
     * **판단이 갈린 하나**: Facebook 의 `likes` 는
     * `total_video_reactions_by_type_total`(반응 전체 합계)이라 순수 좋아요보다 크지만,
     * 좋아요와 같은 계열의 상위 집합이고 이 엔드포인트에 좋아요만 세는 지표가 없다.
     * 막으면 Facebook 의 유일한 참여 신호가 사라지므로 유지한다.
     */
    @Test
    @DisplayName("어댑터 재감사 결과: 지표별 미수집 플랫폼 집합이 그대로다")
    fun adapterReAuditKeepsTheContractStable() {
        fun notReporting(metric: String) = com.ongo.common.enums.Platform.entries
            .map { it.name }
            .filterNot { PlatformMetricAvailability.isAvailable(it, metric) }
            .toSet()

        // 댓글: Pinterest 만 하드코딩 0(`PinterestClient.kt:159`).
        assertEquals(setOf("PINTEREST", "NAVER_CLIP"), notReporting(PlatformMetricAvailability.COMMENTS))

        // Facebook 은 반응 합계라도 좋아요를 준다 — 미수집이 아니다.
        assertTrue(PlatformMetricAvailability.isAvailable("FACEBOOK", PlatformMetricAvailability.LIKES))

        // Threads 의 replies/reposts 는 댓글/공유와 의미가 맞는다.
        assertTrue(PlatformMetricAvailability.isAvailable("THREADS", PlatformMetricAvailability.COMMENTS))
        assertTrue(PlatformMetricAvailability.isAvailable("THREADS", PlatformMetricAvailability.SHARES))

        // Instagram 은 네 지표를 모두 실제로 조회한다(`InstagramClient.kt:264`).
        listOf(
            PlatformMetricAvailability.VIEWS,
            PlatformMetricAvailability.LIKES,
            PlatformMetricAvailability.COMMENTS,
            PlatformMetricAvailability.SHARES,
        ).forEach { assertTrue(PlatformMetricAvailability.isAvailable("INSTAGRAM", it), "INSTAGRAM/$it") }
    }

    // ── 의미 역전 의혹 검증: 독립 검토에서 제기된 정확한 예시 ────────────────

    /**
     * **`byPlatform` 의 값은 "수집하는 지표" 가 아니라 "수집하지 않는 지표" 집합이다.**
     *
     * 필드 이름이 `commonUnavailableMetrics` 이고 항목이
     * `"FACEBOOK" to commonUnavailableMetrics + SHARES` 처럼 **미수집을 더하는** 형태다.
     * 그래서 "수집한다" 는 판정은 **집합에 없을 때** 참이며,
     * `?.contains(metric) == false` 가 그 뜻이다.
     *
     * 알 수 없는 플랫폼은 `null?.contains(...)` 가 `null` 이라 `null == false` → `false`.
     * 이것이 fail-closed 다 — `!contains` 로 쓰면 NPE 이거나 fail-open 이 된다.
     *
     * 아래는 코드 검토에서 "반대로 동작한다" 고 지목된 바로 그 조합들이다. 값이
     * 기대와 일치하는지 한 자리에서 못 박는다.
     */
    @Test
    @DisplayName("지목된 조합의 수집 여부가 기대와 일치한다")
    fun citedCombinationsMatchExpectedSemantics() {
        // 수집한다 → true
        assertTrue(PlatformMetricAvailability.isAvailable("YOUTUBE", PlatformMetricAvailability.VIEWS))
        assertTrue(
            PlatformMetricAvailability.isAvailable("YOUTUBE", PlatformMetricAvailability.SUBSCRIBER_GAINED),
        )

        // 수집하지 않는다 → false
        assertFalse(
            PlatformMetricAvailability.isAvailable("TIKTOK", PlatformMetricAvailability.SUBSCRIBER_GAINED),
        )

        // 분석 API 가 없거나 알 수 없는 플랫폼 → 전부 false(fail-closed)
        listOf(
            PlatformMetricAvailability.VIEWS,
            PlatformMetricAvailability.LIKES,
            PlatformMetricAvailability.COMMENTS,
            PlatformMetricAvailability.SHARES,
            PlatformMetricAvailability.WATCH_TIME_SECONDS,
            PlatformMetricAvailability.SUBSCRIBER_GAINED,
            PlatformMetricAvailability.REVENUE_MICRO,
        ).forEach { metric ->
            assertFalse(PlatformMetricAvailability.isAvailable("NAVER_CLIP", metric), "NAVER_CLIP/$metric")
            assertFalse(PlatformMetricAvailability.isAvailable("SOME_NEW_PLATFORM", metric), "unknown/$metric")
        }
    }

    /**
     * `platformsReporting` 도 같은 방향이다 — **집합에 없는** 플랫폼이 그 지표를 수집한다.
     */
    @Test
    @DisplayName("platformsReporting 의 방향이 수집 기준과 일치한다")
    fun reportingDirectionMatchesCollection() {
        val subscriberPlatforms =
            PlatformMetricAvailability.platformsReporting(PlatformMetricAvailability.SUBSCRIBER_GAINED)

        // `subscribersGained` 를 요청하는 어댑터는 `YouTubeClient` 하나뿐이다.
        assertEquals(setOf("YOUTUBE"), subscriberPlatforms)

        val viewPlatforms = PlatformMetricAvailability.platformsReporting(PlatformMetricAvailability.VIEWS)
        assertTrue("YOUTUBE" in viewPlatforms)
        assertTrue("TIKTOK" in viewPlatforms)
        assertFalse("TUMBLR" in viewPlatforms, "Tumblr 의 views 는 노트 총합이다")
        assertFalse("NAVER_CLIP" in viewPlatforms)
    }

    /** 대소문자가 달라도 같은 판정이어야 한다 — 저장된 문자열이 소문자일 수 있다. */
    @Test
    @DisplayName("플랫폼 이름 대소문자와 무관하게 판정한다")
    fun platformLookupIsCaseInsensitive() {
        assertTrue(PlatformMetricAvailability.isAvailable("youtube", PlatformMetricAvailability.VIEWS))
        assertFalse(PlatformMetricAvailability.isAvailable("tiktok", PlatformMetricAvailability.SUBSCRIBER_GAINED))
    }
}
