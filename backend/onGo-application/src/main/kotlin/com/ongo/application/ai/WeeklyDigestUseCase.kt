package com.ongo.application.ai

import com.ongo.application.ai.dto.WeeklyDigestResponse
import com.ongo.application.ai.result.WeeklyDigestResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.enums.PlanType
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.ai.WeeklyDigest
import com.ongo.domain.ai.WeeklyDigestRepository
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.MetricChange
import com.ongo.domain.subscription.SubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class WeeklyDigestUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val analyticsRepository: AnalyticsRepository,
    private val weeklyDigestRepository: WeeklyDigestRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val userWriteGuard: UserWriteGuard,
) {

    private val log = LoggerFactory.getLogger(WeeklyDigestUseCase::class.java)

    fun generateDigest(userId: Long, weekStartDate: LocalDate, weekEndDate: LocalDate): WeeklyDigest {
        /*
         * 차감·조회·저장보다 **먼저** 본다. 뒤에 두면 제한된 요청도 KPI 를 읽고
         * 크레딧을 깎은 뒤에야 거절된다.
         *
         * 아래 블록의 LLM 호출은 정확히 1 회이므로 토큰도 1 개다.
         *
         * 스케줄러(주 1 회/사용자)는 이 한도(사용자당 분당 10 회)에 구조적으로 닿지
         * 않는다. 다만 버킷은 **모든 AI 기능이 공유**하므로, 같은 분에 대화형 AI 를 10 회
         * 쓴 사용자의 예약 다이제스트는 거절될 수 있다. 그 경우를
         * [WeeklyDigestScheduler] 가 장애가 아닌 건너뜀으로 분류한다.
         */
        rateLimiter.checkRateLimit(userId)

        val kpi = analyticsRepository.getDashboardKpi(userId, 7)
        val topVideos = analyticsRepository.getTopVideos(userId, 7, 3)

        val topVideosStr = topVideos.mapIndexed { i, v ->
            "${i + 1}. ${v.title}"
        }.joinToString("\n").ifEmpty { "데이터 없음" }

        // 전주 대비 변화율은 [DashboardKpi] 가 이미 [MetricChange] 로 계산해 들고 있다.
        //
        // 예전에는 여기서 다시 계산했는데 두 가지가 틀렸다.
        //
        // 1. 이전 기간을 `getDashboardKpi(userId, 14)` 의 **총합**으로 잡았다. 14일 총합은
        //    최근 7일을 포함하므로 분모가 분자를 삼킨다. 조회수가 음수일 수 없는 이상
        //    이 식은 **구조적으로 0 이하만 나온다** — 조회수가 폭증한 주에도 AI 는 하락을
        //    통보받았다. 7일 KPI 의 change 는 겹치지 않는 직전 7일을 분모로 쓴다.
        // 2. 분모가 0 이면 `0.0` 으로 채웠다. 비교할 데이터가 없다는 사실이 "변화 없음"
        //    이라는 측정 결과로 둔갑해 AI 프롬프트에 들어갔다.
        //
        // `null` 은 비교 불가다. [MetricChange.describePercent] 가 `%` 단위까지 붙인
        // 문장을 만든다 — 템플릿에 `%` 를 따로 붙이면 "비교 불가%" 가 된다.
        val viewsChange = MetricChange.describePercent(kpi.totalViewsChange)
        val likesChange = MetricChange.describePercent(kpi.totalLikesChange)

        val userPrompt = PromptTemplates.WEEKLY_DIGEST_USER
            .replace("{weekStart}", weekStartDate.toString())
            .replace("{weekEnd}", weekEndDate.toString())
            /*
             * **`Long?.toString()` 은 문자열 `"null"` 을 만든다.** 세 지표는 수집하는
             * 플랫폼이 없으면 `null` 이므로 [MetricChange.describeCount] 로 문장을 만든다.
             * 이 문장에는 단위가 없다 — 템플릿에도 붙어 있지 않다. **실측 0 은 `"0"`.**
             */
            .replace("{totalViews}", MetricChange.describeCount(kpi.totalViews))
            .replace("{viewsChange}", viewsChange)
            .replace("{totalLikes}", MetricChange.describeCount(kpi.totalLikes))
            .replace("{likesChange}", likesChange)
            .replace("{totalComments}", MetricChange.describeCount(kpi.totalComments))
            .replace("{subscriberChange}", MetricChange.describeCount(kpi.totalSubscribersChange))
            .replace("{topVideos}", topVideosStr)

        // 여기부터가 유료 구간이다. KPI 조회·프롬프트 조립은 위에서 끝냈다 — 그 단계가
        // 실패하면 AI 를 부르지 않았으므로 과금할 이유가 없다.
        //
        // [CreditService.withCredits] 는 차감을 먼저 커밋한 뒤 블록을 돌리고, 블록이
        // 던지면 환불한다. 그래서 AI 실패·파싱 실패·동결·저장 실패가 모두 환불 경계
        // 안에 들어와야 한다. 저장을 블록 밖으로 빼면 저장 실패가 조용히 과금으로 남는다.
        //
        // [InsufficientCreditException] 은 블록이 돌기 전에 던져지므로 아래 catch 에
        // 잡히지 않는다. 잔액 부족이 `AI_CALL_FAILED` 로 둔갑하면 사용자는 충전하면
        // 된다는 것을 알 수 없다.
        return creditService.withCredits(userId, AiFeature.WEEKLY_DIGEST) {
            try {
                val result = chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.WEEKLY_DIGEST_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(WeeklyDigestResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")

                val digest = WeeklyDigest(
                    userId = userId,
                    weekStartDate = weekStartDate,
                    weekEndDate = weekEndDate,
                    summary = result.summary,
                    topVideos = result.topVideos.joinToString("\n") { "${it.title}: ${it.insight}" },
                    anomalies = result.anomalies.joinToString("\n"),
                    actionItems = result.actionItems.joinToString("\n"),
                    generatedAt = LocalDateTime.now(),
                )

                // AI 호출이 끝났다. 저장 직전에 게이트를 다시 본다.
                // 위 chatClientResolver 호출은 수 초가 걸릴 수 있고 그 사이 탈퇴 요청이
                // 들어올 수 있다. 호출 전 검사만 믿으면 동결된 계정에 쓴다.
                userWriteGuard.requireWritable(userId)

                weeklyDigestRepository.save(digest)
            } catch (e: AccountFrozenException) {
                // 동결은 AI 실패가 아니다. 아래 catch 에 잡혀 AI_CALL_FAILED 로 래핑되면
                // 로그·지표에서 동결 건너뜀과 AI 장애를 구분할 수 없고, 재시도 분류도 틀어진다.
                // AI 장애는 재시도할 만하지만 동결은 재시도해도 계속 막힌다.
                // 어느 쪽이든 withCredits 가 환불한다 — 결과를 못 받았으니 과금하지 않는다.
                throw e
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("주간 다이제스트 생성 실패: userId={}", userId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }
        }
    }

    fun getLatestDigest(userId: Long): WeeklyDigestResponse {
        requireDigestAccess(userId)
        val digest = weeklyDigestRepository.findLatestByUserId(userId)
            ?: throw NotFoundException("주간 다이제스트", 0)
        return digest.toResponse()
    }

    fun listDigests(userId: Long, page: Int, size: Int): List<WeeklyDigestResponse> {
        requireDigestAccess(userId)
        return weeklyDigestRepository.findByUserId(userId, page, size)
            .map { it.toResponse() }
    }

    /**
     * 다이제스트는 Pro/Business의 유료 반복 가치다. 예전에 조회 API에 이 검사가 없어
     * 사용자가 유료 플랜에서 무료로 내려간 뒤에도 이전에 생성된 다이제스트를 계속 읽을 수
     * 있었다. 생성 스케줄러의 대상 필터만으로는 과거 데이터 조회를 막을 수 없으므로,
     * 저장소에서 읽기 직전에 현재 구독을 다시 확인한다.
     */
    private fun requireDigestAccess(userId: Long) {
        val planType = subscriptionRepository.findByUserId(userId)?.planType
        if (planType != PlanType.PRO && planType != PlanType.BUSINESS) {
            throw ForbiddenException("주간 다이제스트는 Pro/Business 플랜에서 사용할 수 있습니다.")
        }
    }

    private fun WeeklyDigest.toResponse() = WeeklyDigestResponse(
        id = id!!,
        weekRange = "$weekStartDate ~ $weekEndDate",
        summary = summary,
        topVideos = topVideos.split("\n").filter { it.isNotBlank() },
        anomalies = anomalies.split("\n").filter { it.isNotBlank() },
        actionItems = actionItems.split("\n").filter { it.isNotBlank() },
        generatedAt = generatedAt,
    )
}
