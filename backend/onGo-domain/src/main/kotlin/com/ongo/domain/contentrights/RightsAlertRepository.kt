package com.ongo.domain.contentrights

interface RightsAlertRepository {
    fun findById(id: Long): RightsAlert?
    fun findByUserId(userId: Long): List<RightsAlert>
    fun save(alert: RightsAlert): RightsAlert
    fun markRead(id: Long)
    fun delete(id: Long)
}
