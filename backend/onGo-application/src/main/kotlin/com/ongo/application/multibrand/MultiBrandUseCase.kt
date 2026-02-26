package com.ongo.application.multibrand

import com.ongo.application.multibrand.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.multibrand.Brand
import com.ongo.domain.multibrand.BrandRepository
import com.ongo.domain.multibrand.BrandScheduleItem
import com.ongo.domain.multibrand.BrandScheduleItemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class MultiBrandUseCase(
    private val brandRepository: BrandRepository,
    private val brandScheduleItemRepository: BrandScheduleItemRepository,
) {

    fun getSummary(userId: Long): MultiBrandSummaryResponse {
        val brands = brandRepository.findByUserId(userId)
        val scheduleItems = brandScheduleItemRepository.findByUserId(userId)
        return MultiBrandSummaryResponse(
            totalBrands = brands.size,
            activeBrands = brands.count { it.isActive },
            totalScheduledThisWeek = scheduleItems.count { it.status == "SCHEDULED" },
            totalPublishedThisMonth = scheduleItems.count { it.status == "PUBLISHED" },
            conflicts = "[]",
        )
    }

    fun getBrands(userId: Long): List<BrandResponse> {
        val brands = brandRepository.findByUserId(userId)
        return brands.map { brand ->
            val items = brandScheduleItemRepository.findByUserIdAndBrandId(userId, brand.id!!)
            brand.toResponse(
                totalScheduled = items.count { it.status == "SCHEDULED" },
                totalPublished = items.count { it.status == "PUBLISHED" },
            )
        }
    }

    @Transactional
    fun createBrand(userId: Long, request: CreateBrandRequest): BrandResponse {
        val brand = Brand(
            userId = userId,
            name = request.name,
            color = request.color,
            category = request.category,
            assignedEditors = request.assignedEditors.joinToString(","),
        )
        return brandRepository.save(brand).toResponse(0, 0)
    }

    @Transactional
    fun updateBrand(userId: Long, brandId: Long, request: UpdateBrandRequest): BrandResponse {
        val brand = brandRepository.findById(brandId)
            ?: throw NotFoundException("브랜드", brandId)
        if (brand.userId != userId) throw ForbiddenException("해당 브랜드에 대한 권한이 없습니다")
        val updated = brand.copy(
            name = request.name ?: brand.name,
            color = request.color ?: brand.color,
            category = request.category ?: brand.category,
            isActive = request.isActive ?: brand.isActive,
        )
        return brandRepository.update(updated).toResponse(0, 0)
    }

    @Transactional
    fun deleteBrand(userId: Long, brandId: Long) {
        val brand = brandRepository.findById(brandId)
            ?: throw NotFoundException("브랜드", brandId)
        if (brand.userId != userId) throw ForbiddenException("해당 브랜드에 대한 권한이 없습니다")
        brandRepository.delete(brandId)
    }

    fun getSchedule(userId: Long, startDate: LocalDateTime, endDate: LocalDateTime, brandId: Long?): List<BrandScheduleItemResponse> {
        val items = brandScheduleItemRepository.findByUserIdAndDateRange(userId, startDate, endDate)
        val brands = brandRepository.findByUserId(userId).associateBy { it.id }
        val filtered = if (brandId != null) items.filter { it.brandId == brandId } else items
        return filtered.map { item ->
            val brand = brands[item.brandId]
            item.toResponse(brand?.name ?: "", brand?.color ?: "BLUE")
        }
    }

    @Transactional
    fun createSchedule(userId: Long, request: CreateScheduleRequest): BrandScheduleItemResponse {
        val brand = brandRepository.findById(request.brandId)
            ?: throw NotFoundException("브랜드", request.brandId)
        if (brand.userId != userId) throw ForbiddenException("해당 브랜드에 대한 권한이 없습니다")
        val item = BrandScheduleItem(
            userId = userId,
            brandId = request.brandId,
            title = request.title,
            platform = request.platform,
            scheduledAt = request.scheduledAt,
            assignedTo = request.assignedTo,
            notes = request.notes,
        )
        return brandScheduleItemRepository.save(item).toResponse(brand.name, brand.color)
    }

    @Transactional
    fun updateSchedule(userId: Long, scheduleId: Long, request: UpdateScheduleRequest): BrandScheduleItemResponse {
        val item = brandScheduleItemRepository.findById(scheduleId)
            ?: throw NotFoundException("스케줄 항목", scheduleId)
        if (item.userId != userId) throw ForbiddenException("해당 스케줄에 대한 권한이 없습니다")
        val brand = brandRepository.findById(item.brandId)
        val updated = item.copy(
            title = request.title ?: item.title,
            platform = request.platform ?: item.platform,
            scheduledAt = request.scheduledAt ?: item.scheduledAt,
            status = request.status ?: item.status,
            assignedTo = request.assignedTo ?: item.assignedTo,
        )
        return brandScheduleItemRepository.update(updated).toResponse(brand?.name ?: "", brand?.color ?: "BLUE")
    }

    @Transactional
    fun deleteSchedule(userId: Long, scheduleId: Long) {
        val item = brandScheduleItemRepository.findById(scheduleId)
            ?: throw NotFoundException("스케줄 항목", scheduleId)
        if (item.userId != userId) throw ForbiddenException("해당 스케줄에 대한 권한이 없습니다")
        brandScheduleItemRepository.delete(scheduleId)
    }

    private fun Brand.toResponse(totalScheduled: Int, totalPublished: Int) = BrandResponse(
        id = id!!, name = name, logoUrl = logoUrl, color = color, category = category,
        assignedEditors = assignedEditors, totalScheduled = totalScheduled,
        totalPublished = totalPublished, isActive = isActive, createdAt = createdAt,
    )

    private fun BrandScheduleItem.toResponse(brandName: String, brandColor: String) = BrandScheduleItemResponse(
        id = id!!, brandId = brandId, brandName = brandName, brandColor = brandColor,
        title = title, platform = platform, scheduledAt = scheduledAt, status = status,
        assignedTo = assignedTo, videoId = videoId, notes = notes,
    )
}
