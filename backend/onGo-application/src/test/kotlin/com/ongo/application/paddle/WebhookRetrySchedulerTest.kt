package com.ongo.application.paddle

import com.ongo.application.webhook.WebhookRetryRunner
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 방치된 `PENDING` 웹훅이 재시도 대기열로 돌아오는지, 그리고 기존 `FAILED` 처리가 그대로인지.
 *
 * 수신 기록은 업무 트랜잭션과 분리해 먼저 커밋된다([WebhookEventRecorder]). 그 덕에 실패해도
 * 흔적이 남지만, 커밋 직후 프로세스가 죽거나 `catch (e: Exception)` 이 못 잡는 `Error` 가 나면
 * FAILED 로 옮기는 코드 자체가 실행되지 않는다. 그렇게 남은 `PENDING` 행은
 * `findRetryable` 의 `status = 'FAILED'` 조건에 걸리지 않아 **영원히 재처리되지 않는다.**
 */
class WebhookRetrySchedulerTest {

    private val repository = mockk<WebhookEventRepository>()
    private val paddleWebhookService = mockk<PaddleWebhookService>()
    private val lockPort = mockk<DistributedLockPort>()

    /*
     * 재시도 상태 기계는 포트원과 공유하는 [WebhookRetryRunner] 에 있다. 스케줄러가 무엇을
     * 소유하고 어떻게 재처리하는지만 정하므로, 러너는 실물을 쓰고 그 아래 저장소·락만 목으로
     * 둔다. 이렇게 해야 아래 단언들이 실제로 발행되는 호출을 검증한다.
     */
    private val scheduler = WebhookRetryScheduler(
        WebhookRetryRunner(repository, lockPort),
        paddleWebhookService,
    )

