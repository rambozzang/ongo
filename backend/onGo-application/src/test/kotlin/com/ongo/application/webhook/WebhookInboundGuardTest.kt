package com.ongo.application.webhook

import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 인바운드 웹훅의 **트랜잭션 순서**를 고정한다.
 *
 * 원래 구조는 자기 자신과 교착했다. 바깥 트랜잭션이 `SELECT ... FOR UPDATE` 로 행 잠금을
 * 쥔 채, `catch` 에서 REQUIRES_NEW 로 같은 행을 갱신했다. REQUIRES_NEW 는 바깥을 커밋하지
 * 않고 **보류**하므로 잠금은 그대로 살아 있고, 새 트랜잭션은 그 잠금을 기다린다.
 *
 * PostgreSQL 은 이것을 교착으로 잡지 못한다. 교착 감지는 **잠금 대기 그래프**에서 순환을
 * 찾는데, 바깥 트랜잭션은 잠금이 아니라 애플리케이션 스레드를 기다리므로 그래프에 순환이
 * 없다. `lock_timeout` 이 없으면 무기한 대기한다.
 *
 * 목 기반 테스트는 실제 잠금을 재현하지 못한다. 대신 **트랜잭션 생명주기 순서**를 기록해
 * "실패 기록이 시작될 때 업무 트랜잭션이 이미 끝나 있는가"를 직접 단언한다. 그것이 참이면
 * 잠금은 이미 풀려 있고 교착은 성립할 수 없다.
 */
class WebhookInboundGuardTest {

    private val repository = mockk<WebhookEventRepository>()

    /** `begin(전파)` / `commit` / `rollback` 을 일어난 순서대로 기록한다. */
    private val timeline = mutableListOf<String>()

    private val recordingManager = object : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus {
            val propagation = when (definition?.propagationBehavior) {
                TransactionDefinition.PROPAGATION_REQUIRES_NEW -> "REQUIRES_NEW"
                else -> "REQUIRED"
            }
            timeline += "begin:$propagation"
            return SimpleTransactionStatus()
        }

        override fun commit(status: TransactionStatus) {
            timeline += "commit"
        }

