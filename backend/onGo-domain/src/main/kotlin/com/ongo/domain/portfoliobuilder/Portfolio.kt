package com.ongo.domain.portfoliobuilder

import java.time.LocalDateTime

data class Portfolio(
    val id: Long? = null,
    val userId: Long,
    val title: String,
    val description: String = "",
    val template: String = "MINIMAL",
    val theme: String = "light",
    val isPublished: Boolean = false,
    val publicUrl: String? = null,
    val viewCount: Int = 0,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
