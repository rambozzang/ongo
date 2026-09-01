package com.ongo.application.ai

import com.ongo.application.credit.CreditService
import com.ongo.application.translation.TranslationUseCase
import com.ongo.application.translation.dto.TranslateRequest
import com.ongo.application.trend.TrendUseCase
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.PlanType
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.ai.WeeklyDigest
import com.ongo.domain.ai.WeeklyDigestRepository
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.DashboardKpi
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.translation.TranslationRepository
import com.ongo.domain.translation.VideoTranslation
import com.ongo.domain.trend.TrendRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 유료 LLM 경로가 **공통 [AiRateLimiter] 로 보호되는지** 고정한다.
 *
 * ## 왜 필요한가
 *
 * 크레딧 차감만으로는 부족하다. 크레딧이 넉넉한 사용자(또는 스크립트)가 초당 수십 건을
 * 밀어 넣으면 모델 비용과 커넥션이 그대로 열린다. `AiRateLimiter` 는 그 상한이다.
 *
 * 정적 감사에서 **직접 `chatClientResolver` 를 부르면서 limiter 가 없는** 유료 경로가
 * 셋 나왔다: [TrendUseCase.analyzeTrends](5 크레딧),
 * [WeeklyDigestUseCase.generateDigest](8 크레딧),
 * [TranslationUseCase.requestTranslation](언어당 3 크레딧).
 *
 * ## 순서가 계약이다
 *
 * limiter 는 **차감·상태 저장보다 먼저** 걸려야 한다. 뒤에 두면 거절된 요청이 이미
 * 크레딧을 깎고 `TRANSLATING` 행을 남긴 뒤다. 그래서 아래 테스트들은 "예외가 났다" 로
 * 끝내지 않고 **부작용이 0 인지**까지 본다.
 *
 * 무료 감정 분석과 Shorts orchestrator 의 중앙 제한은 이미 있으므로 건드리지 않는다.
 */
class PaidAiRateLimitTest {

    private val userId = 7L
    private val rateLimiter = mockk<AiRateLimiter>()

    private fun rateLimitExceeded() {
        every { rateLimiter.checkRateLimit(userId) } throws AiRateLimitExceededException()
        every { rateLimiter.checkRateLimit(userId, any()) } throws AiRateLimitExceededException()
    }

    private fun rateLimitPasses() {
        every { rateLimiter.checkRateLimit(userId) } returns Unit
        every { rateLimiter.checkRateLimit(userId, any()) } returns Unit
    }

    // ── 트렌드 분석 (5 크레딧, LLM 1 회) ──────────────────────────────────────

    @Nested
    inner class Trend {
        private val trendRepository = mockk<TrendRepository>()
        private val creditService = mockk<CreditService>()
        private val chatClientResolver = mockk<ChatClientResolver>()

        private val useCase = TrendUseCase(trendRepository, creditService, chatClientResolver, rateLimiter)

        @Test
        @DisplayName("트렌드 분석은 레이트리밋을 1회 확인한다")
        fun checksRateLimitOnce() {
            rateLimitPasses()
            every { trendRepository.findByDate(any(), any()) } returns emptyList()
            every {
                creditService.withCredits(userId, TrendUseCase.ANALYSIS_CREDIT_COST, "TREND_ANALYSIS", any<() -> Any>())
            } throws IllegalStateException("여기까지 왔으면 limiter 는 통과했다")

            assertFailsWith<IllegalStateException> { useCase.analyzeTrends(userId, null) }

            // LLM 호출이 1 회이므로 토큰도 1 개다.
            verify(exactly = 1) { rateLimiter.checkRateLimit(userId) }
        }

        /**
         * limiter 는 **조회보다도 먼저**다. 뒤에 두면 제한된 사용자도 DB 를 읽는다.
         * 기존 유료 경로(`FaqClusteringUseCase` 등)가 전부 이 순서다.
         */
        @Test
        @DisplayName("한도 초과면 조회·차감·AI 가 모두 일어나지 않는다")
        fun rateLimitedTrendHasNoSideEffects() {
            rateLimitExceeded()

            assertFailsWith<AiRateLimitExceededException> { useCase.analyzeTrends(userId, null) }

            verify(exactly = 0) { trendRepository.findByDate(any(), any()) }
            verify(exactly = 0) { creditService.withCredits(any(), any<Int>(), any(), any<() -> Any>()) }
            verify(exactly = 0) { chatClientResolver.resolve(any()) }
        }

        /** 기존 과금 계약(5 크레딧)은 그대로다. */
        @Test
        @DisplayName("트렌드 분석 요금은 5 크레딧 그대로다")
        fun creditCostUnchanged() {
            assertEquals(5, TrendUseCase.ANALYSIS_CREDIT_COST)
        }
    }

