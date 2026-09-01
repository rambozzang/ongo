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
}
