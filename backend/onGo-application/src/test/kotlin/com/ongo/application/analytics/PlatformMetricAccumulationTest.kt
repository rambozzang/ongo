package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.domain.analytics.EngagementBasis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "이 플랫폼이 주는 숫자가 기간값인가 평생 누적인가" 계약을 고정한다.
 *
 * ## 왜 이 계약이 필요했나
 *
 * 13개 어댑터가 모두 `getVideoAnalytics(videoId, token, startDate, endDate)` 라는 같은
 * 시그니처를 갖는다. 그래서 **호출부에서는 전부 기간 조회처럼 보인다.** 실제로는
 * YouTube 하나만 그 날짜를 API 에 반영하고, 나머지는 지금 이 순간의 평생 누적을 준다.
 *
 * Pinterest 가 가장 위험한 예다. `startDate`/`endDate` 를 API 에 **실제로 전달하지만**
 * 응답에서 꺼내는 필드가 `all.lifetime_metrics` 다. 호출부만 봐서는 구분할 수 없고,
 * 어댑터 본문에서 "날짜를 쓰는가" 를 세어도 속는다.
 */
class PlatformMetricAccumulationTest {

    /** YouTube 만 Analytics API 에 기간을 넘기고 그 기간의 값을 받는다. */
    @Test
    fun `YouTube 만 기간값으로 본다`() {
        assertEquals(
            PlatformMetricAccumulation.Basis.INCREMENTAL,
            PlatformMetricAccumulation.basisFor("YOUTUBE"),
        )
        assertEquals(setOf("YOUTUBE"), PlatformMetricAccumulation.incrementalPlatformNames())
    }

    /**
     * **핵심 회귀.** 하나라도 증분으로 잘못 선언되면 그 플랫폼의 평생 누적값이 곧바로
     * 합계에 들어가 화면 숫자를 부풀린다.
     */
    @Test
    fun `YouTube 를 뺀 모든 플랫폼은 누적으로 본다`() {
        val misdeclared = Platform.entries
            .filter { it != Platform.YOUTUBE }
            .filterNot { PlatformMetricAccumulation.isCumulative(it.name) }

        assertTrue(
            misdeclared.isEmpty(),
            "평생 누적을 주는 플랫폼이 증분으로 선언됐다: ${misdeclared.map { it.name }}. " +
                "그 값이 그대로 합산돼 조회수가 수십 배로 나온다.",
        )
    }

    /**
     * 모르는 플랫폼을 증분으로 추정하면 그 값이 곧바로 합계에 들어간다. 누적으로 보면
     * 최악의 경우 첫 관측이 기준선으로 빠질 뿐이다 — 틀린 숫자보다 언제나 낫다.
     */
    @Test
    fun `알 수 없는 플랫폼은 누적으로 본다`() {
        assertTrue(PlatformMetricAccumulation.isCumulative("SOME_NEW_PLATFORM"))
        assertTrue(PlatformMetricAccumulation.isCumulative(""))
    }

    /** 설정 파일이나 DB 값은 대소문자가 섞여 들어온다. */
    @Test
    fun `대소문자를 가리지 않는다`() {
        assertFalse(PlatformMetricAccumulation.isCumulative("youtube"))
        assertFalse(PlatformMetricAccumulation.isCumulative("YouTube"))
    }

    // ------------------------------------------------------------ EngagementBasis

    /** **합산 판정 지점은 하나뿐이어야 한다.** 여기가 흔들리면 모든 집계가 흔들린다. */
    @Test
    fun `INCREMENTAL 만 합산 대상이다`() {
        assertTrue(EngagementBasis.INCREMENTAL.summable)
        assertFalse(EngagementBasis.BASELINE.summable, "기준선 행의 0 은 실측이 아니다")
        assertFalse(EngagementBasis.LEGACY_CUMULATIVE.summable, "누적 스냅샷을 더하면 안 된다")
        assertEquals(setOf("INCREMENTAL"), EngagementBasis.summableNames())
    }

    /**
     * SQL 필터가 이 이름들을 문자열로 비교한다. 이름이 바뀌면 **DB CHECK 제약과 어긋나**
     * 저장이 실패하거나, 필터가 아무 행도 못 찾아 화면이 통째로 비어 버린다.
     */
    @Test
    fun `DB CHECK 제약과 같은 이름을 쓴다`() {
        assertEquals(
            setOf("INCREMENTAL", "BASELINE", "LEGACY_CUMULATIVE"),
            EngagementBasis.entries.map { it.name }.toSet(),
            "V115 의 CHECK (engagement_basis IN (...)) 와 어긋났다",
        )
    }

    /** 정체 모를 문자열을 합산 대상으로 추정하면 그 행이 조용히 숫자를 부풀린다. */
    @Test
    fun `알 수 없는 라벨은 합산에서 빠진다`() {
        assertEquals(EngagementBasis.LEGACY_CUMULATIVE, EngagementBasis.from(null))
        assertEquals(EngagementBasis.LEGACY_CUMULATIVE, EngagementBasis.from(""))
        assertEquals(EngagementBasis.LEGACY_CUMULATIVE, EngagementBasis.from("WHATEVER"))
        assertEquals(EngagementBasis.INCREMENTAL, EngagementBasis.from("INCREMENTAL"))
    }
}
