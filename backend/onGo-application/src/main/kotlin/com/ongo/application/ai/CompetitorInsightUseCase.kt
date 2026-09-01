package com.ongo.application.ai

import com.ongo.application.analytics.AnalyticsRowPlatforms
import com.ongo.application.analytics.ChannelSubscriberTotal
import com.ongo.application.analytics.PlatformMetricAvailability
import com.ongo.domain.analytics.MetricChange
import com.ongo.domain.video.VideoUploadRepository
import com.ongo.application.ai.result.CompetitorInsightResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.competitor.CompetitorRepository
import com.ongo.domain.competitor.measuredAvgViews
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class CompetitorInsightUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val competitorRepository: CompetitorRepository,
    private val channelRepository: ChannelRepository,
    private val analyticsRepository: AnalyticsRepository,
    /** 집계 행의 플랫폼을 알아야 지표별 수집 여부를 판정할 수 있다. */
    private val videoUploadRepository: VideoUploadRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * **트랜잭션을 열지 않는다.** LLM 호출을 `@Transactional` 안에 두면 `ai_credits` 행
     * 잠금과 DB 커넥션이 모델 응답 시간만큼 묶인다. 차감·환불의 커밋 경계는
     * [CreditService.withCredits] 가 잡는다.
     */
    fun execute(userId: Long): CompetitorInsightResult {
        rateLimiter.checkRateLimit(userId)

        val channels = channelRepository.findByUserId(userId)
        /*
         * 구독자 수를 **조회하는 채널만** 더한다. Threads·LinkedIn 어댑터는 팔로워 수를
         * 묻지도 않고 `subscriberCount = 0` 을 박아 넣는다. 그대로 더하면 재지 않은 채널이
         * "구독자 0 명" 이 되어, 아래 경쟁자 목록과 나란히 놓였을 때 모델이 **실재하지 않는
         * 열세**를 설명한다.
         */
        val mySubscribers = MetricChange.describeCount(ChannelSubscriberTotal.measuredTotal(channels))
        /*
         * **조회수를 수집하는 행만 더한다.**
         *
         * 이 숫자는 유료 LLM 프롬프트의 "내 채널 현황" 으로 들어가 경쟁사와 비교된다.
         * `TumblrClient.kt:141` 의 `total_notes`(노트 총합)가 조회수로 섞이면 모델이
         * 그것을 근거로 없는 우위를 설명한다.
         */
        val allAnalytics = analyticsRepository.findAllByUserId(userId)
        val rowPlatforms = AnalyticsRowPlatforms.of(videoUploadRepository.findByUserId(userId))
        val viewRows = rowPlatforms.rowsReporting(allAnalytics, PlatformMetricAvailability.VIEWS)
        /*
         * 측정 행이 없을 때의 `0` 은 프롬프트에서 "조회수 0회, 영상 0편" 이라는 관측이
         * 된다. 모델은 그것을 근거로 "노출이 전혀 없다" 는 진단을 쓴다. 행이 있고 합이
         * 0 이면 그 0 은 실측이므로 그대로 둔다.
         */
        val myTotalViews = if (viewRows.isEmpty()) NOT_COLLECTED else viewRows.sumOf { it.views.toLong() }.toString()
        val myVideoCount = if (viewRows.isEmpty()) {
            NOT_COLLECTED
        } else {
            viewRows.map { it.videoUploadId }.distinct().size.toString()
        }

        val competitors = competitorRepository.findByUserId(userId)
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(30)

        val competitorDataStr = competitors.joinToString("\n") { comp ->
            val analytics = competitorRepository.findAnalyticsByCompetitorIdAndDateRange(
                comp.id!!, startDate, endDate
            )
            /*
             * **구독 증감은 관측된 두 시점이 있어야 말할 수 있다.**
             *
             * 예전에는 기간 내 수집 이력이 없어도 `0L` 을 넣었고, 프롬프트는 "30일 구독자
             * 증감 0" 이라고 알렸다. 모델은 그것을 "정체된 경쟁사" 로 읽고 없는 우위를
             * 설명한다. 관측이 하나뿐이면 시작과 끝이 같은 행이라 변화를 잰 적이 없다.
             *
             * 두 시점이 실제로 관측됐고 값이 같을 때의 `0` 은 실측이므로 그대로 둔다.
             */
            // 구독자 수를 숨긴 채널은 스냅샷 값이 `null` 이다. 0 으로 읽으면 폭락을 지어낸다.
            val firstSubs = analytics.firstOrNull()?.subscriberCount
            val lastSubs = analytics.lastOrNull()?.subscriberCount
            val subGrowth = if (analytics.size >= 2 && firstSubs != null && lastSubs != null) {
                (lastSubs - firstSubs).toString()
            } else {
                NO_COMPETITOR_HISTORY
            }

            val compSubscribers = MetricChange.describeCount(comp.subscriberCount)
            /*
             * 저장된 `avgViews` 는 계산하지 못한 자리에 0 이 남아 있다. 응답 DTO 와 **같은
             * 규칙**으로 갈라내지 않으면, 화면은 "측정 불가" 인데 유료 프롬프트에는
             * "평균 조회수 0" 이 들어가 모델이 없는 열세를 설명한다.
             */
            val compAvgViews = MetricChange.describeCount(
                measuredAvgViews(comp.videoCount, comp.totalViews, comp.avgViews),
            )
            "- ${comp.channelName} (${comp.platform}): 구독자 $compSubscribers, 평균 조회수 $compAvgViews, 영상 수 ${MetricChange.describeCount(comp.videoCount?.toLong())}, 30일 구독자 증감 ${subGrowth}"
        }.ifEmpty { "등록된 경쟁자 없음" }

        val userPrompt = """
내 채널 현황:
- 총 구독자: $mySubscribers
- 총 조회수: $myTotalViews
- 영상 수: $myVideoCount

경쟁자 현황 (최근 30일):
$competitorDataStr

위 데이터를 분석하여 경쟁 벤치마킹 인사이트를 생성해주세요.
""".trimIndent()

        val systemPrompt = """당신은 크리에이터 채널 분석 전문가입니다.
사용자의 채널과 경쟁자 채널 데이터를 비교 분석하여 실질적인 인사이트를 제공합니다.
JSON 형식으로 응답하세요: summary(요약), strengths(강점 리스트), weaknesses(약점 리스트), opportunities(기회 리스트), recommendations(추천 행동 리스트).
각 항목은 2~4개, 구체적이고 실행 가능하게 작성합니다. 한국어로 응답하세요."""

        return creditService.withCredits(userId, AiFeature.COMPETITOR_INSIGHT) {
            try {
                chatClientResolver.resolve(userId).prompt()
                    .system(InputSanitizer.sanitize(systemPrompt))
                    .user(InputSanitizer.sanitize(userPrompt))
                    .call()
                    .entity(CompetitorInsightResult::class.java)
                    ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")
            } catch (e: BusinessException) {
                throw e
            } catch (e: Exception) {
                log.error("경쟁자 인사이트 생성 실패: userId={}", userId, e)
                throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
            }
        }
    }

    companion object {
        /**
         * 내 채널 지표를 **수집하는 플랫폼이 없을 때** 프롬프트에 넣는 문구.
         *
         * 숫자가 아니라 문장이어야 한다 — 어떤 숫자를 넣든 모델은 그것을 측정값으로
         * 읽고 없는 진단을 쓴다.
         */
        const val NOT_COLLECTED = "측정 불가(수집하는 플랫폼 없음)"

        /** 경쟁사의 **기간 내 수집 이력이 없어** 증감을 잴 수 없을 때의 문구. */
        const val NO_COMPETITOR_HISTORY = "측정 불가(기간 내 수집 이력 없음)"
    }
}
