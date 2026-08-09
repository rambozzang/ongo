package com.ongo.infrastructure.external.vimeo

import com.ongo.common.enums.Visibility
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

class VimeoClientHttpContractTest {
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
    fun `Vimeo pull upload sends size metadata and versioned accept header`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"uri\":\"/videos/123\",\"link\":\"https://vimeo.com/123\",\"status\":\"uploading\"}"),
        )
        val client = VimeoClient(
            vimeoApi = proxy(),
            vimeoOAuthApi = mockk(),
            vimeoConfig = mockk(),
        )

        val result = client.uploadVideo(
            PlatformUploadRequest(
                fileUrl = "https://cdn.test/video.mp4",
                title = "제목",
                description = "설명",
                tags = emptyList(),
                visibility = Visibility.PUBLIC.name,
                thumbnailUrl = null,
                accessToken = "vimeo-token",
                fileSize = 1234,
            ),
        )

        assertThat(result.platformVideoId).isEqualTo("123")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/me/videos")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer vimeo-token")
        assertThat(request.getHeader("Accept")).isEqualTo("application/vnd.vimeo.*+json;version=3.4")
        assertThat(request.body.readUtf8())
            .contains("\"approach\":\"pull\"")
            .contains("\"size\":1234")
            .contains("\"link\":\"https://cdn.test/video.mp4\"")
    }

    @Test
    fun `Vimeo OAuth uses basic client authentication and JSON grant body`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"vimeo-token\",\"refresh_token\":\"vimeo-refresh\",\"expires_in\":3600}"),
        )
        val config = mockk<VimeoConfig>()
        every { config.getClientId() } returns "client-id"
        every { config.getClientSecret() } returns "client-secret"
        val client = VimeoClient(
            vimeoApi = mockk(),
            vimeoOAuthApi = oauthProxy(),
            vimeoConfig = config,
        )

        val result = client.exchangeCodeForTokens("auth-code", "https://ongo.test/callback")

        assertThat(result.accessToken).isEqualTo("vimeo-token")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/oauth/access_token")
        assertThat(request.getHeader("Authorization")).isEqualTo("Basic Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ=")
        assertThat(request.getHeader("Accept")).isEqualTo("application/vnd.vimeo.*+json;version=3.4")
        assertThat(request.body.readUtf8())
            .contains("\"grant_type\":\"authorization_code\"")
            .contains("\"code\":\"auth-code\"")
            .contains("\"redirect_uri\":\"https://ongo.test/callback\"")
    }

    private fun proxy(): VimeoApi = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(PlatformRestClientSupport.builder(server.url("").toString().removeSuffix("/")).build()))
        .build()
        .createClient(VimeoApi::class.java)

    private fun oauthProxy(): VimeoOAuthApi = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(PlatformRestClientSupport.builder(server.url("").toString().removeSuffix("/")).build()))
        .build()
        .createClient(VimeoOAuthApi::class.java)
}
