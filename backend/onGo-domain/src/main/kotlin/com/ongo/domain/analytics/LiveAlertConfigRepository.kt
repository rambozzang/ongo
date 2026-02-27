package com.ongo.domain.analytics

interface LiveAlertConfigRepository {
    fun findByUserId(userId: Long): List<LiveAlertConfig>
    fun findById(id: Long): LiveAlertConfig?
    fun save(config: LiveAlertConfig): LiveAlertConfig
    fun update(config: LiveAlertConfig): LiveAlertConfig
}
