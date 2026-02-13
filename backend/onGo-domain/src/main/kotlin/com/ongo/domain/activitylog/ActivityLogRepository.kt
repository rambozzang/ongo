package com.ongo.domain.activitylog

interface ActivityLogRepository {
    fun findByUserId(userId: Long, page: Int, size: Int, action: String?, entityType: String?, startDate: java.time.LocalDateTime?, endDate: java.time.LocalDateTime?): List<ActivityLog>
    fun countByUserId(userId: Long, action: String?, entityType: String?, startDate: java.time.LocalDateTime?, endDate: java.time.LocalDateTime?): Long
    fun save(log: ActivityLog): ActivityLog
}
