package com.ongo.application.benchmark

import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.analytics.ChannelSubscriberTotal
import com.ongo.application.analytics.PlatformMetricAvailability
import com.ongo.application.ai.InputSanitizer
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.result.EngagementBenchmarkResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.CrossPlatformRaw
import com.ongo.domain.analytics.MetricChange
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.settings.UserSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class EngagementBenchmarkUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val channelRepository: ChannelRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val userSettingsRepository: UserSettingsRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * **트랜잭션을 열지 않는다.** LLM 호출을 `@Transactional` 안에 두면 `ai_credits` 행
     * 잠금과 DB 커넥션이 모델 응답 시간만큼 묶인다. 차감·환불의 커밋 경계는
     * [CreditService.withCredits] 가 잡는다.
     */
    fun execute(userId: Long): EngagementBenchmarkResult {
        rateLimiter.checkRateLimit(userId)

        val channels = channelRepository.findByUserId(userId)
        /*
         * 구독자 수를 **조회하는 채널만** 더한다. Threads·LinkedIn 어댑터는 팔로워 수를
         * 묻지도 않고 `subscriberCount = 0` 을 박아 넣으므로, 그대로 더하면 재지 않은
         * 채널이 "구독자 0 명" 으로 벤치마크의 기준선이 된다.
         */
        val totalSubscribers = ChannelSubscriberTotal.measuredTotal(channels)
        val category = userSettingsRepository.findByUserId(userId)?.defaultAiTone ?: "일반"

        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(30)

        val crossPlatformMetrics = analyticsRepository.findCrossPlatformMetrics(userId, 30)

        val platformDataStr = if (crossPlatformMetrics.isEmpty()) {
            "데이터 없음"
        } else {
            crossPlatformMetrics
                .groupBy { it.platform }
                .entries
                .joinToString("\n") { (platform, metrics) ->
                    /*
                     * **플랫폼이 실제로 수집하는 지표만 프롬프트에 넣는다.**
                     *
                     * `TumblrClient.kt:141` 의 `views` 는 `total_notes`(노트 총합),
                     * `PinterestClient.kt:158` 의 `likes` 는 `SAVE`(저장) 수다. 하드코딩 0 과
                     * 달리 **다른 뜻의 큰 숫자**라, 그대로 넣으면 모델이 그것을 조회수·
                     * 좋아요로 읽고 없는 성과를 설명한다. 유료 호출이라 더 나쁘다.
                     */
                    fun reports(metric: String) = PlatformMetricAvailability.isAvailable(platform, metric)
                    fun describe(metric: String, value: () -> Long): String =
                        if (reports(metric)) value().toString() else NOT_COLLECTED

                    val views = if (reports(PlatformMetricAvailability.VIEWS)) metrics.sumOf { it.views } else null
                    val likes = if (reports(PlatformMetricAvailability.LIKES)) metrics.sumOf { it.likes } else null
                    val comments =
                        if (reports(PlatformMetricAvailability.COMMENTS)) metrics.sumOf { it.comments } else null

                    /*
                     * 참여율은 분자와 분모가 **같은 관측**에서 나와야 한다. 분모가 없거나
                     * 분자 지표를 하나도 수집하지 않으면 비율이 성립하지 않는다 —
                     * `"0.00"` 은 "참여가 없었다" 는 관측이 된다.
                     */
                    val numerator = listOfNotNull(likes, comments)
                    val engRate = if (views != null && views > 0 && numerator.isNotEmpty()) {
                        String.format("%.2f%%", numerator.sum().toDouble() / views * 100)
                    } else {
                        NOT_COLLECTED
                    }

                    "- $platform: 조회수 ${describe(PlatformMetricAvailability.VIEWS) { views ?: 0 }}, " +
                        "좋아요 ${describe(PlatformMetricAvailability.LIKES) { likes ?: 0 }}, " +
                        "댓글 ${describe(PlatformMetricAvailability.COMMENTS) { comments ?: 0 }}, " +
                        "참여율 $engRate"
                }
        }

        /*
         * **전체 평균도 지표별로 수집하는 행만 더한다.**
         *
         * 플랫폼별 요약만 고치고 여기를 두면 같은 프롬프트 안에서 두 숫자가 서로 다른
         * 규칙을 쓰게 된다 — 모델은 그 모순을 설명하려 든다.
         */
        fun rowsReporting(metric: String) =
            crossPlatformMetrics.filter { PlatformMetricAvailability.isAvailable(it.platform, metric) }

        val viewRows = rowsReporting(PlatformMetricAvailability.VIEWS)
        val likeRows = rowsReporting(PlatformMetricAvailability.LIKES)
        val commentRows = rowsReporting(PlatformMetricAvailability.COMMENTS)

        val totalViews = viewRows.sumOf { it.views }
        // 참여율의 분자와 분모는 **같은 행**에서 나와야 한다.
        val engagementRows = crossPlatformMetrics.filter { row ->
            PlatformMetricAvailability.isAvailable(row.platform, PlatformMetricAvailability.VIEWS) &&
                listOf(PlatformMetricAvailability.LIKES, PlatformMetricAvailability.COMMENTS)
                    .any { PlatformMetricAvailability.isAvailable(row.platform, it) }
        }
        val engagementViews = engagementRows.sumOf { it.views }
        val overallEngRate = if (engagementViews > 0) {
            val numerator = engagementRows.sumOf { row ->
                fun measured(metric: String, value: Long) =
                    if (PlatformMetricAvailability.isAvailable(row.platform, metric)) value else 0L
                measured(PlatformMetricAvailability.LIKES, row.likes) +
                    measured(PlatformMetricAvailability.COMMENTS, row.comments)
            }
            /*
             * **단위(`%`)를 값이 직접 들고 있다.** 템플릿에 `{engagementRate}%` 처럼
             * 붙여 두면 미측정일 때 `측정 불가(...)%` 라는 문장이 만들어진다.
             * `MetricChange.describePercent` 와 같은 이유다.
             */
            String.format("%.2f%%", numerator.toDouble() / engagementViews * 100)
        } else {
            NOT_COLLECTED
        }

        fun average(rows: List<CrossPlatformRaw>, pick: (CrossPlatformRaw) -> Long): String =
            if (rows.isEmpty()) NOT_COLLECTED else (rows.sumOf(pick) / rows.size).toString()

        val avgViews = average(viewRows) { it.views }
        val avgLikes = average(likeRows) { it.likes }
        val avgComments = average(commentRows) { it.comments }

        val userPrompt = PromptTemplates.ENGAGEMENT_BENCHMARK_USER
            .replace("{category}", InputSanitizer.sanitize(category))
            .replace("{subscriberCount}", MetricChange.describeCount(totalSubscribers))
            .replace("{platformData}", platformDataStr)
            .replace("{avgViews}", avgViews)
            .replace("{avgLikes}", avgLikes)
            .replace("{avgComments}", avgComments)
            .replace("{engagementRate}", overallEngRate)

        return creditService.withCredits(userId, AiFeature.ENGAGEMENT_BENCHMARK) {
            try {
                chatClientResolver.resolve(userId).prompt()
                    .system(PromptTemplates.ENGAGEMENT_BENCHMARK_SYSTEM)
                    .user(userPrompt)
                    .call()
                    .entity(EngagementBenchmarkResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("참여율 벤치마크 분석 실패: userId={}", userId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }
        }
    }

    companion object {
        /**
         * 그 플랫폼이 이 지표를 수집하지 않을 때 프롬프트에 넣는 문구.
         *
         * **숫자가 아니라 문장이어야 한다.** 어떤 숫자를 넣든 모델은 그것을 측정값으로
         * 읽고 없는 성과를 설명한다.
         */
        const val NOT_COLLECTED = "측정 불가(이 플랫폼은 수집하지 않음)"
    }
}
