package com.ongo.domain.trend

import java.time.LocalDate

interface TrendRepository {
    fun findByDate(date: LocalDate, category: String? = null, source: String? = null): List<Trend>
    fun searchByKeyword(keyword: String, limit: Int = 20): List<Trend>
    fun saveBatch(trends: List<Trend>)

    // Alerts
    fun findAlertsByUserId(userId: Long): List<TrendAlert>
    fun findAlertById(id: Long): TrendAlert?
    fun saveAlert(alert: TrendAlert): TrendAlert
    fun updateAlert(id: Long, keyword: String?, threshold: Double?, enabled: Boolean?)
    fun deleteAlert(id: Long)
}
