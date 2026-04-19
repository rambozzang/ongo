package com.ongo.domain.scheduleoptimizer

interface OptimalSlotRepository {
    fun findByUserId(userId: Long): List<OptimalSlot>
    fun findByPlatform(userId: Long, platform: String): List<OptimalSlot>
    fun save(slot: OptimalSlot): OptimalSlot
    fun deleteByUserIdAndPlatform(userId: Long, platform: String)
    fun saveBatch(slots: List<OptimalSlot>): List<OptimalSlot>
}
