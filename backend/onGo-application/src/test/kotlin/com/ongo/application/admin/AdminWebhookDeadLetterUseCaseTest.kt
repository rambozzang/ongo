package com.ongo.application.admin

import com.ongo.application.paddle.PaddleWebhookService
import com.ongo.application.portone.PortOnePaymentService
import com.ongo.common.exception.BusinessException
import com.ongo.domain.webhook.WebhookEvent
import com.ongo.domain.webhook.WebhookEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DEAD_LETTER 운영 조회·재큐잉.
 *
 * 이 화면은 **결제 이벤트를 다루는 관리자 화면**이다. 두 가지가 동시에 참이어야 한다.
 * 운영자가 무슨 일이 있었는지 알 수 있어야 하고, 그 과정에서 원문 본문·서명·전체 멱등 키가
 * 화면이나 로그로 새어나가면 안 된다.
 */
class AdminWebhookDeadLetterUseCaseTest {

    private val repository = mockk<WebhookEventRepository>()
    private val useCase = AdminWebhookDeadLetterUseCase(repository)

    private fun deadLettered(
        id: Long = 1L,
        eventType: String = "transaction.completed",
        eventId: String = "evt_01HQ8ZK3M4N5P6Q7R8S9T0",
        payload: String = """{"secret":"do-not-leak","card":"4111111111111111"}""",
    ) = WebhookEvent(
        id = id,
        eventId = eventId,
        eventType = eventType,
        payload = payload,
        status = "DEAD_LETTER",
        retryCount = 5,
        maxRetries = 5,
        errorMessage = "x".repeat(400),
        createdAt = LocalDateTime.of(2026, 8, 28, 1, 0),
    )

    // ── 민감 정보 비노출 ─────────────────────────────────────────────────────

    /**
     * **이 테스트가 이 화면의 안전 조건이다.** DTO 에 본문을 담는 필드를 하나라도 추가하면
     * 결제 식별자·고객 정보가 관리자 화면과 스크린샷을 통해 퍼진다.
     */
    @Test
    @DisplayName("원문 본문은 어떤 필드로도 나가지 않는다")
    fun payloadNeverLeaves() {
        every { repository.findDeadLettered(any()) } returns listOf(deadLettered())

        val item = useCase.list(50).single()

        val rendered = item.toString()
        assertFalse(rendered.contains("do-not-leak"), "본문이 응답에 실렸다: $rendered")
        assertFalse(rendered.contains("4111111111111111"), "카드번호가 응답에 실렸다: $rendered")
    }

    @Test
    @DisplayName("멱등 키는 가운데를 가려 대조만 가능하게 한다")
    fun eventIdIsMasked() {
        every { repository.findDeadLettered(any()) } returns listOf(deadLettered())

        val item = useCase.list(50).single()

        assertFalse(
            item.maskedEventId.contains("evt_01HQ8ZK3M4N5P6Q7R8S9T0"),
            "멱등 키가 그대로 나갔다: ${item.maskedEventId}",
        )
        assertTrue(item.maskedEventId.startsWith("evt_01HQ"), "대조할 만큼은 남아야 한다: ${item.maskedEventId}")
        assertTrue(item.maskedEventId.endsWith("S9T0"), "대조할 만큼은 남아야 한다: ${item.maskedEventId}")
    }

    @Test
    @DisplayName("짧은 키는 통째로 가린다 — 앞뒤만 남기면 사실상 전부 보인다")
    fun shortEventIdIsFullyMasked() {
        val masked = AdminWebhookDeadLetterUseCase.maskEventId("evt_1234")

        assertEquals("*".repeat("evt_1234".length), masked)
    }

    @Test
    @DisplayName("오류 메시지는 길이를 제한해 담는다")
    fun errorMessageIsTruncated() {
        every { repository.findDeadLettered(any()) } returns listOf(deadLettered())

        val item = useCase.list(50).single()

        assertEquals(AdminWebhookDeadLetterUseCase.ERROR_MESSAGE_LIMIT, item.errorMessage?.length)
    }

