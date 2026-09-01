package com.ongo.application.translation

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.credit.CreditAllocation
import com.ongo.application.credit.CreditService
import com.ongo.application.translation.dto.TranslateRequest
import com.ongo.domain.translation.TranslationCreditAllocation
import com.ongo.domain.translation.TranslationRepository
import com.ongo.domain.translation.VideoTranslation
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
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
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 번역 작업이 **재시작과 동시 요청에서도 크레딧과 상태를 잃지 않는지** 고정한다.
 *
 * ## 무엇이 고객 돈을 잡아먹었나
 *
 * `requestTranslation` 은 언어별 비용을 한 번에 차감하고 `TRANSLATING` 행을 커밋한 뒤
 * virtual thread 로 LLM 을 불렀다. 그 사이 프로세스가 죽으면:
 *
 * 1. 행이 **영원히** `TRANSLATING` 으로 남는다. 복구 경로가 없었다.
 * 2. 재요청도 소용없다 — 이미 `TRANSLATING` 인 언어는 건너뛰므로 사용자는 다시 눌러도
 *    멈춘 행만 돌려받는다. **크레딧은 나갔고 결과는 영영 오지 않는다.**
 * 3. 차감 출처가 메모리 클로저에만 있어 함께 사라진다. 나중에 환불해도 출처를 몰라
 *    구매분이 무료분으로 바뀐다.
 *
 * 이제 출처를 행에 저장하고(V109), 원자적 claim 으로 선점하며, 복구 스캐너가 멈춘 행을
 * 되살린다. 상한을 넘으면 재실행 대신 **저장된 출처로 환불**한다 — 전달하거나 돌려주거나
 * 둘 중 하나이며 멈춘 채로 두지 않는다.
 */
class TranslationRecoveryTest {

    private val translationRepository = mockk<TranslationRepository>(relaxed = true)
    private val videoRepository = mockk<VideoRepository>()
    private val creditService = mockk<CreditService>(relaxed = true)
    private val chatClientResolver = mockk<ChatClientResolver>(relaxed = true)
    private val rateLimiter = mockk<AiRateLimiter>(relaxed = true)

    private val transactionManager = mockk<PlatformTransactionManager>().also {
        every { it.getTransaction(any<TransactionDefinition>()) } returns SimpleTransactionStatus()
        every { it.commit(any<TransactionStatus>()) } returns Unit
        every { it.rollback(any<TransactionStatus>()) } returns Unit
    }

    private val useCase = TranslationUseCase(
        translationRepository = translationRepository,
        videoRepository = videoRepository,
        creditService = creditService,
        chatClientResolver = chatClientResolver,
        rateLimiter = rateLimiter,
        transactionManager = transactionManager,
    )

    private val userId = 7L
    private val videoId = 21L

    private fun row(
        id: Long,
        language: String,
        status: String = TranslationUseCase.STATUS_TRANSLATING,
        allocation: TranslationCreditAllocation? = TranslationCreditAllocation(userId, 1, mapOf(11L to 2)),
        attempts: Int = 1,
    ) = VideoTranslation(
        id = id,
        videoId = videoId,
        language = language,
        status = status,
        creditAllocation = allocation,
        attempts = attempts,
    )

    private fun givenVideo() {
        every { videoRepository.findById(videoId) } returns
            Video(id = videoId, userId = userId, title = "원본 제목", description = "설명")
    }

    // ── 요청 시 언어별 출처 저장 ─────────────────────────────────────────────

