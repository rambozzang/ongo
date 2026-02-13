package com.ongo.application.abtest

import com.ongo.application.abtest.dto.ABTestStatisticsResponse
import com.ongo.application.abtest.dto.VariantStatistics
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.NotFoundException
import com.ongo.domain.abtest.ABTestRepository
import com.ongo.domain.abtest.ABTestVariant
import com.ongo.domain.abtest.ABTestVariantRepository
import org.springframework.stereotype.Service
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

@Service
class ABTestStatisticsService(
    private val abTestRepository: ABTestRepository,
    private val variantRepository: ABTestVariantRepository,
) {

    companion object {
        private const val Z_95 = 1.96     // Z-score for 95% confidence
        private const val DEFAULT_P = 0.5  // assumed proportion for sample size
        private const val MARGIN_OF_ERROR = 0.05
    }

    fun getStatistics(userId: Long, testId: Long): ABTestStatisticsResponse {
        val test = abTestRepository.findById(testId) ?: throw NotFoundException("A/B 테스트", testId)
        if (test.userId != userId) throw ForbiddenException()

        val variants = variantRepository.findByTestId(testId)
        if (variants.size < 2) {
            return emptyStatistics(testId, variants)
        }

        val requiredSampleSize = calculateRequiredSampleSize()
        val currentSampleSize = variants.sumOf { it.views }
        val sampleProgress = if (requiredSampleSize > 0) {
            ((currentSampleSize.toDouble() / requiredSampleSize) * 100).coerceAtMost(100.0)
        } else 0.0

        // Calculate per-variant statistics
        val variantStats = variants.map { variant ->
            val conversionRate = if (variant.views > 0) {
                variant.clicks.toDouble() / variant.views
            } else 0.0

            val ci = wilsonScoreInterval(variant.clicks, variant.views)

            VariantStatistics(
                variantId = variant.id!!,
                name = variant.variantName,
                impressions = variant.views,
                conversions = variant.clicks,
                conversionRate = Math.round(conversionRate * 10000) / 100.0,
                confidenceInterval = ci,
                isWinner = false,
            )
        }

        // Statistical tests between variants
        val (confidence, pValue) = if (variants.all { it.views > 0 }) {
            chiSquareTest(variants)
        } else {
            Pair(0.0, 1.0)
        }

        val isSignificant = confidence >= 95.0 && currentSampleSize >= requiredSampleSize

        // Determine winner
        val winnerVariantId = if (isSignificant) {
            variantStats.maxByOrNull { it.conversionRate }?.variantId
        } else null

        val finalStats = variantStats.map { it.copy(isWinner = it.variantId == winnerVariantId) }

        return ABTestStatisticsResponse(
            testId = testId,
            sampleSizeRequired = requiredSampleSize.toInt(),
            currentSampleSize = currentSampleSize,
            sampleProgress = Math.round(sampleProgress * 10) / 10.0,
            confidence = Math.round(confidence * 10) / 10.0,
            pValue = Math.round(pValue * 10000) / 10000.0,
            isSignificant = isSignificant,
            winnerVariantId = winnerVariantId,
            variants = finalStats,
        )
    }

    /**
     * Required sample size: n = (Z² * p * (1-p)) / E²
     */
    fun calculateRequiredSampleSize(): Long {
        val n = (Z_95.pow(2) * DEFAULT_P * (1 - DEFAULT_P)) / MARGIN_OF_ERROR.pow(2)
        return n.toLong()
    }

    /**
     * Chi-square test for independence between variants.
     * Returns (confidence%, p-value)
     */
    fun chiSquareTest(variants: List<ABTestVariant>): Pair<Double, Double> {
        if (variants.size < 2) return Pair(0.0, 1.0)

        val totalImpressions = variants.sumOf { it.views }
        val totalClicks = variants.sumOf { it.clicks }
        val totalNonClicks = totalImpressions - totalClicks

        if (totalImpressions == 0L || totalClicks == 0L || totalNonClicks == 0L) {
            return Pair(0.0, 1.0)
        }

        var chiSquare = 0.0
        for (variant in variants) {
            val observed = variant.clicks.toDouble()
            val observedNon = (variant.views - variant.clicks).toDouble()

            val expectedClicks = variant.views.toDouble() * totalClicks / totalImpressions
            val expectedNon = variant.views.toDouble() * totalNonClicks / totalImpressions

            if (expectedClicks > 0) {
                chiSquare += (observed - expectedClicks).pow(2) / expectedClicks
            }
            if (expectedNon > 0) {
                chiSquare += (observedNon - expectedNon).pow(2) / expectedNon
            }
        }

        // df = (rows-1)(cols-1) = (variants-1)(2-1) = variants-1
        val df = variants.size - 1
        val pValue = chiSquarePValue(chiSquare, df)
        val confidence = ((1 - pValue) * 100).coerceIn(0.0, 100.0)

        return Pair(confidence, pValue)
    }

    /**
     * Wilson score interval for binomial proportion.
     */
    fun wilsonScoreInterval(successes: Long, total: Long): Pair<Double, Double> {
        if (total == 0L) return Pair(0.0, 0.0)

        val z = Z_95
        val pHat = successes.toDouble() / total
        val n = total.toDouble()

        val denominator = 1 + z * z / n
        val center = (pHat + z * z / (2 * n)) / denominator
        val spread = (z / denominator) * sqrt((pHat * (1 - pHat) / n) + (z * z / (4 * n * n)))

        val lower = (center - spread).coerceAtLeast(0.0)
        val upper = (center + spread).coerceAtMost(1.0)

        return Pair(
            Math.round(lower * 10000) / 100.0,
            Math.round(upper * 10000) / 100.0,
        )
    }

    /**
     * Approximate chi-square p-value using the Wilson-Hilferty transformation.
     */
    private fun chiSquarePValue(chiSquare: Double, df: Int): Double {
        if (chiSquare <= 0 || df <= 0) return 1.0

        val k = df.toDouble()
        val z = ((chiSquare / k).pow(1.0 / 3.0) - (1.0 - 2.0 / (9.0 * k))) / sqrt(2.0 / (9.0 * k))

        // Standard normal CDF approximation
        return 1.0 - normalCDF(z)
    }

    /**
     * Standard normal CDF approximation (Abramowitz and Stegun).
     */
    private fun normalCDF(x: Double): Double {
        if (x < -8.0) return 0.0
        if (x > 8.0) return 1.0

        val b0 = 0.2316419
        val b1 = 0.319381530
        val b2 = -0.356563782
        val b3 = 1.781477937
        val b4 = -1.821255978
        val b5 = 1.330274429

        val absX = abs(x)
        val t = 1.0 / (1.0 + b0 * absX)
        val pdf = Math.exp(-absX * absX / 2.0) / sqrt(2.0 * Math.PI)
        val cdf = 1.0 - pdf * (b1 * t + b2 * t.pow(2) + b3 * t.pow(3) + b4 * t.pow(4) + b5 * t.pow(5))

        return if (x >= 0) cdf else 1.0 - cdf
    }

    private fun emptyStatistics(testId: Long, variants: List<ABTestVariant>) = ABTestStatisticsResponse(
        testId = testId,
        sampleSizeRequired = calculateRequiredSampleSize().toInt(),
        currentSampleSize = 0,
        sampleProgress = 0.0,
        confidence = 0.0,
        pValue = 1.0,
        isSignificant = false,
        winnerVariantId = null,
        variants = variants.map {
            VariantStatistics(
                variantId = it.id!!,
                name = it.variantName,
                impressions = it.views,
                conversions = it.clicks,
                conversionRate = 0.0,
                confidenceInterval = Pair(0.0, 0.0),
                isWinner = false,
            )
        },
    )
}
