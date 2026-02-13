package com.ongo.domain.approval

import java.time.LocalDateTime

data class Approval(
    val id: Long? = null,
    val userId: Long,
    val videoId: Long,
    val videoTitle: String,
    val platforms: String,
    val scheduledAt: LocalDateTime? = null,
    val requesterId: Long,
    val requesterName: String,
    val reviewerId: Long? = null,
    val reviewerName: String? = null,
    val status: String = "PENDING",
    val comment: String? = null,
    val revisionNote: String? = null,
    val requestedAt: LocalDateTime = LocalDateTime.now(),
    val decidedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
