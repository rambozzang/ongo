package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.contentabanalyzer.*
import org.springframework.stereotype.Repository

@Repository
class ContentAbTestStubRepository : ContentAbTestRepository {
    override fun findById(id: Long): ContentAbTest? = null
    override fun findByUserId(userId: Long): List<ContentAbTest> = emptyList()
    override fun findByStatus(userId: Long, status: String): List<ContentAbTest> = emptyList()
    override fun save(test: ContentAbTest): ContentAbTest = test.copy(id = 1)
    override fun updateStatus(id: Long, status: String, winner: String?) {}
}

@Repository
class ContentVariantStubRepository : ContentVariantRepository {
    override fun findByTestId(testId: Long): List<ContentVariant> = emptyList()
    override fun save(variant: ContentVariant): ContentVariant = variant.copy(id = 1)
}
