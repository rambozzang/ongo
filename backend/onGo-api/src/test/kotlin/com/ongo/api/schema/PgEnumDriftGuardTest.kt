package com.ongo.api.schema

import com.ongo.common.enums.NotificationType
import com.ongo.common.enums.Platform
import com.ongo.common.enums.SubscriptionStatus
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Kotlin enum 값이 PostgreSQL enum 타입에 **실제로 존재하는지** 고정한다.
 *
 * ## 무엇이 깨져 있었나
 *
 * `Tables.enumValue` 는 값을 `'X'::pg_enum_type` 으로 캐스팅해 넣는다. PostgreSQL enum
 * 에 없는 값이면 INSERT 가 예외로 죽는다. 그런데 Kotlin enum 에만 값을 추가하고
 * 마이그레이션을 빠뜨리면 **컴파일도 통과하고 대부분의 테스트도 통과한다.** 그 값을
 * 실제로 쓰는 경로를 밟을 때만 터진다.
 *
 * 실제로 세 값이 그 상태였다(V114 로 수정).
 *
 *  - `CHANNEL_TOKEN_EXPIRED` — 채널 토큰이 영구 만료됐을 때의 재연결 안내
 *  - `REVENUE_ALERT` — 수익 알림
 *  - `SUSPENDED` — 관리자의 구독 정지
 *
 * 셋 다 **평상시에는 지나지 않는 경로**라 오래 숨어 있었다. 하필 "문제가 생겼을 때
 * 알려주는 장치" 만 골라 고장 나 있던 셈이다. 가장 비싼 것은 첫 번째다:
 * `ChannelScheduler` 가 채널을 EXPIRED 로 바꾼 **뒤** 알림을 저장하므로, 저장이 죽으면
 * 채널은 끊기고 사용자는 영원히 모른다.
 *
 * ## 왜 Testcontainers 가 아니라 순수 단위 테스트인가
 *
 * 이 검사는 Docker 없이 **항상** 돌아야 한다. 스키마 관련 가드가 전부 `*IT` 에만 있으면
 * Docker 가 없는 환경에서 통째로 건너뛴다 — 그 사이에 드리프트가 들어온다.
 * 여기서는 마이그레이션 SQL 을 글자 그대로 읽어 대조한다.
 *
 * 실제 PostgreSQL 적용 확인은 `SchemaDriftGuardIT` 가 별도로 한다.
 */
class PgEnumDriftGuardTest {

    private val migrationDir = File("src/main/resources/db/migration")

    /**
     * 감시 대상. **새 PG enum 을 쓰기 시작하면 여기 추가해야 한다.**
     *
     * 목록을 여기 두는 이유는 snake_case 타입명과 Kotlin 클래스명이 기계적으로 대응하지
     * 않기 때문이다(`notification_type` ↔ `NotificationType`, `platform_type` ↔ `Platform`).
     * 추측으로 짝을 맞추면 못 찾았을 때 조용히 통과한다 — 그래서 명시한다.
     */
    private val watched: Map<String, List<String>> = mapOf(
        "notification_type" to NotificationType.entries.map { it.name },
        "subscription_status" to SubscriptionStatus.entries.map { it.name },
        "platform_type" to Platform.entries.map { it.name },
        // V116 DERIVED — 월 업로드 한도가 사본을 원본과 구분하는 근거다. 값이 DB 에 없으면
        // 재활용·반복 예약·쇼츠 저장이 INSERT 에서 죽는다.
        "video_source" to com.ongo.domain.contentsource.VideoSource.entries.map { it.name },
    )

    /** 마이그레이션 전체를 합쳐 하나의 텍스트로 본다. Flyway 도 순서대로 전부 적용한다. */
    private val allSql: String by lazy {
        migrationDir.listFiles { f -> f.extension == "sql" }
            ?.sortedBy { it.name }
            ?.joinToString("\n") { it.readText() }
            ?: ""
    }

    /**
     * `CREATE TYPE x AS ENUM (...)` 와 이후의 모든 `ALTER TYPE x ADD VALUE 'y'` 를 합쳐
     * 그 타입이 최종적으로 갖는 값 집합을 구한다.
     */
    private fun dbValuesOf(typeName: String): Set<String> {
        val values = mutableSetOf<String>()

        Regex(
            """CREATE\s+TYPE\s+$typeName\s+AS\s+ENUM\s*\(([^)]*)\)""",
            RegexOption.IGNORE_CASE,
        ).findAll(allSql).forEach { match ->
            Regex("'([^']+)'").findAll(match.groupValues[1]).forEach { values += it.groupValues[1] }
        }

        Regex(
            """ALTER\s+TYPE\s+$typeName\s+ADD\s+VALUE\s+(?:IF\s+NOT\s+EXISTS\s+)?'([^']+)'""",
            RegexOption.IGNORE_CASE,
        ).findAll(allSql).forEach { values += it.groupValues[1] }

        return values
    }

