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
 * 결제 행 잠금이 **실제 발행되는 SQL**에 반영되는지 확인한다.
 *
 * MockK로 리포지토리를 가짜로 만들면 "잠금 조회를 호출했다"까지만 증명되고 정작 `FOR UPDATE`가
 * 붙는지는 증명되지 않는다. 여기서는 jOOQ `MockConnection`으로 실제 발행 SQL 문자열을 잡아
 * 검증한다. `findById`가 잠그지 **않는다는** 대조군을 함께 두어 단언 자체가 무의미해지는 것을 막는다.
 */
class PaymentJooqRepositoryLockTest {

    private val executed = mutableListOf<String>()

    private fun repository(): PaymentJooqRepository {
        // 행이 0건인 빈 결과셋을 돌려준다. MockResult()의 기본값은 업데이트 카운트라
        // fetchOne()이 UPDATE_COUNT 레코드를 반환해 매핑 단계에서 터진다.
        val empty = DSL.using(SQLDialect.POSTGRES).newResult()
        val provider = MockDataProvider { ctx ->
            executed += ctx.sql()
            arrayOf(MockResult(0, empty))
        }
        return PaymentJooqRepository(DSL.using(MockConnection(provider), SQLDialect.POSTGRES))
    }

    @Test
    @DisplayName("findByIdForUpdate는 FOR UPDATE가 붙은 SQL을 발행한다")
    fun lockingReadRendersForUpdate() {
        repository().findByIdForUpdate(42)

        val sql = executed.single().lowercase()
        assertTrue(sql.contains("for update"), "잠금 조회인데 FOR UPDATE가 없다: $sql")
        assertTrue(sql.contains("payments"), "payments 테이블을 조회해야 한다: $sql")
    }

    @Test
    @DisplayName("기존 findById는 잠그지 않는다 — 대조군")
    fun plainReadDoesNotLock() {
        repository().findById(42)

        val sql = executed.single().lowercase()
        assertFalse(sql.contains("for update"), "일반 조회에 FOR UPDATE가 붙었다: $sql")
    }
}
