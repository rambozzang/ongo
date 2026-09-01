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

    internal fun parseRssFeed(xml: String, region: String): List<Trend> {
        /*
         * 제목과 트래픽을 각각 전부 찾은 뒤 인덱스로 맞추면 중간 item 하나에
         * approx_traffic 이 빠지는 순간 **다음 키워드에 이전 키워드의 점수**가 붙는다.
         * item 단위로 읽어야 RSS 필드 누락이 다른 트렌드의 근거를 오염시키지 않는다.
         */
        val itemRegex = Regex("<item\\b[^>]*>(.*?)</item>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val titleRegex = Regex("<title\\b[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val trafficRegex = Regex(
            "<ht:approx_traffic\\b[^>]*>(.*?)</ht:approx_traffic>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

        return itemRegex.findAll(xml).mapNotNull { item ->
            val body = item.groupValues[1]
            val title = titleRegex.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            val score = parseTrafficScore(trafficRegex.find(body)?.groupValues?.get(1))

            if (title == null || score == null) {
                log.debug("Google Trends item을 근거 부족으로 제외한다: titlePresent={}, trafficPresent={}", title != null, score != null)
                return@mapNotNull null
            }

            Trend(
                keyword = title,
                score = score,
                source = "GOOGLE_TRENDS",
                region = region,
                date = LocalDate.now(),
                category = "TRENDING",
            )
        }.toList()
    }

    private fun parseTrafficScore(traffic: String?): Double? {
        if (traffic == null) return null
        val cleaned = traffic.replace("+", "").replace(",", "").trim()
        return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }
}
