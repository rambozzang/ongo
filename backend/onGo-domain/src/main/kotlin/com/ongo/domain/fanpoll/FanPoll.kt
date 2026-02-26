package com.ongo.domain.fanpoll

import java.time.Instant
import java.time.LocalDate

data class FanPoll(
    val id: Long = 0,
    val userId: Long,
    val title: String,
    val description: String? = null,
    val type: String = "SINGLE",
    val status: String = "DRAFT",
    val totalVotes: Int = 0,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val createdAt: Instant = Instant.now(),
)