    // ── 주간 다이제스트 (8 크레딧, LLM 1 회) ──────────────────────────────────

    @Nested
    inner class Digest {
        private val chatClientResolver = mockk<ChatClientResolver>()
        private val creditService = mockk<CreditService>()
        private val analyticsRepository = mockk<AnalyticsRepository>()
        private val weeklyDigestRepository = mockk<WeeklyDigestRepository>()
        private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
        private val userWriteGuard = mockk<UserWriteGuard>(relaxed = true)

        private val useCase = WeeklyDigestUseCase(
            chatClientResolver = chatClientResolver,
            creditService = creditService,
            rateLimiter = rateLimiter,
            analyticsRepository = analyticsRepository,
            weeklyDigestRepository = weeklyDigestRepository,
            subscriptionRepository = subscriptionRepository,
            userWriteGuard = userWriteGuard,
        )

        private val weekStart = LocalDate.of(2026, 8, 17)
        private val weekEnd = LocalDate.of(2026, 8, 23)

        private fun givenAnalytics() {
            every { analyticsRepository.getDashboardKpi(userId, 7) } returns DashboardKpi(
                totalViews = 50_000, totalViewsChange = 12.0,
                totalSubscribers = 100, totalSubscribersChange = 12,
                totalLikes = 1_200, totalLikesChange = 5.0,
                creditBalance = 500, creditTotal = 1000, totalComments = 34,
            )
            every { analyticsRepository.getTopVideos(userId, 7, 3) } returns listOf(
                Video(id = 1, userId = userId, title = "테스트 영상"),
            )
        }

        @Test
        @DisplayName("다이제스트 생성은 레이트리밋을 1회 확인한다")
        fun checksRateLimitOnce() {
            rateLimitPasses()
            givenAnalytics()
            every {
                creditService.withCredits(userId, AiFeature.WEEKLY_DIGEST, any<() -> WeeklyDigest>())
            } throws IllegalStateException("여기까지 왔으면 limiter 는 통과했다")

            assertFailsWith<IllegalStateException> { useCase.generateDigest(userId, weekStart, weekEnd) }

            verify(exactly = 1) { rateLimiter.checkRateLimit(userId) }
        }

        /** **차감·조회·저장보다 먼저**여야 한다. */
        @Test
        @DisplayName("한도 초과면 KPI 조회·차감·AI·저장이 모두 일어나지 않는다")
        fun rateLimitedDigestHasNoSideEffects() {
            rateLimitExceeded()

            assertFailsWith<AiRateLimitExceededException> {
                useCase.generateDigest(userId, weekStart, weekEnd)
            }

            verify(exactly = 0) { analyticsRepository.getDashboardKpi(any(), any()) }
            verify(exactly = 0) { creditService.withCredits(any(), any<AiFeature>(), any<() -> Any>()) }
            verify(exactly = 0) { chatClientResolver.resolve(any()) }
            verify(exactly = 0) { weeklyDigestRepository.save(any()) }
        }

        // ── 스케줄러 회귀 ────────────────────────────────────────────────────
        //
        // 버킷은 사용자당 분당 10 회이고 스케줄러는 사용자당 주 1 회다. 구조적으로 닿지
        // 않는다. 다만 버킷을 **모든 AI 기능이 공유**하므로, 같은 분에 대화형 AI 를 한도까지
        // 쓴 사용자의 예약 다이제스트는 거절될 수 있다. 그때 배치가 죽거나 그 건이 AI 장애로
        // 집계되면 안 된다.

        private fun scheduler(): WeeklyDigestScheduler {
            val subscriptionRepository = mockk<SubscriptionRepository>()
            every { subscriptionRepository.findByPlanType(PlanType.PRO) } returns
                listOf(Subscription(id = userId, userId = userId, planType = PlanType.PRO))
            every { subscriptionRepository.findByPlanType(PlanType.BUSINESS) } returns emptyList()
            return WeeklyDigestScheduler(subscriptionRepository, useCase, mockk(relaxed = true))
        }

        @Test
        @DisplayName("한도에 걸리지 않은 정상 사용자는 스케줄러에서 그대로 생성된다")
        fun schedulerStillGeneratesForNormalUsers() {
            rateLimitPasses()
            givenAnalytics()
            every {
                creditService.withCredits(userId, AiFeature.WEEKLY_DIGEST, any<() -> WeeklyDigest>())
            } answers { thirdArg<() -> WeeklyDigest>().invoke() }
            val requestSpec = mockk<org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec>()
            val callSpec = mockk<org.springframework.ai.chat.client.ChatClient.CallResponseSpec>()
            val chatClient = mockk<org.springframework.ai.chat.client.ChatClient>()
            every { chatClientResolver.resolve(userId) } returns chatClient
            every { chatClient.prompt() } returns requestSpec
            every { requestSpec.system(any<String>()) } returns requestSpec
            every { requestSpec.user(any<String>()) } returns requestSpec
            every { requestSpec.call() } returns callSpec
            every {
                callSpec.entity(com.ongo.application.ai.result.WeeklyDigestResult::class.java)
            } returns com.ongo.application.ai.result.WeeklyDigestResult("요약", emptyList(), emptyList(), emptyList())
            every { weeklyDigestRepository.save(any()) } answers { firstArg<WeeklyDigest>() }

            scheduler().generateWeeklyDigests()

            // limiter 가 정상 사용자를 막으면 여기서 저장이 0 회가 된다.
            verify(exactly = 1) { weeklyDigestRepository.save(any()) }
            verify(exactly = 1) { rateLimiter.checkRateLimit(userId) }
        }

        @Test
        @DisplayName("한도에 걸린 사용자는 배치를 죽이지 않고 건너뛴다")
        fun schedulerSkipsRateLimitedUserWithoutDying() {
            rateLimitExceeded()

            // 예외가 밖으로 나가면 뒤 사용자들이 다이제스트를 못 받는다.
            scheduler().generateWeeklyDigests()

            verify(exactly = 0) { chatClientResolver.resolve(any()) }
            verify(exactly = 0) { weeklyDigestRepository.save(any()) }
        }
    }

