package com.ongo.domain.copyrightcheck

import java.time.LocalDateTime

data class CopyrightCheckResult(
    val id: Long? = null,
    val userId: Long,
    val videoId: Long,
    val videoTitle: String,
    val status: String = "PENDING",
    val issues: String = "[]", // JSONB as String
    val musicDetected: String = "[]", // JSONB as String
    val monetizationEligible: Boolean = true,
    val platformChecks: String = "{}", // JSONB as String
    val checkedAt: LocalDateTime? = null,
)
