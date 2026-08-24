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
@Import(AccountDeletionDataAdapter::class, UserObjectSnapshotAdapter::class)
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

        data.snapshotObjectsAndDeleteUserData(
            jobId = jobId,
            userId = uid,
            policies = UserFkPolicyRegistry.deletable(),
        )

        assertEquals(0, dsl.fetchOne("SELECT count(*) FROM users WHERE id = ?", uid)!!.get(0, Int::class.java))
        assertEquals(0, count("goals", uid))
        assertEquals(0, count("ai_credits", uid))
        assertEquals(0, count("subscriptions", uid))
        assertEquals(0, count("user_settings", uid))
        // DB 단계가 끝났을 뿐 완료가 아니다. 외부 객체 정리가 남아 있고, 그건 이 트랜잭션이
        // 커밋된 뒤에만 안전하다. 여기서 COMPLETED 면 버킷에 남은 파일을 "지웠다"고 기록하게 된다.
        assertEquals(
            AccountDeletionStatus.EXTERNAL_CLEANUP_PENDING,
            jobs.findByIdempotencyKey("adapter-complete-test")!!.status,
        )
    }

    // ---- 객체 정리 원장 스냅샷 ----

    private fun insertVideo(uid: Long, title: String, fileUrl: String?, key: String?): Long =
        dsl.fetchOne(
            """
            INSERT INTO videos (user_id, title, file_url, storage_object_key, status)
            VALUES (?, ?, ?, ?, 'DRAFT')
            RETURNING id
            """.trimIndent(),
            uid, title, fileUrl, key,
        )!!.get(0, Long::class.java)

    private fun newJob(uid: Long, key: String): Long =
        dsl.fetchOne(
            """
            INSERT INTO account_deletion_jobs (user_id, status, idempotency_key)
            VALUES (?, 'IN_PROGRESS', ?)
            RETURNING id
            """.trimIndent(),
            uid, key,
        )!!.get(0, Long::class.java)

    private fun ledgerKeys(jobId: Long): List<String> =
        dsl.fetch("SELECT object_key FROM account_deletion_object_tasks WHERE job_id = ? ORDER BY object_key", jobId)
            .map { it.get(0, String::class.java) }

    /*
     * 원장에는 **저장된 정확한 키만** 들어가야 한다. URL 을 파싱해 추측한 값이 섞이면
     * 남의 파일을 지울 위험이 생긴다.
     */
    @Test
    @DisplayName("스냅샷은 저장된 exact object key 만 원장에 적는다")
    fun snapshotWritesExactKeysOnly() {
        val uid = userId()
        insertVideo(uid, "직접 업로드", "https://storage/x?sig=1", "videos/$uid/a.mp4")
        insertVideo(uid, "쇼츠 렌더", "https://storage/y?sig=1", "shorts/run-9/clip-1-123.mp4")
        val jobId = newJob(uid, "adapter-snapshot-exact")

        data.snapshotObjectsAndDeleteUserData(jobId, uid, UserFkPolicyRegistry.deletable())

        assertEquals(listOf("shorts/run-9/clip-1-123.mp4", "videos/$uid/a.mp4"), ledgerKeys(jobId))
    }

    /*
     * 외부 URL 로 만든 행은 우리 객체가 아니다. 키 컬럼이 비어 있는 것이 곧 그 증거이고,
     * 그런 행은 원장에 들어가지 않는다 — 남의 스토리지를 지우려 들면 안 된다.
     */
    @Test
    @DisplayName("키가 없는 외부 URL 행은 원장에서 제외되고 job 은 완료되지 않는다")
    fun snapshotSkipsExternalUrlRows() {
        val uid = userId()
        insertVideo(uid, "외부 URL", "https://cdn.example.com/foreign.mp4", null)
        val jobId = newJob(uid, "adapter-snapshot-external")

        val snapshot = data.snapshotObjectsAndDeleteUserData(jobId, uid, UserFkPolicyRegistry.deletable())

        assertEquals(emptyList<String>(), ledgerKeys(jobId))
        // 지울 수 없는 행이 있었다는 사실이 남아야 거짓 완료를 막는다.
        assertEquals(1, snapshot.unresolvedRowCount)
        assertEquals(
            1,
            dsl.fetchOne("SELECT unresolved_object_rows FROM account_deletion_jobs WHERE id = ?", jobId)!!
                .get(0, Int::class.java),
        )
    }

    /*
     * 반복 예약은 객체를 복제해 쓰므로 같은 키를 두 사용자가 가리킬 수 있다.
     * 탈퇴자 쪽만 보고 지우면 남은 사용자의 영상이 사라진다.
     */
    @Test
    @DisplayName("다른 살아있는 사용자가 참조하는 키는 원장에서 제외된다")
    fun snapshotSkipsKeysSharedWithAnotherUser() {
        val uid = userId()
        val sharedKey = "videos/shared/copy.mp4"
        insertVideo(uid, "공유본", "https://storage/s?sig=1", sharedKey)
        insertVideo(uid, "내 것", "https://storage/m?sig=1", "videos/$uid/mine.mp4")

        dsl.execute(
            """
            INSERT INTO users (email, name, provider, provider_id, role, plan_type)
            VALUES ('deletion-other@test.io', 'other', 'GOOGLE', 'deletion-other', 'USER', 'FREE')
            """.trimIndent(),
        )
        val otherId = dsl.fetchOne("SELECT id FROM users WHERE email = 'deletion-other@test.io'")!!
            .get(0, Long::class.java)
        insertVideo(otherId, "남의 참조", "https://storage/s?sig=2", sharedKey)

        val jobId = newJob(uid, "adapter-snapshot-shared")
        val snapshot = data.snapshotObjectsAndDeleteUserData(jobId, uid, UserFkPolicyRegistry.deletable())

        assertEquals(listOf("videos/$uid/mine.mp4"), ledgerKeys(jobId))
        assertEquals(1, snapshot.sharedKeyCount)

        dsl.execute("DELETE FROM videos WHERE user_id = ?", otherId)
        dsl.execute("DELETE FROM users WHERE id = ?", otherId)
    }

    /*
     * claimNext 는 job 을 집으면서 IN_PROGRESS 로 바꾼다. 재시도 예약이 상태를 되돌리지
     * 않으면 다음 claim 이 IN_PROGRESS stale(30분) 분기로 빠져 backoff 가 무의미해진다.
     * 실제 DB 의 where/status 로 그 전이를 확인한다.
     */
    @Test
    @DisplayName("재시도 예약은 상태를 EXTERNAL_CLEANUP_PENDING 으로 되돌리고 다음 tick 이 집는다")
    fun cleanupRetryRestoresPendingStatusAndIsClaimable() {
        val uid = userId()
        val jobId = newJob(uid, "adapter-retry-transition")

        val past = java.time.LocalDateTime.now().minusSeconds(1)
        jobs.scheduleCleanupRetry(jobId, past)

        assertEquals(
            AccountDeletionStatus.EXTERNAL_CLEANUP_PENDING.name,
            dsl.fetchOne("SELECT status::text FROM account_deletion_jobs WHERE id = ?", jobId)!!
                .get(0, String::class.java),
        )

        // next_attempt_at 이 지났으므로 stale(30분)을 기다리지 않고 바로 집혀야 한다.
        val now = java.time.LocalDateTime.now()
        val claimed = jobs.claimNext(now, now.minusMinutes(30))
        assertEquals(jobId, claimed?.id)
    }
}
