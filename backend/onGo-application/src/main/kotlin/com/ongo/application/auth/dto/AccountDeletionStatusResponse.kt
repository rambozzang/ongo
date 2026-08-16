package com.ongo.application.auth.dto

import java.time.LocalDateTime

/**
 * 계정 삭제 상태의 공개 응답.
 *
 * userId와 idempotency key는 노출하지 않는다. 지원에 필요한 참조값만 제공한다.
 */
data class AccountDeletionStatusResponse(
    val state: String,
    val status: String?,
    val jobId: Long?,
    val requestedAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val lastErrorCode: String?,
    val supportReference: String?,
    val retryable: Boolean,
)
