package com.ongo.application.ai

import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.domain.comment.CommentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * FAQ 클러스터링의 과금 경계.
 *
 * **AI 를 부르지 않은 요청에 과금하면 안 된다.** 예전에는 댓글을 조회하기 전에 차감했고,
 * 분석할 댓글이 하나도 없으면 빈 응답을 돌려주며 끝났다 — 환불도 하지 않았다. 크레딧만
 * 사라지고 사용자는 왜 줄었는지 알 수 없었다.
 */
class FaqClusteringUseCaseTest {

    private val chatClientResolver = mockk<ChatClientResolver>()
    private val creditService = mockk<CreditService>()
    private val rateLimiter = mockk<AiRateLimiter>(relaxed = true)
    private val commentRepository = mockk<CommentRepository>()

    private val useCase = FaqClusteringUseCase(
        chatClientResolver = chatClientResolver,
        creditService = creditService,
        rateLimiter = rateLimiter,
        commentRepository = commentRepository,
    )

    private val userId = 7L

    @Test
    fun `분석할 댓글이 없으면 차감도 AI 호출도 하지 않는다`() {
        every { commentRepository.findByUserId(userId, 0, 200) } returns emptyList()

        val response = useCase.execute(userId)

        assertTrue(response.clusters.isEmpty())
        assertTrue(response.generatedAt.isNotBlank())
        // 과금 경로에 아예 들어가지 않는다.
        verify(exactly = 0) { creditService.withCredits(any(), any<AiFeature>(), any<() -> Any>()) }
        verify(exactly = 0) { creditService.validateAndDeduct(any(), any<AiFeature>()) }
        verify(exactly = 0) { chatClientResolver.resolve(any()) }
    }

    /** 레이트 리밋은 조회보다 먼저다. 순서가 바뀌면 제한된 사용자도 DB 를 읽는다. */
    @Test
    fun `레이트 리밋을 먼저 확인한다`() {
        every { commentRepository.findByUserId(userId, 0, 200) } returns emptyList()

        useCase.execute(userId)

        verify(exactly = 1) { rateLimiter.checkRateLimit(userId) }
    }

    /**
     * 댓글이 있으면 공통 크레딧 경로를 탄다. AI 호출 실패가 블록 밖으로 전파돼야
     * 그 경로가 환불한다 — 블록 안에서 삼키면 결과 없이 크레딧만 사라진다.
     */
    @Test
    fun `AI 호출이 실패하면 공통 크레딧 경로 밖으로 전파된다`() {
        every { commentRepository.findByUserId(userId, 0, 200) } returns
            listOf(comment(1L, "배송 언제 오나요?"))
        every { creditService.withCredits(userId, AiFeature.FAQ_CLUSTERING, any<() -> Any>()) } answers {
            @Suppress("UNCHECKED_CAST")
            (thirdArg<() -> Any>())()
        }
        every { chatClientResolver.resolve(userId) } throws IllegalStateException("모델 장애")

        val error = assertFailsWithBusiness { useCase.execute(userId) }

        assertEquals("AI_CALL_FAILED", error)
        verify(exactly = 1) { creditService.withCredits(userId, AiFeature.FAQ_CLUSTERING, any<() -> Any>()) }
    }

    private fun assertFailsWithBusiness(block: () -> Unit): String =
        runCatching { block() }
            .exceptionOrNull()
            .let { it as? com.ongo.common.exception.BusinessException }
            ?.code
            ?: error("BusinessException 이 발생하지 않았습니다")

    private fun comment(id: Long, content: String) = com.ongo.domain.comment.Comment(
        id = id,
        userId = userId,
        videoId = 1L,
        platform = "YOUTUBE",
        platformCommentId = "c-$id",
        authorName = "viewer",
        content = content,
    )
}
