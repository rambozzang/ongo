package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.analytics.RevenueStatus
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.conf.StatementType
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * CPM/RPM 집계 SQL 의 계약.
 *
 * 단가의 분자(수익)와 분모(노출·조회)는 **같은 행에서** 나와야 한다. 측정되지 않은
 * 날의 노출을 분모에 넣으면 CPM 이 실제보다 낮게 나오고, 그 숫자는 화면에서
 * "이 플랫폼은 단가가 낮다" 로 읽힌다.
 *
 * `revenue_micro` 는 `NOT NULL DEFAULT 0` 이라 아직 확정되지 않은 날짜도 0 을 들고
 * 있다. 상태 조건이 빠지면 그 0 이 분자에 섞인다.
 */
class CpmRpmQueryContractTest {

    /**
     * `getCpmRpmByPlatform` 은 업로드 ID 조회를 먼저 하고 **비어 있으면 즉시 반환한다.**
     * 그래서 첫 조회에 행을 하나 돌려주지 않으면 집계 SQL 자체가 실행되지 않는다.
     */
    private fun captureAggregateSql(): String {
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

        // 값을 인라인으로 렌더링한다. 기본값(바인드 파라미터)이면 상태·플랫폼 조건이
        // 전부 `?` 로 나와, 조건이 통째로 사라져도 이 검사가 통과해 버린다.
        val settings = Settings().withStatementType(StatementType.STATIC_STATEMENT)
        val repository = RevenueJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES, settings))
        runCatching {
            repository.getCpmRpmByPlatform(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
        }

        assertTrue(executed.size >= 2, "집계 SQL 이 실행되지 않았다: $executed")
        return executed.last().lowercase()
    }

    @Test
    @DisplayName("CPM/RPM 집계는 측정된 수익 행만 더한다")
    fun aggregateCountsOnlyMeasuredRevenueRows() {
        val sql = captureAggregateSql()

        assertTrue("revenue_status" in sql, "측정 상태 조건이 사라졌다: $sql")
        assertTrue(RevenueStatus.MEASURED.name.lowercase() in sql, "MEASURED 조건이 사라졌다: $sql")
    }

    /**
     * 상태 조건과 플랫폼 조건은 짝이다. 한쪽만 남으면 금액은 더해지는데 가용성 판정은
     * "수집하지 않습니다" 라고 말하는 모순이 난다.
     */
    @Test
    @DisplayName("CPM/RPM 집계는 수익 수집 플랫폼으로도 좁힌다")
    fun aggregateAlsoNarrowsToRevenueReportingPlatforms() {
        val sql = captureAggregateSql()

        assertTrue("vu.platform" in sql, "플랫폼 조건이 사라졌다: $sql")
        assertTrue("youtube" in sql, "수익 수집 플랫폼 목록이 빠졌다: $sql")
    }

    /** 분자와 분모가 같은 행에서 나와야 단가가 성립한다. */
    @Test
    @DisplayName("수익과 노출·조회를 같은 집계에서 함께 읽는다")
    fun numeratorAndDenominatorComeFromTheSameRows() {
        val sql = captureAggregateSql()

        assertTrue("revenue_micro" in sql, sql)
        assertTrue("impressions" in sql, sql)
        assertTrue("ad.views" in sql, sql)
    }
}
