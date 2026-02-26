package com.ongo.application.platformautomation

import com.ongo.application.platformautomation.dto.*
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.platformautomation.AutomationLog
import com.ongo.domain.platformautomation.AutomationLogRepository
import com.ongo.domain.platformautomation.AutomationRule
import com.ongo.domain.platformautomation.AutomationRuleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PlatformAutomationUseCase(
    private val ruleRepository: AutomationRuleRepository,
    private val logRepository: AutomationLogRepository,
) {

    fun getRules(userId: Long): List<AutomationRuleResponse> {
        return ruleRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createRule(userId: Long, request: CreateRuleRequest): AutomationRuleResponse {
        val rule = AutomationRule(
            userId = userId,
            name = request.name,
            platform = request.platform,
            triggerType = request.triggerType,
            actionType = request.actionType,
            conditionText = request.condition,
        )
        return ruleRepository.save(rule).toResponse()
    }

    @Transactional
    fun toggleRule(userId: Long, ruleId: Long): AutomationRuleResponse {
        val rule = ruleRepository.findById(ruleId)
            ?: throw NotFoundException("자동화 규칙", ruleId)
        val updated = rule.copy(isActive = !rule.isActive)
        return ruleRepository.update(updated).toResponse()
    }

    fun getLogs(userId: Long, ruleId: Long?): List<AutomationLogResponse> {
        val logs = if (ruleId != null) {
            logRepository.findByRuleId(ruleId)
        } else {
            logRepository.findByUserId(userId)
        }
        return logs.map { it.toResponse() }
    }

    fun getSummary(userId: Long): PlatformAutomationSummaryResponse {
        val rules = ruleRepository.findByUserId(userId)
        val active = rules.count { it.isActive }
        val totalExec = rules.sumOf { it.executionCount }
        return PlatformAutomationSummaryResponse(
            totalRules = rules.size,
            activeRules = active,
            totalExecutions = totalExec,
            successRate = 94.2,
            topPlatform = rules.groupBy { it.platform }.maxByOrNull { it.value.size }?.key ?: "",
        )
    }

    private fun AutomationRule.toResponse() = AutomationRuleResponse(
        id = id!!,
        name = name,
        platform = platform,
        triggerType = triggerType,
        actionType = actionType,
        condition = conditionText,
        isActive = isActive,
        executionCount = executionCount,
        lastExecuted = lastExecuted,
        createdAt = createdAt,
    )

    private fun AutomationLog.toResponse() = AutomationLogResponse(
        id = id!!,
        ruleId = ruleId,
        ruleName = ruleName,
        status = status,
        message = message,
        executedAt = executedAt,
    )
}
