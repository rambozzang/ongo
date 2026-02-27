package com.ongo.domain.analytics

interface LiveAlertRepository {
    fun findByUserId(userId: Long): List<LiveAlert>
    fun findById(id: Long): LiveAlert?
    fun markRead(id: Long)
    fun save(alert: LiveAlert): LiveAlert
}
