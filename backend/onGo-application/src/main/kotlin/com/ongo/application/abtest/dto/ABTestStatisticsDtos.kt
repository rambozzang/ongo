package com.ongo.application.abtest.dto

data class ABTestStatisticsResponse(
    val testId: Long,
    val sampleSizeRequired: Int,
    val currentSampleSize: Long,
    val sampleProgress: Double,
    val confidence: Double,
    val pValue: Double,
    val isSignificant: Boolean,
    val winnerVariantId: Long?,
    val variants: List<VariantStatistics>,
)

data class VariantStatistics(
    val variantId: Long,
    val name: String,
    val impressions: Long,
    val conversions: Long,
    val conversionRate: Double,
    val confidenceInterval: Pair<Double, Double>,
    val isWinner: Boolean,
)
