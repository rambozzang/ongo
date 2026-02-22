package com.ongo.application.trend

import com.ongo.domain.trend.Trend

interface TrendDataSource {
    fun fetchDailyTrends(region: String = "KR"): List<Trend>
}
