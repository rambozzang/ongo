package com.ongo.domain.analytics

data class LiveAlertConfig(
    val id: Long? = null,
    val userId: Long,
    val type: String,
    val enabled: Boolean = true,
    val threshold: Int = 0,
)
