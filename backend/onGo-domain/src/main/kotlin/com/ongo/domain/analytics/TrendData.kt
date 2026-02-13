package com.ongo.domain.analytics

import com.ongo.common.enums.Platform
import java.time.LocalDate

data class TrendData(
    val date: LocalDate,
    val platform: Platform?,
    val views: Long,
)
