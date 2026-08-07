package com.ongo.application.abtest.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class ABTestResponse(
    val id: Long,
    val videoId: Long?,
    val testName: String,
    val status: String,
    val metricType: String,
    val durationHours: Int?,
    val winnerVariantId: Long?,
    val startedAt: LocalDateTime?,
    val endedAt: LocalDateTime?,
    val createdAt: LocalDateTime?,
    val variants: List<ABTestVariantResponse>,
)

data class ABTestVariantResponse(
    val id: Long,
    val variantName: String,
    val title: String?,
    val description: String?,
    val thumbnailUrl: String?,
    val views: Long,
    val clicks: Long,
    val engagementRate: BigDecimal,
)

data class ABTestListResponse(
    val tests: List<ABTestResponse>,
    val totalCount: Int,
)

data class CreateABTestRequest(
    val videoId: Long? = null,
    val testName: String = "A/B 테스트",
    val metricType: String = "CTR",
    val variants: List<CreateVariantRequest> = emptyList(),
    /** 프론트의 표현형. 저장 시 metricType 으로 정규화한다. */
    val type: String? = null,
    val durationHours: Int? = null,
)

data class CreateVariantRequest(
    val variantName: String,
    val title: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
)

data class UpdateABTestRequest(
    val testName: String? = null,
    val metricType: String? = null,
)

data class ABTestSummaryResponse(
    val totalTests: Int,
    val activeTests: Int,
    val completedTests: Int,
    val averageImprovement: Double,
)

data class ABTestVideoResponse(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    val duration: Int?,
    val currentCtr: Double? = null,
    val views: Long? = null,
    val publishedAt: LocalDateTime? = null,
    val hasActiveTest: Boolean = false,
)