        override fun rollback(status: TransactionStatus) {
            timeline += "rollback"
        }
    }

    private val guard = WebhookInboundGuard(
        repository,
        WebhookEventRecorder(repository, recordingManager),
        recordingManager,
    )

    private fun event(status: String = "PENDING", retryCount: Int = 0) = WebhookEvent(
        id = 1L,
        eventId = "evt_1",
        eventType = "transaction.completed",
        payload = "{}",
        status = status,
        retryCount = retryCount,
    )

    private fun firstDelivery() {
        every { repository.findByEventId("evt_1") } returns null
        every { repository.saveIfAbsent(any()) } returns true
        every { repository.findByEventIdForUpdate("evt_1") } returns event()
        every { repository.markProcessed(any(), any()) } returns true
        every { repository.updateIfNotProcessed(any()) } returns true
    }

    // ── 교착 회피: 순서 단언 ─────────────────────────────────────────────────

    /**
     * **이 테스트가 교착 수정의 핵심이다.**
     *
     * 실패 기록의 `begin:REQUIRES_NEW` 가 업무 트랜잭션의 `rollback` **뒤에** 와야 한다.
     * 앞에 오면 잠금을 쥔 채 같은 행을 기다리는 원래 구조다.
     */
    @Test
    @DisplayName("실패 기록은 업무 트랜잭션이 롤백된 뒤에 시작한다 — 행 잠금을 쥔 채 기다리지 않는다")
    fun failureRecordStartsAfterBusinessTransactionEnds() {
        firstDelivery()

        assertFailsWith<IllegalStateException> {
            guard.handle("evt_1", "transaction.completed", "{}") {
                throw IllegalStateException("업무 처리 실패")
            }
        }

        // 수신 기록 → 업무 시작·롤백 → 실패 기록. 셋 다 독립 경계여야 한다.
        assertEquals(
            listOf(
                "begin:REQUIRES_NEW", "commit",
                "begin:REQUIRES_NEW", "rollback",
                "begin:REQUIRES_NEW", "commit",
            ),
            timeline,
            "실패 기록이 업무 트랜잭션 안에서 시작하면 자기 잠금을 기다려 교착한다",
        )
    }

    /**
     * 업무 트랜잭션이 기본 전파(REQUIRED)면 호출자가 트랜잭션을 여는 순간 그것에 참여해
     * 경계가 바깥으로 나간다. 그러면 `catch` 시점에 행 잠금이 살아 있어 교착이 되살아난다.
     * 지금 호출자가 무트랜잭션인 것은 우연이지 보장이 아니다.
     */
    @Test
    @DisplayName("업무 트랜잭션은 REQUIRES_NEW 로 경계를 강제한다 — 바깥 트랜잭션에 참여하지 않는다")
    fun businessTransactionAlwaysGetsItsOwnBoundary() {
        firstDelivery()

        guard.handle("evt_1", "transaction.completed", "{}") { }

        val begins = timeline.filter { it.startsWith("begin:") }
        assertTrue(begins.isNotEmpty(), "트랜잭션을 열지 않았다: $timeline")
        assertTrue(
            begins.all { it == "begin:REQUIRES_NEW" },
            "REQUIRED 로 열린 트랜잭션이 있다 — 바깥에 참여하면 교착이 되살아난다: $timeline",
        )
    }

    @Test
    @DisplayName("실패 기록 시작 시점에 열려 있는 업무 트랜잭션이 없다")
    fun noBusinessTransactionIsOpenWhenFailureIsRecorded() {
        firstDelivery()

        assertFailsWith<IllegalStateException> {
            guard.handle("evt_1", "transaction.completed", "{}") { throw IllegalStateException("실패") }
        }

        // 마지막 REQUIRES_NEW 앞까지의 begin/commit·rollback 수가 같아야 열린 것이 없다.
        val beforeFailureRecord = timeline.dropLast(2)
        val opened = beforeFailureRecord.count { it.startsWith("begin:") }
        val closed = beforeFailureRecord.count { it == "commit" || it == "rollback" }
        assertEquals(opened, closed, "실패 기록 직전에 닫히지 않은 트랜잭션이 있다: $timeline")
    }

    @Test
    @DisplayName("업무 처리는 트랜잭션 안에서 실행된다 — 결제 행 잠금이 유지돼야 한다")
    fun dispatchRunsInsideATransaction() {
        firstDelivery()
        var openWhenDispatched = 0

        guard.handle("evt_1", "transaction.completed", "{}") {
            openWhenDispatched = timeline.count { it.startsWith("begin:") } -
                timeline.count { it == "commit" || it == "rollback" }
        }

        assertEquals(1, openWhenDispatched, "업무 처리 중 트랜잭션이 열려 있지 않다: $timeline")
    }

    // ── 재시도 예산 보존 ─────────────────────────────────────────────────────

    /**
     * `recordReceived` 는 삽입이 충돌해도 다시 읽지 않고 방금 만든 객체(`retryCount = 0`)를
     * 돌려준다. 그것으로 실패를 기록하면 DB 에 쌓인 재시도 횟수가 **1 로 초기화**되어
     * `retry_count < max_retries` 가 영원히 참이 된다 — DEAD_LETTER 에 도달하지 못하고
     * 같은 이벤트를 무한 재시도한다.
     */
    @Test
    @DisplayName("동시 전달이 기존 재시도 횟수를 초기화하지 않는다 — 잠금으로 읽은 행을 기준으로 센다")
    fun concurrentDeliveryDoesNotResetRetryCount() {
        // B 의 시선: 조회 시점에는 행이 없었고, 삽입은 충돌했다.
        every { repository.findByEventId("evt_1") } returns null
        every { repository.saveIfAbsent(any()) } returns false
        // 잠그고 보니 이미 3번 실패한 행이 있다.
        every { repository.findByEventIdForUpdate("evt_1") } returns event(status = "FAILED", retryCount = 3)
        val recorded = slot<WebhookEvent>()
        every { repository.updateIfNotProcessed(capture(recorded)) } returns true

        assertFailsWith<IllegalStateException> {
            guard.handle("evt_1", "transaction.completed", "{}") { throw IllegalStateException("또 실패") }
        }

        assertEquals(4, recorded.captured.retryCount, "재시도 횟수가 초기화되면 DEAD_LETTER 에 도달하지 못한다")
    }

    @Test
    @DisplayName("잠금 전에 실패하면 수신 기록을 기준으로 남긴다")
    fun failureBeforeClaimFallsBackToReceipt() {
        every { repository.findByEventId("evt_1") } returns null
        every { repository.saveIfAbsent(any()) } returns true
        every { repository.findByEventIdForUpdate("evt_1") } throws IllegalStateException("잠금 조회 실패")
        val recorded = slot<WebhookEvent>()
        every { repository.updateIfNotProcessed(capture(recorded)) } returns true

        assertFailsWith<IllegalStateException> {
            guard.handle("evt_1", "transaction.completed", "{}") { }
        }

        assertEquals("evt_1", recorded.captured.eventId)
        assertEquals(1, recorded.captured.retryCount)
    }

    // ── 멱등·동시성 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("이미 완료된 이벤트는 트랜잭션도 열지 않는다")
    fun alreadyProcessedSkipsEverything() {
        every { repository.findByEventId("evt_1") } returns event(status = "PROCESSED")
        var dispatched = false

        val outcome = guard.handle("evt_1", "transaction.completed", "{}") { dispatched = true }

        assertEquals(WebhookInboundOutcome.ALREADY_PROCESSED, outcome)
        assertTrue(!dispatched, "이미 완료된 이벤트를 다시 처리했다")
        assertTrue(timeline.isEmpty(), "불필요한 트랜잭션을 열었다: $timeline")
    }

    @Test
    @DisplayName("잠금 획득 후 완료를 발견하면 업무 처리를 하지 않는다 — 동시 전달")
    fun claimedProcessedSkipsDispatch() {
        every { repository.findByEventId("evt_1") } returns null
        every { repository.saveIfAbsent(any()) } returns false
        every { repository.findByEventIdForUpdate("evt_1") } returns event(status = "PROCESSED")
        var dispatched = false

        val outcome = guard.handle("evt_1", "transaction.completed", "{}") { dispatched = true }

        assertEquals(WebhookInboundOutcome.ALREADY_PROCESSED, outcome)
        assertTrue(!dispatched, "상대가 끝낸 이벤트를 다시 처리했다")
        verify(exactly = 0) { repository.markProcessed(any(), any()) }
    }

    @Test
    @DisplayName("성공하면 event_id 로 완료 표시하고 실패 기록은 남기지 않는다")
    fun successMarksProcessed() {
        firstDelivery()

        val outcome = guard.handle("evt_1", "transaction.completed", "{}") { }

        assertEquals(WebhookInboundOutcome.PROCESSED, outcome)
        verify { repository.markProcessed("evt_1", any()) }
        verify(exactly = 0) { repository.updateIfNotProcessed(any()) }
    }
}
