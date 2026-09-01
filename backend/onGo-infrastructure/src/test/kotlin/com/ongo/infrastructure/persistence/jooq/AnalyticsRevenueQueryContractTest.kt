package com.ongo.infrastructure.persistence.jooq

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.analytics.PlatformMetricAvailability
import com.ongo.domain.analytics.AnalyticsDaily
import com.ongo.domain.analytics.RevenueMeasurement
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 수익 저장 SQL 의 계약.
 *
 * 여기서 지키는 두 가지가 무너지면 실측 수익이 조용히 사라진다.
 *
 * 1. 일반 분석 upsert 는 수익 컬럼을 건드리지 않는다 — 호출자가 수익을 조회하지 않으므로
 *    건드리는 순간 6시간마다 0 으로 덮인다.
 * 2. 수익 갱신은 이미 MEASURED 인 행을 비-MEASURED 로 덮지 않는다 — 권한이 끊기거나
 *    확정이 지연돼도 이미 확인한 금액은 남아야 한다.
 */
class AnalyticsRevenueQueryContractTest {

    private fun capture(block: (AnalyticsJooqRepository) -> Unit): List<String> {
        val executed = mutableListOf<String>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            executed += context.sql()
            arrayOf(MockResult(0, empty))
        }
        val repository = AnalyticsJooqRepository(
            DSL.using(MockConnection(provider), SQLDialect.POSTGRES),
            jacksonObjectMapper(),
        )
        runCatching { block(repository) } // 결과 조회는 mock 이 비어 있어 실패해도 SQL 은 남는다
        return executed
    }

    @Test
    @DisplayName("일반 분석 upsert 는 수익 컬럼을 쓰지 않는다")
    fun generalUpsertNeverTouchesRevenue() {
        val sql = capture {
            it.upsert(
                AnalyticsDaily(
                    videoUploadId = 55L,
                    date = LocalDate.of(2026, 8, 20),
                    views = 120,
                    likes = 9,
                ),
            )
        }.first().lowercase()

        assertTrue("insert into" in sql, sql)
        assertTrue("views" in sql, sql)
        // 수익 컬럼이 여기 등장하면 조회하지도 않은 값으로 실측 수익을 덮게 된다.
        assertFalse("revenue_micro" in sql, "일반 upsert 가 수익을 덮어씁니다: $sql")
        assertFalse("revenue_status" in sql, "일반 upsert 가 수익 상태를 덮어씁니다: $sql")
        assertFalse("revenue_currency" in sql, "일반 upsert 가 수익 통화를 덮어씁니다: $sql")
    }

    /**
     * **INSERT 가 없어야 한다.** upsert 였을 때는 조회 기간 30일 전체에 조회수 0,
     * `created_at` = 동기화 시각인 행이 생겼고, 그 행들이 최적 업로드 시간 추천을
     * "스케줄러가 도는 시각"으로 끌어갔다.
     */
    @Test
    @DisplayName("수익 갱신은 행을 새로 만들지 않는다")
    fun revenueUpdateNeverInserts() {
        val sql = capture {
            it.updateRevenue(55L, LocalDate.of(2026, 8, 20), RevenueMeasurement.PENDING)
        }.first().lowercase()

        assertTrue(sql.trimStart().startsWith("update"), "UPDATE 가 아닙니다: $sql")
        assertFalse("insert" in sql, "수익 갱신이 행을 생성합니다: $sql")
        assertFalse("on conflict" in sql, "upsert 로 되돌아갔습니다: $sql")
        // 대상 행을 정확히 한 날짜로 좁힌다. date 는 파티션 키이기도 하다.
        assertTrue("video_upload_id" in sql, sql)
        assertTrue("date" in sql, sql)
    }

    @Test
    @DisplayName("수익 갱신은 이미 측정된 행을 비측정으로 덮지 않는다")
    fun revenueUpdatePreservesMeasuredRows() {
        val sql = capture {
            it.updateRevenue(55L, LocalDate.of(2026, 8, 20), RevenueMeasurement.PERMISSION_REQUIRED)
        }.first().lowercase()

        val where = sql.substringAfter(" where ")
        // 보존 조건이 없으면 403 한 번에 실측 금액이 전부 지워진다.
        assertTrue("revenue_status" in where, "상태 비교가 없습니다: $sql")
        assertTrue("measured" in where || "?" in where, "보존 조건이 없습니다: $sql")
    }

    @Test
    @DisplayName("수익 갱신은 세 컬럼만 쓴다")
    fun revenueUpdateOnlyWritesRevenueColumns() {
        val sql = capture {
            it.updateRevenue(55L, LocalDate.of(2026, 8, 20), RevenueMeasurement.measured(1_000_000L, "KRW"))
        }.first().lowercase()

        val setClause = sql.substringAfter(" set ").substringBefore(" where ")
        assertTrue("revenue_micro" in setClause, sql)
        assertTrue("revenue_currency" in setClause, sql)
        assertTrue("revenue_status" in setClause, sql)
        // 일반 지표를 여기서 건드리면 수익 주기가 조회수를 0 으로 되돌린다.
        assertFalse("views" in setClause, "수익 갱신이 조회수를 덮어씁니다: $sql")
        assertFalse("likes" in setClause, "수익 갱신이 좋아요를 덮어씁니다: $sql")
        assertFalse("impressions" in setClause, "수익 갱신이 노출수를 덮어씁니다: $sql")
    }

    /**
     * 금액 합산 네 곳이 **상태와 플랫폼을 모두** 거르는지 실제 SQL 로 고정한다.
     *
     * 예전에는 가용성 판정만 플랫폼을 걸렀고 합산은 상태만 봤다. 그래서 수익을 수집하지
     * 않는 플랫폼에 `MEASURED` 행이 하나라도 생기면 금액은 더해지는데 화면은
     * "수집하지 않습니다" 라고 말하는 모순이 났다.
     */
    @Test
    @DisplayName("금액 합산 네 곳이 모두 측정 상태와 수익 수집 플랫폼을 함께 거른다")
    fun everyRevenueSumFiltersStatusAndPlatform() {
        val from = LocalDate.of(2026, 8, 1)
        val to = LocalDate.of(2026, 8, 31)

        listOf<Pair<String, (RevenueJooqRepository) -> Unit>>(
            "getTotalRevenue" to { it.getTotalRevenue(1L, from, to) },
            "getDailyRevenue" to { it.getDailyRevenue(1L, from, to) },
            "getPlatformRevenue" to { it.getPlatformRevenue(1L, from, to) },
            "getCpmRpmByPlatform" to { it.getCpmRpmByPlatform(1L, from, to) },
        ).forEach { (name, call) ->
            val sql = captureRevenue(call).last().lowercase()
            // GROUP BY / ORDER BY 에도 platform 이 나오므로 WHERE 절만 본다.
            // 그러지 않으면 필터가 없어도 통과한다.
            val where = sql.substringAfter(" where ").substringBefore(" group by ")

            assertTrue("revenue_status" in where, "$name 에 측정 상태 필터가 없습니다: $sql")
            assertTrue("platform" in where, "$name 에 수익 수집 플랫폼 필터가 없습니다: $sql")
        }
    }

    /** 판정과 합산이 같은 계약에서 나와야 새 플랫폼 추가 시 한쪽만 고치는 일이 없다. */
    @Test
    @DisplayName("합산 SQL 의 플랫폼 목록이 수익 수집 계약과 일치한다")
    fun sumPlatformListMatchesTheContract() {
        val expected = PlatformMetricAvailability.platformsReporting(PlatformMetricAvailability.REVENUE_MICRO)
        assertTrue(expected.isNotEmpty(), "수익 수집 플랫폼이 비면 모든 수익이 0 으로 사라집니다")

        val binds = captureRevenueBinds {
            it.getPlatformRevenue(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
        }.last().map { it?.toString() }

        expected.forEach {
            assertTrue(it in binds, "$it 가 합산 SQL 의 플랫폼 바인딩에 없습니다: $binds")
        }
        // 수집하지 않는 플랫폼이 새어 들어가면 안 된다.
        assertFalse("TIKTOK" in binds, "수익 미수집 플랫폼이 합산에 포함됐습니다: $binds")
    }

    /** 상태 집계는 원인을 설명해야 하므로 플랫폼을 거르지 않는다. */
    @Test
    @DisplayName("상태 집계는 플랫폼을 거르지 않는다")
    fun statusCountsKeepEveryPlatform() {
        val sql = captureRevenue {
            it.getRevenueStatusCounts(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
        }.last().lowercase()

        // GROUP BY 에는 당연히 나오므로 WHERE 절만 본다.
        val where = sql.substringAfter(" where ").substringBefore(" group by ")
        assertFalse("revenue_status" in where, "상태 집계가 상태를 걸렀습니다: $sql")
        assertFalse("platform" in where, "상태 집계가 플랫폼을 걸렀습니다: $sql")
        assertTrue("group by" in sql, sql)
    }

    private fun captureRevenue(block: (RevenueJooqRepository) -> Unit): List<String> {
        val executed = mutableListOf<String>()
        val provider = MockDataProvider { context ->
            executed += context.sql()
            arrayOf(MockResult(0, uploadIdResult()))
        }
        runCatching { block(RevenueJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))) }
        return executed
    }

    private fun captureRevenueBinds(block: (RevenueJooqRepository) -> Unit): List<List<Any?>> {
        val binds = mutableListOf<List<Any?>>()
        val provider = MockDataProvider { context ->
            binds += context.bindings().toList()
            arrayOf(MockResult(0, uploadIdResult()))
        }
        runCatching { block(RevenueJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))) }
        return binds
    }

    /** getUserUploadIds 가 비면 조기 반환해 합산 SQL 이 아예 실행되지 않는다. */
    private fun uploadIdResult(): org.jooq.Result<org.jooq.Record1<Long>> {
        val create = DSL.using(SQLDialect.POSTGRES)
        val field = DSL.field("id", Long::class.java)
        val result = create.newResult(field)
        result.add(create.newRecord(field).also { it.setValue(field, 55L) })
        return result
    }
    // ── 게시 시각의 원천 ─────────────────────────────────────────────────────

    /**
     * **`publishedAt` 은 `video_uploads.published_at` 이어야 한다.**
     *
     * 예전에는 `v.created_at` — 우리 DB 에 영상 **레코드가 만들어진 시각** — 을 읽어
     * `CrossPlatformDetailRaw.publishedAt` 에 넣었다. 업로드 준비 시각과 실제 게시 시각은
     * 다르고, 예약 게시를 쓰면 며칠씩 벌어진다.
     *
     * 최적 게시시간 추천이 이 값을 요일·시각으로 쓰므로 원천이 틀리면 추천 전체가 틀린다.
     * 도메인 필드 이름만 `publishedAt` 이면 조회하는 컬럼이 바뀌어도 아무도 모른다 —
     * 그래서 SQL 을 직접 본다.
     */
    @Test
    @DisplayName("크로스 플랫폼 상세 조회는 video_uploads.published_at 을 읽는다")
    fun detailMetricsSelectPublishedAtFromVideoUploads() {
        val sql = capture { it.findCrossPlatformDetailMetrics(userId = 7L, days = 30) }.joinToString("\n")

        assertTrue(
            sql.contains("vu.published_at"),
            "게시 시각을 video_uploads.published_at 에서 읽지 않는다:\n$sql",
        )
        assertFalse(
            sql.contains("v.created_at"),
            "레코드 생성 시각(v.created_at)을 게시 시각으로 재사용하고 있다:\n$sql",
        )
    }

    /** GROUP BY 도 같은 컬럼이어야 한다. 하나만 바꾸면 SQL 이 깨지거나 값이 어긋난다. */
    @Test
    @DisplayName("published_at 은 select 와 group by 양쪽에 쓰인다")
    fun publishedAtIsGroupedConsistently() {
        val sql = capture { it.findCrossPlatformDetailMetrics(userId = 7L, days = 30) }.joinToString("\n")

        val groupBy = sql.substringAfter("group by", "")
        assertTrue(groupBy.contains("vu.published_at"), "group by 에 published_at 이 없다:\n$sql")
    }
}
