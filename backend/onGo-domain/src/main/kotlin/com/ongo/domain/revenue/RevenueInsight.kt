package com.ongo.domain.revenue

import java.math.BigDecimal
import java.time.LocalDateTime

data class RevenueInsight(
    val id: Long? = null,
    val userId: Long,
    val insightType: String,
    val content: String, // JSON 직렬화된 인사이트 데이터
    val platform: String? = null,
    val confidence: BigDecimal? = null,
    val createdAt: LocalDateTime? = null,
)
