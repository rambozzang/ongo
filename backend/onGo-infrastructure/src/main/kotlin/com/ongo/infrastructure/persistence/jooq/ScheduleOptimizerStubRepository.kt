package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.scheduleoptimizer.*
import org.springframework.stereotype.Repository

@Repository
class ScheduleRecommendationStubRepository : ScheduleRecommendationRepository {
    override fun findById(id: Long): ScheduleRecommendation? = null
    override fun findByUserId(userId: Long): List<ScheduleRecommendation> = emptyList()
    override fun save(rec: ScheduleRecommendation): ScheduleRecommendation = rec.copy(id = 1)
    override fun updateStatus(id: Long, status: String) {}
}

@Repository
class OptimalSlotStubRepository : OptimalSlotRepository {
    override fun findByUserId(userId: Long): List<OptimalSlot> = emptyList()
    override fun findByPlatform(userId: Long, platform: String): List<OptimalSlot> = emptyList()
    override fun save(slot: OptimalSlot): OptimalSlot = slot.copy(id = 1)
}
