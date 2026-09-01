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
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 웹훅 수신·실패 기록이 **업무 트랜잭션과 분리**돼 있는지.
 *
 * `handleWebhook` 은 실패 시 예외를 다시 던지고, 그 예외는 `RuntimeException` 이라 Spring
 * 기본 롤백 규칙이 트랜잭션 전체를 되돌린다. 기록이 같은 트랜잭션에 있으면 함께 사라지고,
 * `findRetryable` 이 `status = 'FAILED'` 인 행만 고르므로 재처리 대상이 없어진다.
 */
class WebhookEventRecorderTest {

    private val repository = mockk<WebhookEventRepository>()

    /** 실제로 어떤 전파 속성으로 트랜잭션을 여는지 기록한다. */
    private val propagations = mutableListOf<Int>()
    private val recordingManager = object : PlatformTransactionManager {
        override fun getTransaction(definition: TransactionDefinition?): TransactionStatus {
            definition?.let { propagations.add(it.propagationBehavior) }
            return SimpleTransactionStatus()
        }

        override fun commit(status: TransactionStatus) = Unit
        override fun rollback(status: TransactionStatus) = Unit
    }

    private val recorder = WebhookEventRecorder(repository, recordingManager)

    private fun event(retryCount: Int = 0) = WebhookEvent(
        id = 1L,
        eventId = "evt_1",
        eventType = "transaction.completed",
        payload = "{}",
        status = "PENDING",
        retryCount = retryCount,
    )

    /**
     * **이 테스트가 수정의 핵심이다.** REQUIRED 로 열면 바깥 트랜잭션에 참여해 롤백에
     * 휩쓸린다 — 예외를 던지는 순간 기록이 사라진다.
     */
    @Test
    @DisplayName("실패 기록은 새 트랜잭션에서 커밋한다")
    fun failureIsRecordedInItsOwnTransaction() {
        every { repository.updateIfNotProcessed(any()) } returns true

        recorder.recordFailure(event(), IllegalStateException("크레딧 패키지 미식별"))

        assertTrue(
            propagations.contains(TransactionDefinition.PROPAGATION_REQUIRES_NEW),
            "실패 기록이 바깥 트랜잭션에 참여합니다: $propagations",
        )
    }

    @Test
    @DisplayName("수신 기록도 새 트랜잭션에서 커밋한다")
    fun receiptIsRecordedInItsOwnTransaction() {
        // 검사-후-삽입이 아니라 원자적 삽입이어야 한다. 동시에 들어온 두 전달이 모두 조회를
        // 통과하면 유니크 위반으로 트랜잭션이 통째로 abort 된다.
        every { repository.saveIfAbsent(any()) } returns true

        recorder.recordReceived(null, "evt_2", "transaction.completed", "{}")

        assertTrue(propagations.contains(TransactionDefinition.PROPAGATION_REQUIRES_NEW), "$propagations")
    }

    @Test
    @DisplayName("실패는 재시도 대상 상태와 백오프를 남긴다")
    fun failureCarriesRetryState() {
        val saved = slot<WebhookEvent>()
        every { repository.updateIfNotProcessed(capture(saved)) } returns true

        recorder.recordFailure(event(retryCount = 2), IllegalStateException("일시 장애"))

        assertEquals("FAILED", saved.captured.status)
        assertEquals(3, saved.captured.retryCount)
        assertEquals("일시 장애", saved.captured.errorMessage)
        // findRetryable 은 nextRetryAt <= now 인 행만 고른다. 값이 없으면 영원히 안 잡힌다.
        assertTrue(saved.captured.nextRetryAt != null, "다음 재시도 시각이 없습니다")
    }

    /** 오류 메시지가 길어도 컬럼을 넘기지 않는다. */
    @Test
    @DisplayName("긴 오류 메시지는 잘라서 기록한다")
    fun truncatesLongErrorMessage() {
        val saved = slot<WebhookEvent>()
        every { repository.updateIfNotProcessed(capture(saved)) } returns true

        recorder.recordFailure(event(), IllegalStateException("x".repeat(900)))

        assertEquals(500, saved.captured.errorMessage?.length)
    }

    /**
     * 실패 기록이 완료를 덮으면 이미 반영된 처리가 다시 재시도 대상이 된다.
     * 그 판정은 읽고 나서 하는 것이 아니라 갱신문의 조건으로 DB 가 해야 한다.
     */
    @Test
    @DisplayName("실패 기록은 PROCESSED 를 보존하는 조건부 갱신을 쓴다")
    fun failureRecordPreservesProcessedState() {
        every { repository.updateIfNotProcessed(any()) } returns false

        recorder.recordFailure(event(), IllegalStateException("일시 장애"))

        verify { repository.updateIfNotProcessed(any()) }
    }

    /** 재시도로 들어온 이벤트는 새로 만들지 않는다 — 중복 행과 retryCount 초기화를 막는다. */
    @Test
    @DisplayName("이미 있는 이벤트는 다시 저장하지 않는다")
    fun reusesExistingEvent() {
        val existing = event(retryCount = 4)

        val result = recorder.recordReceived(existing, "evt_1", "transaction.completed", "{}")

        assertEquals(existing, result)
        verify(exactly = 0) { repository.saveIfAbsent(any()) }
        assertTrue(propagations.isEmpty(), "불필요한 트랜잭션을 열었습니다: $propagations")
    }
}