    // ── 번역 (언어당 3 크레딧, 언어당 LLM 1 회) ───────────────────────────────

    @Nested
    inner class Translation {
        private val translationRepository = mockk<TranslationRepository>()
        private val videoRepository = mockk<VideoRepository>()
        private val creditService = mockk<CreditService>()
        private val chatClientResolver = mockk<ChatClientResolver>()
        /** 정산 트랜잭션 경계는 이 테스트의 관심사가 아니다. 콜백을 그대로 실행한다. */
        private val directTransactionManager = mockk<org.springframework.transaction.PlatformTransactionManager>().also {
            every { it.getTransaction(any<org.springframework.transaction.TransactionDefinition>()) } returns
                org.springframework.transaction.support.SimpleTransactionStatus()
            every { it.commit(any<org.springframework.transaction.TransactionStatus>()) } returns Unit
            every { it.rollback(any<org.springframework.transaction.TransactionStatus>()) } returns Unit
        }

        private val useCase = TranslationUseCase(
            translationRepository, videoRepository, creditService, chatClientResolver, rateLimiter, directTransactionManager,
        )

        private val videoId = 21L

        private fun givenVideo() {
            every { videoRepository.findById(videoId) } returns
                Video(id = videoId, userId = userId, title = "원본 제목", description = "설명")
        }

        private fun givenNoExistingTranslations(vararg langs: String) {
            langs.forEach { every { translationRepository.findByVideoIdAndLanguage(videoId, it) } returns null }
        }

        /**
         * **fan-out 정책의 핵심.** `startingLangs` 가 실제 비동기 LLM 호출 수이고
         * [AiRateLimiter] 의 토큰 1 개는 LLM 요청 1 회를 뜻한다. 요청당 1 토큰으로 하면
         * 8 개 언어를 요청해 분당 80 회를 태울 수 있다 — 제한이 의미를 잃는다.
         */
        @Test
        @DisplayName("번역은 언어 수만큼 토큰을 쓴다")
        fun consumesOneTokenPerLanguage() {
            rateLimitPasses()
            givenVideo()
            givenNoExistingTranslations("en", "ja", "zh")
            every { creditService.validateAndDeduct(userId, any<Int>(), "TRANSLATION") } throws
                IllegalStateException("여기까지 왔으면 limiter 는 통과했다")

            assertFailsWith<IllegalStateException> {
                useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("en", "ja", "zh")))
            }

