package com.ongo.api.admin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * V102 역분개 마이그레이션의 **문서 수준** 가드.
 *
 * ## 이 테스트가 증명하지 않는 것
 *
 * SQL 이 PostgreSQL 에서 실제로 실행된다는 것은 **증명하지 않는다.** 그건 Testcontainers 가
 * 필요하고 이 환경에는 Docker 가 없다. 자기 참조 FK 와 부분 유니크 인덱스는 문법과 방언에
 * 민감해 실DB 검증이 반드시 따로 필요하다.
 *
 * ## 그럼 무엇을 잡는가
 *
 * 되돌리기 어려운 **의도 위반**을 잡는다. 특히 자기 참조 FK 에 CASCADE 가 붙는 사고는
 * 실행돼도 오류가 나지 않고 조용히 감사 추적을 지운다 — 원본이 사라지고 취소 행만 남거나,
 * 반대로 실행 삭제가 막힌다. 그건 실DB 테스트가 있어도 놓치기 쉬운 종류의 실수다.
 */
class ShortsPilotReversalMigrationTest {

    private val sql: String = File(
        "src/main/resources/db/migration/V102__shorts_pilot_entry_reversal.sql",
    ).readText()

    private val normalized: String = sql.replace(Regex("\\s+"), " ")

    /**
     * 자기 참조 FK 에 CASCADE 가 붙으면 원본이 지워질 때 취소 행만 남는다. 그러면
     * "무엇이 취소됐는지"를 가리키는 행이 존재하지 않는 원본을 가리키게 된다.
     */
    @Test
    fun `자기 참조 FK 에 CASCADE 를 붙이지 않는다`() {
        val fkClause = normalized.substringAfter("fk_shorts_pilot_events_reverses FOREIGN KEY")
            .substringBefore(";")

        assertTrue(
            "CASCADE" !in fkClause.uppercase(),
            "reverses_event_id FK 에 CASCADE 가 붙었다: $fkClause",
        )
    }

    /**
     * run_id FK 는 ON DELETE CASCADE 다(V97). 실행을 지우면 그 실행의 모든 이벤트가 한
     * 문장 안에서 함께 지워지는데, RESTRICT 는 행 단위로 즉시 검사하므로 취소 행이 아직
     * 남은 시점에 원본 삭제를 막아 **실행 삭제 자체가 실패한다.** NO ACTION(=ON DELETE 절
     * 생략)만이 문장 종료 후 검사해 통과한다.
     */
    @Test
    fun `자기 참조 FK 에 RESTRICT 를 명시하지 않는다`() {
        val fkClause = normalized.substringAfter("fk_shorts_pilot_events_reverses FOREIGN KEY")
            .substringBefore(";")

        assertTrue(
            "RESTRICT" !in fkClause.uppercase(),
            "RESTRICT 는 run 삭제 시 CASCADE 와 충돌해 실행 삭제를 막는다: $fkClause",
        )
    }

    /**
     * NO ACTION 의도를 **양성으로** 고정한다.
     *
     * 위 두 테스트는 CASCADE·RESTRICT 가 없다는 것만 본다. ON DELETE SET NULL 이 들어와도
     * 통과하는데, 그러면 실행 삭제 시 역분개 행의 참조만 NULL 로 바뀌어 ck_..._reverses
     * CHECK 를 위반한다. ON DELETE 절 자체가 없어야 한다.
     */
    @Test
    fun `자기 참조 FK 에 ON DELETE 절이 아예 없다`() {
        val fkClause = normalized.substringAfter("fk_shorts_pilot_events_reverses FOREIGN KEY")
            .substringBefore(";")

        assertTrue(
            "ON DELETE" !in fkClause.uppercase(),
            "self FK 는 ON DELETE 를 생략해 NO ACTION 이어야 한다. 명시된 절이 있다: $fkClause",
        )
        assertTrue(
            "ON UPDATE" !in fkClause.uppercase(),
            "self FK 는 ON UPDATE 도 생략한다. id 는 BIGSERIAL 이라 바뀌지 않는다: $fkClause",
        )
    }

