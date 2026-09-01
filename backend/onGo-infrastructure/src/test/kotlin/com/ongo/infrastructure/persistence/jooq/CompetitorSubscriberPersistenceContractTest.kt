package com.ongo.infrastructure.persistence.jooq

import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `competitors.subscriber_count` 와 일별 스냅샷의 **읽기 계약**.
 *
 * ## 무엇이 거짓이었나
 *
 * 두 컬럼 모두 `NOT NULL` 이 아니고 기본값만 `0` 이다
 * (`V4__analytics_tables.sql:9`, `V61__create_competitor_analytics_daily.sql:22`).
 * 그런데 저장소가 `get(SUBSCRIBER_COUNT) ?: 0` 으로 읽어, **NULL 로 저장된 행을 조회할
 * 때마다 0 으로 되살렸다.** 조회 어댑터에서 아무리 정직하게 null 을 넣어도 화면에는
 * "구독자 0명" 이 떴다.
 *
 * 스냅샷 쪽은 더 나쁘다. 성장률이 두 스냅샷의 차이로 계산되므로, 재지 못한 날을 0 으로
 * 읽으면 **어제 10,000 → 오늘 0 이 되어 -100% 폭락**을 지어낸다.
 */
class CompetitorSubscriberPersistenceContractTest {

    private val ctx = DSL.using(SQLDialect.POSTGRES)

    // ── 경쟁자 엔티티 ────────────────────────────────────────────────────────

    private val competitorFields = arrayOf(
        Fields.ID, Fields.USER_ID, Fields.PLATFORM, Fields.PLATFORM_CHANNEL_ID,
        Fields.CHANNEL_NAME, Fields.CHANNEL_URL, Fields.SUBSCRIBER_COUNT, Fields.TOTAL_VIEWS,
        Fields.VIDEO_COUNT, Fields.AVG_VIEWS, Fields.PROFILE_IMAGE_URL, Fields.LAST_SYNCED_AT,
        Fields.CREATED_AT, Fields.UPDATED_AT,
    )