            verify(exactly = 1) { rateLimiter.checkRateLimit(userId, 3) }
        }

        /**
         * 이미 `TRANSLATING` 인 언어는 새 LLM 호출을 만들지 않는다 — 토큰도 쓰지 않는다.
         * 여기서 3 을 쓰면 재요청할 때마다 한도가 헛돌아 정상 사용자를 막는다.
         */
        @Test
        @DisplayName("이미 진행 중인 언어는 토큰을 쓰지 않는다")
        fun inFlightLanguagesDoNotConsumeTokens() {
            rateLimitPasses()
            givenVideo()
            every { translationRepository.findByVideoIdAndLanguage(videoId, "en") } returns
                VideoTranslation(id = 1, videoId = videoId, language = "en", status = TranslationUseCase.STATUS_TRANSLATING)
            every { translationRepository.findByVideoIdAndLanguage(videoId, "ja") } returns null
            every { creditService.validateAndDeduct(userId, any<Int>(), "TRANSLATION") } throws
                IllegalStateException("여기까지 왔으면 limiter 는 통과했다")

            assertFailsWith<IllegalStateException> {
                useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("en", "ja")))
            }

            // 새로 시작하는 언어는 ja 하나뿐이다.
            verify(exactly = 1) { rateLimiter.checkRateLimit(userId, 1) }
        }

        /** 새로 시작할 언어가 없으면 LLM 도 없다. limiter 를 부르는 것 자체가 잘못이다. */
        @Test
        @DisplayName("모든 언어가 진행 중이면 limiter 를 아예 부르지 않는다")
        fun noStartingLanguagesSkipsTheLimiter() {
            givenVideo()
            every { translationRepository.findByVideoIdAndLanguage(videoId, "en") } returns
                VideoTranslation(id = 1, videoId = videoId, language = "en", status = TranslationUseCase.STATUS_TRANSLATING)

            useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("en")))

            verify(exactly = 0) { rateLimiter.checkRateLimit(any()) }
            verify(exactly = 0) { rateLimiter.checkRateLimit(any(), any()) }
            verify(exactly = 0) { creditService.validateAndDeduct(any(), any<Int>(), any()) }
        }

        /**
         * limiter 실패가 **차감과 `TRANSLATING` 저장보다 먼저**여야 한다. 뒤에 있으면
         * 거절된 요청이 크레딧을 깎고 영원히 `TRANSLATING` 인 행을 남긴다.
         */
        @Test
        @DisplayName("한도 초과면 차감도 TRANSLATING 저장도 AI 도 일어나지 않는다")
        fun rateLimitedTranslationHasNoSideEffects() {
            rateLimitExceeded()
            givenVideo()
            givenNoExistingTranslations("en", "ja")

            assertFailsWith<AiRateLimitExceededException> {
                useCase.requestTranslation(userId, videoId, TranslateRequest(listOf("en", "ja")))
            }

            verify(exactly = 0) { creditService.validateAndDeduct(any(), any<Int>(), any()) }
            verify(exactly = 0) { translationRepository.save(any()) }
            verify(exactly = 0) { translationRepository.update(any(), any(), any(), any(), any(), any()) }
            verify(exactly = 0) { chatClientResolver.resolve(any()) }
        }

        /** 기존 과금 계약(언어당 3 크레딧)은 그대로다. */
        @Test
        @DisplayName("번역 요금은 언어당 3 크레딧 그대로다")
        fun creditCostUnchanged() {
            assertEquals(3, TranslationUseCase.CREDIT_PER_LANGUAGE)
        }
    }
}
