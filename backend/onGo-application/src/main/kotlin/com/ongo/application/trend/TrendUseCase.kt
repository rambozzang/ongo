package com.ongo.application.trend

import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.credit.CreditService
import com.ongo.application.trend.dto.*
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.trend.Trend
import com.ongo.domain.trend.TrendAlert
import com.ongo.domain.trend.TrendRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class TrendUseCase(
    private val trendRepository: TrendRepository,
    private val creditService: CreditService,
    private val chatClientResolver: ChatClientResolver,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        const val ANALYSIS_CREDIT_COST = 5
    }

    fun getTrends(date: LocalDate?, category: String?, source: String?): List<TrendResponse> {
        val targetDate = date ?: LocalDate.now()
        return trendRepository.findByDate(targetDate, category, source).map { it.toResponse() }
    }

    fun searchTrends(keyword: String): List<TrendResponse> =
        trendRepository.searchByKeyword(keyword).map { it.toResponse() }

    @Transactional
    fun analyzeTrends(userId: Long, category: String?): TrendAnalysisResponse {
        creditService.validateAndDeduct(userId, ANALYSIS_CREDIT_COST, "TREND_ANALYSIS")

        val trends = trendRepository.findByDate(LocalDate.now(), category)
        val topKeywords = trends.take(20)

        val prompt = buildString {
            appendLine("다음은 오늘의 트렌드 키워드입니다:")
            topKeywords.forEachIndexed { i, t ->
                appendLine("${i + 1}. ${t.keyword} (점수: ${t.score}, 출처: ${t.source})")
            }
            appendLine()
            appendLine("이 트렌드를 분석하고 콘텐츠 크리에이터에게 유용한 인사이트를 제공해주세요.")
            appendLine("1. 전체 요약 (3줄)")
            appendLine("2. 콘텐츠 제작 추천 (5개)")
        }

        return try {
            val response = chatClientResolver.resolve(userId).prompt()
                .system("당신은 한국 콘텐츠 트렌드 분석 전문가입니다. 크리에이터에게 실용적인 조언을 제공합니다.")
                .user(prompt)
                .call()
                .content() ?: ""

            val lines = response.lines().filter { it.isNotBlank() }
            val summary = lines.take(3).joinToString("\n")
            val recommendations = lines.drop(3).take(5).map { it.trimStart('-', ' ', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.') }

            TrendAnalysisResponse(
                summary = summary,
                recommendations = recommendations.ifEmpty { listOf("트렌드 데이터가 충분하지 않습니다.") },
                topKeywords = topKeywords.map { it.toResponse() },
            )
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("트렌드 AI 분석 실패, 크레딧 환불: userId={}", userId, e)
            creditService.refundCredit(userId, ANALYSIS_CREDIT_COST, "TREND_ANALYSIS")
            throw BusinessException("AI_CALL_FAILED", "AI 트렌드 분석에 실패했습니다: ${e.message}")
        }
    }

    // Alert CRUD

    fun getAlerts(userId: Long): List<TrendAlertResponse> =
        trendRepository.findAlertsByUserId(userId).map { it.toResponse() }

    @Transactional
    fun createAlert(userId: Long, request: CreateTrendAlertRequest): TrendAlertResponse {
        val alert = trendRepository.saveAlert(
            TrendAlert(
                userId = userId,
                keyword = request.keyword,
                threshold = request.threshold,
            )
        )
        return alert.toResponse()
    }

    @Transactional
    fun updateAlert(userId: Long, alertId: Long, request: UpdateTrendAlertRequest): TrendAlertResponse {
        val alert = trendRepository.findAlertById(alertId) ?: throw NotFoundException("알림", alertId)
        if (alert.userId != userId) throw BusinessException("FORBIDDEN", "해당 알림에 대한 권한이 없습니다")
        trendRepository.updateAlert(alertId, request.keyword, request.threshold, request.enabled)
        return trendRepository.findAlertById(alertId)!!.toResponse()
    }

    @Transactional
    fun deleteAlert(userId: Long, alertId: Long) {
        val alert = trendRepository.findAlertById(alertId) ?: throw NotFoundException("알림", alertId)
        if (alert.userId != userId) throw BusinessException("FORBIDDEN", "해당 알림에 대한 권한이 없습니다")
        trendRepository.deleteAlert(alertId)
    }

    private fun Trend.toResponse() = TrendResponse(
        id = id!!,
        keyword = keyword,
        score = score,
        source = source,
        category = category,
        platform = platform,
        region = region,
        date = date.toString(),
    )

    private fun TrendAlert.toResponse() = TrendAlertResponse(
        id = id!!,
        keyword = keyword,
        threshold = threshold,
        enabled = enabled,
        createdAt = createdAt?.toString(),
    )
}
