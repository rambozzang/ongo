package com.ongo.application.revenue

import com.ongo.application.revenue.dto.RevenueAlertConfigListResponse
import com.ongo.application.revenue.dto.RevenueAlertConfigResponse
import com.ongo.application.revenue.dto.SaveRevenueAlertConfigRequest
import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.revenue.RevenueAlertConfig
import com.ongo.domain.revenue.RevenueAlertConfigRepository
import com.ongo.domain.revenue.RevenueAlertType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RevenueAlertConfigUseCase(
    private val revenueAlertConfigRepository: RevenueAlertConfigRepository,
) {

    fun getConfigs(userId: Long): RevenueAlertConfigListResponse {
        val configs = revenueAlertConfigRepository.findByUserId(userId)
        return RevenueAlertConfigListResponse(
            configs = configs.map { it.toResponse() },
        )
    }

    @Transactional
    fun saveConfig(userId: Long, request: SaveRevenueAlertConfigRequest): RevenueAlertConfigResponse {
        validateAlertType(request.alertType)
        val config = revenueAlertConfigRepository.save(
            RevenueAlertConfig(
                userId = userId,
                alertType = request.alertType,
                thresholdValue = request.thresholdValue,
                scheduleTime = request.scheduleTime,
                isEnabled = request.isEnabled,
            )
        )
        return config.toResponse()
    }

    @Transactional
    fun updateConfig(userId: Long, id: Long, request: SaveRevenueAlertConfigRequest): RevenueAlertConfigResponse {
        validateAlertType(request.alertType)
        val existing = revenueAlertConfigRepository.findById(id)
            ?: throw NotFoundException("수익 알림 설정", id)
        if (existing.userId != userId) {
            throw ForbiddenException("해당 알림 설정에 대한 접근 권한이 없습니다")
        }
        val updated = revenueAlertConfigRepository.update(
            existing.copy(
                alertType = request.alertType,
                thresholdValue = request.thresholdValue,
                scheduleTime = request.scheduleTime,
                isEnabled = request.isEnabled,
            )
        )
        return updated.toResponse()
    }

    @Transactional
    fun deleteConfig(userId: Long, id: Long) {
        val existing = revenueAlertConfigRepository.findById(id)
            ?: throw NotFoundException("수익 알림 설정", id)
        if (existing.userId != userId) {
            throw ForbiddenException("해당 알림 설정에 대한 접근 권한이 없습니다")
        }
        revenueAlertConfigRepository.deleteById(id)
    }

    /** alertType이 유효한 enum 값인지 검증 */
    private fun validateAlertType(alertType: String) {
        runCatching { RevenueAlertType.valueOf(alertType) }
            .onFailure { throw BusinessException("INVALID_ALERT_TYPE", "유효하지 않은 알림 타입입니다: $alertType") }
    }

    private fun RevenueAlertConfig.toResponse() = RevenueAlertConfigResponse(
        id = id!!,
        alertType = alertType,
        thresholdValue = thresholdValue,
        scheduleTime = scheduleTime,
        isEnabled = isEnabled,
    )
}
