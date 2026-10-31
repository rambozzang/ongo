package com.ongo.application.comment

import com.ongo.application.comment.dto.BatchReplyRequest
import com.ongo.common.exception.BusinessException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * 일괄 답변의 집계가 **실제 전송 결과**를 반영하는지.
 *
 * 답글 경로가 fail-closed 로 바뀌면서(미지원 플랫폼·채널 없음·식별자 없음이 예외가 됨)
 * 그 예외들이 성공으로 세어지면 안 된다. 운영자는 이 숫자로 "몇 명에게 답글이 나갔는가"를
 * 판단한다.
 */
class CommentBatchUseCaseTest {

    private val engagement = mockk<CommentEngagementUseCase>()
    private val useCase = CommentBatchUseCase(engagement)

    private val userId = 7L

    @Test
    @DisplayName("답글 미지원으로 거절된 건은 실패로 센다")
    fun unsupportedRepliesCountAsFailures() {
        every { engagement.replyToComment(userId, 1L, "답글") } throws
            BusinessException("COMMENT_REPLY_UNSUPPORTED", "지원하지 않습니다")
        every { engagement.replyToComment(userId, 2L, "답글") } throws
            BusinessException("COMMENT_REPLY_UNSUPPORTED", "지원하지 않습니다")

        val result = useCase.batchReply(userId, BatchReplyRequest(listOf(1L, 2L), "답글"))

        assertEquals(0, result.successCount, "외부 전송이 없었는데 성공으로 셌습니다")
        assertEquals(2, result.failCount)
    }

    @Test
    @DisplayName("성공과 실패가 섞이면 각각 센다")
    fun countsMixedOutcomes() {
        every { engagement.replyToComment(userId, 1L, "답글") } returns mockk(relaxed = true)
        every { engagement.replyToComment(userId, 2L, "답글") } throws
            BusinessException("COMMENT_PLATFORM_ID_MISSING", "식별자가 없습니다")

        val result = useCase.batchReply(userId, BatchReplyRequest(listOf(1L, 2L), "답글"))

        assertEquals(1, result.successCount)
        assertEquals(1, result.failCount)
    }

    /** 한 건의 실패가 나머지 전송을 막으면 안 된다. */
    @Test
    @DisplayName("한 건이 실패해도 나머지는 계속 전송한다")
    fun oneFailureDoesNotStopTheBatch() {
        every { engagement.replyToComment(userId, 1L, "답글") } throws
            BusinessException("COMMENT_REPLY_UNSUPPORTED", "지원하지 않습니다")
        every { engagement.replyToComment(userId, 2L, "답글") } returns mockk(relaxed = true)
        every { engagement.replyToComment(userId, 3L, "답글") } returns mockk(relaxed = true)

        val result = useCase.batchReply(userId, BatchReplyRequest(listOf(1L, 2L, 3L), "답글"))

        assertEquals(2, result.successCount)
        assertEquals(1, result.failCount)
    }

    // ── 항목별 결과 · 상한 · 숨김 ───────────────────────────────────────

    /** 개수만 주면 화면은 어떤 댓글이 실패했는지 몰라 전부를 처리 완료로 표시했다. */
    @Test
    @DisplayName("요청 순서대로 항목별 결과와 다시 시도 가능 여부를 돌려준다")
    fun reportsPerItemResults() {
        every { engagement.replyToComment(userId, 1L, "답글") } returns mockk(relaxed = true)
        every { engagement.replyToComment(userId, 2L, "답글") } throws
            com.ongo.common.exception.PlatformApiException("YOUTUBE", "일시 오류")
        every { engagement.replyToComment(userId, 3L, "답글") } throws
            BusinessException("COMMENT_REPLY_UNSUPPORTED", "지원하지 않습니다")

        val result = useCase.batchReply(userId, BatchReplyRequest(listOf(1L, 2L, 3L), "답글"))

        assertEquals(listOf(1L, 2L, 3L), result.items.map { it.commentId })
        assertEquals(listOf(true, false, false), result.items.map { it.success })
        assertEquals("PLATFORM_API_FAILED", result.items[1].errorCode)
        assertEquals(true, result.items[1].retryable, "플랫폼 일시 오류는 다시 시도할 수 있다")
        assertEquals(false, result.items[2].retryable, "미지원은 다시 보내도 같다")
    }

    @Test
    @DisplayName("같은 댓글이 두 번 들어와도 한 번만 보낸다")
    fun deduplicatesIds() {
        every { engagement.replyToComment(userId, 1L, "답글") } returns mockk(relaxed = true)

        val result = useCase.batchReply(userId, BatchReplyRequest(listOf(1L, 1L), "답글"))

        assertEquals(1, result.totalRequested)
        io.mockk.verify(exactly = 1) { engagement.replyToComment(userId, 1L, "답글") }
    }

    @Test
    @DisplayName("빈 답변·너무 긴 답변·빈 선택·상한 초과는 아무것도 보내지 않고 거절한다")
    fun rejectsInvalidRequests() {
        val cases = listOf(
            BatchReplyRequest(listOf(1L), "   ") to "BATCH_REPLY_EMPTY",
            BatchReplyRequest(listOf(1L), "가".repeat(CommentBatchUseCase.MAX_REPLY_LENGTH + 1)) to "BATCH_REPLY_TOO_LONG",
            BatchReplyRequest(emptyList(), "답글") to "BATCH_EMPTY",
            BatchReplyRequest((1L..(CommentBatchUseCase.MAX_BATCH_SIZE + 1).toLong()).toList(), "답글") to "BATCH_TOO_LARGE",
        )
        for ((request, code) in cases) {
            val error = kotlin.test.assertFailsWith<BusinessException> { useCase.batchReply(userId, request) }
            assertEquals(code, error.code)
        }
        io.mockk.verify(exactly = 0) { engagement.replyToComment(any(), any(), any()) }
    }

    /** 토글이면 이미 숨긴 댓글이 섞였을 때 도로 보이게 된다. "처리 완료" 는 숨김으로 정해야 한다. */
    @Test
    @DisplayName("일괄 숨김은 토글이 아니라 숨김으로 정한다")
    fun batchHideSetsHiddenExplicitly() {
        every { engagement.setHidden(userId, any(), true) } returns mockk(relaxed = true)

        val result = useCase.batchHide(userId, com.ongo.application.comment.dto.BatchHideRequest(listOf(1L, 2L)))

        assertEquals(2, result.successCount)
        io.mockk.verify(exactly = 2) { engagement.setHidden(userId, any(), true) }
        io.mockk.verify(exactly = 0) { engagement.hideComment(any(), any()) }
    }

    /** 플랫폼 API 속도 제한에 한꺼번에 걸리지 않게 동시에 보내는 수를 제한한다. */
    @Test
    @DisplayName("동시에 보내는 답변은 상한을 넘지 않는다")
    fun limitsConcurrency() {
        val inFlight = java.util.concurrent.atomic.AtomicInteger(0)
        val peak = java.util.concurrent.atomic.AtomicInteger(0)
        every { engagement.replyToComment(userId, any(), "답글") } answers {
            val now = inFlight.incrementAndGet()
            peak.accumulateAndGet(now, ::maxOf)
            Thread.sleep(20)
            inFlight.decrementAndGet()
            mockk(relaxed = true)
        }

        useCase.batchReply(userId, BatchReplyRequest((1L..20L).toList(), "답글"))

        kotlin.test.assertTrue(peak.get() <= CommentBatchUseCase.MAX_CONCURRENCY, "동시 ${peak.get()}건")
    }
}
