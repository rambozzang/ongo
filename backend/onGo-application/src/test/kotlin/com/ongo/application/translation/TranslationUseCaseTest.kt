package com.ongo.application.translation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.credit.CreditService
import com.ongo.application.translation.dto.TranslateRequest
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.translation.TranslationRepository
import com.ongo.domain.translation.VideoTranslation
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 번역의 과금 경계.
 *
 * 번역은 **언어별 선차감 → 언어별 비동기 실행 → 언어별 부분 환불**이다. 그 의미를 지키면서
 * 고쳐야 할 것이 셋이었다.
 *
 *  1. 이미 돌고 있는 언어를 다시 요청하면 두 번 차감됐다
 *  2. JSON 파싱 실패는 결과가 없는데도 환불하지 않았다
 *  3. 상태 저장이 환불보다 먼저라, 저장이 실패하면 환불에 도달하지 못했다
 *
 * 비동기 부분(virtual thread)은 여기서 직접 검증하지 않는다 — 트랜잭션 커밋 이후 실행되고
 * 별도 스레드라, 단위 테스트로는 타이밍을 고정할 수 없다. 대신 **차감 계산과 실패 보상
 * 헬퍼의 계약**을 고정한다.
 */
class TranslationUseCaseTest {

    private val translationRepository = mockk<TranslationRepository>(relaxUnitFun = true)
    private val videoRepository = mockk<VideoRepository>()
    private val chatClientResolver = mockk<ChatClientResolver>()
    private val creditService = mockk<CreditService>(relaxed = true)
    private val rateLimiter = mockk<AiRateLimiter>(relaxed = true)

    /** 정산 트랜잭션 경계는 이 테스트의 관심사가 아니다. 콜백을 그대로 실행한다. */
    private val directTransactionManager = mockk<org.springframework.transaction.PlatformTransactionManager>().also {
        every { it.getTransaction(any<org.springframework.transaction.TransactionDefinition>()) } returns
            org.springframework.transaction.support.SimpleTransactionStatus()
        every { it.commit(any<org.springframework.transaction.TransactionStatus>()) } returns Unit
        every { it.rollback(any<org.springframework.transaction.TransactionStatus>()) } returns Unit
    }

    private val useCase = TranslationUseCase(
        translationRepository = translationRepository,
        videoRepository = videoRepository,
        creditService = creditService,
        chatClientResolver = chatClientResolver,
        rateLimiter = rateLimiter,
        transactionManager = directTransactionManager,
    )

    private val userId = 7L
    private val videoId = 300L

    private fun stubVideo() {
        every { videoRepository.findById(videoId) } returns Video(
            id = videoId,
            userId = userId,
            title = "원본 제목",
            description = "원본 설명",
            status = UploadStatus.PUBLISHED,
        )
    }

    private fun translation(id: Long, language: String, status: String) =
        VideoTranslation(id = id, videoId = videoId, language = language, status = status)

    /* ---- 1. 진행 중 언어 재차감 없음 ---- */

    /**
     * `(video_id, language)` 는 유니크다. 언어당 행은 하나뿐이고, 이미 `TRANSLATING` 이면
     * 결과가 만들어지는 중이다. 다시 청구하면 같은 결과에 두 번 과금한 것이 된다.
     */
    @Test
    fun `이미 진행 중인 언어는 다시 차감하지 않는다`() {
        stubVideo()
        every { translationRepository.findByVideoIdAndLanguage(videoId, "en") } returns
            translation(1L, "en", "TRANSLATING")

        val responses = useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("en")))

        assertEquals(1, responses.size)
        verify(exactly = 0) { creditService.validateAndDeduct(any(), any(), any()) }
        // 상태를 되돌리지도 않는다 — 돌고 있는 작업을 끊으면 그 크레딧이 낭비된다.
        verify(exactly = 0) { translationRepository.update(any(), any(), any(), any(), any(), any()) }
    }

    /* ---- 2. 새로 시작하는 언어만 차감 ---- */

    @Test
    fun `새로 시작하는 언어분만 차감한다`() {
        stubVideo()
        every { translationRepository.findByVideoIdAndLanguage(videoId, "en") } returns
            translation(1L, "en", "TRANSLATING")
        every { translationRepository.findByVideoIdAndLanguage(videoId, "ja") } returns null
        every { translationRepository.findByVideoIdAndLanguage(videoId, "zh") } returns null
        every { translationRepository.save(any()) } answers {
            firstArg<VideoTranslation>().copy(id = 99L)
        }

        useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("en", "ja", "zh")))

        // en 은 진행 중이므로 ja·zh 두 언어분만 청구한다.
        verify(exactly = 1) {
            creditService.validateAndDeduct(userId, 2 * TranslationUseCase.CREDIT_PER_LANGUAGE, "TRANSLATION")
        }
    }

    @Test
    fun `모든 언어가 진행 중이면 차감하지 않는다`() {
        stubVideo()
        every { translationRepository.findByVideoIdAndLanguage(videoId, "en") } returns
            translation(1L, "en", "TRANSLATING")
        every { translationRepository.findByVideoIdAndLanguage(videoId, "ja") } returns
            translation(2L, "ja", "TRANSLATING")

        useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("en", "ja")))

        verify(exactly = 0) { creditService.validateAndDeduct(any(), any(), any()) }
    }

    /**
     * 완료·실패한 언어는 재번역 요청이다. 그때는 다시 청구하는 것이 맞다 —
     * 새 결과를 만들기 위해 모델을 다시 부른다.
     */
    @Test
    fun `완료된 언어의 재번역은 다시 차감한다`() {
        stubVideo()
        every { translationRepository.findByVideoIdAndLanguage(videoId, "en") } returns
            translation(1L, "en", "COMPLETED")
        every { translationRepository.update(1L, any(), any(), any(), any(), any()) } returns Unit
        every { translationRepository.findById(1L) } returns translation(1L, "en", "TRANSLATING")

        useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("en")))

        verify(exactly = 1) {
            creditService.validateAndDeduct(userId, TranslationUseCase.CREDIT_PER_LANGUAGE, "TRANSLATION")
        }
    }

    /* ---- 3. 신규 INSERT 경합은 유니크 제약이 판정 ---- */

    /**
     * 동시에 같은 언어를 넣으면 한쪽이 유니크 위반으로 끝난다. 이 메서드는
     * `@Transactional` 이므로 그때 **차감도 함께 롤백**된다 — 여기서는 예외가 그대로
     * 올라가는 것까지만 고정한다(롤백 자체는 실제 트랜잭션 매니저의 몫이다).
     */
    @Test
    fun `신규 저장이 유니크 위반이면 예외를 삼키지 않는다`() {
        stubVideo()
        every { translationRepository.findByVideoIdAndLanguage(videoId, "ja") } returns null
        every { translationRepository.save(any()) } throws
            IllegalStateException("duplicate key value violates unique constraint")

        assertFailsWith<IllegalStateException> {
            useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("ja")))
        }
    }

    /* ---- 4. 지원하지 않는 언어 ---- */

    @Test
    fun `지원하지 않는 언어만 있으면 차감하지 않는다`() {
        stubVideo()

        assertFailsWith<com.ongo.common.exception.BusinessException> {
            useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("xx")))
        }

        verify(exactly = 0) { creditService.validateAndDeduct(any(), any(), any()) }
    }
}
