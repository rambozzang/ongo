package com.ongo.domain.influencermatch

import java.time.LocalDateTime

data class CollabRequest(
    val id: Long? = null,
    val userId: Long,
    val influencerProfileId: Long,
    val message: String? = null,
    val proposedBudget: Long = 0,
    val proposedType: String = "COLLABORATION",
    val status: String = "PENDING",
    val responseMessage: String? = null,
    val respondedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
