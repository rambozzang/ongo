package com.ongo.application.repurpose

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.InsufficientCreditException
import com.ongo.domain.repurpose.RepurposeClipRepository
import com.ongo.domain.repurpose.RepurposeJob
import com.ongo.domain.repurpose.RepurposeJobRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 리퍼포징 job 행의 수명.
 *
 * job 은 `PROCESSING` 으로 시작해 `COMPLETED` 또는 `FAILED` 로 끝나야 한다. 끝내는 코드가
 * 없는 `PROCESSING` 이 남으면 사용자는 "분석 중" 을 영원히 보고, 그 행을 정리하는 경로는
 * 어디에도 없다.
 *
 * 여기서 고정하는 것은 **언제 job 을 만드는가**다. 크레딧 차감이 확정된 뒤여야 한다.
 */
class RepurposeUseCaseTest {

    private val jobRepository = mockk<RepurposeJobRepository>()
    private val clipRepository = mockk<RepurposeClipRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val chatClientResolver = mockk<ChatClientResolver>()
    private val creditService = mockk<CreditService>()
    private val rateLimiter = mockk<AiRateLimiter>(relaxed = true)

    private val useCase = RepurposeUseCase(
        repurposeJobRepository = jobRepository,
        repurposeClipRepository = clipRepository,
        videoRepository = videoRepository,
        chatClientResolver = chatClientResolver,
        creditService = creditService,
        rateLimiter = rateLimiter,
    )

    private val userId = 7L
    private val videoId = 300L

    private fun stubVideo() {
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = userId,
            title = "원본 영상",
            description = "대본",
            status = UploadStatus.PUBLISHED,
        )
    }

    /**
     * `withCredits` 가 차감에 실패하면 블록이 실행되지 않는다. 실제 구현과 같은 계약이다.
     */
    private fun stubInsufficientCredits() {
        every {
            creditService.withCredits(userId, AiFeature.CONTENT_REPURPOSE, any<() -> Any>())
        } throws InsufficientCreditException(required = 10, available = 2)
    }

    /** 차감이 성공한 경우. 블록을 그대로 실행한다. */
    private fun stubCreditsGranted() {
        every {
            creditService.withCredits(userId, AiFeature.CONTENT_REPURPOSE, any<() -> Any>())
        } answers {
            @Suppress("UNCHECKED_CAST")
            (thirdArg<() -> Any>())()
        }
    }

    /**
     * **잔액 부족이면 job 을 만들지 않는다.**
     *
     * 예전에는 `withCredits` 보다 먼저 `PROCESSING` job 을 저장했다. 그러면 차감이 실패하는
     * 순간 아무도 끝내지 않을 job 이 목록에 남았다.
     */
    @Test
    fun `잔액이 부족하면 job 을 만들지 않는다`() {
        stubVideo()
        stubInsufficientCredits()

        assertFailsWith<InsufficientCreditException> {
            useCase.analyzeForRepurpose(userId, videoId)
        }

        verify(exactly = 0) { jobRepository.save(any()) }
        verify(exactly = 0) { jobRepository.updateStatus(any(), any(), any()) }
        // 외부 모델에도 닿지 않는다.
        verify(exactly = 0) { chatClientResolver.resolve(any()) }
    }

    /** 영상이 없으면 차감도 job 생성도 없다. 검증이 차감보다 먼저다. */
    @Test
    fun `영상이 없으면 차감을 시도하지 않는다`() {
        every { videoRepository.findById(videoId) } returns null

        assertFailsWith<BusinessException> {
            useCase.analyzeForRepurpose(userId, videoId)
        }

        verify(exactly = 0) { creditService.withCredits(any(), any<AiFeature>(), any<() -> Any>()) }
        verify(exactly = 0) { jobRepository.save(any()) }
    }

    /** 남의 영상도 마찬가지다. */
    @Test
    fun `다른 사용자의 영상이면 차감을 시도하지 않는다`() {
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = 999L,
            title = "남의 영상",
            status = UploadStatus.PUBLISHED,
        )

        assertFailsWith<BusinessException> {
            useCase.analyzeForRepurpose(userId, videoId)
        }

        verify(exactly = 0) { creditService.withCredits(any(), any<AiFeature>(), any<() -> Any>()) }
        verify(exactly = 0) { jobRepository.save(any()) }
    }

    /**
     * 차감이 확정된 뒤에는 job 이 만들어지고, AI 호출이 실패하면 `FAILED` 로 닫힌다.
     * `PROCESSING` 으로 남겨두면 안 된다.
     */
    @Test
    fun `차감 이후 AI 호출이 실패하면 job 을 FAILED 로 닫는다`() {
        stubVideo()
        stubCreditsGranted()
        every { jobRepository.save(any()) } answers {
            firstArg<RepurposeJob>().copy(id = 55L)
        }
        every { jobRepository.updateStatus(55L, "FAILED", 0) } returns null
        every { chatClientResolver.resolve(userId) } throws IllegalStateException("모델 장애")

        val error = assertFailsWith<BusinessException> {
            useCase.analyzeForRepurpose(userId, videoId)
        }

        assertEquals("AI_CALL_FAILED", error.code)
        verify(exactly = 1) { jobRepository.save(match { it.status == "PROCESSING" }) }
        verify(exactly = 1) { jobRepository.updateStatus(55L, "FAILED", 0) }
    }

    /**
     * job 저장 자체가 실패하면 예외가 `withCredits` 밖으로 나가 환불된다.
     * 그때는 job 이 아예 없으므로 남길 상태도 없다.
     */
    @Test
    fun `job 저장이 실패하면 상태를 남기지 않고 예외를 올린다`() {
        stubVideo()
        stubCreditsGranted()
        every { jobRepository.save(any()) } throws IllegalStateException("job 저장 실패")

        assertFailsWith<IllegalStateException> {
            useCase.analyzeForRepurpose(userId, videoId)
        }

        verify(exactly = 0) { jobRepository.updateStatus(any(), any(), any()) }
        verify(exactly = 0) { chatClientResolver.resolve(any()) }
    }
}
