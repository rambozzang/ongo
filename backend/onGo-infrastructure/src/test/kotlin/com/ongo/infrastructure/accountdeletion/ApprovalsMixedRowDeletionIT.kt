package com.ongo.infrastructure.accountdeletion

import com.ongo.domain.accountdeletion.AccountDeletionPreflight
import com.ongo.domain.accountdeletion.RowOperation
import com.ongo.domain.accountdeletion.UserFkKey
import com.ongo.infrastructure.testsupport.SqlStates
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import org.junit.jupiter.api.BeforeEach
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
 * 한 `approvals` 행이 여러 사용자를 참조할 때, 한 사용자를 지운다고 **행이 통째로 사라지면
 * 안 된다**는 것을 고정한다.
 *
 * `approvals` 는 `users` 를 세 번 참조한다.
 * - `user_id` — 소유자
 * - `requester_id` — 승인을 요청한 사람
 * - `reviewer_id` — 검토자
 *
 * `user_id` 가 `ON DELETE CASCADE` 이면, 소유자를 지울 때 DB 가 행을 지운다.
 * 그 행에 걸린 **요청자와 검토자의 승인 데이터가 함께 사라진다.**
 * 애플리케이션이 정책으로 막든 말든 DB 층에서 벌어지는 일이라 코드로는 못 막는다.
 *
 * 이 테스트는 그 경로를 닫아둔다. 정책 레지스트리에서 `approvals` 는 `ROW_BLOCK` 이므로
 * 삭제 엔진은 애초에 진행하지 않지만, **엔진을 우회한 삭제나 정책 회귀에서도** 조용한
 * 유실이 일어나지 않아야 한다. 그래서 스키마 자체를 검증한다.
 *
 * `ROW_DETACH` 정책을 채택하기로 결정하면 그때 nullable + `SET NULL` 로 바꾸고
 * 이 테스트의 기대도 함께 바꾼다. 그 전까지 가장 보수적인 상태는 "막힌다"이다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ApprovalsMixedRowDeletionIT {

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

        private const val OWNER = "mixed-owner@test.io"
        private const val REQUESTER = "mixed-requester@test.io"
        private const val REVIEWER = "mixed-reviewer@test.io"
    }

    private fun userId(email: String): Long =
        dsl.fetchOne("SELECT id FROM users WHERE email = ?", email)!!.get(0, Long::class.java)

    private fun approvalCount(): Int =
        dsl.fetchOne("SELECT count(*) FROM approvals")!!.get(0, Int::class.java)

    @BeforeEach
    fun setUp() {
        dsl.execute("DELETE FROM approvals")
        dsl.execute("DELETE FROM videos WHERE title = 'mixed-row-test'")
        dsl.execute("DELETE FROM users WHERE email IN (?, ?, ?)", OWNER, REQUESTER, REVIEWER)

        listOf(OWNER to "owner", REQUESTER to "requester", REVIEWER to "reviewer").forEach { (email, pid) ->
            dsl.execute(
                """
                INSERT INTO users (email, name, provider, provider_id, role, plan_type)
                VALUES (?, ?, 'GOOGLE', ?, 'USER', 'FREE')
                """.trimIndent(),
                email, pid, "mixed-$pid",
            )
        }
        dsl.execute(
            "INSERT INTO videos (user_id, title) VALUES (?, 'mixed-row-test')",
            userId(OWNER),
        )
        dsl.execute(
            """
            INSERT INTO approvals (user_id, video_id, video_title, platforms, requester_id, reviewer_id, status)
            VALUES (?, (SELECT id FROM videos WHERE title = 'mixed-row-test'), 'mixed-row-test', 'YOUTUBE', ?, ?, 'PENDING')
            """.trimIndent(),
            userId(OWNER), userId(REQUESTER), userId(REVIEWER),
        )
    }


    /**
     * 사용자 삭제가 **외래키 위반으로** 막히는지 단언한다.
     *
     * `Exception::class` 로 넓게 잡으면 SQL 문법 오류나 연결 실패도 통과시킨다.
     * 그러면 "막혔다"와 "다른 이유로 실패했다"를 구분하지 못한다.
     * 스프링 번역 계층과 SQLState 23503 을 함께 본다.
     */
    private fun assertBlockedByForeignKey(email: String) {
        val thrown = assertThrows<DataIntegrityViolationException> {
            dsl.execute("DELETE FROM users WHERE email = ?", email)
        }
        assertEquals(SqlStates.FOREIGN_KEY_VIOLATION, SqlStates.of(thrown)) {
            "외래키 위반(23503)이 아닌 이유로 실패했다: ${thrown.message}"
        }
    }

    @Test
    @DisplayName("소유자를 지워도 다른 사용자가 걸린 승인 행이 조용히 사라지면 안 된다")
    fun deletingOwnerMustNotSilentlyDropOthersApproval() {
        assertEquals(1, approvalCount()) { "사전 조건: 혼합 행이 1건 있어야 한다" }

        // user_id 가 CASCADE 이면 이 삭제가 성공하고 행이 사라진다 — 그게 유실이다.
        // NO ACTION 이면 외래키 위반으로 막힌다. 막히는 게 맞다.
        assertBlockedByForeignKey(OWNER)

        assertEquals(1, approvalCount()) {
            "소유자 삭제로 승인 행이 사라졌다. 요청자와 검토자의 데이터가 함께 유실된다"
        }
    }

    @Test
    @DisplayName("preflight 는 ROW_BLOCK 을 돌려 DELETE 를 아예 시도하지 않는다")
    fun preflightBlocksBeforeAnyDeleteIsAttempted() {
        // 위 테스트가 "직접 DELETE 는 외래키로 막힌다"(스키마 층)를 고정한다면,
        // 이건 "애플리케이션은 애초에 DELETE 를 실행하지 않는다"(정책 층)를 고정한다.
        // 두 층을 따로 둔다. 스키마가 막아주니 정책은 대충 해도 된다는 결론을 막기 위해서다.
        val approvalsOwnerFk = UserFkKey(
            schema = "public",
            constraintName = "approvals_user_id_fkey",
            table = "approvals",
            localColumns = listOf("user_id"),
            referencedColumns = listOf("id"),
        )
        val ownerId = userId(OWNER)

        val result = AccountDeletionPreflight.evaluate(
            actualFks = listOf(approvalsOwnerFk),
            userRowCounter = { key ->
                dsl.fetchOne(
                    "SELECT count(*) FROM ${key.table} WHERE ${key.localColumns.single()} = ?",
                    ownerId,
                )!!.get(0, Long::class.java)
            },
        )

        assertTrue(result is AccountDeletionPreflight.Result.BlockedForUser) {
            "approvals 행을 가진 사용자는 사용자별 차단이어야 한다. 실제: $result"
        }
        assertEquals(
            listOf("approvals_user_id_fkey"),
            (result as AccountDeletionPreflight.Result.BlockedForUser)
                .blocking.map { it.key.constraintName },
        )
        assertEquals(RowOperation.ROW_BLOCK, result.blocking.single().rowOperation)

        // 판정만 했을 뿐 아무것도 지우지 않았다.
        assertEquals(1, approvalCount())
    }

    @Test
    @DisplayName("검토자 삭제도 승인 행을 지우지 않는다")
    fun deletingReviewerMustNotDropApproval() {
        assertBlockedByForeignKey(REVIEWER)
        assertEquals(1, approvalCount())
    }

    @Test
    @DisplayName("요청자 삭제도 승인 행을 지우지 않는다")
    fun deletingRequesterMustNotDropApproval() {
        assertBlockedByForeignKey(REQUESTER)
        assertEquals(1, approvalCount())
    }
}