    /**
     * 왜 RESTRICT 가 아닌지가 SQL 에 남아 있어야 한다.
     *
     * 이건 원래 지시(RESTRICT)와 다른 선택이다. 근거가 코드에 없으면 다음에 읽는 사람이
     * "실수로 빠뜨렸다"고 보고 RESTRICT 를 넣는다. 그 순간 무효화 이력이 있는 실행을
     * 고객이 지울 수 없게 된다.
     */
    @Test
    fun `NO ACTION 을 고른 이유가 SQL 주석에 남아 있다`() {
        for (phrase in listOf(
            "NO ACTION",
            "RESTRICT 가 아니다",
            "run_id cascade",
            "self FK 자체는 cascade 하지 않는다",
        )) {
            assertTrue(phrase in normalized, "self FK 선택 근거에서 빠진 문구: $phrase")
        }
    }

    /** 부분 인덱스가 아니면 취소가 아닌 행까지 유니크 대상이 되어 삽입이 막힌다. */
    @Test
    fun `취소 유니크 인덱스는 부분 인덱스다`() {
        assertTrue(
            "uq_shorts_pilot_events_reversal" in normalized,
            "취소 중복을 막는 유니크 인덱스가 없다",
        )
        val index = normalized.substringAfter("uq_shorts_pilot_events_reversal").substringBefore(";")
        assertTrue(
            "WHERE event_type = 'OPERATOR_ENTRY_REVERSED'" in index,
            "부분 인덱스 조건이 없다: $index",
        )
    }

    /** 기존 CHECK 재생성 관례(V98/V99)를 따라야 재적용이 안전하다. */
    @Test
    fun `기존 제약을 DROP IF EXISTS 후 재생성한다`() {
        for (constraint in listOf(
            "ck_shorts_pilot_events_type",
            "ck_shorts_pilot_events_reverses",
            "fk_shorts_pilot_events_reverses",
        )) {
            assertTrue(
                "DROP CONSTRAINT IF EXISTS $constraint" in normalized,
                "$constraint 를 DROP IF EXISTS 로 먼저 지우지 않는다",
            )
        }
    }

    /** 새 타입이 CHECK 에 없으면 삽입이 곧바로 실패한다. */
    @Test
    fun `새 이벤트 타입을 CHECK 목록에 넣는다`() {
        val typeCheck = normalized.substringAfter("ADD CONSTRAINT ck_shorts_pilot_events_type")
            .substringBefore(";")

        assertTrue("'OPERATOR_ENTRY_REVERSED'" in typeCheck, "새 타입이 CHECK 에 없다")
        // 기존 여섯 타입이 빠지면 기존 행이 전부 무효가 된다.
        for (existing in listOf(
            "'PILOT_ENROLLED'",
            "'STAGE_RERUN'",
            "'RENDER_ATTEMPT_FAILED'",
            "'OPERATOR_TIME_LOGGED'",
            "'OPERATOR_REVENUE_LOGGED'",
            "'OPERATOR_EXTERNAL_COST_LOGGED'",
        )) {
            assertTrue(existing in typeCheck, "기존 타입 $existing 가 CHECK 에서 빠졌다")
        }
    }

    /** 참조는 취소 행에만 붙어야 한다. 다른 행에 붙으면 엉뚱한 원본이 합계에서 빠진다. */
    @Test
    fun `취소 행에만 참조가 붙도록 CHECK 한다`() {
        val check = normalized.substringAfter("ADD CONSTRAINT ck_shorts_pilot_events_reverses")
            .substringBefore(";")

        assertTrue(
            "event_type = 'OPERATOR_ENTRY_REVERSED' AND reverses_event_id IS NOT NULL" in check,
            "취소 행에 참조를 강제하지 않는다: $check",
        )
        assertTrue(
            "event_type <> 'OPERATOR_ENTRY_REVERSED' AND reverses_event_id IS NULL" in check,
            "취소가 아닌 행에 참조가 붙는 것을 막지 않는다: $check",
        )
    }

    /**
     * 컬럼 추가는 재적용 가능해야 한다. V98/V99 와 같은 관례다.
     */
    @Test
    fun `컬럼 추가가 재적용 가능하다`() {
        assertTrue(
            "ADD COLUMN IF NOT EXISTS reverses_event_id BIGINT" in normalized,
            "컬럼 추가에 IF NOT EXISTS 가 없다",
        )
    }
}
