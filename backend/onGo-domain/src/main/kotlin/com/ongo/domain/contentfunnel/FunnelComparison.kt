package com.ongo.domain.contentfunnel

import java.time.LocalDateTime

data class FunnelComparison(
    val id: Long? = null,
    val userId: Long,
    val videoIdA: Long,
    val videoTitleA: String,
    val videoIdB: Long,
    val videoTitleB: String,
    val winner: String? = null,
    val createdAt: LocalDateTime? = null,
)
