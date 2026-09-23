package com.ongo.common

/** Requested and actually applied range for a plan-limited analytics response. */
data class PeriodLimitMetadata(
    val requestedDays: Int,
    val appliedDays: Int,
    val maxDays: Int,
    val wasTruncated: Boolean,
)
