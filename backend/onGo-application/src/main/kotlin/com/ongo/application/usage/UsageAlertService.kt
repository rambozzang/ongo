package com.ongo.application.usage

import com.ongo.domain.usage.UsageAlertConfig
import com.ongo.domain.usage.UsageAlertRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UsageAlertService(
    private val usageAlertRepository: UsageAlertRepository,
) {

    fun getAlertConfigs(userId: Long): List<UsageAlertConfigResponse> =
        usageAlertRepository.findByUserId(userId).map { it.toResponse() }

    @Transactional
    fun upsertAlertConfig(userId: Long, request: UpsertAlertConfigRequest): UsageAlertConfigResponse {
        val existing = usageAlertRepository.findByUserIdAndType(userId, request.alertType)
        return if (existing != null) {
            val updated = existing.copy(
                thresholdPercent = request.thresholdPercent,
                enabled = request.enabled,
            )
            usageAlertRepository.update(updated)
            updated.toResponse()
        } else {
            val saved = usageAlertRepository.save(UsageAlertConfig(
                userId = userId,
                alertType = request.alertType,
                thresholdPercent = request.thresholdPercent,
                enabled = request.enabled,
            ))
            saved.toResponse()
        }
    }

    @Transactional
    fun deleteAlertConfig(userId: Long, alertType: String) {
        val config = usageAlertRepository.findByUserIdAndType(userId, alertType) ?: return
        usageAlertRepository.delete(config.id!!)
    }

    private fun UsageAlertConfig.toResponse(): UsageAlertConfigResponse = UsageAlertConfigResponse(
        id = id!!,
        alertType = alertType,
        thresholdPercent = thresholdPercent,
        enabled = enabled,
        lastAlertedAt = lastAlertedAt?.toString(),
    )
}

data class UpsertAlertConfigRequest(
    val alertType: String,
    val thresholdPercent: Int = 80,
    val enabled: Boolean = true,
)

data class UsageAlertConfigResponse(
    val id: Long,
    val alertType: String,
    val thresholdPercent: Int,
    val enabled: Boolean,
    val lastAlertedAt: String?,
)
