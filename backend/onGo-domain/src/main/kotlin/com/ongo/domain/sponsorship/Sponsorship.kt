package com.ongo.domain.sponsorship

import java.time.LocalDate
import java.time.LocalDateTime

data class Sponsorship(
    val id: Long = 0,
    val userId: Long,
    val brandName: String,
    val brandLogo: String? = null,
    val contactName: String,
    val contactEmail: String,
    val status: String = "INQUIRY",
    val dealValue: Long = 0,
    val currency: String = "KRW",
    val startDate: LocalDate,
    val endDate: LocalDate,
    val notes: String? = null,
    val contractUrl: String? = null,
    val paymentStatus: String = "PENDING",
    val paidAmount: Long = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
