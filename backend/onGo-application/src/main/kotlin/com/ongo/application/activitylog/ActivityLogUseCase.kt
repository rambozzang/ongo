package com.ongo.application.activitylog

import com.ongo.application.activitylog.dto.ActivityLogListResponse
import com.ongo.application.activitylog.dto.ActivityLogResponse
import com.ongo.domain.activitylog.ActivityLog
import com.ongo.domain.activitylog.ActivityLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ActivityLogUseCase(
    private val activityLogRepository: ActivityLogRepository,
) {

    fun listLogs(
        userId: Long,
        page: Int,
        size: Int,
        action: String?,
        entityType: String?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): ActivityLogListResponse {
        val logs = activityLogRepository.findByUserId(userId, page, size, action, entityType, startDate, endDate)
        val total = activityLogRepository.countByUserId(userId, action, entityType, startDate, endDate)
        return ActivityLogListResponse(
            logs = logs.map { it.toResponse() },
            totalElements = total,
            page = page,
            size = size,
        )
    }

    @Transactional
    fun logActivity(
        userId: Long,
        action: String,
        entityType: String? = null,
        entityId: Long? = null,
        details: String = "{}",
        ipAddress: String? = null,
        userAgent: String? = null,
    ): ActivityLogResponse {
        val log = ActivityLog(
            userId = userId,
            action = action,
            entityType = entityType,
            entityId = entityId,
            details = details,
            ipAddress = ipAddress,
            userAgent = userAgent,
        )
        return activityLogRepository.save(log).toResponse()
    }

    private fun ActivityLog.toResponse(): ActivityLogResponse = ActivityLogResponse(
        id = id!!,
        userId = userId,
        action = action,
        entityType = entityType,
        entityId = entityId,
        details = details,
        ipAddress = ipAddress,
        createdAt = createdAt,
    )
}
