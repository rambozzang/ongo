package com.ongo.infrastructure.accountdeletion

import com.ongo.domain.accountdeletion.AccountDeletionDataPort
import com.ongo.domain.accountdeletion.AccountDeletionJobRepository
import com.ongo.domain.accountdeletion.AccountDeletionStatus
import com.ongo.domain.accountdeletion.UserFkPolicyRegistry
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * 정책 승인된 무료 계정의 삭제가 실제 PostgreSQL에서 자식 row와 users를 함께 지우고,
 * 감사 job을 COMPLETED로 남기는지 확인한다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Import(AccountDeletionDataAdapter::class)
class AccountDeletionDataAdapterIT {

    @Autowired lateinit var dsl: DSLContext
    @Autowired lateinit var data: AccountDeletionDataPort
    @Autowired lateinit var jobs: AccountDeletionJobRepository

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

        private const val EMAIL = "deletion-complete@test.io"
    }

    private fun userId(): Long =
        dsl.fetchOne("SELECT id FROM users WHERE email = ?", EMAIL)!!.get(0, Long::class.java)

    private fun count(table: String, userId: Long): Int =
        dsl.fetchOne("SELECT count(*) FROM $table WHERE user_id = ?", userId)!!.get(0, Int::class.java)

    @BeforeEach
    fun setUp() {
        dsl.execute(
            "DELETE FROM account_deletion_jobs WHERE user_id IN (SELECT id FROM users WHERE email = ?)",
            EMAIL,
        )
        dsl.execute("DELETE FROM users WHERE email = ?", EMAIL)
        dsl.execute(
            """
            INSERT INTO users (email, name, provider, provider_id, role, plan_type)
            VALUES (?, 'deletable', 'GOOGLE', 'deletion-complete', 'USER', 'FREE')
            """.trimIndent(),
            EMAIL,
        )
        val uid = userId()
        dsl.execute("INSERT INTO ai_credits (user_id, free_reset_date) VALUES (?, CURRENT_DATE)", uid)
        dsl.execute("INSERT INTO subscriptions (user_id, plan_type, status) VALUES (?, 'FREE', 'FREE')", uid)
        dsl.execute("INSERT INTO user_settings (user_id) VALUES (?)", uid)
        dsl.execute(
            """
            INSERT INTO goals (user_id, title, metric_type, target_value, start_date, end_date)
            VALUES (?, 'delete me', 'VIEWS', 100, CURRENT_DATE, CURRENT_DATE + 7)
            """.trimIndent(),
            uid,
        )
    }

    @Test
    @DisplayName("정책 승인된 무료 계정은 자식 row·users 삭제와 job 완료가 하나의 경로로 처리된다")
    fun deletesOwnedRowsAndCompletesJob() {
        val uid = userId()
        val jobId = dsl.fetchOne(
            """
            INSERT INTO account_deletion_jobs (user_id, status, idempotency_key)
            VALUES (?, 'IN_PROGRESS', 'adapter-complete-test')
            RETURNING id
            """.trimIndent(),
            uid,
        )!!.get(0, Long::class.java)

        data.deleteUserDataAndComplete(
            jobId = jobId,
            userId = uid,
            policies = UserFkPolicyRegistry.deletable(),
        )

        assertEquals(0, dsl.fetchOne("SELECT count(*) FROM users WHERE id = ?", uid)!!.get(0, Int::class.java))
        assertEquals(0, count("goals", uid))
        assertEquals(0, count("ai_credits", uid))
        assertEquals(0, count("subscriptions", uid))
        assertEquals(0, count("user_settings", uid))
        assertEquals(AccountDeletionStatus.COMPLETED, jobs.findByIdempotencyKey("adapter-complete-test")!!.status)
    }
}
