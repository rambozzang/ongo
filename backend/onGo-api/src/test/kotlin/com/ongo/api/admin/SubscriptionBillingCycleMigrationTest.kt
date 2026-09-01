package com.ongo.api.admin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * V105 결제 주기 예약 컬럼의 문서 수준 가드.
 *
 * 기존 구독은 현재 billing_cycle을 그대로 사용하고, 새 다운그레이드 요청만
 * pending_billing_cycle을 채우는 계약을 SQL에 고정한다. 실제 PostgreSQL 적용은
 * Testcontainers/운영 롤아웃에서 별도로 확인한다.
 */
class SubscriptionBillingCycleMigrationTest {

    private val sql = File(
        "src/main/resources/db/migration/V105__pending_billing_cycle.sql",
    ).readText()

    private val normalized = sql.replace(Regex("\\s+"), " ")

    @Test
    fun `기존 구독을 보존하는 nullable pending cycle 컬럼을 추가한다`() {
        assertTrue(
            "ADD COLUMN IF NOT EXISTS pending_billing_cycle billing_cycle" in normalized,
            "기존 구독과 호환되는 pending_billing_cycle 컬럼 추가가 없다",
        )
    }

    @Test
    fun `예약 주기는 현재 주기와 분리된 컬럼으로 기록된다`() {
        assertTrue(
            "pending_billing_cycle" in normalized && "billing_cycle" in normalized,
            "예약 주기와 현재 주기를 구분하는 SQL이 없다",
        )
        assertTrue(
            "NULL이면 현재 billing_cycle을 그대로 사용" in normalized,
            "기존 NULL 행의 호환 규칙이 SQL에 남아 있지 않다",
        )
    }
}
