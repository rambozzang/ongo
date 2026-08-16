package com.ongo.application.admin.dto

import java.time.LocalDateTime

/** 운영자 화면에 표시할 계정 삭제 작업. 개인정보 본문과 idempotency key는 포함하지 않는다. */
data class AccountDeletionAdminJobResponse(
    val jobId: Long,
    val userId: Long,
    val status: String,
    val attemptCount: Int,
    val requestedAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val lastErrorCode: String?,
    val supportReference: String?,
)
