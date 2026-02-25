package com.ongo.domain.copyrightcheck

interface CopyrightCheckRepository {
    fun findById(id: Long): CopyrightCheckResult?
    fun findByUserId(userId: Long): List<CopyrightCheckResult>
    fun save(result: CopyrightCheckResult): CopyrightCheckResult
    fun update(result: CopyrightCheckResult): CopyrightCheckResult
    fun delete(id: Long)
}
