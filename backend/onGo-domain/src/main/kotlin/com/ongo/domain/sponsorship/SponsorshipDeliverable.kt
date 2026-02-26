package com.ongo.domain.sponsorship

import java.time.LocalDate
import java.time.LocalDateTime

data class SponsorshipDeliverable(
    val id: Long = 0,
    val sponsorshipId: Long,
    val type: String,
    val title: String,
    val description: String? = null,
    val dueDate: LocalDate,
    val isCompleted: Boolean = false,
    val videoId: String? = null,
    val platform: String,
    val completedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
