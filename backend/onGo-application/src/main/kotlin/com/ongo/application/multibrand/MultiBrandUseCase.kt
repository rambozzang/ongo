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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Service
class MultiBrandUseCase(
    private val brandRepository: BrandRepository,
    private val brandScheduleItemRepository: BrandScheduleItemRepository,
) {

    fun getSummary(userId: Long): MultiBrandSummaryResponse {
        val brands = brandRepository.findByUserId(userId)

        // 이번 주 범위 계산
        val now = LocalDate.now()
        val weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay()
        val weekEnd = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(LocalTime.MAX)

        // 이번 달 범위 계산
        val monthStart = now.withDayOfMonth(1).atStartOfDay()
        val monthEnd = now.with(TemporalAdjusters.lastDayOfMonth()).atTime(LocalTime.MAX)

        val weekItems = brandScheduleItemRepository.findByUserIdAndDateRange(userId, weekStart, weekEnd)
        val monthItems = brandScheduleItemRepository.findByUserIdAndDateRange(userId, monthStart, monthEnd)

        val brandsMap = brands.associateBy { it.id }

        // 충돌 감지: 같은 날 같은 플랫폼에 여러 브랜드 일정이 겹치는 경우
        val conflicts = detectConflicts(weekItems, brandsMap)

        // 브랜드별 성과 요약
        val brandPerformances = brands.filter { it.isActive }.map { brand ->
            val brandMonthItems = monthItems.filter { it.brandId == brand.id }
            BrandPerformanceReportResponse(
                brandId = brand.id!!,
                brandName = brand.name,
                period = now.format(DateTimeFormatter.ofPattern("yyyy-MM")),
                totalUploads = brandMonthItems.count { it.status == "PUBLISHED" },
                totalViews = 0L,
                totalEngagement = 0L,
                avgCtr = 0.0,
                revenueGenerated = 0L,
                topContent = emptyList(),
            )
        }

        return MultiBrandSummaryResponse(
            totalBrands = brands.size,
            activeBrands = brands.count { it.isActive },
            totalScheduledThisWeek = weekItems.count { it.status == "SCHEDULED" || it.status == "DRAFT" },
            totalPublishedThisMonth = monthItems.count { it.status == "PUBLISHED" },
            conflicts = conflicts,
            brandPerformances = brandPerformances,
        )
    }

    fun getBrands(userId: Long): List<BrandResponse> {
        val brands = brandRepository.findByUserId(userId)
        return brands.map { brand ->
            val totalScheduled = brandScheduleItemRepository.countByBrandIdAndStatus(brand.id!!, "SCHEDULED")
            val totalPublished = brandScheduleItemRepository.countByBrandIdAndStatus(brand.id!!, "PUBLISHED")
            brand.toResponse(
                totalScheduled = totalScheduled,
                totalPublished = totalPublished,
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
            assignedEditors = serializeEditors(request.assignedEditors),
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
            assignedEditors = if (request.assignedEditors != null) serializeEditors(request.assignedEditors) else brand.assignedEditors,
            isActive = request.isActive ?: brand.isActive,
        )
        val saved = brandRepository.update(updated)
        val totalScheduled = brandScheduleItemRepository.countByBrandIdAndStatus(brandId, "SCHEDULED")
        val totalPublished = brandScheduleItemRepository.countByBrandIdAndStatus(brandId, "PUBLISHED")
        return saved.toResponse(totalScheduled, totalPublished)
    }

    @Transactional
    fun deleteBrand(userId: Long, brandId: Long) {
        val brand = brandRepository.findById(brandId)
            ?: throw NotFoundException("브랜드", brandId)
        if (brand.userId != userId) throw ForbiddenException("해당 브랜드에 대한 권한이 없습니다")
        brandScheduleItemRepository.deleteByBrandId(brandId)
        brandRepository.delete(brandId)
    }

    fun getSchedule(userId: Long, startDate: LocalDateTime, endDate: LocalDateTime, brandId: Long?): List<BrandScheduleItemResponse> {
        val items = if (brandId != null) {
            brandScheduleItemRepository.findByUserIdAndBrandIdAndDateRange(userId, brandId, startDate, endDate)
        } else {
            brandScheduleItemRepository.findByUserIdAndDateRange(userId, startDate, endDate)
        }
        val brands = brandRepository.findByUserId(userId).associateBy { it.id }
        return items.map { item ->
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
            notes = request.notes ?: item.notes,
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

    private fun detectConflicts(
        items: List<BrandScheduleItem>,
        brandsMap: Map<Long?, Brand>,
    ): List<CalendarConflictResponse> {
        // 같은 날짜 + 같은 플랫폼에 여러 일정이 있으면 충돌
        val grouped = items
            .filter { it.status != "PUBLISHED" && it.status != "FAILED" }
            .groupBy { it.scheduledAt.toLocalDate().toString() to it.platform }

        return grouped
            .filter { it.value.size > 1 }
            .map { (key, conflictItems) ->
                val (date, platform) = key
                val severity = when {
                    conflictItems.size >= 3 -> "HIGH"
                    conflictItems.size == 2 -> "MEDIUM"
                    else -> "LOW"
                }
                val brandNames = conflictItems.map { brandsMap[it.brandId]?.name ?: "알 수 없음" }.distinct()
                CalendarConflictResponse(
                    date = date,
                    items = conflictItems.map { item ->
                        val brand = brandsMap[item.brandId]
                        item.toResponse(brand?.name ?: "", brand?.color ?: "BLUE")
                    },
                    severity = severity,
                    message = "${date}에 ${platform}에서 ${brandNames.joinToString(", ")} 일정이 겹칩니다",
                )
            }
    }

    private fun serializeEditors(editors: List<String>): String =
        editors.joinToString(",")

    private fun deserializeEditors(editorsStr: String): List<String> =
        if (editorsStr.isBlank() || editorsStr == "[]") emptyList()
        else editorsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }

    private fun Brand.toResponse(totalScheduled: Int, totalPublished: Int) = BrandResponse(
        id = id!!,
        name = name,
        logoUrl = logoUrl,
        color = color,
        category = category,
        assignedEditors = deserializeEditors(assignedEditors),
        totalScheduled = totalScheduled,
        totalPublished = totalPublished,
        isActive = isActive,
        createdAt = createdAt,
    )

    private fun BrandScheduleItem.toResponse(brandName: String, brandColor: String) = BrandScheduleItemResponse(
        id = id!!,
        brandId = brandId,
        brandName = brandName,
        brandColor = brandColor,
        title = title,
        platform = platform,
        scheduledAt = scheduledAt,
        status = status,
        assignedTo = assignedTo,
        videoId = videoId,
        notes = notes,
    )
}
