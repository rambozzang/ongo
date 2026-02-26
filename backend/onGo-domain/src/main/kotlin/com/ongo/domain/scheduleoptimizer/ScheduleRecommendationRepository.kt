package com.ongo.domain.scheduleoptimizer

interface ScheduleRecommendationRepository {
    fun findById(id: Long): ScheduleRecommendation?
    fun findByUserId(userId: Long): List<ScheduleRecommendation>
    fun save(rec: ScheduleRecommendation): ScheduleRecommendation
    fun updateStatus(id: Long, status: String)
}
