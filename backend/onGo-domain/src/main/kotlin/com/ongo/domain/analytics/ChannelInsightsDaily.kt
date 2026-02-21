package com.ongo.domain.analytics

import com.ongo.common.enums.Platform
import java.time.LocalDate
import java.time.LocalDateTime

data class ChannelInsightsDaily(
    val id: Long? = null,
    val userId: Long,
    val platform: Platform,
    val date: LocalDate,
    val trafficSource: Map<String, Long> = emptyMap(),
    val demographicsAge: Map<String, Double> = emptyMap(),
    val demographicsGender: Map<String, Double> = emptyMap(),
    val demographicsCountry: Map<String, Long> = emptyMap(),
    val createdAt: LocalDateTime? = null,
)
