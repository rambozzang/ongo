package com.ongo.domain.fanfunding

import java.time.LocalDateTime

data class FundingTransaction(
    val id: Long? = null,
    val userId: Long,
    val source: String = "SUPER_CHAT",
    val platform: String,
    val amount: Long = 0,
    val currency: String = "KRW",
    val donorName: String? = null,
    val message: String? = null,
    val videoId: String? = null,
    val videoTitle: String? = null,
    val createdAt: LocalDateTime? = null,
)
