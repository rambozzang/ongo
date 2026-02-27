package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.brandsafety.*
import org.springframework.stereotype.Repository

@Repository
class BrandSafetyCheckStubRepository : BrandSafetyCheckRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<BrandSafetyCheck> = emptyList()
    override fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<BrandSafetyCheck> = emptyList()
    override fun findById(id: Long): BrandSafetyCheck? = null
    override fun save(check: BrandSafetyCheck): BrandSafetyCheck = check.copy(id = 1)
    override fun update(check: BrandSafetyCheck): BrandSafetyCheck = check
}

@Repository
class BrandSafetyRuleStubRepository : BrandSafetyRuleRepository {
    override fun findByWorkspaceId(workspaceId: Long): List<BrandSafetyRule> = emptyList()
    override fun findById(id: Long): BrandSafetyRule? = null
    override fun save(rule: BrandSafetyRule): BrandSafetyRule = rule.copy(id = 1)
    override fun update(rule: BrandSafetyRule): BrandSafetyRule = rule
}

@Repository
class SafetyCheckItemStubRepository : SafetyCheckItemRepository {
    override fun findByCheckId(checkId: Long): List<SafetyCheckItem> = emptyList()
    override fun save(item: SafetyCheckItem): SafetyCheckItem = item.copy(id = 1)
    override fun saveBatch(items: List<SafetyCheckItem>): List<SafetyCheckItem> = items
}