    // ── 조회 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PG 를 이벤트 타입으로 판별한다")
    fun providerIsDerivedFromEventType() {
        every { repository.findDeadLettered(any()) } returns listOf(
            deadLettered(id = 1L, eventType = "transaction.completed"),
            deadLettered(id = 2L, eventType = "Transaction.Paid"),
            deadLettered(id = 3L, eventType = "Something.Unknown"),
        )

        val providers = useCase.list(50).map { it.provider }

        assertEquals(listOf("PADDLE", "PORTONE", "UNKNOWN"), providers)
    }

    @Test
    @DisplayName("조회 상한은 범위 안으로 강제한다 — 전체를 끌어오지 않는다")
    fun limitIsClamped() {
        val limit = slot<Int>()
        every { repository.findDeadLettered(capture(limit)) } returns emptyList()

        useCase.list(100_000)
        assertEquals(AdminWebhookDeadLetterUseCase.MAX_LIST_LIMIT, limit.captured)

        useCase.list(0)
        assertTrue(limit.captured >= 1, "0 이하를 그대로 넘기면 조회가 비어버린다")
    }

    // ── 재큐잉 ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("재큐잉은 두 PG 가 소유한 타입만 허용한다 — 남의 이벤트는 되돌리지 않는다")
    fun requeueScopesToOwnedTypes() {
        val types = slot<Set<String>>()
        every { repository.requeueDeadLettered(any(), any(), capture(types)) } returns true

        useCase.requeue(7L)

        assertTrue(types.captured.containsAll(PaddleWebhookService.REPROCESSABLE_EVENT_TYPES))
        assertTrue(types.captured.containsAll(PortOnePaymentService.REPROCESSABLE_EVENT_TYPES))
        assertFalse("Something.Unknown" in types.captured, "모르는 타입까지 허용하면 안 된다")
    }

    @Test
    @DisplayName("재큐잉은 지금 시각을 다음 재시도로 넣는다 — 다음 스케줄러 실행이 집어간다")
    fun requeueSchedulesImmediateRetry() {
        val retryAt = slot<LocalDateTime>()
        every { repository.requeueDeadLettered(any(), capture(retryAt), any()) } returns true

        val before = LocalDateTime.now()
        val result = useCase.requeue(7L)

        assertTrue(!retryAt.captured.isBefore(before), "과거로 넣으면 안 된다")
        assertEquals("FAILED", result.status)
        assertEquals(7L, result.id)
    }

    /**
     * 갱신 행이 0이면 아무것도 되돌리지 않은 것이다. 성공으로 보고하면 운영자가 복구됐다고
     * 믿고 넘어가 결제가 영영 반영되지 않는다.
     */
    @Test
    @DisplayName("거부되면 성공으로 보고하지 않는다 — 가짜 건수 금지")
    fun rejectedRequeueFailsLoudly() {
        every { repository.requeueDeadLettered(any(), any(), any()) } returns false

        val error = assertFailsWith<BusinessException> { useCase.requeue(7L) }

        assertEquals("WEBHOOK_REQUEUE_REJECTED", error.code)
    }

    @Test
    @DisplayName("재큐잉은 결제를 직접 처리하지 않는다 — 상태만 되돌린다")
    fun requeueDoesNotProcessAnything() {
        every { repository.requeueDeadLettered(any(), any(), any()) } returns true

        useCase.requeue(7L)

        // 되돌린 뒤의 행을 다시 읽어 화면에 채우면 없는 사실을 지어내게 된다.
        verify(exactly = 0) { repository.findDeadLettered(any()) }
        verify(exactly = 0) { repository.markProcessed(any(), any()) }
        verify(exactly = 0) { repository.updateIfNotProcessed(any()) }
    }
}
