package com.ongo.application.analytics.dto

data class CohortGroupResponse(
    val name: String,
    val videoCount: Int,
    val avgViews: Long,
    val cumulativeViewCurve: List<DataPoint>,
)

data class DataPoint(
    val day: Int,
    val value: Long,
    val normalizedPercent: Double,
)

data class CohortAnalysisResponse(
    val groupBy: String,
    val cohorts: List<CohortGroupResponse>,
    val dateRange: DateRangeInfo,
)

data class DateRangeInfo(
    val from: String,
    val to: String,
)

data class RetentionDataPoint(
    val timestamp: Int,
    val retentionRate: Double,
    val viewCount: Long,
)

data class DropOffPoint(
    val timestamp: Int,
    val dropRate: Double,
    val possibleReason: String,
)

data class RetentionCurveResponse(
    val videoId: Long,
    val retentionPoints: List<RetentionDataPoint>,
    val avgRetention: List<RetentionDataPoint>,
    val dropOffPoints: List<DropOffPoint>,
)
