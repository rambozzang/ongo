package com.ongo.infrastructure.external.trends

import com.ongo.application.trend.TrendDataSource
import com.ongo.domain.trend.Trend
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.LocalDate

@Service
class GoogleTrendsService : TrendDataSource {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = RestClient.builder()
        .baseUrl("https://trends.google.com")
        .build()

    override fun fetchDailyTrends(region: String): List<Trend> {
        return try {
            val response = restClient.get()
                .uri("/trending/rss?geo=$region")
                .retrieve()
                .body(String::class.java) ?: return emptyList()

            parseRssFeed(response, region)
        } catch (e: Exception) {
            log.warn("Google Trends RSS 수집 실패: {}", e.message)
            emptyList()
        }
    }

    private fun parseRssFeed(xml: String, region: String): List<Trend> {
        val trends = mutableListOf<Trend>()
        val titleRegex = Regex("<title>(.+?)</title>")
        val trafficRegex = Regex("<ht:approx_traffic>(.+?)</ht:approx_traffic>")

        val titles = titleRegex.findAll(xml).map { it.groupValues[1] }.toList().drop(1)
        val traffics = trafficRegex.findAll(xml).map { it.groupValues[1] }.toList()

        titles.forEachIndexed { index, title ->
            val traffic = traffics.getOrNull(index)
            val score = parseTrafficScore(traffic)
            trends.add(
                Trend(
                    keyword = title.trim(),
                    score = score,
                    source = "GOOGLE_TRENDS",
                    region = region,
                    date = LocalDate.now(),
                    category = "TRENDING",
                )
            )
        }
        return trends
    }

    private fun parseTrafficScore(traffic: String?): Double {
        if (traffic == null) return 0.0
        val cleaned = traffic.replace("+", "").replace(",", "").trim()
        return cleaned.toDoubleOrNull() ?: 0.0
    }
}
