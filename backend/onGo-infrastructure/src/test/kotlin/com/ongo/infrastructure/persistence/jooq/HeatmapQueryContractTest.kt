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
 * 게시 시간 히트맵의 **SQL 계약**.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * EXTRACT(DOW  FROM analytics_daily.date)        // 지표가 집계된 날
 * EXTRACT(HOUR FROM analytics_daily.created_at)  // 그 행을 저장한 시각
 * ```
 *
 * 둘 다 **게시 시각이 아니다.** `created_at` 은 `AnalyticsSyncScheduler` 가 행을 쓴
 * 시각이라, "언제 올려야 잘 되나" 를 그린다는 히트맵이 실제로는 **동기화 배치가 돈
 * 시간대**를 그리고 있었다. 스케줄러가 매일 같은 시각에 돌면 전체 조회수가 그 한 칸에
 * 쌓여, 그 시간이 항상 최적 업로드 시간으로 뽑힌다.
 *
 * 화면만의 문제가 아니다. `SuggestScheduleUseCase` 가 이 값을 유료 AI 프롬프트에
 * `"{hour}시={views}조회"` 로 넣어, 모델이 그 축을 관측으로 읽고 추천을 만들었다.
 *
 * 게다가 `video_uploads` 조인이 없어 `TumblrClient.kt:141` 의 `total_notes`
 * (좋아요+리블로그+답글 총합)가 조회수로 섞였다.
 *
 * ## 지금의 계약
 *
 * 축은 `video_uploads.published_at` 이고, `published_at IS NULL`(예약·초안)과 조회수를
 * 보고하지 않는 플랫폼은 제외한다.
 */
class HeatmapQueryContractTest {

    /**
     * `getHeatmapData` 는 업로드 ID 조회를 먼저 하고 **비어 있으면 즉시 반환한다.**
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
        runCatching { repository.getHeatmapData(1L) }

        return executed
    }

    private fun heatmapSql(): String {
        val sqls = captureSql().map { it.lowercase() }.filter { "extract" in it }
        assertTrue(sqls.isNotEmpty(), "히트맵 집계 SQL 이 실행되지 않았다:\n${captureSql()}")
        return sqls.single()
    }

    // ── 축이 게시 시각인가 ──────────────────────────────────────────────────

    /** **이 케이스가 동기화 배치 시각을 최적 업로드 시간으로 그리던 자리다.** */
    @Test
    @DisplayName("요일과 시각을 published_at 에서 뽑는다")
    fun axesComeFromPublishedAt() {
        val sql = heatmapSql()

        assertTrue("extract(dow from" in sql, "요일 추출이 없다: $sql")
        assertTrue("extract(hour from" in sql, "시각 추출이 없다: $sql")
        // 두 추출 모두 published_at 이어야 한다.
        Regex("""extract\((?:dow|hour) from ([^)]+)\)""").findAll(sql).forEach { match ->
            assertTrue(
                "published_at" in match.groupValues[1],
                "게시 시각이 아닌 컬럼을 축으로 썼다: ${match.value}",
            )
        }
    }

    /** 행을 저장한 시각은 게시 시각이 아니다 — 축에서 완전히 빠져야 한다. */
    @Test
    @DisplayName("created_at 을 시간축으로 쓰지 않는다")
    fun createdAtIsNotAnAxis() {
        val sql = heatmapSql()

        assertFalse(
            "extract(hour from" in sql && "created_at" in sql.substringAfter("extract(hour from").substringBefore(")"),
            "동기화 저장 시각을 게시 시각으로 썼다: $sql",
        )
        assertFalse("created_at" in sql, "created_at 이 아직 쿼리에 남아 있다: $sql")
    }

    /** 집계일(`analytics_daily.date`)도 게시 요일이 아니다. */
    @Test
    @DisplayName("집계일을 요일축으로 쓰지 않는다")
    fun aggregateDateIsNotTheDayAxis() {
        val sql = heatmapSql()
        val dowArg = Regex("""extract\(dow from ([^)]+)\)""").find(sql)?.groupValues?.get(1)

        assertTrue(dowArg != null, "요일 추출을 찾지 못했다: $sql")
        assertFalse(
            dowArg!!.trim().endsWith(".date") || dowArg.trim() == "date",
            "집계일을 게시 요일로 썼다: $dowArg",
        )
    }

    // ── 게시되지 않은 업로드 ────────────────────────────────────────────────

    /**
     * 예약·초안 업로드는 `published_at IS NULL` 이라 축이 없다. Postgres 의
     * `EXTRACT(... FROM NULL)` 은 NULL 을 내고, 매핑에서 `?: 0` 이 **일요일 0 시** 칸에
     * 몰아넣는다. 실제로 그 시간에 올린 적이 없는데 히트맵이 그 칸을 채운다.
     */
    @Test
    @DisplayName("게시되지 않은 업로드를 제외한다")
    fun unpublishedUploadsAreExcluded() {
        val sql = heatmapSql()

        assertTrue(
            "published_at is not null" in sql,
            "예약·초안 업로드가 일요일 0시 칸으로 접힌다: $sql",
        )
    }

    // ── 플랫폼 오염 ─────────────────────────────────────────────────────────

    /** 플랫폼을 알려면 `video_uploads` 와 조인해야 한다. */
    @Test
    @DisplayName("video_uploads 와 조인한다")
    fun joinsVideoUploads() {
        assertTrue("video_uploads" in heatmapSql(), "조인이 없어 플랫폼을 알 수 없다")
    }

    /** **Tumblr 의 노트 총합이 조회수로 섞이면 그 시간대가 통째로 최적 시간이 된다.** */
    @Test
    @DisplayName("조회수를 보고하는 플랫폼만 합산한다")
    fun onlyViewReportingPlatformsAreSummed() {
        val sql = heatmapSql()

        assertTrue("platform" in sql, "플랫폼 조건이 없다: $sql")
        assertTrue("youtube" in sql, "수집 플랫폼 목록이 빠졌다: $sql")
        assertFalse("tumblr" in sql, "조회수를 주지 않는 플랫폼이 목록에 들어갔다: $sql")
    }

    /** 합산 대상은 조회수다 — 계약이 다른 컬럼으로 조용히 바뀌지 않도록 고정한다. */
    @Test
    @DisplayName("조회수를 합산한다")
    fun sumsViews() {
        assertTrue(Regex("""sum\([^)]*views[^)]*\)""").containsMatchIn(heatmapSql()), "조회수 합계가 아니다")
    }
}
