package com.ongo.application.creatorbenchmark.dto

data class BenchmarkResultResponse(
    val id: Long,
    val platform: String,
    val category: String,
    val myValue: Double,
    val avgValue: Double,
    val topValue: Double,
    val percentile: Int,
    val metric: String,
    val trend: String,
    val updatedAt: String,
)

data class BenchmarkPeerResponse(
    val id: Long,
    val name: String,
    val platform: String,
    val subscribers: Long,
    val avgViews: Long,
    val engagementRate: Double,
    val category: String?,
)

data class CreatorBenchmarkSummaryResponse(
    val totalMetrics: Int,
    val aboveAvgCount: Int,
    val topPercentile: Int,
    val strongestMetric: String,
    val weakestMetric: String,
)
