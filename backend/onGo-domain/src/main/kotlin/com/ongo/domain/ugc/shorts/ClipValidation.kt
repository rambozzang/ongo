package com.ongo.domain.ugc.shorts

import java.time.Instant

/** 클립 하나에 대한 결정론적 또는 AI 검증 결과. */
data class ClipValidation(
    val id: Long = 0,
    val clipId: Long,
    val ruleCode: String,
    val severity: ClipValidationSeverity = ClipValidationSeverity.WARNING,
    val passed: Boolean = true,
    val message: String? = null,
    val createdAt: Instant = Instant.now(),
)
