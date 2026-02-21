package com.ongo.domain.competitor

import java.time.LocalDate

interface CompetitorRepository {
    fun findById(id: Long): Competitor?
    fun findByUserId(userId: Long): List<Competitor>
    fun save(competitor: Competitor): Competitor
    fun update(competitor: Competitor): Competitor
    fun delete(id: Long)
    fun countByUserId(userId: Long): Int

    // --- CompetitorAnalyticsDaily ---
    fun findAnalyticsByCompetitorIdAndDateRange(
        competitorId: Long, startDate: LocalDate, endDate: LocalDate
    ): List<CompetitorAnalyticsDaily>

    fun findAnalyticsByCompetitorIdsAndDateRange(
        competitorIds: List<Long>, startDate: LocalDate, endDate: LocalDate
    ): List<CompetitorAnalyticsDaily>

    fun upsertAnalytics(analytics: CompetitorAnalyticsDaily)

    fun findAll(): List<Competitor>
}
