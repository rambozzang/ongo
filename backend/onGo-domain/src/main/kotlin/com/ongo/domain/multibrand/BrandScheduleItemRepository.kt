package com.ongo.domain.multibrand

import java.time.LocalDateTime

interface BrandScheduleItemRepository {
    fun findById(id: Long): BrandScheduleItem?
    fun findByUserId(userId: Long): List<BrandScheduleItem>
    fun findByUserIdAndDateRange(userId: Long, startDate: LocalDateTime, endDate: LocalDateTime): List<BrandScheduleItem>
    fun findByUserIdAndBrandId(userId: Long, brandId: Long): List<BrandScheduleItem>
    fun save(item: BrandScheduleItem): BrandScheduleItem
    fun update(item: BrandScheduleItem): BrandScheduleItem
    fun delete(id: Long)
}
