package com.ongo.infrastructure.persistence.jooq

import com.ongo.domain.multibrand.*
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class BrandStubRepository : BrandRepository {
    override fun findById(id: Long): Brand? = null
    override fun findByUserId(userId: Long): List<Brand> = emptyList()
    override fun save(brand: Brand): Brand = brand.copy(id = 1)
    override fun update(brand: Brand): Brand = brand
    override fun delete(id: Long) {}
}

@Repository
class BrandScheduleItemStubRepository : BrandScheduleItemRepository {
    override fun findById(id: Long): BrandScheduleItem? = null
    override fun findByUserId(userId: Long): List<BrandScheduleItem> = emptyList()
    override fun findByUserIdAndDateRange(userId: Long, startDate: LocalDateTime, endDate: LocalDateTime): List<BrandScheduleItem> = emptyList()
    override fun findByUserIdAndBrandId(userId: Long, brandId: Long): List<BrandScheduleItem> = emptyList()
    override fun save(item: BrandScheduleItem): BrandScheduleItem = item.copy(id = 1)
    override fun update(item: BrandScheduleItem): BrandScheduleItem = item
    override fun delete(id: Long) {}
}
