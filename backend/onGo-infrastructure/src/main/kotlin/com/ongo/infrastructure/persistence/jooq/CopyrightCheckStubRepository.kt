package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.copyrightcheck.*
import org.springframework.stereotype.Repository

@Repository
class CopyrightCheckStubRepository : CopyrightCheckRepository {
    override fun findById(id: Long): CopyrightCheckResult? = null
    override fun findByUserId(userId: Long): List<CopyrightCheckResult> = emptyList()
    override fun save(result: CopyrightCheckResult): CopyrightCheckResult = result.copy(id = 1)
    override fun update(result: CopyrightCheckResult): CopyrightCheckResult = result
    override fun delete(id: Long) {}
}
