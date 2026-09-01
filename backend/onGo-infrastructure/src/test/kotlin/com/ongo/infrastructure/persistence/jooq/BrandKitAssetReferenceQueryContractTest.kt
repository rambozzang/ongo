package com.ongo.infrastructure.persistence.jooq

import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 에셋 참조 조회가 **어떤 SQL 로 나가는지** 고정한다.
 *
 * 이 조회는 삭제를 막는 근거다. 조건 하나가 어긋나면 두 방향으로 틀린다.
 *
 *  - 너무 넓으면 남의 브랜드킷 때문에 내 에셋을 못 지우고, 그 사실 자체가 남의 데이터를
 *    알려 주는 통로가 된다.
 *  - 너무 좁으면 쓰고 있는 파일을 지워 로고가 조용히 깨진다.
 *
 * 어느 쪽도 목 없이 단위 테스트로는 드러나지 않아 SQL 을 직접 본다.
 */
class BrandKitAssetReferenceQueryContractTest {

    private class Captured(val sql: String, val bindings: List<Any?>)

    private fun capture(block: (BrandKitJooqRepository) -> Unit): Captured {
        val executed = mutableListOf<Captured>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            executed += Captured(context.sql().lowercase(), context.bindings().toList())
            arrayOf(MockResult(0, empty))
        }
        runCatching { block(BrandKitJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))) }
        return executed.first()
    }

    private fun referenceQuery() = capture { it.findNamesReferencingAsset(7L, 42L) }

    /** 이름을 돌려줘야 사용자가 어느 브랜드킷을 고칠지 안다. 건수만으로는 찾아갈 수 없다. */
    @Test
    @DisplayName("브랜드킷 이름을 조회한다")
    fun selectsTheBrandKitName() {
        val sql = referenceQuery().sql

        assertTrue(sql.startsWith("select"), "조회가 아니다: $sql")
        assertTrue("name" in sql, "이름을 가져오지 않는다: $sql")
        assertTrue("brand_kits" in sql, "브랜드킷을 보지 않는다: $sql")
    }

    /** **소유자 범위가 없으면 남의 브랜드킷이 내 삭제를 막는다.** */
    @Test
    @DisplayName("요청한 사용자 범위로만 찾는다")
    fun scopedToTheRequestingUser() {
        val captured = referenceQuery()

        assertTrue("user_id" in captured.sql, "소유자 조건이 없다: ${captured.sql}")
        assertTrue(7L in captured.bindings, "요청자 id 를 걸지 않는다: ${captured.bindings}")
    }

    /**
     * **JSONB 포함 연산으로 원소를 통째로 맞춘다.**
     *
     * 문자열 `LIKE` 로 찾으면 `assetId: 4` 가 `assetId: 42` 에도 걸려, 쓰지도 않는 에셋의
     * 삭제가 막힌다.
     */
    @Test
    @DisplayName("JSONB 포함 연산으로 assetId 를 맞춘다")
    fun matchesByJsonbContainment() {
        val captured = referenceQuery()

        assertTrue("@>" in captured.sql, "포함 연산을 쓰지 않는다: ${captured.sql}")
        assertTrue("assets_json" in captured.sql, "에셋 목록을 보지 않는다: ${captured.sql}")
        assertTrue(
            captured.bindings.any { it.toString().contains("\"assetId\": 42") },
            "assetId 를 원소로 맞추지 않는다: ${captured.bindings}",
        )
        assertTrue("jsonb" in captured.sql, "jsonb 로 캐스트하지 않아 타입이 어긋난다: ${captured.sql}")
    }

    /**
     * `LIKE` 로 새는 것을 막는다. 부분 문자열 검색이 섞이면 위 포함 연산이 있어도
     * 조건이 넓어진다.
     */
    @Test
    @DisplayName("문자열 부분 일치로 찾지 않는다")
    fun neverFallsBackToStringMatching() {
        val sql = referenceQuery().sql

        assertTrue("like" !in sql, "부분 문자열 검색이 섞였다 — assetId 4 가 42 에 걸린다: $sql")
    }
}
