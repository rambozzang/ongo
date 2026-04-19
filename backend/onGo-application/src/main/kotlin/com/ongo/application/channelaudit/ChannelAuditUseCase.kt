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
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.channelaudit.ChannelAuditReport
import com.ongo.domain.channelaudit.ChannelAuditRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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

    @Transactional
    fun generateAudit(userId: Long): ChannelAuditResponse {
        rateLimiter.checkRateLimit(userId)
        creditService.validateAndDeduct(userId, AiFeature.CHANNEL_AUDIT)

        try {
            val channels = channelRepository.findByUserId(userId)
            val totalSubscribers = channels.sumOf { it.subscriberCount }

            val kpi = analyticsRepository.getDashboardKpi(userId, 30)
            val topVideos = analyticsRepository.getTopVideos(userId, 30, 10)

            val videoPerformance = topVideos.mapIndexed { i, v ->
                val engagementRate = if (kpi.totalViews > 0) {
                    String.format("%.2f", (kpi.totalLikes.toDouble() / kpi.totalViews) * 100)
                } else "0.00"
                "${i + 1}. ${v.title} — 참여율 ${engagementRate}%"
            }.joinToString("\n").ifEmpty { "데이터 없음" }

            val crossPlatform = analyticsRepository.findCrossPlatformMetrics(userId, 30)
            val platformSummary = crossPlatform
                .groupBy { it.platform }
                .entries
                .joinToString("\n") { (platform, metrics) ->
                    val views = metrics.sumOf { it.views }
                    val likes = metrics.sumOf { it.likes }
                    "$platform: 조회수 $views, 좋아요 $likes"
                }.ifEmpty { "데이터 없음" }

            val userPrompt = PromptTemplates.CHANNEL_AUDIT_USER
                .replace("{subscriberCount}", totalSubscribers.toString())
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

            return toResponse(report, result)
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("채널 오디트 생성 실패, 크레딧 환불 처리: userId={}", userId, e)
            creditService.refundCredit(userId, AiFeature.CHANNEL_AUDIT.creditCost, AiFeature.CHANNEL_AUDIT.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
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
}