    /**
     * 추출이 비면 아래 비교가 공허하게 통과한다. 먼저 막는다.
     *
     * 정규식이 스키마 형식 변화로 아무것도 못 잡는 순간 이 가드는 **fail-open** 이 된다.
     */
    @Test
    fun `감시 대상 타입을 마이그레이션에서 실제로 찾는다`() {
        assertTrue(allSql.isNotEmpty(), "마이그레이션 SQL 을 하나도 읽지 못했다: ${migrationDir.absolutePath}")

        watched.keys.forEach { typeName ->
            val found = dbValuesOf(typeName)
            assertTrue(
                found.isNotEmpty(),
                "$typeName 의 값을 마이그레이션에서 하나도 추출하지 못했다 — " +
                    "CREATE TYPE 형식이 바뀌었거나 타입이 사라졌다. 이대로면 아래 검사가 무의미하다.",
            )
        }
    }

    /**
     * **핵심 회귀.** 여기가 깨지면 해당 값을 쓰는 경로가 런타임에 예외로 죽는다.
     */
    @Test
    fun `Kotlin enum 의 모든 값이 PG enum 타입에 존재한다`() {
        val problems = watched.mapNotNull { (typeName, kotlinValues) ->
            val dbValues = dbValuesOf(typeName)
            val missing = kotlinValues.filterNot { it in dbValues }
            if (missing.isEmpty()) null else "$typeName 에 없는 값: $missing"
        }

        if (problems.isNotEmpty()) {
            fail(
                "Kotlin enum 에만 있고 DB enum 타입에 없는 값이 있다. " +
                    "'값'::타입 캐스팅이 실패해 해당 기능이 런타임에 예외로 죽는다. " +
                    "새 마이그레이션에 ALTER TYPE ... ADD VALUE IF NOT EXISTS 를 추가할 것.\n" +
                    problems.joinToString("\n"),
            )
        }
    }

    /**
     * V114 가 실제로 세 값을 넣는지 직접 확인한다.
     *
     * 위 검사는 "어딘가에는 있다" 만 본다. 이 검사는 **수정이 되돌려지면** 알려준다 —
     * 위 검사만 있으면 누군가 Kotlin enum 에서 값을 지워도 통과하기 때문이다.
     */
    @Test
    fun `V114 가 누락됐던 세 값을 넣는다`() {
        val v114 = File(migrationDir, "V114__align_pg_enums_with_kotlin.sql")
        assertTrue(v114.exists(), "V114 마이그레이션이 없다: ${v114.absolutePath}")

        val normalized = v114.readText().replace(Regex("\\s+"), " ").uppercase()

        listOf(
            "NOTIFICATION_TYPE" to "CHANNEL_TOKEN_EXPIRED",
            "NOTIFICATION_TYPE" to "REVENUE_ALERT",
            "SUBSCRIPTION_STATUS" to "SUSPENDED",
        ).forEach { (type, value) ->
            assertTrue(
                "ALTER TYPE $type ADD VALUE IF NOT EXISTS '$value'" in normalized,
                "$type 에 $value 를 추가하는 문장이 V114 에 없다",
            )
        }
    }

    /**
     * enum 값은 **더하기만 한다.** PostgreSQL 은 enum 값 삭제를 지원하지 않고,
     * 타입을 다시 만들면 그 타입을 쓰는 컬럼의 기존 데이터가 위험해진다.
     */
    @Test
    fun `V114 가 타입을 다시 만들거나 데이터를 건드리지 않는다`() {
        val normalized = File(migrationDir, "V114__align_pg_enums_with_kotlin.sql")
            .readText().replace(Regex("\\s+"), " ").uppercase()

        listOf("DROP TYPE", "DROP COLUMN", "DROP TABLE", "TRUNCATE", "DELETE FROM", "UPDATE ").forEach { danger ->
            assertTrue(danger !in normalized, "되돌릴 수 없는 문장이 섞였다: $danger")
        }
    }
}
