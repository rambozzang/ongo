package com.ongo.domain.approval

import java.time.LocalDateTime

data class ApprovalComment(
    val id: Long? = null,
    val approvalId: Long,
    val userId: Long,
    val userName: String,
    val content: String,
    val field: String? = null,
    val createdAt: LocalDateTime? = null,
)
