package com.ongo.domain.abtestresult

interface TestVariantRepository {
    fun findByResultId(resultId: Long): List<TestVariant>
    fun findById(id: Long): TestVariant?
    fun save(variant: TestVariant): TestVariant
    fun saveBatch(variants: List<TestVariant>): List<TestVariant>
    fun update(variant: TestVariant): TestVariant
    fun deleteByResultId(resultId: Long)
}
