package com.ongo.infrastructure.persistence.jooq

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.conf.StatementType
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 첫 화면 KPI 의 **조회수·좋아요·댓글 합계 SQL 계약**.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * DSL.sum(VIEWS)           // 전 플랫폼
 * DSL.sum(LIKES)           // 전 플랫폼
 * DSL.sum(COMMENTS_COUNT)  // 전 플랫폼
 * ```
 *
 * 구독 증가만 플랫폼 필터를 달았고 나머지 셋은 그대로였다. 하드코딩 0 만 섞이는
 * 문제였다면 합계는 그대로였겠지만, 어댑터 중에는 **다른 뜻의 큰 숫자**를 같은 컬럼에
 * 넣는 것들이 있다.
 *
 * - `TumblrClient.kt:141` `views = total_notes` — 좋아요+리블로그+답글 총합
 * - `PinterestClient.kt:158` `likes = SAVE` — 저장 수
 * - `PinterestClient.kt:159` `comments = 0` — 조회하지도 않는 자리채움
 *
 * 첫 화면이라 이 오염이 **사용자가 처음 보는 숫자**를 바꾼다.
 *
 * ## 지금의 계약
 *
 * 구독 증가와 같은 `CASE WHEN vu.platform IN (수집 플랫폼) THEN ... END` 를 쓴다.
 * 일치하는 행이 없으면 `SUM` 이 NULL 이 되어 "측정된 0" 과 "물어볼 곳 없음" 이 갈린다.
 * 구독 증가 계약은 [DashboardKpiSubscriberQueryContractTest] 가 계속 지킨다.
 */
class DashboardKpiMetricQueryContractTest {

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

    /** 현재·이전 두 기간의 집계 SQL. */
    private fun aggregateSql(): List<String> =
        captureSql().map { it.lowercase() }.filter { "total_views" in it }

    /** `alias` 로 별칭된 `SUM(...)` 의 본문. */
    private fun sumBody(sql: String, alias: String): String {
        val end = sql.indexOf("""as "$alias"""")
        assertTrue(end > 0, "$alias 별칭을 찾지 못했다: $sql")
        val start = sql.lastIndexOf("sum(", end)
        assertTrue(start >= 0, "$alias 이 SUM 이 아니다: $sql")
        return sql.substring(start, end)
    }

    @Test
    @DisplayName("두 기간 집계 쿼리가 모두 실행된다")
    fun bothPeriodsAreQueried() {
        assertEquals(2, aggregateSql().size, "현재·이전 두 기간 쿼리가 모두 실행되지 않았다")
    }

    // ── 지표별 플랫폼 필터 ──────────────────────────────────────────────────

    /** **이 케이스가 Tumblr 노트 총합을 첫 화면 조회수로 올리던 자리다.** */
    @Test
    @DisplayName("조회수 합계는 수집 플랫폼 조건을 건 CASE 로 감싼다")
    fun viewsSumIsGuardedByAPlatformCase() {
        aggregateSql().forEach { sql ->
            val body = sumBody(sql, "total_views")
            assertTrue("case" in body, "CASE 없이 전 플랫폼을 더한다: $body")
            assertTrue("platform" in body, "플랫폼 조건이 없다: $body")
            assertTrue("youtube" in body, "수집 플랫폼 목록이 빠졌다: $body")
            assertFalse("tumblr" in body, "노트 총합을 주는 플랫폼이 목록에 들어갔다: $body")
        }
    }

    /** **이 케이스가 Pinterest 저장 수를 좋아요로 올리던 자리다.** */
    @Test
    @DisplayName("좋아요 합계는 수집 플랫폼 조건을 건 CASE 로 감싼다")
    fun likesSumIsGuardedByAPlatformCase() {
        aggregateSql().forEach { sql ->
            val body = sumBody(sql, "total_likes")
            assertTrue("case" in body, "CASE 없이 전 플랫폼을 더한다: $body")
            assertTrue("youtube" in body, "수집 플랫폼 목록이 빠졌다: $body")
            assertFalse("pinterest" in body, "저장 수를 주는 플랫폼이 좋아요 목록에 들어갔다: $body")
        }
    }

    @Test
    @DisplayName("댓글 합계는 수집 플랫폼 조건을 건 CASE 로 감싼다")
    fun commentsSumIsGuardedByAPlatformCase() {
        aggregateSql().forEach { sql ->
            val body = sumBody(sql, "total_comments")
            assertTrue("case" in body, "CASE 없이 전 플랫폼을 더한다: $body")
            assertTrue("youtube" in body, "수집 플랫폼 목록이 빠졌다: $body")
            assertFalse("pinterest" in body, "댓글을 조회하지 않는 플랫폼이 목록에 들어갔다: $body")
        }
    }

    /** 선행 작업으로 이미 걸린 구독 증가 필터를 되돌리지 않았는지 함께 본다. */
    @Test
    @DisplayName("구독 증가 필터를 회귀시키지 않는다")
    fun subscriberFilterIsNotRegressed() {
        aggregateSql().forEach { sql ->
            val body = sumBody(sql, "total_subs")
            assertTrue("case" in body && "youtube" in body, "구독 증가 필터가 사라졌다: $body")
        }
    }

    // ── NULL 신호를 지우지 않는가 ───────────────────────────────────────────

    /**
     * `ELSE 0` 을 붙이면 조건에 맞지 않는 행이 0 이 되어 **합계가 NULL 이 되지 않는다.**
     * 그러면 "측정된 0" 과 "물어볼 곳 없음" 을 다시 구분할 수 없다.
     */
    @Test
    @DisplayName("네 지표 모두 CASE 에 else 0 을 붙이지 않는다")
    fun noCaseHasElseZero() {
        aggregateSql().forEach { sql ->
            listOf("total_views", "total_likes", "total_comments", "total_subs").forEach { alias ->
                assertFalse("else" in sumBody(sql, alias), "$alias 의 else 절이 NULL 신호를 지운다")
            }
        }
    }

    /** 플랫폼을 알려면 `video_uploads` 와 조인해야 한다. */
    @Test
    @DisplayName("두 기간 쿼리 모두 video_uploads 와 조인한다")
    fun bothPeriodsJoinVideoUploads() {
        aggregateSql().forEach { sql ->
            assertTrue("video_uploads" in sql, "조인이 없어 플랫폼을 알 수 없다: $sql")
        }
    }

    @Test
    @DisplayName("사용자 업로드 ID 조회는 게시 확정된 업로드만 선택한다")
    fun uploadIdQueryOnlyIncludesPublishedUploads() {
        val sql = captureSql().firstOrNull()?.lowercase()
            ?: error("사용자 업로드 ID 조회 SQL 이 실행되지 않았다")

        assertTrue("video_uploads" in sql, "업로드 ID 조회가 video_uploads 를 읽지 않는다: $sql")
        assertTrue("status" in sql && "published" in sql, "게시 확정 상태 조건이 없다: $sql")
    }

    /** 현재와 이전이 **같은 필터**여야 증감이 의미를 갖는다. */
    @Test
    @DisplayName("현재와 이전 기간이 같은 필터를 쓴다")
    fun bothPeriodsUseTheSameFilter() {
        val bodies = aggregateSql().map { sql ->
            listOf("total_views", "total_likes", "total_comments", "total_subs").map { sumBody(sql, it) }
        }

        assertEquals(bodies[0], bodies[1], "한쪽 기간만 필터를 걸었다")
    }
}
