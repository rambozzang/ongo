package com.ongo.domain.thumbnailabtest

import java.math.BigDecimal
import java.time.LocalDateTime

data class ThumbnailAbTest(
    val id: Long = 0,
    val workspaceId: Long,
    val videoTitle: String,
    val platform: String,
    val status: String = "PENDING",
    val variantAImageUrl: String? = null,
    val variantACtr: BigDecimal = BigDecimal.ZERO,
    val variantAImpressions: Long = 0,
    val variantAClicks: Long = 0,
    val variantBImageUrl: String? = null,
    val variantBCtr: BigDecimal = BigDecimal.ZERO,
    val variantBImpressions: Long = 0,
    val variantBClicks: Long = 0,
    val winner: String? = null,
    val startedAt: LocalDateTime = LocalDateTime.now(),
    val endedAt: LocalDateTime? = null,
)
