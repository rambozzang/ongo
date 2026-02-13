package com.ongo.domain.abtest

interface ABTestRepository {
    fun findById(id: Long): ABTest?
    fun findByUserId(userId: Long): List<ABTest>
    fun findByStatus(status: String): List<ABTest>
    fun save(abTest: ABTest): ABTest
    fun update(abTest: ABTest): ABTest
    fun delete(id: Long)
}

interface ABTestVariantRepository {
    fun findByTestId(testId: Long): List<ABTestVariant>
    fun save(variant: ABTestVariant): ABTestVariant
    fun deleteByTestId(testId: Long)
}
