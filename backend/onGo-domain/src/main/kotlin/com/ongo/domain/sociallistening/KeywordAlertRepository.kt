package com.ongo.domain.sociallistening

interface KeywordAlertRepository {
    fun findById(id: Long): KeywordAlert?
    fun findByUserId(userId: Long): List<KeywordAlert>
    fun save(alert: KeywordAlert): KeywordAlert
    fun update(alert: KeywordAlert): KeywordAlert
    fun delete(id: Long)
}
