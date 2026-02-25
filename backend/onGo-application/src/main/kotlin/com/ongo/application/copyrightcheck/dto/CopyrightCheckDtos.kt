package com.ongo.application.copyrightcheck.dto

import java.time.LocalDateTime

data class CopyrightCheckResultResponse(
    val id: Long,
    val videoId: Long,
    val videoTitle: String,
    val status: String,
    val issues: String,
    val musicDetected: String,
    val monetizationEligible: Boolean,
    val platformChecks: String,
    val checkedAt: LocalDateTime?,
)

data class RunCheckRequest(
    val videoId: Long,
    val videoTitle: String,
)

data class RunCheckResponse(
    val id: Long,
    val videoId: Long,
    val videoTitle: String,
    val status: String,
    val issues: String,
    val musicDetected: String,
    val monetizationEligible: Boolean,
    val platformChecks: String,
    val checkedAt: LocalDateTime?,
)
