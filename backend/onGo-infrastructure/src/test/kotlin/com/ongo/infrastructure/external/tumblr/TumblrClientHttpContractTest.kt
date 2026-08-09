package com.ongo.infrastructure.external.tumblr

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.common.enums.Visibility
import com.ongo.infrastructure.external.platform.PlatformFileTransferHelper
import com.ongo.infrastructure.external.platform.PlatformRestClientSupport
import com.ongo.infrastructure.external.platform.PlatformUploadRequest
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

class TumblrClientHttpContractTest {
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
    fun `Tumblr creates an NPF native video with JSON and file multipart parts`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody("video-bytes"))
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"meta\":{\"status\":201},\"response\":{\"id\":123,\"id_string\":\"123\"}}"),
        )

        val config = mockk<TumblrConfig>()
        every { config.getApiBaseUrl() } returns server.url("").toString().removeSuffix("/")
        val client = TumblrClient(
            tumblrApi = mockk(),
            tumblrOAuthApi = mockk(),
            tumblrConfig = config,
            objectMapper = jacksonObjectMapper(),
            fileTransferHelper = PlatformFileTransferHelper(jacksonObjectMapper()),
        )

        val result = client.uploadVideo(
            PlatformUploadRequest(
                fileUrl = server.url("/source/video.mp4").toString(),
                title = "제목",
                description = "설명",
                tags = listOf("#tag"),
                visibility = Visibility.PUBLIC.name,
                thumbnailUrl = null,
                accessToken = "tumblr-token",
                platformChannelId = "creator",
            ),
        )

        assertThat(result.platformVideoId).isEqualTo("creator:123")
        assertThat(result.platformUrl).isEqualTo("https://creator.tumblr.com/post/123")

        val source = server.takeRequest()
        assertThat(source.method).isEqualTo("GET")
        assertThat(source.path).isEqualTo("/source/video.mp4")

        val post = server.takeRequest()
        assertThat(post.method).isEqualTo("POST")
        assertThat(post.path).isEqualTo("/v2/blog/creator/posts")
        assertThat(post.getHeader("Authorization")).isEqualTo("Bearer tumblr-token")
        assertThat(post.getHeader("Content-Type")).startsWith("multipart/form-data")
        val body = post.body.readUtf8()
        assertThat(body)
            .contains("name=\"json\"")
            .contains("\"type\":\"video\"")
            .contains("\"identifier\":\"ongo-video\"")
            .contains("name=\"ongo-video\"")
            .contains("video-bytes")
    }

    @Test
    fun `Tumblr OAuth uses the official API token endpoint and form body`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"tumblr-token\",\"refresh_token\":\"tumblr-refresh\",\"expires_in\":3600,\"scope\":\"write offline_access\"}"),
        )
        val config = mockk<TumblrConfig>()
        every { config.getConsumerKey() } returns "consumer-key"
        every { config.getConsumerSecret() } returns "consumer-secret"
        val client = TumblrClient(
            tumblrApi = mockk(),
            tumblrOAuthApi = oauthProxy(),
            tumblrConfig = config,
            objectMapper = jacksonObjectMapper(),
            fileTransferHelper = mockk(),
        )

        val result = client.exchangeCodeForTokens("auth-code", "https://ongo.test/callback")

        assertThat(result.accessToken).isEqualTo("tumblr-token")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/v2/oauth2/token")
        assertThat(request.getHeader("Content-Type")).startsWith("application/x-www-form-urlencoded")
        assertThat(request.body.readUtf8())
            .contains("grant_type=authorization_code")
            .contains("code=auth-code")
            .contains("client_id=consumer-key")
            .contains("client_secret=consumer-secret")
            .contains("redirect_uri=https%3A%2F%2Fongo.test%2Fcallback")
    }

    private fun oauthProxy(): TumblrOAuthApi = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(PlatformRestClientSupport.builder(server.url("").toString().removeSuffix("/")).build()))
        .build()
        .createClient(TumblrOAuthApi::class.java)
}
