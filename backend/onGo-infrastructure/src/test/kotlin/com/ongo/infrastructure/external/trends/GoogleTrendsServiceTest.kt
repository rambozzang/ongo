package com.ongo.infrastructure.external.trends

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GoogleTrendsServiceTest {

    private val service = GoogleTrendsService()

    @Test
    @DisplayName("트래픽이 없는 RSS 항목은 0점으로 위장하지 않고 제외한다")
    fun omitsItemsWithoutMeasuredTraffic() {
        val trends = service.parseRssFeed(
            """
            <rss><channel>
              <item><title>근거 없는 키워드</title></item>
              <item><title>측정된 키워드</title><ht:approx_traffic>1,200+</ht:approx_traffic></item>
            </channel></rss>
            """.trimIndent(),
            "KR",
        )

        assertEquals(listOf("측정된 키워드"), trends.map { it.keyword })
        assertEquals(1_200.0, trends.single().score)
    }

    @Test
    @DisplayName("항목별 트래픽 파싱은 누락된 앞 항목 때문에 뒤 점수를 밀지 않는다")
    fun keepsItemTrafficAligned() {
        val trends = service.parseRssFeed(
            """
            <rss><channel>
              <item><title>첫 번째</title></item>
              <item><title>두 번째</title><ht:approx_traffic>2,400+</ht:approx_traffic></item>
              <item><title>세 번째</title><ht:approx_traffic>300+</ht:approx_traffic></item>
            </channel></rss>
            """.trimIndent(),
            "KR",
        )

        assertEquals(listOf("두 번째", "세 번째"), trends.map { it.keyword })
        assertEquals(listOf(2_400.0, 300.0), trends.map { it.score })
        assertTrue(trends.none { it.score == 0.0 })
    }

    @Test
    @DisplayName("잘못된 트래픽 값은 트렌드 근거로 저장하지 않는다")
    fun omitsInvalidTraffic() {
        val trends = service.parseRssFeed(
            "<rss><channel><item><title>불명확한 키워드</title><ht:approx_traffic>Breakout</ht:approx_traffic></item></channel></rss>",
            "KR",
        )

        assertTrue(trends.isEmpty())
    }
}
