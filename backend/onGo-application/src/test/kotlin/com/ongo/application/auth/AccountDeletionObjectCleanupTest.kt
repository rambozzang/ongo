package com.ongo.application.auth

import com.ongo.application.common.FileStoragePort
import com.ongo.domain.accountdeletion.AccountDeletionDataPort
import com.ongo.domain.accountdeletion.AccountDeletionJobRepository
import com.ongo.domain.accountdeletion.AccountDeletionObjectTask
import com.ongo.domain.accountdeletion.AccountDeletionObjectTaskRepository
import com.ongo.domain.accountdeletion.UserFkScanner
import com.ongo.domain.subscription.SubscriptionRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 탈퇴 객체 정리.
 *
 * 지켜야 할 성질은 둘이다.
 * - **거짓 완료 금지**: 실제로 안 지워진 게 하나라도 있으면 COMPLETED 로 올리지 않는다.
 *   버킷에는 남았는데 "다 지웠다"고 기록하는 것이 개인정보 관점에서 가장 나쁘다.
 * - **재개 가능**: 실패·중단은 원장에 남아 다음 tick 이 이어서 처리한다.
 */
class AccountDeletionObjectCleanupTest {

    private val jobs = mockk<AccountDeletionJobRepository>(relaxed = true)
    private val scanner = mockk<UserFkScanner>(relaxed = true)
    private val deletionData = mockk<AccountDeletionDataPort>(relaxed = true)
    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val objectTasks = mockk<AccountDeletionObjectTaskRepository>(relaxed = true)
    private val fileStoragePort = mockk<FileStoragePort>(relaxed = true)

    private lateinit var processor: AccountDeletionJobProcessor

