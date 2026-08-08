package com.ongo.domain.scheduleoptimizer

interface ScheduleRecommendationRepository {
    fun findByIdAndUserId(id: Long, userId: Long): ScheduleRecommendation?
    fun findByUserId(userId: Long): List<ScheduleRecommendation>
    fun save(rec: ScheduleRecommendation): ScheduleRecommendation
    fun updateStatus(id: Long, userId: Long, status: String): Boolean
}
