package com.ongo.infrastructure.persistence.jooq

import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 사용자 조회가 **어떤 SQL 로 나가는지** 고정한다.
 *
 * ## 왜 SQL 을 직접 보는가
 *
 * `findByIdForUpdate` 의 존재 이유는 행 잠금 하나뿐이다. `.forUpdate()` 가 빠져도 코틀린
 * 코드는 그대로 컴파일되고 단위 테스트도 전부 통과한다 — 잠금이 사라졌다는 사실은 **실제
 * 동시 요청이 들어왔을 때만** 드러나고, 그때는 이미 한도를 넘긴 행이 만들어진 뒤다.
 *
 * 진짜 경합 검증은 동시 트랜잭션이 필요해 Testcontainers 통합테스트여야 하지만, 렌더링된
 * SQL 은 DB 없이도 확인할 수 있다. 여기서는 **잠금이 문장에 실제로 실려 나가는지**까지를
 * 책임진다.
 */
class UserJooqRepositoryQueryContractTest {

    private fun captureSql(block: (UserJooqRepository) -> Unit): String {
        val executed = mutableListOf<String>()
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { context ->
            executed += context.sql()
            arrayOf(MockResult(0, empty))
        }
        block(UserJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES)))
        return executed.single().lowercase()
    }

    @Test
    @DisplayName("잠금 조회는 FOR UPDATE 를 붙여 행을 잠근다")
    fun lockingReadEmitsForUpdate() {
        val sql = captureSql { it.findByIdForUpdate(1L) }

        assertTrue("for update" in sql, "행 잠금 없이 조회하면 동시 요청이 직렬화되지 않습니다: $sql")
        assertTrue("from users" in sql, sql)
    }

    /**
     * **일반 조회는 잠그지 않는다.**
     *
     * 읽기만 하는 경로까지 잠그면 서로 무관한 요청이 줄을 서고, 트랜잭션이 긴 곳에서는
     * 사용자 한 명의 작업이 그 사람의 다른 요청을 모두 막는다.
     */
    @Test
    @DisplayName("일반 조회는 잠그지 않는다")
    fun plainReadDoesNotLock() {
        val sql = captureSql { it.findById(1L) }

        assertFalse("for update" in sql, "읽기 전용 조회가 행을 잠그고 있습니다: $sql")
    }
}
