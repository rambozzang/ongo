package com.ongo.application.revenue

import com.ongo.application.revenue.dto.*
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.revenue.RevenueRepository
import com.ongo.domain.user.UserRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RevenueUseCase(
    private val revenueRepository: RevenueRepository,
    private val userRepository: UserRepository,
) {

    @Cacheable(value = ["revenueSummary"], key = "#userId + '-' + #days")
    fun getRevenueSummary(userId: Long, days: Int): RevenueSummaryResponse {
        userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)

        val now = LocalDate.now()
        val from = now.minusDays(days.toLong())
        val previousFrom = from.minusDays(days.toLong())

        val currentTotal = revenueRepository.getTotalRevenue(userId, from, now)
        val previousTotal = revenueRepository.getTotalRevenue(userId, previousFrom, from.minusDays(1))

        val growthPercent = if (previousTotal == 0L) {
            if (currentTotal > 0) 100.0 else 0.0
        } else {
            ((currentTotal - previousTotal).toDouble() / previousTotal.toDouble()) * 100.0
        }

        val platformRevenue = revenueRepository.getPlatformRevenue(userId, from, now)
        val breakdown = platformRevenue.map { pr ->
            PlatformRevenueItem(
                platform = pr.platform,
                revenueMicro = pr.totalRevenueMicro,
                revenueKrw = pr.totalRevenueMicro / 1_000_000,
                percentage = if (currentTotal > 0) (pr.totalRevenueMicro.toDouble() / currentTotal * 100) else 0.0,
            )
        }

        return RevenueSummaryResponse(
            totalRevenue = currentTotal,
            totalRevenueKrw = currentTotal / 1_000_000,
            growthPercent = Math.round(growthPercent * 100) / 100.0,
            platformBreakdown = breakdown,
        )
    }

    @Cacheable(value = ["revenueTrends"], key = "#userId + '-' + #days")
    fun getRevenueTrends(userId: Long, days: Int): RevenueTrendResponse {
        userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)

        val now = LocalDate.now()
        val from = now.minusDays(days.toLong())
        val dailyRevenue = revenueRepository.getDailyRevenue(userId, from, now)

        val points = dailyRevenue.map { dr ->
            RevenueTrendPoint(
                date = dr.date,
                revenueMicro = dr.revenueMicro,
                revenueKrw = dr.revenueMicro / 1_000_000,
                platform = dr.platform,
            )
        }

        return RevenueTrendResponse(data = points)
    }

    fun getPlatformRevenue(userId: Long, days: Int): PlatformRevenueResponse {
        userRepository.findById(userId) ?: throw NotFoundException("사용자", userId)

        val now = LocalDate.now()
        val from = now.minusDays(days.toLong())
        val totalRevenue = revenueRepository.getTotalRevenue(userId, from, now)
        val platformRevenue = revenueRepository.getPlatformRevenue(userId, from, now)

        val items = platformRevenue.map { pr ->
            PlatformRevenueItem(
                platform = pr.platform,
                revenueMicro = pr.totalRevenueMicro,
                revenueKrw = pr.totalRevenueMicro / 1_000_000,
                percentage = if (totalRevenue > 0) (pr.totalRevenueMicro.toDouble() / totalRevenue * 100) else 0.0,
            )
        }

        return PlatformRevenueResponse(platforms = items)
    }
}
