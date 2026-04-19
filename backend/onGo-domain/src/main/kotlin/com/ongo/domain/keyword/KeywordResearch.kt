package com.ongo.domain.keyword

import java.time.LocalDateTime

data class KeywordResearch(
    val id: Long? = null,
    val userId: Long,
    val keyword: String,
    val platforms: String,
    val resultJson: String,
    val createdAt: LocalDateTime? = null,
)
