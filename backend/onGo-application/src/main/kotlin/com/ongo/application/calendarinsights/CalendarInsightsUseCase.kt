package com.ongo.application.calendarinsights

import com.ongo.application.calendarinsights.dto.*
import com.ongo.domain.calendarinsights.*
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class CalendarInsightsUseCase(
    private val insightRepository: CalendarInsightRepository,
    private val timeSlotRepository: OptimalTimeSlotRepository,
    private val patternRepository: UploadPatternRepository,
) {

    fun getInsights(workspaceId: Long, year: Int, month: Int): List<CalendarInsightResponse> {
        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)
        return insightRepository.findByWorkspaceIdAndDateBetween(workspaceId, startDate, endDate)
            .map { toInsightResponse(it) }
    }

    fun getOptimalTimeSlots(workspaceId: Long, platform: String? = null): List<OptimalTimeSlotResponse> {
        val slots = if (platform != null) timeSlotRepository.findByWorkspaceIdAndPlatform(workspaceId, platform)
        else timeSlotRepository.findByWorkspaceId(workspaceId)
        return slots.map { toSlotResponse(it) }
    }

    fun getUploadPatterns(workspaceId: Long): List<UploadPatternResponse> {
        return patternRepository.findByWorkspaceId(workspaceId).map { toPatternResponse(it) }
    }

    fun getSummary(workspaceId: Long): CalendarInsightsSummaryResponse {
        val patterns = patternRepository.findByWorkspaceId(workspaceId)
        val totalUploads = patterns.sumOf { it.totalUploads }
        val avgPerWeek = if (patterns.isNotEmpty()) patterns.map { it.avgUploadsPerWeek.toDouble() }.sum() else 0.0
        val bestPattern = patterns.maxByOrNull { it.totalUploads }
        val avgConsistency = if (patterns.isNotEmpty()) patterns.map { it.consistency }.average().toInt() else 0
        return CalendarInsightsSummaryResponse(
            totalUploads = totalUploads,
            avgUploadsPerWeek = avgPerWeek,
            bestDay = bestPattern?.mostActiveDay ?: "-",
            bestHour = bestPattern?.mostActiveHour ?: 0,
            consistencyScore = avgConsistency,
        )
    }

    private fun toInsightResponse(i: CalendarInsight) = CalendarInsightResponse(
        id = i.id, date = i.date.toString(), dayOfWeek = i.dayOfWeek, hour = i.hour,
        uploadCount = i.uploadCount, avgViews = i.avgViews,
        avgEngagement = i.avgEngagement.toDouble(), score = i.score,
    )

    private fun toSlotResponse(s: OptimalTimeSlot) = OptimalTimeSlotResponse(
        dayOfWeek = s.dayOfWeek, hour = s.hour, score = s.score,
        expectedViews = s.expectedViews, expectedEngagement = s.expectedEngagement.toDouble(),
        reason = s.reason,
    )

    private fun toPatternResponse(p: UploadPattern) = UploadPatternResponse(
        platform = p.platform, totalUploads = p.totalUploads,
        avgUploadsPerWeek = p.avgUploadsPerWeek.toDouble(),
        mostActiveDay = p.mostActiveDay, mostActiveHour = p.mostActiveHour,
        consistency = p.consistency,
    )
}
