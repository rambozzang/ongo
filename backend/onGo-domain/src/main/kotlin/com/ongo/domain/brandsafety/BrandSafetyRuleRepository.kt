package com.ongo.domain.brandsafety

interface BrandSafetyRuleRepository {
    fun findByWorkspaceId(workspaceId: Long): List<BrandSafetyRule>
    fun findById(id: Long): BrandSafetyRule?
    fun save(rule: BrandSafetyRule): BrandSafetyRule
    fun update(rule: BrandSafetyRule): BrandSafetyRule
}
