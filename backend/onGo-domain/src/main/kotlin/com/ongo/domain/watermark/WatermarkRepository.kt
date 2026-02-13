package com.ongo.domain.watermark

interface WatermarkRepository {
    fun findById(id: Long): Watermark?
    fun findByUserId(userId: Long): List<Watermark>
    fun findDefaultByUserId(userId: Long): Watermark?
    fun save(watermark: Watermark): Watermark
    fun update(watermark: Watermark): Watermark
    fun delete(id: Long)
    fun clearDefault(userId: Long)
}
