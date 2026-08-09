package com.ongo.infrastructure.external.instagram

import com.ongo.common.exception.PlatformApiException
import com.ongo.infrastructure.external.instagram.dto.InstagramError
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
