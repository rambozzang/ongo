package com.ongo.infrastructure.external.instagram

import com.ongo.common.exception.PlatformApiException
import com.ongo.infrastructure.external.instagram.dto.InstagramError
import com.ongo.infrastructure.external.instagram.dto.InstagramInsightsResponse
import com.ongo.infrastructure.external.instagram.dto.InstagramMediaResponse
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class InstagramClientTest {

    @Test
    fun `게시 상태 조회는 컨테이너가 아니라 게시된 media 상세와 permalink를 확인한다`() {
        val api = mockk<InstagramApi>()
        every {
            api.getMedia("media-1", "id,permalink", "token")
        } returns media(id = "media-1", permalink = "https://instagram.com/reel/media-1")

        val result = client(api).getVideoStatus("media-1", "token")

        assertThat(result.status).isEqualTo("PUBLISHED")
        assertThat(result.platformVideoId).isEqualTo("media-1")
        assertThat(result.platformUrl).isEqualTo("https://instagram.com/reel/media-1")
    }

    @Test
    fun `Graph 오류는 not_found나 0 분석값으로 삼키지 않고 API 오류로 전달한다`() {
        val api = mockk<InstagramApi>()
        every { api.getMedia("media-1", "id,permalink", "token") } returns media(
            id = null,
            permalink = null,
            error = InstagramError("권한 없음", "OAuthException", 190, null, null),
        )

        assertThatThrownBy { client(api).getVideoStatus("media-1", "token") }
            .isInstanceOf(PlatformApiException::class.java)
    }

    /** Meta 가 2025-04-21 `plays` 를 폐기했다. 요청에 넣으면 호출 전체가 오류라 수집이 매번 실패했다. */
    @Test
    fun `분석은 폐기된 plays 대신 views 를 요청하고 조회수로 쓴다`() {
        val api = mockk<InstagramApi>()
        val metric = io.mockk.slot<String>()
        fun m(name: String, v: Long) = InstagramInsightsResponse.InsightMetric(name, "lifetime", listOf(InstagramInsightsResponse.InsightValue(v)), name)
        every { api.getMediaInsights("media-1", capture(metric), "token") } returns InstagramInsightsResponse(
            data = listOf(m("views", 1200), m("likes", 30), m("comments", 4), m("shares", 2), m("saved", 1)),
            error = null,
        )

        val analytics = client(api).getVideoAnalytics("media-1", "token", java.time.LocalDate.now(), java.time.LocalDate.now())

        assertThat(metric.captured.split(",")).contains("views").doesNotContain("plays", "impressions")
        assertThat(analytics.views).isEqualTo(1200)
        assertThat(analytics.likes).isEqualTo(30)
    }

    private fun client(api: InstagramApi) = InstagramClient(
        instagramApi = api,
        instagramOAuthApi = mockk(),
        instagramConfig = mockk<InstagramConfig>(relaxed = true),
    )

    private fun media(
        id: String?,
        permalink: String?,
        error: InstagramError? = null,
    ) = InstagramMediaResponse(
        id = id,
        caption = null,
        mediaType = "REELS",
        mediaUrl = null,
        permalink = permalink,
        timestamp = null,
        likeCount = null,
        commentsCount = null,
        error = error,
    )
}
