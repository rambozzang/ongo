package com.ongo.application.channelaudit

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.result.ChannelAuditResult
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.application.analytics.ChannelSubscriberTotal
import com.ongo.application.analytics.PlatformMetricAvailability
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.analytics.CrossPlatformRaw
import com.ongo.domain.analytics.MetricChange
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channelaudit.ChannelAuditReport
import com.ongo.domain.channelaudit.ChannelAuditRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ChannelAuditUseCase(
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
    private val analyticsRepository: AnalyticsRepository,
    private val channelRepository: ChannelRepository,
    private val channelAuditRepository: ChannelAuditRepository,
) {

    private val log = LoggerFactory.getLogger(ChannelAuditUseCase::class.java)
    private val objectMapper = jacksonObjectMapper()

    /**
     * **트랜잭션을 열지 않는다.** LLM 호출을 `@Transactional` 안에 두면 `ai_credits` 행
     * 잠금과 DB 커넥션이 모델 응답 시간만큼 묶인다. 차감·환불의 커밋 경계는
     * [CreditService.withCredits] 가 잡고, 결과 저장은 호출 이후 짧게 끝난다.
     */
    fun generateAudit(userId: Long): ChannelAuditResponse {
        // 차감 전에 막는다. 레이트 리밋에 걸린 요청까지 차감·환불을 왕복할 이유가 없다.
        rateLimiter.checkRateLimit(userId)

        return creditService.withCredits(userId, AiFeature.CHANNEL_AUDIT) {
        try {
            val channels = channelRepository.findByUserId(userId)
            /*
             * 구독자 수를 **조회하는 채널만** 더한다. Threads·LinkedIn 어댑터는 팔로워
             * 수를 묻지도 않고 `subscriberCount = 0` 을 박아 넣으므로, 그대로 더하면
             * 재지 않은 채널이 "구독자 0 명" 인 진단 근거가 된다.
             */
            val totalSubscribers = ChannelSubscriberTotal.measuredTotal(channels)

            val kpi = analyticsRepository.getDashboardKpi(userId, 30)
            val topVideos = analyticsRepository.getTopVideos(userId, 30, 10)

            /*
             * **영상별 참여율은 영상별 데이터로만 계산한다.**
             *
             * 예전에는 채널 전체 평균(`kpi.totalLikes / kpi.totalViews`)을 계산해
             * 상위 10개 **모든 영상에 똑같이** 붙였다. `engagementRate` 가 `v` 를 전혀
             * 쓰지 않았으므로 열 줄이 전부 같은 숫자였는데, 프롬프트는 그것을 "그 영상의
             * 참여율" 이라고 말했다. AI 는 열 개의 동일한 가짜 수치를 근거로 "어느 영상이
             * 잘됐는지" 진단을 썼고, 사용자는 15 크레딧을 내고 그 조언을 받았다.
             *
             * 필요한 데이터는 이미 있었다 — [AnalyticsRepository.findCrossPlatformMetrics]
             * 가 영상별·플랫폼별 조회수/좋아요/댓글/공유를 준다. 바로 아래에서 플랫폼 요약을
             * 만드느라 이미 부르고 있었는데 영상별 참여율에는 쓰지 않았다.
             */
            val crossPlatform = analyticsRepository.findCrossPlatformMetrics(userId, 30)
            val engagementByVideo = videoEngagementRates(crossPlatform)

            val videoPerformance = topVideos.mapIndexed { i, v ->
                val rate = v.id?.let { engagementByVideo[it] }
                // 분모가 0 이거나 분석 행이 없으면 **측정 불가**다. 0.00% 는 "참여가 없었다"
                // 는 관측 결과로 읽히는데 우리는 그것을 관측한 적이 없다.
                val text = rate?.let { String.format("%.2f%%", it) } ?: ENGAGEMENT_UNAVAILABLE
                "${i + 1}. ${v.title} — 참여율 $text"
            }.joinToString("\n").ifEmpty { "데이터 없음" }
            val platformSummary = crossPlatform
                .groupBy { it.platform }
                .entries
                .joinToString("\n") { (platform, metrics) ->
                    /*
                     * **그 플랫폼이 수집하는 지표만 넣는다.**
                     *
                     * `TumblrClient.kt:141` 의 `views` 는 `total_notes`(노트 총합),
                     * `PinterestClient.kt:158` 의 `likes` 는 `SAVE`(저장) 수다. 하드코딩 0 과
                     * 달리 다른 뜻의 큰 숫자라, 모델이 그것을 조회수·좋아요로 읽는다.
                     * 바로 아래 영상별 참여율은 이미 같은 계약을 쓰고 있었다.
                     */
                    fun describe(metric: String, value: () -> Long): String =
                        if (PlatformMetricAvailability.isAvailable(platform, metric)) {
                            value().toString()
                        } else {
                            METRIC_NOT_COLLECTED
                        }

                    val views = describe(PlatformMetricAvailability.VIEWS) { metrics.sumOf { it.views } }
                    val likes = describe(PlatformMetricAvailability.LIKES) { metrics.sumOf { it.likes } }
                    "$platform: 조회수 $views, 좋아요 $likes"
                }.ifEmpty { "데이터 없음" }

            val userPrompt = PromptTemplates.CHANNEL_AUDIT_USER
                .replace("{subscriberCount}", MetricChange.describeCount(totalSubscribers))
                .replace("{videoPerformance}", videoPerformance)
                .replace("{platformSummary}", platformSummary)

            val result = chatClientResolver.resolve(userId).prompt()
                .system(PromptTemplates.CHANNEL_AUDIT_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(ChannelAuditResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")

            val contentJson = objectMapper.writeValueAsString(result)
            val report = channelAuditRepository.save(
                ChannelAuditReport(
                    userId = userId,
                    overallScore = result.overallScore,
                    content = contentJson,
                )
            )

            toResponse(report, result)
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("채널 오디트 생성 실패: userId={}", userId, e)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
        }
    }

    fun getAudits(userId: Long, page: Int, size: Int): ChannelAuditListResponse {
        val audits = channelAuditRepository.findByUserId(userId, page, size)
        val totalCount = channelAuditRepository.countByUserId(userId)
        return ChannelAuditListResponse(
            audits = audits.map { parseToResponse(it) },
            totalCount = totalCount,
            page = page,
            size = size,
        )
    }

    fun getAuditDetail(userId: Long, id: Long): ChannelAuditResponse {
        val report = channelAuditRepository.findById(id)
            ?: throw NotFoundException("채널 오디트", id)
        if (report.userId != userId) {
            throw BusinessException("FORBIDDEN", "접근 권한이 없습니다")
        }
        return parseToResponse(report)
    }

    private fun parseToResponse(report: ChannelAuditReport): ChannelAuditResponse {
        val result = runCatching {
            objectMapper.readValue<ChannelAuditResult>(report.content)
        }.getOrElse {
            ChannelAuditResult(
                overallScore = report.overallScore,
                strengths = emptyList(),
                weaknesses = emptyList(),
                actionItems = emptyList(),
                outlierVideos = emptyList(),
                growthForecast = "",
            )
        }
        return toResponse(report, result)
    }

    private fun toResponse(report: ChannelAuditReport, result: ChannelAuditResult): ChannelAuditResponse =
        ChannelAuditResponse(
            id = report.id!!,
            overallScore = result.overallScore,
            strengths = result.strengths,
            weaknesses = result.weaknesses,
            actionItems = result.actionItems.map {
                ChannelAuditResponse.ActionItemResponse(
                    priority = it.priority,
                    action = it.action,
                    expectedImpact = it.expectedImpact,
                )
            },
            outlierVideos = result.outlierVideos.map {
                ChannelAuditResponse.OutlierVideoResponse(
                    videoTitle = it.videoTitle,
                    metric = it.metric,
                    reason = it.reason,
                )
            },
            growthForecast = result.growthForecast,
            createdAt = report.createdAt,
        )

    /**
     * 영상별 참여율(%). **측정할 수 없으면 그 영상은 지도에서 빠진다.**
     *
     * 한 영상이 여러 플랫폼에 게시됐으면 플랫폼별 행을 합산한다. 다만 분자는
     * **그 플랫폼이 실제로 주는 지표만** 더한다 — Facebook 은 공유를, Pinterest 는 댓글을
     * API 로 주지 않아 그 자리의 0 을 더하면 참여율이 실제보다 낮게 나온다
     * ([PlatformMetricAvailability]). 대시보드 영상 비교가 이미 같은 계산을 쓴다.
     *
     * 다음 두 경우는 값을 만들지 않는다.
     *
     * - 조회수 합이 0 — 비율의 분모가 없다. 0.00% 는 "참여가 없었다" 는 주장이 된다.
     * - 그 영상이 게시된 플랫폼 전부가 좋아요·댓글·공유를 하나도 주지 않는다 —
     *   분자를 만들 재료가 없다.
     */
    private fun videoEngagementRates(rows: List<CrossPlatformRaw>): Map<Long, Double?> =
        rows.groupBy { it.videoId }.mapValues { (_, videoRows) ->
            val views = videoRows.sumOf { it.views }
            val anyEngagementMetricAvailable = videoRows.any { row ->
                ENGAGEMENT_METRICS.any { PlatformMetricAvailability.isAvailable(row.platform, it) }
            }
            if (views <= 0L || !anyEngagementMetricAvailable) {
                null
            } else {
                val engagements = videoRows.sumOf { row ->
                    fun measured(metric: String, value: Long) =
                        if (PlatformMetricAvailability.isAvailable(row.platform, metric)) value else 0L
                    measured(PlatformMetricAvailability.LIKES, row.likes) +
                        measured(PlatformMetricAvailability.COMMENTS, row.comments) +
                        measured(PlatformMetricAvailability.SHARES, row.shares)
                }
                Math.round((engagements.toDouble() / views) * 100 * 100) / 100.0
            }
        }

    companion object {
        /**
         * 영상별 참여율을 낼 수 없을 때 프롬프트에 넣는 문구.
         *
         * 숫자가 아니라 **문장**이어야 한다. 어떤 숫자를 넣든 모델은 그것을 측정값으로
         * 읽고 없는 추세를 설명한다.
         */
        const val ENGAGEMENT_UNAVAILABLE = "측정 불가(영상별 분석 데이터 없음)"

        /**
         * 그 플랫폼이 이 지표를 **수집하지 않을 때** 프롬프트에 넣는 문구.
         *
         * [ENGAGEMENT_UNAVAILABLE] 과 구분한다 — 저쪽은 "행이 없다", 이쪽은 "물어볼 수
         * 있는 지표가 아니다" 이다. 어느 쪽이든 숫자를 넣으면 모델이 측정값으로 읽는다.
         */
        const val METRIC_NOT_COLLECTED = "측정 불가(이 플랫폼은 수집하지 않음)"

        private val ENGAGEMENT_METRICS = listOf(
            PlatformMetricAvailability.LIKES,
            PlatformMetricAvailability.COMMENTS,
            PlatformMetricAvailability.SHARES,
        )
    }
}
