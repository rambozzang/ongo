package com.ongo.application.revenueanalyzer

import com.ongo.application.revenueanalyzer.dto.*
import com.ongo.domain.revenueanalyzer.RevenueProjection
import com.ongo.domain.revenueanalyzer.RevenueProjectionRepository
import com.ongo.domain.revenueanalyzer.RevenueStream
import com.ongo.domain.revenueanalyzer.RevenueStreamRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class RevenueAnalyzerUseCase(
    private val streamRepository: RevenueStreamRepository,
    private val projectionRepository: RevenueProjectionRepository,
) {

    fun getStreams(userId: Long): List<RevenueStreamResponse> {
        return streamRepository.findByUserId(userId).map { it.toResponse() }
    }

    fun getProjections(userId: Long, channelId: Long): List<RevenueProjectionResponse> {
        return projectionRepository.findByChannelId(channelId).map { it.toProjectionResponse() }
    }

    fun analyze(userId: Long, channelId: Long): List<RevenueStreamResponse> {
        return streamRepository.findByUserId(userId)
            .filter { it.channelId == channelId }
            .map { it.toResponse() }
    }

    fun getSummary(userId: Long): RevenueAnalyzerSummaryResponse {
        val streams = streamRepository.findByUserId(userId)
        val totalRevenue = streams.fold(BigDecimal.ZERO) { acc, s -> acc + s.amount }
        val avgGrowth = if (streams.isNotEmpty()) {
            streams.fold(BigDecimal.ZERO) { acc, s -> acc + s.growth }
                .divide(BigDecimal(streams.size), 2, java.math.RoundingMode.HALF_UP)
        } else BigDecimal.ZERO
        val topSource = streams.groupBy { it.source }
            .mapValues { (_, v) -> v.fold(BigDecimal.ZERO) { acc, s -> acc + s.amount } }
            .maxByOrNull { it.value }?.key ?: ""
        val channelCount = streams.map { it.channelId }.distinct().size
        val projections = projectionRepository.findByUserId(userId)
        val projectedTotal = projections.fold(BigDecimal.ZERO) { acc, p -> acc + p.projectedMonthly }
        val avgConfidence = if (projections.isNotEmpty()) projections.map { it.confidence }.average().toInt() else 0
        return RevenueAnalyzerSummaryResponse(
            totalRevenue = totalRevenue,
            monthlyGrowth = avgGrowth,
            topSource = topSource,
            channelCount = channelCount,
            projectedNextMonth = projectedTotal,
            avgConfidence = avgConfidence,
        )
    }

    private fun RevenueStream.toResponse() = RevenueStreamResponse(
        id = id!!,
        channelId = channelId,
        channelName = channelName,
        source = source,
        amount = amount,
        currency = currency,
        period = period,
        growth = growth,
        createdAt = createdAt,
    )

    private fun RevenueProjection.toProjectionResponse() = RevenueProjectionResponse(
        id = id!!,
        channelId = channelId,
        source = source,
        currentMonthly = currentMonthly,
        projectedMonthly = projectedMonthly,
        confidence = confidence,
        projectionDate = projectionDate,
        factors = factors,
        createdAt = createdAt,
    )
}
