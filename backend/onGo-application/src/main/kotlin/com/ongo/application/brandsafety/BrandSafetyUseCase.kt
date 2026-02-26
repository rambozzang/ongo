package com.ongo.application.brandsafety

import com.ongo.application.brandsafety.dto.*
import com.ongo.domain.brandsafety.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class BrandSafetyUseCase(
    private val checkRepository: BrandSafetyCheckRepository,
    private val itemRepository: SafetyCheckItemRepository,
    private val ruleRepository: BrandSafetyRuleRepository,
) {

    fun getChecks(workspaceId: Long, status: String? = null): List<BrandSafetyCheckResponse> {
        val checks = if (status != null) checkRepository.findByWorkspaceIdAndStatus(workspaceId, status)
        else checkRepository.findByWorkspaceId(workspaceId)
        return checks.map { toCheckResponse(it) }
    }

    fun getCheck(id: Long): BrandSafetyCheckResponse? {
        val check = checkRepository.findById(id) ?: return null
        return toCheckResponse(check)
    }

    @Transactional
    fun runCheck(workspaceId: Long, request: RunSafetyCheckRequest): BrandSafetyCheckResponse {
        val saved = checkRepository.save(
            BrandSafetyCheck(
                workspaceId = workspaceId,
                contentTitle = request.contentTitle,
                contentType = request.contentType,
                platform = request.platform,
            )
        )
        // AI 안전성 검사 연동 포인트 — Phase 1에서는 PENDING 상태 반환
        return toCheckResponse(saved)
    }

    fun getRules(workspaceId: Long): List<BrandSafetyRuleResponse> {
        return ruleRepository.findByWorkspaceId(workspaceId).map { toRuleResponse(it) }
    }

    @Transactional
    fun toggleRule(workspaceId: Long, id: Long, isEnabled: Boolean): BrandSafetyRuleResponse {
        val rule = ruleRepository.findById(id) ?: throw IllegalArgumentException("규칙을 찾을 수 없습니다")
        val updated = ruleRepository.update(rule.copy(isEnabled = isEnabled))
        return toRuleResponse(updated)
    }

    fun getSummary(workspaceId: Long): BrandSafetySummaryResponse {
        val all = checkRepository.findByWorkspaceId(workspaceId)
        val safe = all.count { it.status == "SAFE" }
        val warning = all.count { it.status == "WARNING" }
        val violation = all.count { it.status == "VIOLATION" }
        val avgScore = if (all.isNotEmpty()) all.map { it.overallScore.toDouble() }.average() else 0.0
        return BrandSafetySummaryResponse(all.size, safe, warning, violation, avgScore)
    }

    private fun toCheckResponse(c: BrandSafetyCheck): BrandSafetyCheckResponse {
        val items = itemRepository.findByCheckId(c.id)
        return BrandSafetyCheckResponse(
            id = c.id, contentTitle = c.contentTitle, contentType = c.contentType,
            platform = c.platform, status = c.status, overallScore = c.overallScore,
            checks = items.map { toItemResponse(it) },
            checkedAt = c.checkedAt?.toString(), createdAt = c.createdAt.toString(),
        )
    }

    private fun toItemResponse(i: SafetyCheckItem) = SafetyCheckItemResponse(
        id = i.id, category = i.category, name = i.name, status = i.status,
        severity = i.severity, description = i.description, recommendation = i.recommendation,
    )

    private fun toRuleResponse(r: BrandSafetyRule) = BrandSafetyRuleResponse(
        id = r.id, name = r.name, category = r.category, description = r.description,
        isEnabled = r.isEnabled, severity = r.severity, createdAt = r.createdAt.toString(),
    )
}
