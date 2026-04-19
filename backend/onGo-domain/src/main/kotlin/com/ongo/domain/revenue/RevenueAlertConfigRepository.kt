package com.ongo.domain.revenue

interface RevenueAlertConfigRepository {
    fun findByUserId(userId: Long): List<RevenueAlertConfig>
    fun findById(id: Long): RevenueAlertConfig?
    fun findAllEnabledByAlertType(alertType: String): List<RevenueAlertConfig>
    fun save(config: RevenueAlertConfig): RevenueAlertConfig
    fun update(config: RevenueAlertConfig): RevenueAlertConfig
    fun deleteById(id: Long)
}
