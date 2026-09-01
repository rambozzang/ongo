package com.ongo.api.admin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * V107 광고 수익 측정 상태 마이그레이션의 **문서 수준** 가드.
 *
 * ## 이 테스트가 증명하지 않는 것
 *
 * SQL 이 PostgreSQL 에서 실제로 실행된다는 것은 **증명하지 않는다.** 그건 Testcontainers 가
 * 필요하고 이 환경에는 Docker 가 없다. 더구나 `SchemaDriftGuardIT` 조차 빈 DB 에
 * V1→V110 을 한 번에 붓는 **신규 설치 경로**라, `analytics_daily` 가 비어 있어 아래에서
 * 다루는 락·스캔 위험을 구조적으로 재현하지 못한다. 실제 소요 시간과 락 유지 시간은
 * 운영 스냅샷 복제본에서 따로 재야 한다.
 *
 * ## 그럼 무엇을 잡는가
 *
 * 되돌리기 어려운 **의도 위반** 둘을 잡는다. 둘 다 실행하면 오류가 나지 않고 조용히
 * 통과하기 때문에, 실DB 테스트가 있어도 놓치기 쉬운 종류다.
 *
 * 1. `SET LOCAL lock_timeout` 이 사라지거나 첫 ALTER 뒤로 밀리는 것.
 *    이 문장은 부모와 **모든 월별 파티션**에 ACCESS EXCLUSIVE 를 잡고, 마이그레이션은
 *    구 서비스가 살아 있는 동안 돈다. 상한이 없으면 롱 리드 하나 뒤에 줄을 서는 순간
 *    그 뒤의 모든 조회·수집이 함께 막힌다.
 * 2. `ADD CONSTRAINT` 앞의 `DROP CONSTRAINT IF EXISTS` 가 빠지는 것.
 *    PostgreSQL 의 ADD CONSTRAINT 에는 IF NOT EXISTS 가 없다. 선행 DROP 이 없으면 이
 *    파일은 한 번만 적용할 수 있고, 중단 뒤 사람이 일부를 손으로 넣어 둔 상태에서
 *    재시도하면 "이미 존재한다"로 막힌다.
 *
 * 나머지 테스트는 그 두 가지를 고치면서 **기존 의미를 바꾸지 않았는지** 고정한다.
 */
class AnalyticsRevenueStatusMigrationTest {

    private val sql: String = File(
        "src/main/resources/db/migration/V107__analytics_revenue_status.sql",
    ).readText()

    private val normalized: String = sql.replace(Regex("\\s+"), " ")

    /**
     * 주석을 걷어낸 실행문만 남긴다.
     *
     * 순서 검증이 주석 문구에 흔들리면 안 된다. 예를 들어 주석이 "ADD CONSTRAINT 에는
     * IF NOT EXISTS 가 없다" 라고 설명하는데, 그 문자열이 실제 문장보다 앞에 있으므로
     * 원문에서 위치를 재면 순서 판정이 뒤집힌다.
     *
     * V107 에는 `--` 줄 주석만 있고 `--` 를 포함한 문자열 리터럴이 없어 이 방식이 안전하다.
     */
    private val statements: String = sql.lineSequence()
        .joinToString(" ") { it.substringBefore("--") }
        .replace(Regex("\\s+"), " ")

    private val lockTimeout = "SET LOCAL lock_timeout = '5s';"
    private val firstAlter = "ALTER TABLE analytics_daily ADD COLUMN IF NOT EXISTS revenue_currency"

    // ── 1. 락 상한 ───────────────────────────────────────────────────────────

    /**
     * 상한이 **락을 잡는 첫 문장보다 먼저** 와야 의미가 있다. 뒤에 두면 이미 대기 큐에
     * 들어간 뒤라 아무것도 막지 못한다.
     */
    @Test
    fun `락 상한이 첫 ALTER 보다 먼저 온다`() {
        assertTrue(lockTimeout in statements, "SET LOCAL lock_timeout 상한이 없다")
        assertTrue(firstAlter in statements, "revenue_currency 컬럼 추가 문장을 찾지 못했다")

        assertTrue(
            statements.indexOf(lockTimeout) < statements.indexOf(firstAlter),
            "락 상한이 첫 ALTER 뒤에 있다 — 이미 대기 큐에 들어간 뒤라 아무것도 막지 못한다",
        )
    }

    /**
     * **왜** 상한이 필요한지가 SQL 에 남아 있어야 한다.
     *
     * 근거가 없으면 다음에 읽는 사람이 "배포가 자꾸 실패한다"는 이유로 이 줄을 지운다.
     * 특히 스크립트의 MIGRATE_TIMEOUT_SECONDS 가 이미 있으니 중복이라고 오해하기 쉬운데,
     * 그건 클라이언트 측이라 서버의 ALTER 를 취소하지 못한다는 것이 핵심이다.
     */
    @Test
    fun `락 상한을 둔 이유가 SQL 주석에 남아 있다`() {
        for (phrase in listOf(
            "ACCESS EXCLUSIVE",
            "구 서비스가 살아 있는 동안",
            "클라이언트 측",
            "서버 측 상한",
            "무중단 실패",
        )) {
            assertTrue(phrase in normalized, "락 상한 근거에서 빠진 문구: $phrase")
        }
    }