    private fun competitorRepository(
        subscriberCount: Long?,
        videoCount: Int? = 12,
        totalViews: Long? = 1_000L,
    ): CompetitorJooqRepository {
        val rows = ctx.newResult(*competitorFields).apply {
            add(
                ctx.newRecord(*competitorFields).also {
                    it.set(Fields.ID, 5L)
                    it.set(Fields.USER_ID, 7L)
                    it.set(Fields.PLATFORM, "YOUTUBE")
                    it.set(Fields.PLATFORM_CHANNEL_ID, "UC_rival")
                    it.set(Fields.CHANNEL_NAME, "경쟁 채널")
                    it.set(Fields.SUBSCRIBER_COUNT, subscriberCount)
                    it.set(Fields.TOTAL_VIEWS, totalViews)
                    it.set(Fields.VIDEO_COUNT, videoCount)
                    it.set(Fields.AVG_VIEWS, 83L)
                },
            )
        }
        val provider = MockDataProvider { arrayOf(MockResult(rows.size, rows)) }
        return CompetitorJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))
    }

    /** **이 케이스가 저장된 NULL 을 0 으로 되살리던 자리다.** */
    @Test
    @DisplayName("NULL 로 저장된 경쟁자 구독자 수는 null 로 읽는다")
    fun nullCompetitorSubscriberStaysNull() {
        val competitor = competitorRepository(null).findById(5L)

        assertNull(competitor?.subscriberCount, "저장된 NULL 을 0 으로 되살렸다")
    }

    /** **실제로 0 이 저장된 행은 0 이다.** */
    @Test
    @DisplayName("0 으로 저장된 경쟁자 구독자 수는 0 으로 읽는다")
    fun storedZeroCompetitorSubscriberStaysZero() {
        val competitor = competitorRepository(0L).findById(5L)

        assertEquals(0L, competitor?.subscriberCount, "실측 0 을 잃었다")
    }

    @Test
    @DisplayName("저장된 경쟁자 구독자 수는 그대로 읽는다")
    fun storedCompetitorSubscriberIsPreserved() {
        val competitor = competitorRepository(8_000L).findById(5L)

        assertEquals(8_000L, competitor?.subscriberCount)
    }

    // ── 경쟁자 영상 수 ───────────────────────────────────────────────────────

    private fun competitorVideoCount(stored: Int?) =
        competitorRepository(subscriberCount = 8_000L, videoCount = stored).findById(5L)?.videoCount

    /** **영상 수는 평균 조회수의 분모다.** NULL 을 0 으로 되살리면 평균까지 사라진다. */
    @Test
    @DisplayName("NULL 로 저장된 영상 수는 null 로 읽는다")
    fun nullVideoCountStaysNull() {
        assertNull(competitorVideoCount(null), "저장된 NULL 을 0 으로 되살렸다")
    }

    /** 영상이 실제로 0 개면 그 0 은 관측이다. */
    @Test
    @DisplayName("0 으로 저장된 영상 수는 0 으로 읽는다")
    fun storedZeroVideoCountStaysZero() {
        assertEquals(0, competitorVideoCount(0), "실측 0 을 잃었다")
    }

    @Test
    @DisplayName("저장된 영상 수는 그대로 읽는다")
    fun storedVideoCountIsPreserved() {
        assertEquals(12, competitorVideoCount(12))
    }

    // ── 경쟁자 총 조회수 ─────────────────────────────────────────────────────

    private fun competitorTotalViews(stored: Long?) =
        competitorRepository(subscriberCount = 8_000L, totalViews = stored).findById(5L)?.totalViews

    /** **총 조회수는 평균의 분자다.** NULL 을 0 으로 되살리면 "평균 0회" 가 만들어진다. */
    @Test
    @DisplayName("NULL 로 저장된 총 조회수는 null 로 읽는다")
    fun nullTotalViewsStaysNull() {
        assertNull(competitorTotalViews(null), "저장된 NULL 을 0 으로 되살렸다")
    }

    /** 조회가 실제로 0 이면 그 0 은 관측이다. */
    @Test
    @DisplayName("0 으로 저장된 총 조회수는 0 으로 읽는다")
    fun storedZeroTotalViewsStaysZero() {
        assertEquals(0L, competitorTotalViews(0L), "실측 0 을 잃었다")
    }

    @Test
    @DisplayName("저장된 총 조회수는 그대로 읽는다")
    fun storedTotalViewsIsPreserved() {
        assertEquals(1_000L, competitorTotalViews(1_000L))
    }

    // ── 일별 스냅샷 ──────────────────────────────────────────────────────────

    private val analyticsFields = arrayOf(
        Fields.ID, Fields.COMPETITOR_ID, Fields.DATE, Fields.SUBSCRIBER_COUNT,
        Fields.VIDEO_COUNT, Fields.AVG_VIEWS, Fields.AVG_LIKES, Fields.AVG_COMMENTS,
        Fields.TOTAL_VIEWS, Fields.CREATED_AT,
    )

    private fun analyticsRepository(subscriberCount: Long?): CompetitorJooqRepository {
        val rows = ctx.newResult(*analyticsFields).apply {
            add(
                ctx.newRecord(*analyticsFields).also {
                    it.set(Fields.ID, 1L)
                    it.set(Fields.COMPETITOR_ID, 5L)
                    it.set(Fields.DATE, LocalDate.of(2026, 8, 1))
                    it.set(Fields.SUBSCRIBER_COUNT, subscriberCount)
                    it.set(Fields.VIDEO_COUNT, 12)
                    it.set(Fields.AVG_VIEWS, 83L)
                    it.set(Fields.AVG_LIKES, 5L)
                    it.set(Fields.AVG_COMMENTS, 1L)
                    it.set(Fields.TOTAL_VIEWS, 1_000L)
                },
            )
        }
        val provider = MockDataProvider { arrayOf(MockResult(rows.size, rows)) }
        return CompetitorJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))
    }

    private fun snapshotSubscribers(stored: Long?) = analyticsRepository(stored)
        .findAnalyticsByCompetitorIdAndDateRange(
            5L,
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31),
        )
        .single()
        .subscriberCount

    /** **재지 못한 날을 0 으로 읽으면 성장률이 -100% 폭락을 지어낸다.** */
    @Test
    @DisplayName("NULL 로 저장된 스냅샷 구독자 수는 null 로 읽는다")
    fun nullSnapshotSubscriberStaysNull() {
        assertNull(snapshotSubscribers(null), "저장된 NULL 을 0 으로 되살렸다")
    }

    /** 그날 실제로 0 명이었으면 그 0 은 관측이다. */
    @Test
    @DisplayName("0 으로 저장된 스냅샷 구독자 수는 0 으로 읽는다")
    fun storedZeroSnapshotSubscriberStaysZero() {
        assertEquals(0L, snapshotSubscribers(0L), "실측 0 을 잃었다")
    }

    @Test
    @DisplayName("저장된 스냅샷 구독자 수는 그대로 읽는다")
    fun storedSnapshotSubscriberIsPreserved() {
        assertEquals(8_000L, snapshotSubscribers(8_000L))
    }
}
