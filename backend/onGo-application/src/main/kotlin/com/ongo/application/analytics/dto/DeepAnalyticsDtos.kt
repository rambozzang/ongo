package com.ongo.application.analytics.dto

data class TrafficSourceResponse(
    val period: String,
    val sources: Map<String, Long>,
    val total: Long,
)

data class DemographicsResponse(
    val period: String,
    val ageDistribution: Map<String, Double>,
    val genderDistribution: Map<String, Double>,
    val topCountries: Map<String, Long>,
)

data class CTRTrendPoint(
    val date: String,
    val impressions: Long,
    val views: Long,
    val ctr: Double,
)

data class CTRResponse(
    val period: String,
    val avgCTR: Double,
    val totalImpressions: Long,
    val data: List<CTRTrendPoint>,
)

data class AvgViewDurationResponse(
    val period: String,
    val avgDurationSeconds: Long,
    val data: List<AvgViewDurationPoint>,
)

data class AvgViewDurationPoint(
    val date: String,
    val avgDurationSeconds: Long,
    val totalWatchTimeSeconds: Long,
    val totalViews: Long,
)

data class SubscriberConversionResponse(
    val period: String,
    val totalGained: Long,
    val data: List<SubscriberConversionPoint>,
)

data class SubscriberConversionPoint(
    val date: String,
    val gained: Int,
    val views: Long,
    val conversionRate: Double,
)
