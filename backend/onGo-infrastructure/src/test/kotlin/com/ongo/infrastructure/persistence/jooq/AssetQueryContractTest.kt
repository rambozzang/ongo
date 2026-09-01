package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.asset.AssetQuery
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * 목록과 총계가 **같은 조건**으로 나가는지 SQL 로 고정한다.
 *
 * 조건을 두 질의에 따로 적으면 하나만 고쳤을 때 총계가 조용히 어긋난다. 화면은 3 건을
 * 보여 주면서 "총 240 건"이라 말하고, 페이지네이션은 존재하지 않는 페이지를 가리킨다.
 * 눈으로 보고 알기 어려운 종류의 어긋남이라 여기서 못 박는다.
 */
class AssetQueryContractTest {

    private class Captured(val sql: String, val bindings: List<Any?>)

    private fun capture(block: (AssetJooqRepository) -> Unit): Captured {
        val executed = mutableListOf<Captured>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            executed += Captured(context.sql().lowercase(), context.bindings().toList())
            arrayOf(MockResult(0, empty))
        }
        runCatching { block(AssetJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))) }
        return executed.first()
    }

    /** `where` 절만 떼어 낸다 — 목록에는 정렬·페이징이 뒤따르므로 그 앞까지만 본다. */
    private fun predicate(sql: String): String =
        sql.substringAfter(" where ").substringBefore(" order by").trim()

    private fun listWhere(query: AssetQuery): String =
        predicate(capture { it.findByUserId(7L, query, 0, 20) }.sql)

    private fun countWhere(query: AssetQuery): String =
        predicate(capture { it.count(7L, query) }.sql)

    /* ── 목록과 총계의 조건 일치 ──────────────────────────────────────── */

    /** **핵심.** 두 질의의 `where` 가 다르면 총계는 아무것도 세지 않은 숫자가 된다. */
    @Test
    @DisplayName("목록과 총계는 완전히 같은 조건을 쓴다")
    fun listAndCountShareTheExactSameCondition() {
        val query = AssetQuery(fileType = "VIDEO", folder = "brand-kit", search = "여름", tag = "logo")

        assertEquals(listWhere(query), countWhere(query), "총계가 목록과 다른 조건으로 셌다")
    }

    @Test
    @DisplayName("조건이 없을 때도 같은 조건을 쓴다")
    fun emptyQueryAlsoMatches() {
        assertEquals(listWhere(AssetQuery()), countWhere(AssetQuery()))
    }

    /* ── 각 조건이 실제로 SQL 에 들어가는지 ───────────────────────────── */

    @Test
    @DisplayName("소유자 조건은 항상 걸린다")
    fun ownerIsAlwaysScoped() {
        assertTrue("user_id" in listWhere(AssetQuery()), "소유자 조건이 없다 — 남의 에셋이 섞인다")
        assertTrue("user_id" in countWhere(AssetQuery()), "총계에 소유자 조건이 없다")
    }

    @Test
    @DisplayName("타입·폴더 조건이 SQL 에 들어간다")
    fun typeAndFolderReachTheQuery() {
        val where = listWhere(AssetQuery(fileType = "VIDEO", folder = "brand-kit"))

        assertTrue("file_type" in where, "타입 조건이 없다: $where")
        assertTrue("folder" in where, "폴더 조건이 없다: $where")
    }

    /**
     * 검색은 **전체 라이브러리**를 대상으로 해야 한다. 한 페이지만 뒤지는 검색은
     * "없다"와 "이 페이지에 없다"를 구분하지 못한다.
     */
    @Test
    @DisplayName("검색은 파일명과 태그를 SQL 에서 본다")
    fun searchRunsInTheDatabase() {
        val where = listWhere(AssetQuery(search = "여름"))

        assertTrue("filename" in where, "저장 파일명을 보지 않는다: $where")
        assertTrue("original_filename" in where, "원본 파일명을 보지 않는다: $where")
        assertTrue("unnest" in where || "tags" in where, "태그를 보지 않는다: $where")
    }

    /** 태그 조건은 배열 포함 여부라 SQL 에서 봐야 다른 페이지의 태그도 걸린다. */
    @Test
    @DisplayName("태그 조건은 배열 포함으로 건다")
    fun tagUsesArrayContainment() {
        val where = listWhere(AssetQuery(tag = "logo"))

        assertTrue("any(" in where || "tags" in where, "태그 조건이 없다: $where")
    }

    /**
     * **`%`·`_` 를 이스케이프하지 않으면** 사용자가 친 `%` 하나가 전체 조회가 된다.
     * 총계는 전체 개수가 되고, 검색은 아무것도 거르지 않는다.
     */
    @Test
    @DisplayName("검색어의 와일드카드를 이스케이프한다")
    fun searchEscapesWildcards() {
        val captured = capture { it.findByUserId(7L, AssetQuery(search = "100%_a"), 0, 20) }

        assertTrue(
            captured.bindings.any { it.toString().contains("!%") && it.toString().contains("!_") },
            "와일드카드가 그대로 나갔다 — 검색이 전체 조회가 된다: ${captured.bindings}",
        )
        assertTrue("escape" in captured.sql, "ESCAPE 절이 없다: ${captured.sql}")
    }

    /* ── 페이징 ───────────────────────────────────────────────────────── */

    /**
     * 총계 질의에는 `limit`/`offset` 이 없어야 한다. 붙으면 총계가 페이지 크기를 넘지 못해
     * "다음 페이지"가 영원히 비활성이 된다.
     */
    @Test
    @DisplayName("총계 질의에는 페이징이 붙지 않는다")
    fun countHasNoPaging() {
        val sql = capture { it.count(7L, AssetQuery()) }.sql

        assertFalse("limit" in sql, "총계에 limit 이 붙었다: $sql")
        assertFalse("offset" in sql, "총계에 offset 이 붙었다: $sql")
        assertTrue("count" in sql, "세지 않는다: $sql")
    }

    /** offset 은 `page * size` 다. 곱셈이 빠지면 2 페이지가 1 페이지와 같아진다. */
    @Test
    @DisplayName("offset 은 page × size 로 계산한다")
    fun offsetIsPageTimesSize() {
        val bindings = capture { it.findByUserId(7L, AssetQuery(), 4, 24) }.bindings

        assertTrue(96L in bindings || 96 in bindings, "offset 이 4×24 가 아니다: $bindings")
        assertTrue(24L in bindings || 24 in bindings, "limit 이 24 가 아니다: $bindings")
    }

    /**
     * 정렬이 흔들리면 같은 행이 두 페이지에 나오거나 아예 빠진다. `created_at` 만으로는
     * 같은 초에 올린 행들의 순서가 정해지지 않으므로 id 로 마무리한다.
     */
    @Test
    @DisplayName("정렬은 최신순이고 동점은 id 로 끊는다")
    fun orderingIsStable() {
        val sql = capture { it.findByUserId(7L, AssetQuery(), 0, 20) }.sql

        assertTrue("order by" in sql, "정렬이 없다: $sql")
        assertTrue("created_at" in sql, "최신순이 아니다: $sql")
        assertTrue(sql.substringAfter("order by").contains("id"), "동점 처리가 없다: $sql")
    }
}