    // ── 2. 재실행 가능성 ─────────────────────────────────────────────────────

    /** 두 CHECK 모두 ADD 앞에 DROP IF EXISTS 가 있어야 재적용이 된다. */
    @Test
    fun `두 CHECK 를 각각 ADD 전에 DROP IF EXISTS 한다`() {
        for (constraint in listOf("chk_analytics_revenue_status", "chk_analytics_revenue_currency")) {
            val drop = "DROP CONSTRAINT IF EXISTS $constraint"
            val add = "ADD CONSTRAINT $constraint"

            assertTrue(drop in statements, "$constraint 를 DROP IF EXISTS 로 먼저 지우지 않는다")
            assertTrue(add in statements, "$constraint 를 추가하는 문장이 없다")
            assertTrue(
                statements.indexOf(drop) < statements.indexOf(add),
                "$constraint 의 DROP 이 ADD 뒤에 있다 — 방금 만든 제약을 지운다",
            )
        }
    }

    /** 컬럼과 인덱스는 원래부터 재적용 가능했다. 고치면서 잃지 않았는지 본다. */
    @Test
    fun `컬럼과 인덱스 추가가 재적용 가능하다`() {
        assertTrue(
            "ADD COLUMN IF NOT EXISTS revenue_currency CHAR(3)" in statements,
            "revenue_currency 컬럼 추가에 IF NOT EXISTS 가 없다",
        )
        assertTrue(
            "ADD COLUMN IF NOT EXISTS revenue_status VARCHAR(20) NOT NULL DEFAULT 'UNSUPPORTED'"
                in statements,
            "revenue_status 컬럼 정의가 바뀌었다 — 기존 행은 UNSUPPORTED 여야 한다",
        )
        assertTrue(
            "CREATE INDEX IF NOT EXISTS idx_analytics_daily_revenue_status" in statements,
            "수익 상태 인덱스 생성에 IF NOT EXISTS 가 없다",
        )
    }

    // ── 3. 기존 의미 보존 ────────────────────────────────────────────────────

    /**
     * 허용 값이 코드의 `RevenueStatus` 와 1:1 이다. 하나라도 빠지면 그 상태를 쓰는
     * 저장이 곧바로 실패하고, 늘어나면 화면이 해석하지 못하는 값이 새어 들어온다.
     */
    @Test
    fun `상태 CHECK 의 허용 값이 그대로다`() {
        val check = statements
            .substringAfter("ADD CONSTRAINT chk_analytics_revenue_status")
            .substringBefore(";")

        for (status in listOf(
            "'MEASURED'",
            "'PENDING'",
            "'PERMISSION_REQUIRED'",
            "'UNSUPPORTED'",
            "'ERROR'",
        )) {
            assertTrue(status in check, "상태 CHECK 에서 $status 가 빠졌다: $check")
        }
    }

    /**
     * 통화 CHECK 의 두 분기를 모두 고정한다.
     *
     * 한쪽만 남으면 "측정했는데 통화가 없는 행" 또는 "측정하지 않았는데 통화가 붙은 행"이
     * 저장된다. 둘 다 마이크로 단위 금액을 엉뚱한 통화로 읽게 만든다.
     */
    @Test
    fun `통화 CHECK 의 두 분기가 그대로다`() {
        val check = statements
            .substringAfter("ADD CONSTRAINT chk_analytics_revenue_currency")
            .substringBefore(";")

        assertTrue(
            "revenue_status = 'MEASURED' AND revenue_currency IS NOT NULL" in check,
            "측정된 행에 통화를 강제하지 않는다: $check",
        )
        assertTrue(
            "revenue_status <> 'MEASURED' AND revenue_currency IS NULL" in check,
            "측정하지 않은 행에 통화가 붙는 것을 막지 않는다: $check",
        )
    }

    /**
     * 인덱스 대상 컬럼을 고정한다.
     *
     * 저선택도라 불필요해 보이지만 `RevenueJooqRepository.MEASURED_ONLY` 와
     * `AnalyticsJooqRepository` 가 `revenue_status` 로 거르고 MEASURED 가 희소하므로
     * 실제로 쓰인다. 지우면 수익 조회가 전 파티션을 훑는다.
     */
    @Test
    fun `수익 상태 인덱스가 그대로다`() {
        val index = statements
            .substringAfter("idx_analytics_daily_revenue_status")
            .substringBefore(";")

        assertTrue(
            "ON analytics_daily (revenue_status)" in index,
            "인덱스 대상이 revenue_status 가 아니다: $index",
        )
    }
}
