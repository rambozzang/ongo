package com.ongo.application.contentcalendarai

import com.ongo.application.contentcalendarai.dto.*
import com.ongo.domain.contentcalendarai.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class ContentCalendarAiUseCase(
    private val suggestionRepository: CalendarSuggestionRepository,
    private val slotRepository: CalendarAiSlotRepository,
) {

    fun getSuggestions(workspaceId: Long, status: String? = null): List<CalendarSuggestionResponse> {
        val list = if (status != null) suggestionRepository.findByWorkspaceIdAndStatus(workspaceId, status)
        else suggestionRepository.findByWorkspaceId(workspaceId)
        return list.map { toSuggestionResponse(it) }
    }

    @Transactional
    fun generate(workspaceId: Long, request: GenerateCalendarRequest): List<CalendarSuggestionResponse> {
        // AI 캘린더 생성 연동 포인트 — Phase 1에서는 빈 결과 반환
        return emptyList()
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
