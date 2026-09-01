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
 * 사용량 합계가 **어떤 테이블을 세는지** SQL 로 고정한다.
 *
 * 합계에서 한 테이블이 빠져도 코틀린은 컴파일되고 단위 테스트도 통과한다. 드러나는 것은
 * 요금제 한도를 훨씬 넘긴 스토리지 청구서가 도착했을 때뿐이고, 그때는 이미 쓴 돈이다.
 *
 * `content_images` 가 빠져 있었다. 이미지 게시는 `content/{videoId}/` 에 파일을 남기는데
 * 합계에 잡히지 않아 **무제한으로 올릴 수 있었다.**
 */
class StorageQuotaQueryContractTest {

    private fun capture(block: (StorageQuotaPortAdapter) -> Unit): List<String> {
        val executed = mutableListOf<String>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            executed += context.sql().lowercase()
            arrayOf(MockResult(0, empty))
        }
        runCatching { block(StorageQuotaPortAdapter(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))) }
        return executed
    }

    private fun usageQueries(): List<String> =
        capture { it.calculateUserStorageBytes(7L) }.filter { it.startsWith("select") }

    /* ── 합계 구성 ────────────────────────────────────────────────────── */

    @Test
    @DisplayName("사용량 합계는 videos 를 센다")
    fun countsVideos() {
        assertTrue(
            usageQueries().any { "videos" in it && "sum" in it },
            "영상 보관분이 합계에서 빠졌다",
        )
    }

    @Test
    @DisplayName("사용량 합계는 assets 를 센다")
    fun countsAssets() {
        assertTrue(
            usageQueries().any { "assets" in it && "sum" in it },
            "에셋이 합계에서 빠졌다",
        )
    }

    /**
     * **핵심 회귀.** 이 조건이 없으면 이미지 게시가 요금제 한도를 완전히 우회한다.
     */
    @Test
    @DisplayName("사용량 합계는 content_images 를 센다")
    fun countsContentImages() {
        val queries = usageQueries()
        assertTrue(
            queries.any { "content_images" in it && "sum" in it },
            "이미지가 합계에서 빠졌다 — 무제한 업로드가 열린다: $queries",
        )
    }

    /**
     * `content_images` 에는 `user_id` 가 없다. `videos` 로 이어 붙여 소유자를 찾아야 하고,
     * 그 조인이 빠지면 **모든 사용자의 이미지 합계**가 한 사람에게 청구된다.
     */
    @Test
    @DisplayName("이미지 합계는 videos 로 소유자를 찾아 건다")
    fun contentImageSumIsScopedToTheOwner() {
        val query = usageQueries().firstOrNull { "content_images" in it }
            ?: error("이미지 합계 질의가 없다")

        assertTrue("join" in query, "소유자 조인이 없다: $query")
        assertTrue("videos" in query, "videos 로 소유자를 찾지 않는다: $query")
        assertTrue("user_id" in query, "사용자 조건이 없다: $query")
    }

    /**
     * 두 테이블 모두 `id` · `file_size_bytes` · `created_at` 을 갖는다. 한정하지 않으면
     * 어느 쪽 컬럼인지 DB 가 정하거나 오류가 나고, 합계가 조용히 다른 값이 된다.
     */
    @Test
    @DisplayName("이미지 합계의 컬럼은 테이블명으로 한정한다")
    fun contentImageColumnsAreQualified() {
        val query = usageQueries().firstOrNull { "content_images" in it }
            ?: error("이미지 합계 질의가 없다")

        assertTrue(
            """"content_images"."file_size_bytes"""" in query,
            "합계 컬럼이 한정되지 않아 videos 의 크기를 셀 수 있다: $query",
        )
        assertTrue(
            """"videos"."user_id"""" in query,
            "소유자 컬럼이 한정되지 않았다: $query",
        )
    }

    /* ── 예약분 ──────────────────────────────────────────────────────── */

    /**
     * 진행 중인 업로드(예약)를 세지 않으면 같은 사용자가 동시에 여러 건을 시작해
     * 전부 한도를 통과한다. 이 조건은 이미 있었고 회귀만 막는다.
     */
    @Test
    @DisplayName("진행 중인 업로드 예약분도 센다")
    fun countsInFlightReservations() {
        val queries = capture { it.calculateUserStorageBytes(7L) }
        assertTrue(
            queries.any { "file_url" in it && "is null" in it },
            "예약분 조건이 사라졌다: $queries",
        )
    }
}
