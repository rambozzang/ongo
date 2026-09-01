package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.video.ContentImage
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 게시 이미지 행이 **실제 저장 키를 적어 두는지** SQL 로 고정한다.
 *
 * 이 컬럼을 쓰지 않아도 업로드는 성공하고 화면도 정상이다. 드러나는 것은 그 사용자가
 * 탈퇴한 뒤 — 키가 없어 자동 정리 대상에서 빠지고, 파일은 버킷에 남고, 우리는 계속
 * 그 용량을 낸다. 되짚어 지우는 것은 허용하지 않으므로 여기서 놓치면 수기 작업이 된다.
 */
class ContentImageStorageKeyContractTest {

    private fun capture(block: (ContentImageJooqRepository) -> Unit): Pair<String, List<Any?>> {
        val executed = mutableListOf<Pair<String, List<Any?>>>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            executed += context.sql().lowercase() to context.bindings().toList()
            arrayOf(MockResult(0, empty))
        }
        /*
         * `save`/`saveAll` 은 쓴 뒤 방금 만든 행을 다시 읽는다. 목 커넥션은 빈 결과를 주므로
         * 그 조회가 실패하지만, 검증 대상인 INSERT 는 그 앞에서 이미 포착된다.
         */
        runCatching { block(ContentImageJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))) }
        return executed.first()
    }

    private fun image(key: String? = "content/7/images/1700000000_a.png") = ContentImage(
        videoId = 7L,
        imageUrl = "https://storage.test/a.png",
        fileSizeBytes = 1_234L,
        originalFilename = "a.png",
        contentType = "image/png",
        storageObjectKey = key,
    )

    @Test
    @DisplayName("단건 저장은 저장 키를 함께 기록한다")
    fun saveWritesTheStorageKey() {
        val (sql, bindings) = capture { it.save(image()) }

        assertTrue(sql.startsWith("insert"), "INSERT 가 아니다: $sql")
        assertTrue("storage_object_key" in sql, "저장 키 컬럼이 없다 — 탈퇴 정리가 이 행을 놓친다: $sql")
        assertTrue(
            "content/7/images/1700000000_a.png" in bindings,
            "저장 키 값이 바인딩되지 않았다: $bindings",
        )
    }

    @Test
    @DisplayName("다건 저장도 저장 키를 함께 기록한다")
    fun saveAllWritesTheStorageKey() {
        val (sql, bindings) = capture { it.saveAll(listOf(image())) }

        assertTrue("storage_object_key" in sql, "다건 경로에서 저장 키가 빠졌다: $sql")
        assertTrue("content/7/images/1700000000_a.png" in bindings, "저장 키 값이 없다: $bindings")
    }

    /**
     * 사용량 합계가 이미지를 세려면 크기가 반드시 있어야 한다. 이 컬럼이 비면 합계가 0 으로
     * 잡혀 요금제 한도를 그대로 우회한다.
     */
    @Test
    @DisplayName("저장은 파일 크기를 함께 기록한다")
    fun saveWritesTheFileSize() {
        val (sql, bindings) = capture { it.save(image()) }

        assertTrue("file_size_bytes" in sql, "크기 컬럼이 없다 — 쿼터가 0 으로 잡힌다: $sql")
        assertTrue(1_234L in bindings, "크기 값이 바인딩되지 않았다: $bindings")
    }
}
