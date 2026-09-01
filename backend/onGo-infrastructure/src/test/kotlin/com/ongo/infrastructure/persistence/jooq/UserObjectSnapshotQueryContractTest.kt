package com.ongo.infrastructure.persistence.jooq

import com.ongo.infrastructure.accountdeletion.UserObjectSnapshotAdapter
import com.ongo.infrastructure.persistence.jooq.Fields.STORAGE_OBJECT_KEY
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 탈퇴 정리가 **어느 테이블의 객체까지 수거하는지** SQL 로 고정한다.
 *
 * 한 테이블이 빠지면 그 파일은 탈퇴 후에도 버킷에 남는다. 사용자는 지웠다고 들었는데
 * 실제로는 남아 있고, 우리는 그 용량을 계속 낸다. 코드만 봐서는 드러나지 않고 버킷을
 * 뒤져야 알 수 있어 오래 방치된다.
 *
 * `content_images` 가 빠져 있었다.
 */
class UserObjectSnapshotQueryContractTest {

    /** 키 조회에는 값을 하나 돌려줘야 뒤따르는 공유 검사 질의까지 실행된다. */
    private fun capture(): List<String> {
        val executed = mutableListOf<String>()
        val dsl = DSL.using(SQLDialect.POSTGRES)
        val withKey = dsl.newResult(STORAGE_OBJECT_KEY).also { result ->
            result.add(dsl.newRecord(STORAGE_OBJECT_KEY).also { it.setValue(STORAGE_OBJECT_KEY, "videos/1/a.mp4") })
        }
        val provider = MockDataProvider { context ->
            val sql = context.sql().lowercase()
            executed += sql
            val body = if ("storage_object_key" in sql && "count" !in sql) withKey else dsl.newResult()
            arrayOf(MockResult(0, body))
        }
        runCatching { UserObjectSnapshotAdapter(DSL.using(MockConnection(provider), SQLDialect.POSTGRES)).snapshot(7L) }
        return executed
    }

    /**
     * **수거 질의만** 고른다.
     *
     * 공유 검사도 같은 테이블·같은 컬럼을 읽으므로 테이블명만 보면 둘을 구분하지 못한다.
     * 실제로 그 때문에 "이미지 키를 수거한다" 단정이 수거 코드를 통째로 지워도 통과했다 —
     * 공유 검사 질의가 대신 걸렸다. 소유자 비교 방향(`=` 대 `<>`)으로 갈라야 한다.
     */
    private fun collectionQueries(): List<String> =
        capture().filter { "storage_object_key" in it && "count" !in it && "<>" !in it }

    /* ── 수거 대상 ────────────────────────────────────────────────────── */

    @Test
    @DisplayName("탈퇴 스냅샷은 videos 의 객체 키를 수거한다")
    fun collectsVideoKeys() {
        assertTrue(
            collectionQueries().any { "videos" in it && "content_images" !in it },
            "영상 객체가 수거 대상에서 빠졌다",
        )
    }

    @Test
    @DisplayName("탈퇴 스냅샷은 assets 의 객체 키를 수거한다")
    fun collectsAssetKeys() {
        assertTrue(
            collectionQueries().any { "assets" in it },
            "에셋 객체가 수거 대상에서 빠졌다",
        )
    }

    /**
     * **핵심 회귀.** 이 질의가 없으면 탈퇴자의 게시 이미지가 버킷에 영구히 남는다.
     */
    @Test
    @DisplayName("탈퇴 스냅샷은 content_images 의 객체 키를 수거한다")
    fun collectsContentImageKeys() {
        val queries = collectionQueries()
        assertTrue(
            queries.any { "content_images" in it },
            "이미지 객체가 수거 대상에서 빠졌다 — 탈퇴 후에도 남는다: $queries",
        )
    }

    /** `content_images` 에는 소유자가 없다. `videos` 로 이어 붙이지 않으면 남의 파일을 지운다. */
    @Test
    @DisplayName("이미지 키 수거는 videos 로 소유자를 확인한다")
    fun contentImageKeysAreScopedToTheOwner() {
        val query = collectionQueries().firstOrNull { "content_images" in it }
            ?: error("이미지 키 수거 질의가 없다")

        assertTrue("join" in query, "소유자 조인이 없다: $query")
        assertTrue(
            """"videos"."user_id" = """ in query,
            "소유자를 한정해 고르지 않는다 — 다른 사용자 이미지가 삭제 목록에 든다: $query",
        )
    }

    /* ── 수기 확인 대상 ───────────────────────────────────────────────── */

    /**
     * 키가 없는 과거 행은 **자동으로 지우지 않고 센다.** URL 로 키를 추측해 지우면
     * 빗나갔을 때 남의 파일이 사라지고, 되돌릴 수 없다.
     */
    @Test
    @DisplayName("키가 없는 이미지 행은 미해결로 센다")
    fun countsLegacyContentImageRows() {
        val queries = capture()
        assertTrue(
            queries.any { "content_images" in it && "count" in it },
            "키 없는 이미지 행을 세지 않아 job 이 그냥 완료된다: $queries",
        )
    }

    /**
     * 반복 예약은 객체를 복제해 여러 행이 같은 키를 가리킬 수 있다. 탈퇴자 쪽만 보고
     * 지우면 남아 있는 사용자의 파일이 사라진다.
     */
    @Test
    @DisplayName("다른 사용자가 같은 키를 가리키는지 확인한다")
    fun checksKeysSharedWithOtherUsers() {
        val queries = capture().filter { "<>" in it || "!=" in it }
        assertTrue(queries.any { "videos" in it }, "영상 공유 검사가 없다: $queries")
        assertTrue(queries.any { "assets" in it }, "에셋 공유 검사가 없다: $queries")
        assertTrue(
            queries.any { "content_images" in it },
            "이미지 공유 검사가 없다 — 복제된 객체를 지우면 남은 사용자가 파일을 잃는다: $queries",
        )
    }
}
