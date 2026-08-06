package com.ongo.infrastructure.accountdeletion

import com.ongo.domain.accountdeletion.AccountDeletionJobRepository
import com.ongo.domain.accountdeletion.AccountDeletionState
import com.ongo.domain.accountdeletion.AccountDeletionStatus
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 삭제 요청이 **원자적**이고 **동시 요청에 하나만 성공**하는지 실제 PostgreSQL 로 고정한다.
 *
 * 요청 row 생성과 사용자 게이트 전환이 나뉘면 두 가지 나쁜 상태가 생긴다.
 * - 게이트는 켜졌는데 job 이 없다 → 사용자가 영원히 아무것도 못 쓴다
 * - job 은 있는데 쓰기가 계속 허용된다 → 삭제 도중에 새 데이터가 들어온다
 *
 * 둘 다 사후 복구가 어렵다. 그래서 한 트랜잭션 + 사용자 행 잠금으로 묶는다.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AccountDeletionRequestIT {

    @Autowired lateinit var repo: AccountDeletionJobRepository
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

        private const val EMAIL = "deletion-request@test.io"
        private const val WORKERS = 6
    }

    private fun userId(): Long =
        dsl.fetchOne("SELECT id FROM users WHERE email = ?", EMAIL)!!.get(0, Long::class.java)

    private fun jobCount(uid: Long): Int =
        dsl.fetchOne("SELECT count(*) FROM account_deletion_jobs WHERE user_id = ?", uid)!!
            .get(0, Int::class.java)

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
            VALUES (?, 'del', 'GOOGLE', 'del-req', 'USER', 'FREE')
            """.trimIndent(),
            EMAIL,
        )
    }

    @Test
    @DisplayName("요청하면 job 생성과 게이트 전환이 함께 일어난다")
    fun requestCreatesJobAndFlipsGateTogether() {
        val uid = userId()
        assertEquals(AccountDeletionState.ACTIVE, repo.findDeletionState(uid)) { "사전 조건" }

        val job = repo.requestDeletion(uid, "key-atomic-1")

        assertEquals(AccountDeletionStatus.REQUESTED, job.status)
        assertNotNull(job.id)
        assertNotNull(job.requestedAt)
        assertEquals(AccountDeletionState.DELETION_REQUESTED, repo.findDeletionState(uid)) {
            "job 은 만들어졌는데 게이트가 안 켜졌다. 삭제 도중 새 데이터가 들어올 수 있다"
        }
        assertEquals(1, jobCount(uid))
    }

    @Test
    @DisplayName("같은 사용자가 다시 요청해도 job 이 늘지 않고 기존 것을 돌려준다")
    fun repeatedRequestIsIdempotent() {
        val uid = userId()

        val first = repo.requestDeletion(uid, "key-idem-1")
        val second = repo.requestDeletion(uid, "key-idem-2")

        // 두 번째는 새 키를 들고 왔지만 진행 중 job 이 있으므로 그것을 받는다.
        assertEquals(first.id, second.id) { "재요청이 새 job 을 만들었다" }
        assertEquals(1, jobCount(uid))
    }

    @Test
    @DisplayName("동시 요청 중 하나만 job 을 만든다")
    fun concurrentRequestsCreateExactlyOneJob() {
        val uid = userId()
        val pool = Executors.newFixedThreadPool(WORKERS)
        val start = CountDownLatch(1)

        try {
            val tasks = (1..WORKERS).map { i ->
                Callable {
                    start.await()
                    runCatching { repo.requestDeletion(uid, "key-concurrent-$i") }
                }
            }
            val futures = tasks.map { pool.submit(it) }
            start.countDown()
            val results = futures.map { it.get(30, TimeUnit.SECONDS) }

            // 부분 유일 인덱스가 중복 insert 를 막고, 행 잠금이 뒤에 온 요청을 기다리게 한다.
            // 예외로 실패한 요청이 있어도 job 은 하나여야 한다.
            assertEquals(1, jobCount(uid)) {
                "동시 요청에서 job 이 ${jobCount(uid)}건 생겼다"
            }

            val succeeded = results.filter { it.isSuccess }
            assertTrue(succeeded.isNotEmpty()) { "모든 동시 요청이 실패했다" }

            // 성공한 요청은 전부 같은 job 을 가리켜야 한다.
            val ids = succeeded.mapNotNull { it.getOrNull()?.id }.toSet()
            assertEquals(1, ids.size) { "성공한 요청들이 서로 다른 job 을 봤다: $ids" }

            assertEquals(AccountDeletionState.DELETION_REQUESTED, repo.findDeletionState(uid))
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    @DisplayName("정책으로 막힌 기록은 계정을 얼리지 않는다 — 게이트가 ACTIVE 로 남는다")
    fun blockedRecordNeverFreezesTheAccount() {
        val uid = userId()

        val job = repo.recordBlocked(
            userId = uid,
            idempotencyKey = "key-blocked-1",
            errorCode = "ACCOUNT_DELETION_BLOCKED_POLICY_REVIEW",
            supportReference = "review-block:competitors_user_id_fkey",
        )

        assertEquals(AccountDeletionStatus.BLOCKED_POLICY, job.status)

        // 여기서 게이트가 켜지면 정책 판단이 끝날 때까지 계정이 잠긴다.
        // 삭제를 못 하게 하는 것을 넘어 계정을 못 쓰게 만드는 것이라 훨씬 나쁘다.
        assertEquals(AccountDeletionState.ACTIVE, repo.findDeletionState(uid)) {
            "막힌 기록이 계정을 영구 동결시켰다"
        }
        assertEquals(null, repo.findActiveByUserId(uid)) { "종료 상태는 진행 중이 아니다" }
    }

    @Test
    @DisplayName("막힌 기록이 있어도 나중에 정상 요청할 수 있다")
    fun blockedRecordDoesNotPreventLaterRequest() {
        val uid = userId()
        repo.recordBlocked(uid, "key-blocked-2", "ACCOUNT_DELETION_BLOCKED_POLICY_REVIEW", null)

        // 정책이 정해지면 그대로 재요청할 수 있어야 한다.
        val job = repo.requestDeletion(uid, "key-after-block")

        assertEquals(AccountDeletionStatus.REQUESTED, job.status)
        assertEquals(AccountDeletionState.DELETION_REQUESTED, repo.findDeletionState(uid))
        assertEquals(2, jobCount(uid)) { "막힌 기록과 새 요청이 모두 남아야 한다" }
    }

    @Test
    @DisplayName("같은 키로 다시 기록해도 행이 늘지 않는다")
    fun blockedRecordIsIdempotentOnKey() {
        val uid = userId()
        val first = repo.recordBlocked(uid, "key-blocked-3", "CODE", null)
        val second = repo.recordBlocked(uid, "key-blocked-3", "CODE", null)

        assertEquals(first.id, second.id)
        assertEquals(1, jobCount(uid))
        assertEquals(AccountDeletionState.ACTIVE, repo.findDeletionState(uid))
    }

    @Test
    @DisplayName("게이트가 켜져 있으면 반드시 진행 중 job 이 있다")
    fun frozenGateAlwaysHasAnActiveJob() {
        val uid = userId()

        // 요청 전: 게이트 꺼짐 + 진행 중 job 없음
        assertEquals(AccountDeletionState.ACTIVE, repo.findDeletionState(uid))
        assertEquals(null, repo.findActiveByUserId(uid))

        repo.requestDeletion(uid, "key-invariant-1")

        // 요청 후: 게이트 켜짐 + 진행 중 job 있음. 둘은 항상 같이 움직여야 한다.
        // 한쪽만 켜진 상태가 생기면 사용자가 영원히 못 쓰거나 삭제 중 데이터가 유입된다.
        assertEquals(AccountDeletionState.DELETION_REQUESTED, repo.findDeletionState(uid))
        assertNotNull(repo.findActiveByUserId(uid))
    }

    @Test
    @DisplayName("진행 중 job 을 조회할 수 있고 종료되면 조회되지 않는다")
    fun activeJobLookupReflectsTerminalStatus() {
        val uid = userId()
        val job = repo.requestDeletion(uid, "key-active-1")

        assertEquals(job.id, repo.findActiveByUserId(uid)?.id)
        assertEquals(job.id, repo.findByIdempotencyKey("key-active-1")?.id)

        dsl.execute(
            "UPDATE account_deletion_jobs SET status = ? WHERE id = ?",
            AccountDeletionStatus.BLOCKED_POLICY.name, job.id,
        )

        // 종료 상태는 진행 중이 아니다. 그래야 재요청이 가능하다.
        assertEquals(null, repo.findActiveByUserId(uid))
        assertNotNull(repo.findByIdempotencyKey("key-active-1")) { "기록 자체는 남아야 한다" }
    }
}
