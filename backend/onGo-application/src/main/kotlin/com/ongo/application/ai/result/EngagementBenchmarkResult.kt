package com.ongo.application.ai.result

data class EngagementBenchmarkResult(
    val myEngagementRate: Double,
    val categoryAverage: Double,
    val percentile: Int,
    val platformBenchmarks: List<PlatformBenchmark>,
    val strengths: List<String>,
    val improvements: List<String>,
) {
    data class PlatformBenchmark(
        val platform: String,
        val myRate: Double,
        val categoryAverage: Double,
        val rating: String,
    )
}
