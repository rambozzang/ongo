package com.ongo.domain.usage

interface UsageAlertRepository {
    fun findByUserId(userId: Long): List<UsageAlertConfig>
    fun findByUserIdAndType(userId: Long, alertType: String): UsageAlertConfig?
    fun save(config: UsageAlertConfig): UsageAlertConfig
    fun update(config: UsageAlertConfig)
    fun delete(id: Long)
}