    private val jobId = 42L

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        processor = AccountDeletionJobProcessor(
            jobs, scanner, deletionData, subscriptionRepository, objectTasks, fileStoragePort,
        )
    }

    private fun task(id: Long, key: String) = AccountDeletionObjectTask(id = id, jobId = jobId, objectKey = key)

    @Test
    fun `모든 객체가 지워지면 job 을 완료로 올린다`() {
        every { objectTasks.findPending(jobId, any()) } returns
            listOf(task(1L, "videos/1/a.mp4"), task(2L, "shorts/run-3/clip-4-9.mp4"))
        every { objectTasks.countUnfinished(jobId) } returns 0

        processor.cleanupObjects(jobId)

        verify(exactly = 1) { fileStoragePort.deleteByKey("videos/1/a.mp4") }
        verify(exactly = 1) { fileStoragePort.deleteByKey("shorts/run-3/clip-4-9.mp4") }
        verify(exactly = 1) { objectTasks.markDone(1L) }
        verify(exactly = 1) { objectTasks.markDone(2L) }
        verify(exactly = 1) { jobs.markCompleted(jobId, any()) }
    }

    /*
     * 하나가 실패하면 나머지는 계속 진행하되 job 은 완료시키지 않는다. 실패 건은 PENDING 으로
     * 남아 다음 tick 이 다시 집는다 — 여기서 완료시키면 남은 파일이 영원히 방치된다.
     */
    @Test
    fun `한 건이 실패하면 완료시키지 않고 나머지는 계속 처리한다`() {
        every { objectTasks.findPending(jobId, any()) } returns
            listOf(task(1L, "videos/1/a.mp4"), task(2L, "videos/1/b.mp4"))
        every { fileStoragePort.deleteByKey("videos/1/a.mp4") } throws IllegalStateException("스토리지 장애")
        every { objectTasks.countUnfinished(jobId) } returns 1

        processor.cleanupObjects(jobId)

        verify(exactly = 1) { objectTasks.markAttemptFailed(1L, any()) }
        verify(exactly = 0) { objectTasks.markDone(1L) }
        // 실패해도 뒤 건은 처리된다.
        verify(exactly = 1) { objectTasks.markDone(2L) }
        verify(exactly = 0) { jobs.markCompleted(any(), any()) }
    }

    @Test
    fun `다음 tick 이 남은 건을 이어서 처리하고 그때 완료된다`() {
        // 1차: 실패로 하나 남음
        every { objectTasks.findPending(jobId, any()) } returns listOf(task(1L, "videos/1/a.mp4"))
        every { fileStoragePort.deleteByKey("videos/1/a.mp4") } throws IllegalStateException("일시 장애")
        every { objectTasks.countUnfinished(jobId) } returns 1
        processor.cleanupObjects(jobId)
        verify(exactly = 0) { jobs.markCompleted(any(), any()) }

        // 2차: 같은 건이 PENDING 으로 남아 다시 집히고 이번엔 성공
        clearMocks(fileStoragePort, jobs, answers = false)
        every { objectTasks.findPending(jobId, any()) } returns listOf(task(1L, "videos/1/a.mp4"))
        every { fileStoragePort.deleteByKey(any()) } just runs
        every { objectTasks.countUnfinished(jobId) } returns 0

        processor.cleanupObjects(jobId)

        verify(exactly = 1) { jobs.markCompleted(jobId, any()) }
    }

    /*
     * V96 이전 행은 객체 키가 없다. URL 로 추측해 지우면 남의 파일을 지울 위험이 있어 하지 않고,
     * 그렇다고 완료시키면 버킷에 남은 파일을 "지웠다"고 기록하게 된다. 사람이 보게 남긴다.
     */
    @Test
    fun `키를 확정할 수 없는 legacy 행이 있으면 완료 대신 차단 상태로 남긴다`() {
        every { objectTasks.findPending(jobId, any()) } returns emptyList()
        every { objectTasks.countUnfinished(jobId) } returns 0

        processor.cleanupObjects(jobId, unresolvedRowCount = 3)

        verify(exactly = 0) { jobs.markCompleted(any(), any()) }
        verify(exactly = 1) { jobs.markBlocked(jobId, "ACCOUNT_DELETION_OBJECT_KEY_UNRESOLVED", any()) }
        // 추측 삭제 금지 — 지울 키가 원장에 없으므로 아무 것도 지우지 않는다.
        verify(exactly = 0) { fileStoragePort.deleteByKey(any()) }
    }

    @Test
    fun `BLOCKED 건이 남아 있으면 완료시키지 않는다`() {
        every { objectTasks.findPending(jobId, any()) } returns emptyList()
        // countUnfinished 는 PENDING 과 BLOCKED 를 함께 센다.
        every { objectTasks.countUnfinished(jobId) } returns 1

        processor.cleanupObjects(jobId)

        verify(exactly = 0) { jobs.markCompleted(any(), any()) }
    }

    @Test
    fun `지울 객체가 없으면 바로 완료된다`() {
        every { objectTasks.findPending(jobId, any()) } returns emptyList()
        every { objectTasks.countUnfinished(jobId) } returns 0

        processor.cleanupObjects(jobId)

        verify(exactly = 0) { fileStoragePort.deleteByKey(any()) }
        verify(exactly = 1) { jobs.markCompleted(jobId, any()) }
    }

    // ---- 크래시/재시도 복구 ----

    /*
     * DB 커밋 뒤 다시 집힌 job 은 삭제를 다시 하면 안 된다. 사용자 행은 이미 사라졌고,
     * preflight 를 다시 돌리면 스캔·정책 판정이 엉뚱한 결과를 낸다. 남은 일은 원장의 객체뿐이다.
     */
    @Test
    fun `dbCommittedAt 이 있으면 DB 삭제를 다시 하지 않고 정리 대상만 돌려준다`() {
        val job = com.ongo.domain.accountdeletion.AccountDeletionJob(
            id = jobId,
            userId = 7L,
            idempotencyKey = "resume",
            status = com.ongo.domain.accountdeletion.AccountDeletionStatus.EXTERNAL_CLEANUP_PENDING,
            dbCommittedAt = java.time.LocalDateTime.now().minusMinutes(5),
            unresolvedObjectRows = 2,
        )

        val pending = processor.process(job)

        // 삭제도 preflight 도 다시 하지 않는다.
        verify(exactly = 0) { deletionData.snapshotObjectsAndDeleteUserData(any(), any(), any()) }
        verify(exactly = 0) { scanner.actualUserFks() }
        kotlin.test.assertEquals(jobId, pending?.jobId)
        // 크래시 전에 남긴 미확정 건수가 살아남아야 거짓 완료를 막는다.
        kotlin.test.assertEquals(2, pending?.unresolvedRowCount)
    }

    /*
     * 커밋 직후 죽어 unresolved 가 메모리에서 사라진 상황. 재개된 tick 이 DB 에 남은 값을
     * 읽어 오지 못하면 "원장에 남은 일 없음"만 보고 완료로 올린다 — 실제로는 파일이 남았는데.
     */
    @Test
    fun `크래시로 재개돼도 영속된 미확정 건수가 거짓 완료를 막는다`() {
        val job = com.ongo.domain.accountdeletion.AccountDeletionJob(
            id = jobId,
            userId = 7L,
            idempotencyKey = "crash",
            status = com.ongo.domain.accountdeletion.AccountDeletionStatus.EXTERNAL_CLEANUP_PENDING,
            dbCommittedAt = java.time.LocalDateTime.now().minusMinutes(5),
            unresolvedObjectRows = 1,
        )
        every { objectTasks.findPending(jobId, any()) } returns emptyList()
        every { objectTasks.countUnfinished(jobId) } returns 0

        val pending = processor.process(job)!!
        processor.cleanupObjects(pending.jobId, pending.unresolvedRowCount)

        verify(exactly = 0) { jobs.markCompleted(any(), any()) }
        verify(exactly = 1) { jobs.markBlocked(jobId, "ACCOUNT_DELETION_OBJECT_KEY_UNRESOLVED", any()) }
    }

    // ---- 재시도 간격 ----

    /*
     * 실패한 정리는 **다음 tick** 에 다시 집혀야 한다. stale 조건(30분)에 기대면 파일이
     * 그만큼 오래 남는다. 첫 실패의 backoff 는 tick 주기 수준이어야 한다.
     */
    @Test
    fun `정리가 남으면 다음 tick 수준의 짧은 간격으로 재시도를 예약한다`() {
        every { objectTasks.findPending(jobId, any()) } returns emptyList()
        every { objectTasks.countUnfinished(jobId) } returns 1

        processor.cleanupObjects(jobId, attempt = 0)

        verify(exactly = 1) { jobs.scheduleCleanupRetry(eq(jobId), any()) }
        kotlin.test.assertEquals(15L, AccountDeletionJobProcessor.cleanupBackoff(0))
    }

    /*
     * 계속 실패하는 job 하나가 매 tick 을 독차지하면 다른 사용자의 탈퇴가 밀린다.
     * claimNext 는 한 번에 job 하나만 집기 때문이다. 시도가 쌓일수록 간격을 벌린다.
     */
    @Test
    fun `반복 실패는 간격을 벌려 다른 job 의 기아를 막는다`() {
        kotlin.test.assertTrue(AccountDeletionJobProcessor.cleanupBackoff(3) > AccountDeletionJobProcessor.cleanupBackoff(0))
        kotlin.test.assertEquals(600L, AccountDeletionJobProcessor.cleanupBackoff(20), "상한이 있어야 영구 방치되지 않는다")
    }

    @Test
    fun `성공적으로 끝나면 재시도를 예약하지 않는다`() {
        every { objectTasks.findPending(jobId, any()) } returns emptyList()
        every { objectTasks.countUnfinished(jobId) } returns 0

        processor.cleanupObjects(jobId)

        verify(exactly = 0) { jobs.scheduleCleanupRetry(any(), any()) }
        verify(exactly = 1) { jobs.markCompleted(jobId, any()) }
    }

    /*
     * claimNext 는 job 을 집으면서 IN_PROGRESS 로 바꾼다. 재시도 예약이 상태를 되돌리지
     * 않으면 다음 claim 이 EXTERNAL_CLEANUP_PENDING 분기가 아니라 IN_PROGRESS stale(30분)
     * 분기로 빠져 backoff 가 통째로 무의미해진다. 그래서 한 번의 호출로 둘 다 되돌린다.
     */
    @Test
    fun `재시도 예약은 상태를 EXTERNAL_CLEANUP_PENDING 으로 되돌리는 단일 호출이어야 한다`() {
        every { objectTasks.findPending(jobId, any()) } returns emptyList()
        every { objectTasks.countUnfinished(jobId) } returns 1

        processor.cleanupObjects(jobId, attempt = 0)

        // 상태 복귀와 시각 갱신이 갈라지면 원자성이 깨진다. 호출은 하나여야 한다.
        verify(exactly = 1) { jobs.scheduleCleanupRetry(eq(jobId), any()) }
        // 이 경로에서 다른 상태 전이가 끼어들면 안 된다.
        verify(exactly = 0) { jobs.markCompleted(any(), any()) }
        verify(exactly = 0) { jobs.markFailed(any(), any(), any()) }
        verify(exactly = 0) { jobs.markBlocked(any(), any(), any()) }
    }

    // ---- 단계별 예외 분리 (worker 레벨) ----

    private fun worker() = AccountDeletionWorker(jobs, processor)

    private fun pendingJob() = com.ongo.domain.accountdeletion.AccountDeletionJob(
        id = jobId,
        userId = 7L,
        idempotencyKey = "stage",
        status = com.ongo.domain.accountdeletion.AccountDeletionStatus.EXTERNAL_CLEANUP_PENDING,
        dbCommittedAt = java.time.LocalDateTime.now().minusMinutes(5),
    )

    /*
     * 외부 정리 중 터진 예외를 markFailed 로 보내면 안 된다. DB 삭제는 이미 커밋됐는데
     * 운영자의 retry 가 dbCommittedAt 을 지워 DB 단계를 처음부터 다시 돌리기 때문이다.
     * 사용자 행은 이미 없으므로 그 재실행은 엉뚱한 판정을 내고 원장을 잃을 수 있다.
     */
    @Test
    fun `정리 단계의 예상 밖 예외는 실패가 아니라 재시도로 되돌린다`() {
        every { jobs.claimNext(any(), any()) } returns pendingJob()
        // countUnfinished 같은 DB 오류를 흉내낸다.
        every { objectTasks.findPending(jobId, any()) } returns emptyList()
        every { objectTasks.countUnfinished(jobId) } throws IllegalStateException("DB 장애")

        worker().processNext()

        verify(exactly = 1) { jobs.scheduleCleanupRetry(eq(jobId), any()) }
        // 이게 불리면 retry 가 dbCommittedAt 을 지워 DB 단계가 재실행된다.
        verify(exactly = 0) { jobs.markFailed(any(), any(), any()) }
        verify(exactly = 0) { jobs.markCompleted(any(), any()) }
    }

    /*
     * 반대로 DB 단계의 예외는 롤백됐으므로 실패로 남겨 재요청할 수 있어야 한다.
     * 이걸 재시도로 처리하면 아직 아무것도 안 지운 job 이 정리 대기 상태로 잘못 남는다.
     */
    @Test
    fun `DB 단계의 예외만 실패로 기록한다`() {
        val fresh = com.ongo.domain.accountdeletion.AccountDeletionJob(
            id = jobId, userId = 7L, idempotencyKey = "fresh",
            status = com.ongo.domain.accountdeletion.AccountDeletionStatus.IN_PROGRESS,
        )
        every { jobs.claimNext(any(), any()) } returns fresh
        every { scanner.actualUserFks() } throws IllegalStateException("스캔 실패")

        worker().processNext()

        verify(exactly = 1) { jobs.markFailed(eq(jobId), any(), any()) }
        verify(exactly = 0) { jobs.scheduleCleanupRetry(any(), any()) }
    }
}