    /**
     * **작업 단위는 언어 행이다.** 요청 영수증을 쪼개 각 행이 자기 몫만 갖게 해야 한다.
     * 전체를 모든 행에 복사하면 세 행이 같은 몫을 환불해 없던 크레딧이 생긴다.
     */
    @Test
    @DisplayName("다중 언어 요청은 언어별 차감 출처를 각 행에 저장한다")
    fun multiLanguageRequestPersistsPerRowAllocation() {
        givenVideo()
        listOf("en", "ja", "zh").forEach {
            every { translationRepository.findByVideoIdAndLanguage(videoId, it) } returns null
        }
        // 무료 3 + 패키지 11 에서 6 을 가져간 차감(3개 언어 × 3크레딧)
        every { creditService.validateAndDeduct(userId, 9, "TRANSLATION") } returns
            CreditAllocation.restored(userId, "TRANSLATION", 3, mapOf(11L to 6))

        val saved = mutableListOf<VideoTranslation>()
        every { translationRepository.save(any()) } answers {
            val v = firstArg<VideoTranslation>()
            saved += v
            v.copy(id = saved.size.toLong())
        }
        every { translationRepository.findById(any()) } answers { row(firstArg(), "en") }

        useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("en", "ja", "zh")))

        assertEquals(3, saved.size)
        assertTrue(saved.all { it.creditAllocation != null }, "출처가 저장되지 않은 행이 있다")
        // 합계가 차감액과 같아야 한다. 어긋나면 어느 행이 남의 몫을 들고 있는 것이다.
        assertEquals(9, saved.sumOf { it.creditAllocation!!.total }, "언어별 몫의 합이 차감액과 다르다")
        assertEquals(3, saved.sumOf { it.creditAllocation!!.freeAmount })
        // 무료분이 먼저 소진되므로 언어마다 구성이 다르다. 중요한 것은 **합계 보존**이다.
        assertEquals(6, saved.sumOf { it.creditAllocation!!.purchasedAmounts[11L] ?: 0 })
    }

    // ── 원자적 claim ─────────────────────────────────────────────────────────

    /**
     * claim 이 없으면 복구 tick 두 개가 같은 행을 통과해 LLM 을 두 번 태운다.
     * 사용자 재시도까지 겹치면 세 번이다.
     */
    @Test
    @DisplayName("선점하지 못하면 모델을 부르지 않는다")
    fun losingTheClaimSkipsTheModelCall() {
        givenVideo()
        every { translationRepository.claimForTranslation(any(), any(), any()) } returns null
        every { translationRepository.findStalled(any(), any()) } returns listOf(row(1L, "en"))

        useCase.recoverStalledTranslations()
        Thread.sleep(200)

        verify(exactly = 0) { chatClientResolver.resolve(any()) }
        verify(exactly = 0) { creditService.refundAllocation(any()) }
    }

    // ── 재시작 복구 ──────────────────────────────────────────────────────────

    /**
     * **이것이 영구 정지를 푸는 지점이다.** 예전에는 멈춘 행을 집는 코드가 아예 없어
     * 고객이 크레딧만 잃었다.
     */
    @Test
    @DisplayName("멈춘 TRANSLATING 행을 복구 스캐너가 선점해 재개한다")
    fun stalledRowsAreClaimedAndResumed() {
        givenVideo()
        val stalled = row(1L, "en", attempts = 1)
        every { translationRepository.findStalled(any(), any()) } returns listOf(stalled)
        val claimedId = slot<Long>()
        every { translationRepository.claimForTranslation(capture(claimedId), any(), any()) } returns stalled

        useCase.recoverStalledTranslations()
        Thread.sleep(300)

        assertEquals(1L, claimedId.captured)
        verify(atLeast = 1) { chatClientResolver.resolve(userId) }
    }

    /** stale 판정 기준이 실제로 전달되는지 본다. 값이 없으면 살아 있는 작업을 뺏는다. */
    @Test
    @DisplayName("복구 스캐너는 stale 기준 시각으로 조회한다")
    fun recoveryUsesTheStaleThreshold() {
        every { translationRepository.findStalled(any(), any()) } returns emptyList()
        val before = slot<LocalDateTime>()
        val limit = slot<Int>()
        every { translationRepository.findStalled(capture(before), capture(limit)) } returns emptyList()

        useCase.recoverStalledTranslations()

        assertTrue(
            before.captured.isBefore(LocalDateTime.now().minusMinutes(TranslationUseCase.STALE_AFTER_MINUTES - 1)),
            "stale 기준이 너무 최근이다: ${before.captured}",
        )
        assertEquals(TranslationUseCase.RECOVERY_BATCH_LIMIT, limit.captured)
    }

    // ── 재시도 상한 → 환불 ───────────────────────────────────────────────────

    /**
     * 상한이 없으면 죽는 입력 하나가 LLM 호출을 무한히 태운다. 반대로 상한에서 그냥
     * 멈추면 고객은 크레딧만 잃고 영원히 `TRANSLATING` 을 본다.
     */
    @Test
    @DisplayName("재시도 상한을 넘으면 재실행 대신 저장된 출처로 환불한다")
    fun exceedingMaxAttemptsRefundsInsteadOfRerunning() {
        givenVideo()
        val exhausted = row(1L, "en", attempts = TranslationUseCase.MAX_ATTEMPTS + 1)
        every { translationRepository.findStalled(any(), any()) } returns listOf(exhausted)
        every { translationRepository.claimForTranslation(1L, any(), any()) } returns exhausted
        every { translationRepository.findById(1L) } returns exhausted
        every { translationRepository.settleFailure(1L, "FAILED") } returns true

        useCase.recoverStalledTranslations()
        Thread.sleep(300)

        verify(exactly = 0) { chatClientResolver.resolve(any()) }
        val refunded = slot<CreditAllocation>()
        verify(exactly = 1) { creditService.refundAllocation(capture(refunded)) }
        assertEquals(1, refunded.captured.freeAmount)
        assertEquals(
            listOf(CreditAllocation.PurchasedPortion(11L, 2)),
            refunded.captured.purchasedPortions,
            "구매 패키지 몫이 보존되지 않았다",
        )
    }

    // ── 환불 멱등성 (재시작을 견딘다) ────────────────────────────────────────

    /**
     * 인메모리 카운터는 재시작을 견디지 못한다. 복구 tick 과 사용자 재시도가 같은 행을
     * 두 번 환불하지 않도록 **DB 조건부 갱신**이 승자를 정해야 한다.
     */
    @Test
    @DisplayName("이미 정산된 행은 다시 환불하지 않는다")
    fun alreadySettledRowIsNotRefundedAgain() {
        givenVideo()
        val exhausted = row(1L, "en", attempts = TranslationUseCase.MAX_ATTEMPTS + 1)
        every { translationRepository.findStalled(any(), any()) } returns listOf(exhausted)
        every { translationRepository.claimForTranslation(1L, any(), any()) } returns exhausted
        every { translationRepository.findById(1L) } returns exhausted
        // 다른 워커가 먼저 정산했다.
        every { translationRepository.settleFailure(1L, "FAILED") } returns false

        useCase.recoverStalledTranslations()
        Thread.sleep(300)

        verify(exactly = 0) { creditService.refundAllocation(any()) }
        verify(exactly = 0) { creditService.refundAllocation(any(), any()) }
    }

    /**
     * V109 이전 행은 출처를 모른다. 무료분으로 돌려주면 구매분이 소실되므로
     * **자동 환불하지 않는다.** 대신 상태는 확정해 복구 스캐너가 영원히 다시 집지 않게 한다.
     */
    @Test
    @DisplayName("차감 출처가 없는 레거시 행은 자동 환불하지 않는다")
    fun legacyRowWithoutAllocationIsNotAutoRefunded() {
        givenVideo()
        val legacy = row(1L, "en", allocation = null, attempts = TranslationUseCase.MAX_ATTEMPTS + 1)
        every { translationRepository.findStalled(any(), any()) } returns listOf(legacy)
        every { translationRepository.claimForTranslation(1L, any(), any()) } returns legacy
        every { translationRepository.findById(1L) } returns legacy
        every { translationRepository.settleFailure(1L, "FAILED") } returns true

        useCase.recoverStalledTranslations()
        Thread.sleep(300)

        verify(exactly = 0) { creditService.refundAllocation(any()) }
        verify(exactly = 0) { creditService.refundAllocation(any(), any()) }
        // 상태는 확정한다 — 미정산으로 두면 스캐너가 영원히 다시 집는다.
        verify(exactly = 1) { translationRepository.settleFailure(1L, "FAILED") }
    }

    // ── 중복 요청 ────────────────────────────────────────────────────────────

    /** 진행 중인 언어는 재차감도 재실행도 하지 않는다. 멈춘 행은 스캐너 소관이다. */
    @Test
    @DisplayName("이미 진행 중인 언어는 재차감도 재실행도 하지 않는다")
    fun inFlightLanguageIsNotRestartedByDuplicateRequest() {
        givenVideo()
        every { translationRepository.findByVideoIdAndLanguage(videoId, "en") } returns row(1L, "en")

        useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("en")))

        verify(exactly = 0) { creditService.validateAndDeduct(any(), any<Int>(), any()) }
        verify(exactly = 0) { translationRepository.save(any()) }
        verify(exactly = 0) { chatClientResolver.resolve(any()) }
    }

    // ── DB 실패 재시도 ───────────────────────────────────────────────────────

    /**
     * 정산 트랜잭션이 실패하면 상태도 크레딧도 그대로여야 다음 주기가 다시 시도한다.
     * 여기서 예외가 밖으로 나가면 virtual thread 만 죽고 흔적이 남지 않는다.
     */
    @Test
    @DisplayName("정산 DB 실패는 삼키지 않고 다음 주기에 재시도한다")
    fun settlementFailureIsLoggedAndRetriable() {
        givenVideo()
        val exhausted = row(1L, "en", attempts = TranslationUseCase.MAX_ATTEMPTS + 1)
        every { translationRepository.findStalled(any(), any()) } returns listOf(exhausted)
        every { translationRepository.claimForTranslation(1L, any(), any()) } returns exhausted
        every { translationRepository.findById(1L) } returns exhausted
        every { translationRepository.settleFailure(1L, "FAILED") } throws IllegalStateException("DB 장애")

        // 예외가 밖으로 나가면 배치가 죽는다. 삼키되 로그로 남긴다.
        useCase.recoverStalledTranslations()
        Thread.sleep(300)

        verify(exactly = 0) { creditService.refundAllocation(any()) }
    }

    /**
     * 원본 영상이 사라지면 재실행할 대상이 없다. 멈춘 채로 두지 않고 정산으로 닫는다.
     *
     * **환불 대상은 스냅샷의 userId 다.** 예전에는 이 경로가
     * `failAndRefund(id, row.videoId, ...)` 로 **videoId 를 userId 자리에** 넘겼다.
     * videos.user_id 로 되짚을 수도 없는 상황이라(영상이 없다) 그대로면 존재하지 않거나
     * **남의 계정으로 크레딧이 들어간다.**
     */
    @Test
    @DisplayName("원본 영상이 없어도 스냅샷의 userId 로 환불한다")
    fun missingSourceVideoRefundsToTheSnapshotUser() {
        every { videoRepository.findById(videoId) } returns null
        val stalled = row(1L, "en")
        every { translationRepository.findStalled(any(), any()) } returns listOf(stalled)
        every { translationRepository.findById(1L) } returns stalled
        every { translationRepository.settleFailure(1L, "FAILED") } returns true

        useCase.recoverStalledTranslations()
        Thread.sleep(300)

        verify(exactly = 0) { chatClientResolver.resolve(any()) }
        verify(exactly = 1) { translationRepository.settleFailure(1L, "FAILED") }

        val refunded = slot<CreditAllocation>()
        verify(exactly = 1) { creditService.refundAllocation(capture(refunded)) }
        // videoId(21) 가 아니라 실제 userId(7) 여야 한다.
        assertEquals(userId, refunded.captured.userId, "videoId 를 userId 자리에 넘겼다")
        assertEquals(1, refunded.captured.freeAmount)
        assertEquals(
            listOf(CreditAllocation.PurchasedPortion(11L, 2)),
            refunded.captured.purchasedPortions,
        )
    }

    /**
     * 원본도 스냅샷도 없으면 **환불 대상을 확정할 수 없다.** 추측한 계정에 크레딧을 넣는
     * 것은 아무것도 안 하는 것보다 나쁘다. 자동 환불을 막고 수기 정산으로 넘긴다.
     */
    @Test
    @DisplayName("원본도 스냅샷도 없으면 환불 대상을 추측하지 않는다")
    fun missingVideoAndSnapshotDoesNotGuessTheUser() {
        every { videoRepository.findById(videoId) } returns null
        val legacy = row(1L, "en", allocation = null)
        every { translationRepository.findStalled(any(), any()) } returns listOf(legacy)
        every { translationRepository.findById(1L) } returns legacy
        every { translationRepository.settleFailure(1L, "FAILED") } returns true

        useCase.recoverStalledTranslations()
        Thread.sleep(300)

        verify(exactly = 0) { creditService.refundAllocation(any()) }
        verify(exactly = 0) { creditService.refundAllocation(any(), any()) }
        verify(exactly = 1) { translationRepository.settleFailure(1L, "FAILED") }
    }

    // ── 중복 언어 정규화 ─────────────────────────────────────────────────────

    /**
     * `["en","en","ja"]` 은 예전에 비용을 9 크레딧으로 계산해 차감한 뒤, 두 번째 `en`
     * INSERT 가 `uq_video_translations_video_language` 에 걸려 **트랜잭션이 통째로
     * 롤백**됐다. 사용자는 이유를 알 수 없는 실패를 받았다.
     */
    @Test
    @DisplayName("중복 언어는 고유 언어 수 기준으로 차감한다")
    fun duplicateLanguagesAreChargedOnce() {
        givenVideo()
        listOf("en", "ja").forEach {
            every { translationRepository.findByVideoIdAndLanguage(videoId, it) } returns null
        }
        every { creditService.validateAndDeduct(userId, any<Int>(), "TRANSLATION") } returns
            CreditAllocation.restored(userId, "TRANSLATION", 6, emptyMap())
        every { translationRepository.save(any()) } answers { firstArg<VideoTranslation>().copy(id = 1L) }
        every { translationRepository.findById(any()) } answers { row(firstArg(), "en") }

        useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("en", "en", "ja")))

        // 3 개가 아니라 2 개 언어분이다.
        verify(exactly = 1) { creditService.validateAndDeduct(userId, 6, "TRANSLATION") }
        verify(exactly = 0) { creditService.validateAndDeduct(userId, 9, "TRANSLATION") }
    }

    /** 레이트리밋도 고유 언어 수만큼 쓴다. 중복을 세면 한도가 헛돈다. */
    @Test
    @DisplayName("중복 언어는 레이트리밋 토큰도 고유 언어 수만큼만 쓴다")
    fun duplicateLanguagesConsumeTokensOnce() {
        givenVideo()
        listOf("en", "ja").forEach {
            every { translationRepository.findByVideoIdAndLanguage(videoId, it) } returns null
        }
        every { creditService.validateAndDeduct(userId, any<Int>(), "TRANSLATION") } returns
            CreditAllocation.restored(userId, "TRANSLATION", 6, emptyMap())
        every { translationRepository.save(any()) } answers { firstArg<VideoTranslation>().copy(id = 1L) }
        every { translationRepository.findById(any()) } answers { row(firstArg(), "en") }

        useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("en", "en", "ja")))

        verify(exactly = 1) { rateLimiter.checkRateLimit(userId, 2) }
    }

    /** 중복이 유니크 제약에 걸리지 않도록 저장도 언어당 한 번이다. */
    @Test
    @DisplayName("중복 언어는 행을 한 번만 만든다")
    fun duplicateLanguagesSaveOnlyOnce() {
        givenVideo()
        listOf("en", "ja").forEach {
            every { translationRepository.findByVideoIdAndLanguage(videoId, it) } returns null
        }
        every { creditService.validateAndDeduct(userId, any<Int>(), "TRANSLATION") } returns
            CreditAllocation.restored(userId, "TRANSLATION", 6, emptyMap())
        val saved = mutableListOf<VideoTranslation>()
        every { translationRepository.save(any()) } answers {
            val v = firstArg<VideoTranslation>()
            saved += v
            v.copy(id = saved.size.toLong())
        }
        every { translationRepository.findById(any()) } answers { row(firstArg(), "en") }

        val responses = useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("en", "en", "ja")))

        assertEquals(listOf("en", "ja"), saved.map { it.language })
        assertEquals(2, responses.size, "응답에도 중복이 남았다")
    }

    /** 진행 중인 언어를 중복 요청해도 차감이 늘지 않는다. */
    @Test
    @DisplayName("진행 중인 언어를 중복 요청해도 차감하지 않는다")
    fun duplicateOfInFlightLanguageChargesNothing() {
        givenVideo()
        every { translationRepository.findByVideoIdAndLanguage(videoId, "en") } returns row(1L, "en")

        useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("en", "en")))

        verify(exactly = 0) { creditService.validateAndDeduct(any(), any<Int>(), any()) }
        verify(exactly = 0) { rateLimiter.checkRateLimit(any(), any()) }
    }
}
