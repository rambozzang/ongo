package com.ongo.application.automation

import com.ongo.application.automation.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.automation.AutomationRule
import com.ongo.domain.automation.AutomationRuleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AutomationUseCase(
    private val automationRuleRepository: AutomationRuleRepository,
) {

    companion object {
        val TRIGGER_TYPES = setOf(
            "VIDEO_UPLOADED", "SCHEDULE_DUE", "COMMENT_RECEIVED",
            "ANALYTICS_MILESTONE", "CREDIT_LOW",
            "VIEWS_MILESTONE", "VIRAL_DETECTED", "ENGAGEMENT_DROP"
        )
        val ACTION_TYPES = setOf(
            "SEND_NOTIFICATION", "AUTO_PUBLISH", "ADD_TAG", "GENERATE_METADATA"
        )

        val SMART_TRIGGER_TEMPLATES = listOf(
            SmartTriggerTemplate(
                triggerType = "VIEWS_MILESTONE",
                name = "조회수 마일스톤 알림",
                description = "영상 조회수가 특정 수치에 도달하면 알림을 보냅니다",
                defaultConfig = mapOf("milestones" to listOf(1000, 5000, 10000, 100000)),
                configSchema = mapOf(
                    "milestones" to "number[]",
                    "description" to "도달 시 알림을 받을 조회수 목표값 목록"
                )
            ),
            SmartTriggerTemplate(
                triggerType = "VIRAL_DETECTED",
                name = "바이럴 감지",
                description = "영상 조회수가 평균의 N배 이상일 때 감지합니다",
                defaultConfig = mapOf("multiplier" to 3.0),
                configSchema = mapOf(
                    "multiplier" to "number",
                    "description" to "평균 대비 몇 배 이상이면 바이럴로 판단할지 (기본: 3배)"
                )
            ),
            SmartTriggerTemplate(
                triggerType = "ENGAGEMENT_DROP",
                name = "참여율 하락 감지",
                description = "영상 참여율이 평균 대비 N% 이상 하락하면 알림을 보냅니다",
                defaultConfig = mapOf("dropPercent" to 50),
                configSchema = mapOf(
                    "dropPercent" to "number",
                    "description" to "평균 대비 몇 % 이상 하락 시 감지할지 (기본: 50%)"
                )
            )
        )
    }

    fun listRules(userId: Long): List<AutomationRuleResponse> {
        return automationRuleRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createRule(userId: Long, request: CreateAutomationRuleRequest): AutomationRuleResponse {
        require(request.triggerType in TRIGGER_TYPES) { "유효하지 않은 트리거 타입: ${request.triggerType}" }
        require(request.actionType in ACTION_TYPES) { "유효하지 않은 액션 타입: ${request.actionType}" }

        val rule = AutomationRule(
            userId = userId,
            name = request.name,
            description = request.description,
            triggerType = request.triggerType,
            triggerConfig = request.triggerConfig,
            actionType = request.actionType,
            actionConfig = request.actionConfig,
            isActive = request.isActive,
        )
        return automationRuleRepository.save(rule).toResponse()
    }

    @Transactional
    fun updateRule(userId: Long, ruleId: Long, request: UpdateAutomationRuleRequest): AutomationRuleResponse {
        val rule = automationRuleRepository.findById(ruleId) ?: throw NotFoundException("자동화 규칙", ruleId)
        if (rule.userId != userId) throw ForbiddenException("해당 규칙에 대한 권한이 없습니다")

        request.triggerType?.let { require(it in TRIGGER_TYPES) { "유효하지 않은 트리거 타입: $it" } }
        request.actionType?.let { require(it in ACTION_TYPES) { "유효하지 않은 액션 타입: $it" } }

        val updated = rule.copy(
            name = request.name ?: rule.name,
            description = request.description ?: rule.description,
            triggerType = request.triggerType ?: rule.triggerType,
            triggerConfig = request.triggerConfig ?: rule.triggerConfig,
            actionType = request.actionType ?: rule.actionType,
            actionConfig = request.actionConfig ?: rule.actionConfig,
        )
        return automationRuleRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteRule(userId: Long, ruleId: Long) {
        val rule = automationRuleRepository.findById(ruleId) ?: throw NotFoundException("자동화 규칙", ruleId)
        if (rule.userId != userId) throw ForbiddenException("해당 규칙에 대한 권한이 없습니다")
        automationRuleRepository.delete(ruleId)
    }

    @Transactional
    fun toggleRule(userId: Long, ruleId: Long): AutomationRuleResponse {
        val rule = automationRuleRepository.findById(ruleId) ?: throw NotFoundException("자동화 규칙", ruleId)
        if (rule.userId != userId) throw ForbiddenException("해당 규칙에 대한 권한이 없습니다")

        val updated = rule.copy(isActive = !rule.isActive)
        return automationRuleRepository.update(updated).toResponse()
    }

    fun getSmartTriggerTemplates(): List<SmartTriggerTemplate> {
        return SMART_TRIGGER_TEMPLATES
    }

    private fun AutomationRule.toResponse() = AutomationRuleResponse(
        id = id!!,
        name = name,
        description = description,
        triggerType = triggerType,
        triggerConfig = triggerConfig,
        actionType = actionType,
        actionConfig = actionConfig,
        isActive = isActive,
        lastTriggeredAt = lastTriggeredAt,
        executionCount = executionCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
