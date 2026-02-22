package com.ongo.domain.branddeal

import java.time.LocalDate
import java.time.LocalDateTime

data class BrandDeal(
    val id: Long? = null,
    val userId: Long,
    val brandName: String,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val dealValue: Long? = null,
    val currency: String = "KRW",
    val status: String = "INQUIRY",
    val deadline: LocalDate? = null,
    val deliverables: String? = "[]",
    val notes: String? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
