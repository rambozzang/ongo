package com.ongo.application.fanfunding

import com.ongo.application.fanfunding.dto.*
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.fanfunding.FundingGoal
import com.ongo.domain.fanfunding.FundingGoalRepository
import com.ongo.domain.fanfunding.FundingTransaction
import com.ongo.domain.fanfunding.FundingTransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class FanFundingUseCase(
    private val fundingTransactionRepository: FundingTransactionRepository,
    private val fundingGoalRepository: FundingGoalRepository,
) {

    fun getSummary(userId: Long, period: String?): FundingSummaryResponse {
        val days = parsePeriodToDays(period ?: "30d")
        val transactions = fundingTransactionRepository.findByUserIdAndPeriod(userId, days)
        val totalRevenue = transactions.sumOf { it.amount }

        val now = LocalDateTime.now()
        val thisMonthTx = transactions.filter { it.createdAt?.month == now.month && it.createdAt?.year == now.year }
        val lastMonthTx = transactions.filter { it.createdAt?.month == now.minusMonths(1).month && it.createdAt?.year == now.minusMonths(1).year }
        val thisMonthRevenue = thisMonthTx.sumOf { it.amount }
        val lastMonthRevenue = lastMonthTx.sumOf { it.amount }
        val growthRate = if (lastMonthRevenue > 0) ((thisMonthRevenue - lastMonthRevenue).toDouble() / lastMonthRevenue * 100) else 0.0

        return FundingSummaryResponse(
            totalRevenue = totalRevenue,
            thisMonthRevenue = thisMonthRevenue,
            lastMonthRevenue = lastMonthRevenue,
            growthRate = growthRate,
            topDonors = "[]",
            bySource = "[]",
            byPlatform = "[]",
            dailyTrends = "[]",
            membershipCount = 0,
            membershipMRR = 0,
        )
    }

    fun getTransactions(userId: Long, source: String?, platform: String?, dateFrom: String?, dateTo: String?): List<FundingTransactionResponse> {
        return fundingTransactionRepository.findByUserIdAndFilter(userId, source, platform, dateFrom, dateTo)
            .map { it.toResponse() }
    }

    fun getGoals(userId: Long): List<FundingGoalResponse> {
        return fundingGoalRepository.findByUserId(userId).map { it.toResponse() }
    }

    @Transactional
    fun createGoal(userId: Long, request: CreateGoalRequest): FundingGoalResponse {
        val goal = FundingGoal(
            userId = userId,
            title = request.title,
            targetAmount = request.targetAmount,
            deadline = request.deadline,
            isActive = request.isActive,
        )
        return fundingGoalRepository.save(goal).toResponse()
    }

    @Transactional
    fun updateGoal(userId: Long, goalId: Long, request: UpdateGoalRequest): FundingGoalResponse {
        val goal = fundingGoalRepository.findById(goalId)
            ?: throw NotFoundException("펀딩 목표", goalId)
        if (goal.userId != userId) throw ForbiddenException("해당 펀딩 목표에 대한 권한이 없습니다")
        val updated = goal.copy(
            title = request.title ?: goal.title,
            targetAmount = request.targetAmount ?: goal.targetAmount,
            deadline = request.deadline ?: goal.deadline,
            isActive = request.isActive ?: goal.isActive,
        )
        return fundingGoalRepository.update(updated).toResponse()
    }

    @Transactional
    fun deleteGoal(userId: Long, goalId: Long) {
        val goal = fundingGoalRepository.findById(goalId)
            ?: throw NotFoundException("펀딩 목표", goalId)
        if (goal.userId != userId) throw ForbiddenException("해당 펀딩 목표에 대한 권한이 없습니다")
        fundingGoalRepository.delete(goalId)
    }

    private fun parsePeriodToDays(period: String): Int {
        return when {
            period.endsWith("d") -> period.removeSuffix("d").toIntOrNull() ?: 30
            period.endsWith("w") -> (period.removeSuffix("w").toIntOrNull() ?: 4) * 7
            period.endsWith("m") -> (period.removeSuffix("m").toIntOrNull() ?: 1) * 30
            else -> 30
        }
    }

    private fun FundingTransaction.toResponse() = FundingTransactionResponse(
        id = id!!,
        source = source,
        platform = platform,
        amount = amount,
        currency = currency,
        donorName = donorName,
        message = message,
        videoId = videoId,
        videoTitle = videoTitle,
        createdAt = createdAt,
    )

    private fun FundingGoal.toResponse() = FundingGoalResponse(
        id = id!!,
        title = title,
        targetAmount = targetAmount,
        currentAmount = currentAmount,
        deadline = deadline,
        isActive = isActive,
        createdAt = createdAt,
    )
}
