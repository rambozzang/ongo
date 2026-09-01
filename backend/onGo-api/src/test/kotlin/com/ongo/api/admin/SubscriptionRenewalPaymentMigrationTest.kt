package com.ongo.api.admin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * V104 자동 갱신 결제 원장 연결의 **문서 수준** 가드.
 *
 * 이 테스트는 SQL을 실제 PostgreSQL에서 실행했다는 뜻이 아니다. Docker가 없는 환경에서도
 * V104를 V103 기준으로 잘못 작성하거나, 레거시 행을 깨뜨리는 방향으로 바꾸는 실수를
 * 빠르게 잡기 위한 테스트다. 실제 적용은 운영 전 스키마 롤아웃 검증에서 별도로 한다.
 */
class SubscriptionRenewalPaymentMigrationTest {

    private val sql = File(
        "src/main/resources/db/migration/V104__renewal_attempt_payment.sql",
    ).readText()

    private val normalized = sql.replace(Regex("\\s+"), " ")

    @Test
    fun `기존 갱신 행을 보존하는 nullable payment id 컬럼을 추가한다`() {
        assertTrue(
            "ADD COLUMN IF NOT EXISTS payment_id BIGINT" in normalized,
            "기존 V103 행과 호환되는 nullable payment_id 추가가 없다",
        )
        assertTrue(
            "V103 이전 행은 NULL" in normalized,
            "내부 결제 원장이 없는 레거시 행의 처리 규칙이 SQL에 남아 있지 않다",
        )
    }

    @Test
    fun `payment id 외래키는 payments를 삭제하지 않는다`() {
        val fk = normalized
            .substringAfter("FOREIGN KEY (payment_id)")
            .substringBefore(";")
        assertTrue("REFERENCES payments(id)" in fk, "payment_id 외래키가 payments를 참조하지 않는다: $fk")
        assertTrue("CASCADE" !in fk.uppercase(), "결제 삭제가 갱신 이력까지 지우면 안 된다: $fk")
        assertTrue("ON DELETE" !in fk.uppercase(), "결제 이력 FK는 NO ACTION이어야 한다: $fk")
    }

    @Test
    fun `하나의 내부 결제는 하나의 갱신 주기에만 연결된다`() {
        val index = normalized
            .substringAfter("uq_renewal_attempts_payment")
            .substringBefore(";")
        assertTrue("CREATE UNIQUE INDEX IF NOT EXISTS uq_renewal_attempts_payment" in normalized)
        assertTrue("(payment_id)" in index, "유니크 대상이 payment_id가 아니다: $index")
        assertTrue("WHERE payment_id IS NOT NULL" in index, "레거시 NULL 행을 고려한 부분 인덱스가 없다: $index")
    }
}
