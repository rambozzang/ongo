package com.ongo.application.ai

import com.ongo.application.ai.result.BatchReplyDraftResult
import com.ongo.application.comment.dto.BatchAiDraftResponse
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.domain.comment.Comment
import com.ongo.domain.comment.CommentRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.chat.client.ChatClient

/**
 * 댓글 AI 일괄 초안. 본인 댓글만, 상한 안에서, **나온 초안만큼만** 크레딧을 가져가야 한다.
 */
class GenerateReplyBatchTest {

    private val userId = 7L
    private val resolver = mockk<ChatClientResolver>()
    private val credits = mockk<CreditService>()
    private val limiter = mockk<AiRateLimiter>()
    private val comments = mockk<CommentRepository>()
    private val chat = mockk<ChatClient>(relaxed = true)
    private val useCase = GenerateReplyUseCase(resolver, credits, limiter, comments)

    /** 크레딧 서비스는 블록을 그대로 실행하고, 돌려줄 몫을 기록한다. */
    private var unusedRefund: Int? = null
    private var charged: Int? = null

    init {
        every { limiter.checkRateLimit(userId) } just runs
        every { resolver.resolve(userId) } returns chat
        every {
            credits.withCreditsRefundingUnused<BatchAiDraftResponse>(userId, any(), AiFeature.BATCH_REPLY_DRAFT.name, any(), any())
        } answers {
            charged = secondArg()
            @Suppress("UNCHECKED_CAST")
            val result = (arg<() -> BatchAiDraftResponse>(4))()
            @Suppress("UNCHECKED_CAST")
            unusedRefund = (arg<(BatchAiDraftResponse) -> Int>(3))(result)
            CreditService.Settled(result, secondArg<Int>() - unusedRefund!!)
        }
    }

    private fun comment(id: Long, owner: Long = userId) =
        Comment(id = id, userId = owner, authorName = "a$id", content = "질문 $id")

    private fun modelReturns(vararg drafts: Pair<Int, String>) {
        every {
            chat.prompt().system(any<String>()).user(any<String>()).call().entity(BatchReplyDraftResult::class.java)
        } returns BatchReplyDraftResult(drafts.map { (i, text) -> BatchReplyDraftResult.DraftItem(commentIndex = i, draftReply = text) })
    }

    /** 예전에는 ID 목록을 그대로 읽어, 남의 댓글 내용과 초안이 응답에 실렸다. */
    @Test
    @DisplayName("남의 댓글이 하나라도 섞이면 크레딧도 모델도 쓰지 않고 거부한다")
    fun rejectsOthersComments() {
        every { comments.findByIds(listOf(1L, 2L)) } returns listOf(comment(1L), comment(2L, owner = 99L))

        assertThrows<ForbiddenException> { useCase.executeBatch(userId, listOf(1L, 2L), "FRIENDLY") }
        verify(exactly = 0) { credits.withCreditsRefundingUnused<Any>(any(), any(), any(), any(), any()) }
    }

    @Test
    @DisplayName("상한을 넘으면 거절한다")
    fun rejectsTooMany() {
        val ids = (1L..(GenerateReplyUseCase.MAX_BATCH_DRAFTS + 1).toLong()).toList()
        val error = assertThrows<BusinessException> { useCase.executeBatch(userId, ids, "FRIENDLY") }
        assertEquals("BATCH_TOO_LARGE", error.code)
    }

    /** 3개를 요청했는데 모델이 2개만(그중 하나는 중복·하나는 빈 값) 주면, 나온 초안 몫만 가져간다. */
    @Test
    @DisplayName("나오지 않은·중복·빈 초안의 몫은 돌려준다")
    fun refundsMissingDrafts() {
        every { comments.findByIds(listOf(1L, 2L, 3L)) } returns listOf(comment(3L), comment(1L), comment(2L))
        modelReturns(0 to "고마워요!", 0 to "중복", 1 to "   ")

        val response = useCase.executeBatch(userId, listOf(1L, 2L, 3L), "FRIENDLY")

        val per = AiFeature.BATCH_REPLY_DRAFT.creditCost
        assertEquals(3 * per, charged)
        assertEquals(listOf(1L), response.drafts.map { it.commentId }, "요청 순서의 첫 댓글(1)에 첫 초안이 붙어야 한다")
        assertEquals("고마워요!", response.drafts.single().draftReply)
        assertEquals(per, response.totalCreditsUsed)
        assertEquals(2 * per, unusedRefund)
    }

    @Test
    @DisplayName("초안이 하나도 없으면 실패로 끝나 전액 환불 경로를 탄다")
    fun noDraftsIsFailure() {
        every { comments.findByIds(listOf(1L)) } returns listOf(comment(1L))
        modelReturns()

        val error = assertThrows<BusinessException> { useCase.executeBatch(userId, listOf(1L), "FRIENDLY") }
        assertEquals("AI_PARSE_ERROR", error.code)
    }

    /** 원가 상한 때문에 덜 돌려줬으면 응답의 사용 크레딧도 실제로 가져간 값이어야 한다. */
    @Test
    @DisplayName("응답의 사용 크레딧은 실제로 가져간 크레딧이다")
    fun reportsActualCharge() {
        every { comments.findByIds(listOf(1L, 2L)) } returns listOf(comment(1L), comment(2L))
        modelReturns(0 to "초안")
        every {
            credits.withCreditsRefundingUnused<BatchAiDraftResponse>(userId, any(), AiFeature.BATCH_REPLY_DRAFT.name, any(), any())
        } answers {
            @Suppress("UNCHECKED_CAST")
            val result = (arg<() -> BatchAiDraftResponse>(4))()
            CreditService.Settled(result, 5) // 원가 때문에 3 이 아니라 5 를 가져갔다
        }

        assertEquals(5, useCase.executeBatch(userId, listOf(1L, 2L), "FRIENDLY").totalCreditsUsed)
    }
}
