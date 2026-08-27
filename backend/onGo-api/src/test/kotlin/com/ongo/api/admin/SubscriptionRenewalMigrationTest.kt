package com.ongo.api.admin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * V103 정기 청구 마이그레이션의 **문서 수준** 가드.
 *
 * ## 이 테스트가 증명하지 않는 것
 *
 * SQL 이 PostgreSQL 에서 실제로 실행된다는 것은 **증명하지 않는다.** Testcontainers 가
 * 필요하고 이 환경에는 Docker 가 없다. 실DB 검증은 따로 반드시 필요하다.
 *
 * ## 그럼 무엇을 잡는가
 *
 * 되돌리기 어려운 의도 위반이다. 특히 유니크 인덱스가 빠지면 이중 청구가 가능해지는데,
 * 그건 실행돼도 오류가 나지 않고 고객 돈이 두 번 빠져나간 뒤에야 드러난다.
 */
class SubscriptionRenewalMigrationTest {

    private val sql: String = File(
        "src/main/resources/db/migration/V103__subscription_renewal_attempts.sql",
    ).readText()

    private val normalized: String = sql.replace(Regex("\\s+"), " ")

    /** 이 인덱스가 빠지면 인스턴스 둘이 같은 주기를 각각 청구한다. */
    @Test
    fun `주기 유니크 인덱스가 있다`() {
        assertTrue("uq_subscription_renewal_attempts_period" in normalized, "주기 유니크 인덱스가 없다")
        val index = normalized.substringAfter("uq_subscription_renewal_attempts_period").substringBefore(";")
        assertTrue(
            "(subscription_id, period_start)" in index,
            "유니크 대상이 (subscription_id, period_start) 가 아니다: $index",
        )
    }

    /**
     * 선점 시점에는 결과를 모른다. ATTEMPTED 가 CHECK 에 없으면 선점 자체가 실패하고,
     * 그러면 청구 전에 자리를 잡을 방법이 사라진다.
     */
    @Test
    fun `결과 CHECK 에 네 값이 모두 있다`() {
        val check = normalized.substringAfter("ck_subscription_renewal_attempts_outcome")
            .substringBefore(")")
        for (value in listOf("'ATTEMPTED'", "'CHARGED'", "'CHARGE_FAILED'", "'BILLING_KEY_MISSING'")) {
            assertTrue(value in check, "결과 CHECK 에서 빠진 값: $value")
        }
    }

    /** 빌링키는 이 값 하나로 반복 청구가 가능하다. 평문 컬럼을 만들면 안 된다. */
    @Test
    fun `빌링키 컬럼 이름에 암호화가 드러난다`() {
        assertTrue(
            "ADD COLUMN IF NOT EXISTS billing_key_encrypted" in normalized,
            "빌링키 컬럼 추가가 없거나 재적용 가능하지 않다",
        )
        assertTrue(
            "billing_key TEXT" !in normalized && "ADD COLUMN IF NOT EXISTS billing_key " !in normalized,
            "평문 빌링키 컬럼이 생겼다",
        )
    }

    /** 구독이 지워지면 그 갱신 이력도 함께 사라져야 한다 — 고아 행이 남으면 집계가 틀어진다. */
    @Test
    fun `구독 FK 가 CASCADE 다`() {
        val fk = normalized.substringAfter("subscription_id BIGINT NOT NULL REFERENCES")
            .substringBefore(",")
        assertTrue("ON DELETE CASCADE" in fk, "구독 FK 에 CASCADE 가 없다: $fk")
    }

    @Test
    fun `테이블 생성이 재적용 가능하다`() {
        assertTrue("CREATE TABLE IF NOT EXISTS subscription_renewal_attempts" in normalized)
        assertTrue("CREATE UNIQUE INDEX IF NOT EXISTS" in normalized)
    }
}
