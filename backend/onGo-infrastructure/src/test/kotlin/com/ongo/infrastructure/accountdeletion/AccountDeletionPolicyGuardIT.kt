package com.ongo.infrastructure.accountdeletion

import com.ongo.domain.accountdeletion.FkPolicy
import com.ongo.domain.accountdeletion.UserFkKey
import com.ongo.domain.accountdeletion.UserFkPolicyRegistry
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * `users` 를 참조하는 모든 외래키가 삭제 정책으로 분류돼 있는지 고정한다.
 *
 * 계정 삭제는 fail-closed 다. 분류되지 않은 외래키가 하나라도 있으면 삭제 job 은
 * **전역** `BLOCKED_POLICY` 로 끝나야 한다. 새 기능이 정책 검토를 우회하고 사용자 데이터를
 * 지우거나, 반대로 조용히 탈퇴를 깨뜨리는 것을 막기 위해서다.
 *
 * 이 테스트는 그 전제를 CI 로 끌어올린다. 새 테이블이 `users` 를 참조하면 여기서 실패하고,
 * 개발자는 `UserFkPolicyRegistry` 에 근거와 함께 정책을 등록해야 한다.
 *
 * 스키마는 `onGo-api` 의 `db/migration` 을 Flyway 가 적용한 결과다. 즉 여기서 통과하면
 * 운영에 적용될 마이그레이션 기준으로 통과한 것이다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AccountDeletionPolicyGuardIT {

    @Autowired lateinit var dsl: DSLContext

    companion object {
        @Container @JvmStatic
        val pg = PostgreSQLContainer("postgres:16").apply {
            withDatabaseName("ongo_test")
            withUsername("test"); withPassword("test")
        }

        @JvmStatic @DynamicPropertySource
        fun props(r: DynamicPropertyRegistry) {
            r.add("spring.datasource.url") { pg.jdbcUrl }
            r.add("spring.datasource.username") { pg.username }
            r.add("spring.datasource.password") { pg.password }
        }
    }

    /**
     * 실제 스키마의 `users` 참조 외래키.
     *
     * `information_schema` 대신 `pg_constraint` 를 직접 읽는다. `information_schema` 는
     * 다중 컬럼 외래키에서 행이 중복돼 개수가 틀어진다.
     */
    private fun actualUserFks(): List<UserFkKey> =
        dsl.fetch(
            """
            SELECT
                n.nspname AS schema_name,
                c.conname  AS constraint_name,
                c.conrelid::regclass::text AS table_name,
                (SELECT string_agg(a.attname, ',' ORDER BY k.ord)
                   FROM unnest(c.conkey) WITH ORDINALITY k(attnum, ord)
                   JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum) AS local_cols,
                (SELECT string_agg(a.attname, ',' ORDER BY k.ord)
                   FROM unnest(c.confkey) WITH ORDINALITY k(attnum, ord)
                   JOIN pg_attribute a ON a.attrelid = c.confrelid AND a.attnum = k.attnum) AS ref_cols
            FROM pg_constraint c
            JOIN pg_namespace n ON n.oid = c.connamespace
            WHERE c.contype = 'f' AND c.confrelid = 'users'::regclass
            """.trimIndent()
        ).map {
            UserFkKey(
                schema = it.get("schema_name", String::class.java),
                constraintName = it.get("constraint_name", String::class.java),
                table = it.get("table_name", String::class.java),
                localColumns = it.get("local_cols", String::class.java).split(","),
                referencedColumns = it.get("ref_cols", String::class.java).split(","),
            )
        }

    @Test
    @DisplayName("users 를 참조하는 모든 외래키는 삭제 정책으로 분류돼야 한다")
    fun everyUserFkIsClassified() {
        val actual = actualUserFks()

        // 조회가 헛돌면 아무것도 검증하지 못한 채 통과한다. 통과의 의미를 지킨다.
        assertTrue(actual.size > 100) {
            "users 참조 외래키를 ${actual.size}건만 읽었다. 조회가 깨졌을 가능성이 높다"
        }

        val unclassified = actual.filter { UserFkPolicyRegistry.find(it) == null }
            .sortedBy { it.constraintName }

        assertTrue(unclassified.isEmpty()) {
            buildString {
                append("정책이 없는 users 참조 외래키가 ${unclassified.size}건 있다.\n")
                append("계정 삭제는 fail-closed 라 이 상태에서는 삭제 job 이 전역 차단된다.\n")
                unclassified.forEach {
                    append("  - ${it.constraintName} (${it.table}.${it.localColumns.joinToString("+")})\n")
                }
                append("\nUserFkPolicyRegistry 에 근거와 함께 등록해라. ")
                append("판단이 안 서면 REVIEW_BLOCK 이 정답이다. ")
                append("docs/plans/account-deletion-policy-table.md 의 근거도 함께 갱신해라.")
            }
        }
    }

    @Test
    @DisplayName("레지스트리에 실제 스키마에 없는 외래키가 남아 있으면 안 된다")
    fun registryHasNoStaleEntries() {
        val actual = actualUserFks().toSet()
        val stale = UserFkPolicyRegistry.entries
            .map { it.key }
            .filterNot { it in actual }
            .sortedBy { it.constraintName }

        // 테이블이나 제약이 사라졌는데 항목이 남으면, 그 항목은 아무것도 지키지 않으면서
        // 분류가 끝난 것처럼 보이게 한다.
        assertTrue(stale.isEmpty()) {
            buildString {
                append("실제 스키마에 없는 정책 항목이 ${stale.size}건 있다.\n")
                stale.forEach { append("  - ${it.constraintName} (${it.table})\n") }
                append("\n제약 이름이 바뀌었거나 테이블이 사라졌다. 레지스트리에서 빼라.")
            }
        }
    }

    @Test
    @DisplayName("정책 항목에는 근거가 있어야 한다")
    fun everyEntryHasRationale() {
        val missing = UserFkPolicyRegistry.entries
            .filter { it.rationale.isBlank() }
            .map { it.key.constraintName }

        assertTrue(missing.isEmpty()) {
            "근거 없는 정책 항목: ${missing.joinToString()}. 근거 없이 정책을 올리지 않는다"
        }
    }

    @Test
    @DisplayName("결정된 보존 정책이 없으므로 PRESERVE_ANONYMIZE 는 0건이어야 한다")
    fun noPreserveAnonymizeUntilPolicyDecided() {
        val preserve = UserFkPolicyRegistry.entries
            .filter { it.policy == FkPolicy.PRESERVE_ANONYMIZE }
            .map { it.key.constraintName }

        // activity_logs / coupon_usages 를 PRESERVE 로 올리려면 user_id NOT NULL 해제
        // 마이그레이션과 제품·감사 정책 승인이 선행돼야 한다. 그 전에 올리면
        // "결정된 척"이 되고, 익명화 코드 없이 보존만 하다가 탈퇴가 조용히 막힌다.
        assertTrue(preserve.isEmpty()) {
            "PRESERVE_ANONYMIZE 로 분류된 항목이 있다: ${preserve.joinToString()}. " +
                "보존 정책 승인과 NOT NULL 해제 마이그레이션이 선행돼야 한다"
        }
    }

    @Test
    @DisplayName("DELETE 로 분류된 외래키는 user_id 단일 컬럼이어야 한다")
    fun deletableFksAreSingleUserIdColumn() {
        // user_id 가 아닌 컬럼(reviewer_id, owner_id 등)은 소유가 아니라 관계다.
        // 관계 참조를 DELETE 로 올리면 남의 데이터가 사라진다.
        val suspicious = UserFkPolicyRegistry.deletable()
            .filterNot { it.key.localColumns == listOf("user_id") }
            .map { "${it.key.constraintName}(${it.key.localColumns.joinToString("+")})" }

        assertTrue(suspicious.isEmpty()) {
            "DELETE 인데 user_id 단일 컬럼이 아닌 항목: ${suspicious.joinToString()}. " +
                "관계 참조는 소유가 아니다"
        }
    }

    @Test
    @DisplayName("정책과 행 연산이 어긋나면 안 된다")
    fun policyAndRowOperationAgree() {
        val mismatched = UserFkPolicyRegistry.entries.filter {
            when (it.policy) {
                FkPolicy.DELETE -> it.rowOperation == com.ongo.domain.accountdeletion.RowOperation.ROW_BLOCK
                FkPolicy.REVIEW_BLOCK -> it.rowOperation != com.ongo.domain.accountdeletion.RowOperation.ROW_BLOCK
                FkPolicy.PRESERVE_ANONYMIZE -> false
            }
        }.map { "${it.key.constraintName}: ${it.policy}/${it.rowOperation}" }

        assertTrue(mismatched.isEmpty()) {
            "정책과 행 연산이 어긋난 항목: ${mismatched.joinToString()}"
        }
    }

    @Test
    @DisplayName("레지스트리 항목 수가 실제 외래키 수와 같아야 한다")
    fun registrySizeMatchesSchema() {
        assertEquals(actualUserFks().size, UserFkPolicyRegistry.entries.size) {
            "레지스트리와 실제 스키마의 users 참조 외래키 수가 다르다"
        }
    }
}
