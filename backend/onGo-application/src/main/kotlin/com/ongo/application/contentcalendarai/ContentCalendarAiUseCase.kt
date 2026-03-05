package com.ongo.application.contentcalendarai

import com.ongo.application.ai.AiRateLimiter
import com.ongo.application.ai.ChatClientResolver
import com.ongo.application.ai.PromptTemplates
import com.ongo.application.ai.result.ContentCalendarResult
import com.ongo.application.contentcalendarai.dto.*
import com.ongo.application.credit.CreditService
import com.ongo.common.enums.AiFeature
import com.ongo.common.exception.BusinessException
import com.ongo.domain.contentcalendarai.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class ContentCalendarAiUseCase(
    private val suggestionRepository: CalendarSuggestionRepository,
    private val slotRepository: CalendarAiSlotRepository,
    private val chatClientResolver: ChatClientResolver,
    private val creditService: CreditService,
    private val rateLimiter: AiRateLimiter,
) {

    private val log = LoggerFactory.getLogger(ContentCalendarAiUseCase::class.java)

    fun getSuggestions(workspaceId: Long, status: String? = null): List<CalendarSuggestionResponse> {
        val list = if (status != null) suggestionRepository.findByWorkspaceIdAndStatus(workspaceId, status)
        else suggestionRepository.findByWorkspaceId(workspaceId)
        return list.map { toSuggestionResponse(it) }
    }

    @Transactional
    fun generate(workspaceId: Long, request: GenerateCalendarRequest): List<CalendarSuggestionResponse> {
        rateLimiter.checkRateLimit(workspaceId)
        creditService.validateAndDeduct(workspaceId, AiFeature.CONTENT_CALENDAR)

        val userPrompt = PromptTemplates.CONTENT_CALENDAR_USER
            .replace("{startDate}", request.startDate)
            .replace("{endDate}", request.endDate)
            .replace("{platforms}", request.platforms.joinToString(", "))
            .replace("{frequency}", request.frequency)

        try {
            val result = chatClientResolver.resolve(workspaceId).prompt()
                .system(PromptTemplates.CONTENT_CALENDAR_SYSTEM)
                .user(userPrompt)
                .call()
                .entity(ContentCalendarResult::class.java)
                ?: throw BusinessException("AI_PARSE_ERROR", "AI 응답을 파싱할 수 없습니다")

            val entities = result.suggestions.map { item ->
                CalendarSuggestion(
                    workspaceId = workspaceId,
                    title = item.title,
                    description = item.description,
                    suggestedDate = LocalDate.parse(item.suggestedDate),
                    suggestedTime = item.suggestedTime,
                    platform = item.platform,
                    contentType = item.contentType,
                    topic = item.topic,
                    expectedEngagement = BigDecimal.valueOf(item.expectedEngagement),
                    confidence = item.confidence,
                    status = "PENDING",
                )
            }

            val saved = suggestionRepository.saveBatch(entities)
            return saved.map { toSuggestionResponse(it) }
        } catch (e: BusinessException) {
            throw e
        } catch (e: Exception) {
            log.error("AI 콘텐츠 캘린더 생성 실패, 크레딧 환불 처리: userId={}", workspaceId, e)
            creditService.refundCredit(workspaceId, AiFeature.CONTENT_CALENDAR.creditCost, AiFeature.CONTENT_CALENDAR.name)
            throw BusinessException("AI_CALL_FAILED", "AI 호출에 실패했습니다: ${e.message}")
        }
    }

    fun getSlots(workspaceId: Long, startDate: String, endDate: String): List<CalendarAiSlotResponse> {
        val start = LocalDate.parse(startDate)
        val end = LocalDate.parse(endDate)
        return slotRepository.findByWorkspaceIdAndDateBetween(workspaceId, start, end)
            .map { toSlotResponse(it) }
    }

    @Transactional
    fun acceptSuggestion(id: Long): CalendarSuggestionResponse? {
        suggestionRepository.updateStatus(id, "ACCEPTED")
        return suggestionRepository.findById(id)?.let { toSuggestionResponse(it) }
    }

    fun getSummary(workspaceId: Long): ContentCalendarAiSummaryResponse {
        val all = suggestionRepository.findByWorkspaceId(workspaceId)
        val accepted = all.count { it.status == "ACCEPTED" }
        val avgConf = if (all.isNotEmpty()) all.map { it.confidence.toDouble() }.average() else 0.0
        val topPlatform = all.groupBy { it.platform }.maxByOrNull { it.value.size }?.key ?: "-"
        val bestSlot = all.maxByOrNull { it.expectedEngagement }?.let { "${it.suggestedDate} ${it.suggestedTime}" } ?: "-"
        return ContentCalendarAiSummaryResponse(all.size, accepted, avgConf, bestSlot, topPlatform)
    }

    private fun toSuggestionResponse(s: CalendarSuggestion) = CalendarSuggestionResponse(
        id = s.id, title = s.title, description = s.description,
        suggestedDate = s.suggestedDate.toString(), suggestedTime = s.suggestedTime,
        platform = s.platform, contentType = s.contentType, topic = s.topic,
        expectedEngagement = s.expectedEngagement.toDouble(), confidence = s.confidence,
        status = s.status, createdAt = s.createdAt.toString(),
    )

    private fun toSlotResponse(s: CalendarAiSlot) = CalendarAiSlotResponse(
        date = s.slotDate.toString(), time = s.slotTime,
        platform = s.platform, score = s.score, reason = s.reason,
    )
}
