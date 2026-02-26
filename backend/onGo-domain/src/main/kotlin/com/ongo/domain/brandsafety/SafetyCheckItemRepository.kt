package com.ongo.domain.brandsafety

interface SafetyCheckItemRepository {
    fun findByCheckId(checkId: Long): List<SafetyCheckItem>
    fun save(item: SafetyCheckItem): SafetyCheckItem
    fun saveBatch(items: List<SafetyCheckItem>): List<SafetyCheckItem>
}
