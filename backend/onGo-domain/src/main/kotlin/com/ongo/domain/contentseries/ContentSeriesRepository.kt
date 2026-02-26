package com.ongo.domain.contentseries

interface ContentSeriesRepository {
    fun findById(id: Long): ContentSeries?
    fun findByUserId(userId: Long): List<ContentSeries>
    fun save(series: ContentSeries): ContentSeries
    fun update(series: ContentSeries): ContentSeries
    fun delete(id: Long)
}
