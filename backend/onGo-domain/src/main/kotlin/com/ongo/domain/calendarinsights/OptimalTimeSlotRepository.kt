package com.ongo.domain.calendarinsights

interface OptimalTimeSlotRepository {
    fun findByWorkspaceId(workspaceId: Long): List<OptimalTimeSlot>
    fun findByWorkspaceIdAndPlatform(workspaceId: Long, platform: String): List<OptimalTimeSlot>
    fun save(slot: OptimalTimeSlot): OptimalTimeSlot
}