    /** 락을 잡은 것처럼 블록을 그대로 실행한다. */
    private fun lockAcquired() {
        every { lockPort.withLock(any(), any()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }
    }

    private fun failedEvent(retryCount: Int = 0, maxRetries: Int = 5) = WebhookEvent(
        id = 7L,
        eventId = "evt_1",
        eventType = "transaction.completed",
        payload = """{"event_type":"transaction.completed","data":{"id":"txn_1"}}""",
        status = "FAILED",
        retryCount = retryCount,
        maxRetries = maxRetries,
        nextRetryAt = LocalDateTime.now().minusMinutes(1),
    )

    private fun noRecovery() {
        every { repository.recoverStalePending(any(), any(), any(), any()) } returns 0
    }

    @Test
    @DisplayName("방치된 PENDING 을 먼저 되살린 뒤 재시도 대상을 조회한다")
    fun recoversStalePendingBeforeQueryingRetryables() {
        lockAcquired()
        noRecovery()
        every { repository.findRetryable(any(), any()) } returns emptyList()

        scheduler.retryFailedWebhooks()

        // 되살리기가 조회보다 늦으면 되살린 행이 그 실행에서 한 번 더 방치된다.
        verifyOrder {
            repository.recoverStalePending(any(), any(), any(), any())
            repository.findRetryable(any(), any())
        }
    }

    @Test
    @DisplayName("재시도 조회에도 Paddle 소유 타입만 넘긴다 — 포트원 FAILED 행이 딸려오면 안 된다")
    fun retryQueryIsScopedToPaddleOwnedTypes() {
        lockAcquired()
        noRecovery()
        val types = slot<Set<String>>()
        every { repository.findRetryable(any(), capture(types)) } returns emptyList()

        scheduler.retryFailedWebhooks()

        assertEquals(PaddleWebhookService.REPROCESSABLE_EVENT_TYPES, types.captured)
        PORTONE_EVENT_TYPES.forEach {
            assertTrue(it !in types.captured, "포트원 이벤트 타입 $it 이 재시도 조회 범위에 있다")
        }
    }

    @Test
    @DisplayName("처리 중인 정상 수신을 건드리지 않도록 충분한 나이 기준을 쓴다")
    fun usesGenerousAgeThreshold() {
        lockAcquired()
        val olderThan = slot<LocalDateTime>()
        val retryAt = slot<LocalDateTime>()
        val limit = slot<Int>()
        every {
            repository.recoverStalePending(capture(olderThan), capture(retryAt), any(), capture(limit))
        } returns 0
        every { repository.findRetryable(any(), any()) } returns emptyList()

        scheduler.retryFailedWebhooks()

        val age = Duration.between(olderThan.captured, retryAt.captured).toMinutes()
        // 웹훅 한 건 처리는 HTTP 요청 하나(초 단위)다. 기준이 짧으면 아직 처리 중인
        // 수신을 FAILED 로 뺏어와 같은 이벤트가 두 곳에서 동시에 처리된다.
        assertTrue(age >= 30, "나이 기준이 ${age}분뿐이다 — 처리 중인 수신을 가로챌 수 있다")
        assertTrue(limit.captured > 0, "상한이 0 이하면 아무것도 되살리지 못한다")
    }

    @Test
    @DisplayName("Paddle 재처리기가 다루는 타입만 되살린다 — 포트원 행은 제외")
    fun recoversOnlyPaddleReprocessableTypes() {
        lockAcquired()
        val types = slot<Set<String>>()
        every { repository.recoverStalePending(any(), any(), capture(types), any()) } returns 0
        every { repository.findRetryable(any(), any()) } returns emptyList()

        scheduler.retryFailedWebhooks()

        assertEquals(PaddleWebhookService.REPROCESSABLE_EVENT_TYPES, types.captured)
        // webhook_events 는 포트원과 같은 테이블이다. 포트원 행이 넘어오면 Paddle 페이로드로
        // 파싱되어 event_type 이 없다는 이유로 no-op 한 뒤 PROCESSED 로 찍힌다.
        PORTONE_EVENT_TYPES.forEach {
            assertTrue(it !in types.captured, "포트원 이벤트 타입 $it 을 되살리려 한다")
        }
    }

    /**
     * **소유권 격리가 무너졌을 때 무슨 일이 벌어지는지 고정한다.**
     *
     * 조회 조건이 상태만 보게 되돌아가면 포트원의 FAILED 행이 이 루프로 들어온다. 그때
     * 재처리기는 `event_type` 키가 없어 조용히 no-op 하고, 아래 루프는 그것을 성공으로 보고
     * **PROCESSED 로 찍는다.** 처리된 적 없는 결제 웹훅이 완료로 남고 포트원 재전송도
     * 멱등 게이트에 막힌다. 그래서 조회 범위에 포트원 타입이 절대 들어가면 안 된다.
     */
    @Test
    @DisplayName("포트원 FAILED 이벤트는 Paddle 재처리 대상이 될 수 없다")
    fun portOneFailedEventsAreNeverHandedToPaddle() {
        lockAcquired()
        noRecovery()
        val types = slot<Set<String>>()
        // 조회 범위 밖의 행은 애초에 돌아오지 않는다는 계약을 그대로 재현한다.
        every { repository.findRetryable(any(), capture(types)) } answers {
            listOf(portOneFailedEvent()).filter { it.eventType in types.captured }
        }

        scheduler.retryFailedWebhooks()

        verify(exactly = 0) { paddleWebhookService.reprocessWebhookEvent(any()) }
        // PROCESSED 오염이 없어야 한다 — 어떤 상태 갱신도 일어나면 안 된다.
        verify(exactly = 0) { repository.markProcessed(any(), any()) }
        verify(exactly = 0) { repository.updateIfNotProcessed(any()) }
    }

    /**
     * **인바운드 성공과 스케줄러 실패 기록의 경합.**
     *
     * 스케줄러가 재처리에 실패한 뒤 catch 로 들어가는 사이, 인바운드 재전달이 행 잠금을 얻어
     * 업무 처리를 끝내고 PROCESSED 를 커밋할 수 있다. 이때 스케줄러가 낡은 스냅샷으로
     * FAILED 를 무조건 덮으면 **이미 반영된 처리가 다시 재시도 대상이 된다** — 환불 크레딧
     * 회수처럼 멱등하지 않은 처리가 두 번 실행된다.
     *
     * 그래서 실패 기록은 PROCESSED 를 보존하는 조건부 갱신이어야 한다.
     */
    @Test
    @DisplayName("스케줄러 실패 기록은 인바운드가 먼저 완료한 행을 덮지 않는다")
    fun schedulerFailureNeverOverwritesInboundSuccess() {
        lockAcquired()
        noRecovery()
        val event = failedEvent(retryCount = 1)
        every { repository.findRetryable(any(), any()) } returns listOf(event)
        every { paddleWebhookService.reprocessWebhookEvent(event) } throws IllegalStateException("일시 장애")
        // 인바운드가 먼저 완료해 조건부 갱신이 0행을 반환하는 상황.
        val recorded = slot<WebhookEvent>()
        every { repository.updateIfNotProcessed(capture(recorded)) } returns false

        scheduler.retryFailedWebhooks()

        assertEquals("FAILED", recorded.captured.status)
        // 실패한 재처리를 완료로 표시하면 그 이벤트는 영영 반영되지 않는다.
        verify(exactly = 0) { repository.markProcessed(any(), any()) }
    }

    @Test
    @DisplayName("DEAD_LETTER 기록도 완료된 행을 덮지 않는다")
    fun deadLetterAlsoPreservesProcessed() {
        lockAcquired()
        noRecovery()
        val event = failedEvent(retryCount = 4, maxRetries = 5)
        every { repository.findRetryable(any(), any()) } returns listOf(event)
        every { paddleWebhookService.reprocessWebhookEvent(event) } throws IllegalStateException("계속 실패")
        every { repository.updateIfNotProcessed(any()) } returns false

        scheduler.retryFailedWebhooks()

        verify { repository.updateIfNotProcessed(any()) }
        verify(exactly = 0) { repository.markProcessed(any(), any()) }
    }

    private fun portOneFailedEvent() = WebhookEvent(
        id = 99L,
        eventId = "portone:webhook-1",
        eventType = "Transaction.Paid",
        payload = """{"type":"Transaction.Paid","data":{"paymentId":"pay_1"}}""",
        status = "FAILED",
        retryCount = 1,
        nextRetryAt = LocalDateTime.now().minusMinutes(1),
    )

    private companion object {
        val PORTONE_EVENT_TYPES =
            listOf("Transaction.Paid", "Transaction.Cancelled", "Transaction.PartialCancelled")
    }

    @Test
    @DisplayName("되살린 이벤트는 같은 실행에서 곧바로 재처리된다")
    fun recoveredEventIsReprocessedInTheSameRun() {
        lockAcquired()
        every { repository.recoverStalePending(any(), any(), any(), any()) } returns 1
        val event = failedEvent()
        every { repository.findRetryable(any(), any()) } returns listOf(event)
        every { paddleWebhookService.reprocessWebhookEvent(event) } returns Unit
        every { repository.markProcessed(any(), any()) } returns true

        scheduler.retryFailedWebhooks()

        verify { paddleWebhookService.reprocessWebhookEvent(event) }
        verify { repository.markProcessed("evt_1", any()) }
        // 성공했는데 실패 기록이 남으면 같은 이벤트가 한 번 더 재처리된다.
        verify(exactly = 0) { repository.updateIfNotProcessed(any()) }
    }

    @Test
    @DisplayName("락을 못 잡으면 되살리기도 하지 않는다 — 인스턴스 간 중복 실행 방지")
    fun skipsEverythingWithoutTheLock() {
        every { lockPort.withLock(any(), any()) } returns false

        scheduler.retryFailedWebhooks()

        verify(exactly = 0) { repository.recoverStalePending(any(), any(), any(), any()) }
        verify(exactly = 0) { repository.findRetryable(any(), any()) }
    }

    // ── 기존 FAILED 처리 비회귀 ───────────────────────────────────────────────

    @Test
    @DisplayName("재처리 실패는 retryCount 를 올리고 백오프를 남긴다")
    fun failureKeepsRetryState() {
        lockAcquired()
        noRecovery()
        val event = failedEvent(retryCount = 2)
        every { repository.findRetryable(any(), any()) } returns listOf(event)
        every { paddleWebhookService.reprocessWebhookEvent(event) } throws IllegalStateException("일시 장애")
        val saved = slot<WebhookEvent>()
        every { repository.updateIfNotProcessed(capture(saved)) } returns true

        scheduler.retryFailedWebhooks()

        assertEquals("FAILED", saved.captured.status)
        assertEquals(3, saved.captured.retryCount)
        assertNotNull(saved.captured.nextRetryAt, "다음 재시도 시각이 없으면 findRetryable 이 못 잡는다")
    }

    @Test
    @DisplayName("최대 재시도를 넘기면 DEAD_LETTER 로 끝낸다 — 무한 재시도 금지")
    fun exhaustedRetriesGoToDeadLetter() {
        lockAcquired()
        noRecovery()
        val event = failedEvent(retryCount = 4, maxRetries = 5)
        every { repository.findRetryable(any(), any()) } returns listOf(event)
        every { paddleWebhookService.reprocessWebhookEvent(event) } throws IllegalStateException("계속 실패")
        val saved = slot<WebhookEvent>()
        every { repository.updateIfNotProcessed(capture(saved)) } returns true

        scheduler.retryFailedWebhooks()

        assertEquals("DEAD_LETTER", saved.captured.status)
        assertEquals(5, saved.captured.retryCount)
    }
}
