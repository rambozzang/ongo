package com.ongo.infrastructure.persistence.jooq

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.conf.StatementType
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 대시보드 KPI 의 **구독 증가 합계 SQL 계약**.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * DSL.sum(SUBSCRIBER_GAINED).`as`("total_subs")   // 전 플랫폼, 조인 없음
 * val currentSubs = current?.get("total_subs", Long::class.java) ?: 0L
 * ```
 *
 * `subscriber_gained` 를 실제로 조회하는 어댑터는 `YouTubeClient` 하나뿐이다
 * (`YouTubeClient.kt:149` metrics 목록). 나머지 12개는 `0` 을 하드코딩한다.
 *
 * 합계 자체는 0 이 더해져도 바뀌지 않지만, **수집 플랫폼이 하나도 없을 때의 `0` 이
 * 실측과 구분되지 않았다.** `?: 0L` 이 그 구분을 마저 지웠다.
 *
 * ## 지금의 계약
 *
 * `CASE WHEN vu.platform IN (수집 플랫폼) THEN ad.subscriber_gained END` 로 감싸면
 * 조건에 맞지 않는 행은 NULL 이 되고 `SUM` 은 NULL 을 건너뛴다. 일치하는 행이 하나도
 * 없으면 **합계가 NULL** 이 되어 "측정된 0" 과 "물어볼 곳 없음" 이 갈린다.
 */
class DashboardKpiSubscriberQueryContractTest {

    /**
     * `getDashboardKpi` 는 업로드 ID 조회를 먼저 하고 **비어 있으면 즉시 반환한다.**
     * 첫 조회에 행을 하나 돌려줘야 집계 SQL 이 실행된다.
     */
    private fun captureSql(): List<String> {
        val ctx = DSL.using(SQLDialect.POSTGRES)
        val idField = DSL.field("id", Long::class.java)
        val uploadIdRows = ctx.newResult(idField).apply {
            add(ctx.newRecord(idField).also { it.set(idField, 42L) })
        }

        val executed = mutableListOf<String>()
        var call = 0
        val provider = MockDataProvider { context ->
            executed += context.sql()
            val result = if (call++ == 0) uploadIdRows else ctx.newResult()
            arrayOf(MockResult(result.size, result))
        }

        // 값을 인라인으로 렌더링한다. 바인드 파라미터면 플랫폼 목록이 `?` 로 나와
        // 조건이 통째로 사라져도 이 검사가 통과해 버린다.
        val settings = Settings().withStatementType(StatementType.STATIC_STATEMENT)
        val repository = AnalyticsJooqRepository(
            DSL.using(MockConnection(provider), SQLDialect.POSTGRES, settings),
            jacksonObjectMapper(),
        )
        runCatching { repository.getDashboardKpi(1L, 30) }

        return executed
    }

    private fun aggregateSql(): List<String> =
        captureSql().map { it.lowercase() }.filter { "subscriber_gained" in it }

    @Test
    @DisplayName("구독 합계는 수집 플랫폼 조건을 건 CASE 로 감싼다")
    fun subscriberSumIsGuardedByAPlatformCase() {
        val sqls = aggregateSql()

        assertTrue(sqls.isNotEmpty(), "구독 합계 SQL 이 실행되지 않았다")
        sqls.forEach { sql ->
            assertTrue("case" in sql, "CASE 없이 전 플랫폼을 더한다: $sql")
            assertTrue("youtube" in sql, "수집 플랫폼 목록이 빠졌다: $sql")
        }
    }

    /** 플랫폼을 알려면 `video_uploads` 와 조인해야 한다. */
    @Test
    @DisplayName("구독 합계 쿼리는 video_uploads 와 조인한다")
    fun subscriberQueryJoinsVideoUploads() {
        aggregateSql().forEach { sql ->
            assertTrue("video_uploads" in sql, "조인이 없어 플랫폼을 알 수 없다: $sql")
        }
    }

    /**
     * `ELSE 0` 을 붙이면 조건에 맞지 않는 행이 0 이 되어 **합계가 NULL 이 되지 않는다.**
     * 그러면 "측정된 0" 과 "물어볼 곳 없음" 을 다시 구분할 수 없다.
     */
    @Test
    @DisplayName("CASE 에 else 0 을 붙이지 않는다")
    fun caseHasNoElseZero() {
        aggregateSql().forEach { sql ->
            val caseStart = sql.indexOf("case")
            val caseEnd = sql.indexOf("end", caseStart)
            assertTrue(caseEnd > caseStart, "CASE ... END 를 찾지 못했다: $sql")
            assertFalse(
                "else" in sql.substring(caseStart, caseEnd),
                "else 절이 NULL 신호를 지운다: $sql",
            )
        }
    }

    /** 현재 기간과 이전 기간 **둘 다** 같은 계약이어야 증감이 의미를 갖는다. */
    @Test
    @DisplayName("현재와 이전 기간 쿼리가 같은 필터를 쓴다")
    fun bothPeriodsUseTheSameFilter() {
        val sqls = aggregateSql()

        assertTrue(sqls.size >= 2, "두 기간 쿼리가 모두 실행되지 않았다: ${sqls.size}")
        assertTrue(
            sqls.all { "case" in it && "youtube" in it && "video_uploads" in it },
            "한쪽 기간만 필터를 걸었다",
        )
    }
}
