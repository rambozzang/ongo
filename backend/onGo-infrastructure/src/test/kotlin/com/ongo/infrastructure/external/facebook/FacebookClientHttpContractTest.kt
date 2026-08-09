package com.ongo.infrastructure.external.facebook

import com.ongo.common.enums.Visibility
import com.ongo.infrastructure.external.platform.PlatformRestClientSupport
import com.ongo.infrastructure.external.platform.PlatformUploadRequest
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

class FacebookClientHttpContractTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Facebook cloud upload sends page, metadata, source URL, and token`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"id":"fb-video-1"}"""))

        val api = proxy<FacebookApi>()
        val client = FacebookClient(api, mockk(), mockk(relaxed = true))
        val result = client.uploadVideo(
            PlatformUploadRequest(
                fileUrl = "https://cdn.test/video.mp4",
                title = "테스트 제목",
                description = "테스트 설명",
                tags = listOf("#tag"),
                visibility = Visibility.PUBLIC.name,
                thumbnailUrl = null,
                accessToken = "fb-token",
                platformChannelId = "page-1",
            ),
        )

        assertThat(result.platformVideoId).isEqualTo("fb-video-1")
        assertThat(result.platformUrl).isEqualTo("https://www.facebook.com/fb-video-1")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).contains("/page-1/videos")
        assertThat(request.path).contains("file_url=https%3A%2F%2Fcdn.test%2Fvideo.mp4")
        assertThat(request.path).contains("title=")
        assertThat(request.path).contains("description=")
        assertThat(request.requestUrl?.queryParameter("description")).contains("테스트 설명").contains("#tag")
        assertThat(request.path).contains("access_token=fb-token")
    }

    private inline fun <reified T : Any> proxy(): T {
        val restClient = PlatformRestClientSupport
            .builder(server.url("/v21.0").toString().removeSuffix("/"))
            .build()
        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(T::class.java)
    }
}
