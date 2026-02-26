package com.ongo.domain.brandsafety

interface BrandSafetyCheckRepository {
    fun findByWorkspaceId(workspaceId: Long): List<BrandSafetyCheck>
    fun findByWorkspaceIdAndStatus(workspaceId: Long, status: String): List<BrandSafetyCheck>
    fun findById(id: Long): BrandSafetyCheck?
    fun save(check: BrandSafetyCheck): BrandSafetyCheck
    fun update(check: BrandSafetyCheck): BrandSafetyCheck
}
