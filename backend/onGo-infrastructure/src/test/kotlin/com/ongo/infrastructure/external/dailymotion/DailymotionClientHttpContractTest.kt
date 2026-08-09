package com.ongo.infrastructure.external.dailymotion

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.common.enums.Visibility
import com.ongo.infrastructure.external.platform.PlatformFileTransferHelper
import com.ongo.infrastructure.external.platform.PlatformRestClientSupport
import com.ongo.infrastructure.external.platform.PlatformUploadRequest
import io.mockk.mockk
import io.mockk.every
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

class DailymotionClientHttpContractTest {
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
    fun `Dailymotion uploads a file through v2 session and creates a profile video`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "video/mp4").setBody("video-bytes"))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"upload_url\":\"${server.url("/upload?uuid=1&seal=signed")}\",\"progress_url\":\"${server.url("/progress")}\"}"),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"url\":\"https://upload.dm.test/files/file-1\"}"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"video_id\":\"dm-video-1\",\"url\":\"https://www.dailymotion.com/video/dm-video-1\",\"status\":\"processing\"}"),
        )

        val client = DailymotionClient(
            dailymotionApi = proxy(),
            dailymotionOAuthApi = mockk(),
            dailymotionConfig = mockk(),
            objectMapper = ObjectMapper(),
            fileTransferHelper = PlatformFileTransferHelper(ObjectMapper()),
        )

        val result = client.uploadVideo(
            PlatformUploadRequest(
                fileUrl = server.url("/source/video.mp4").toString(),
                title = "제목",
                description = "설명",
                tags = listOf("#tag", "topic"),
                visibility = Visibility.PUBLIC.name,
                thumbnailUrl = null,
                accessToken = "dm-token",
                platformChannelId = "profile-1",
            ),
        )

        assertThat(result.platformVideoId).isEqualTo("dm-video-1")
        assertThat(result.platformUrl).isEqualTo("https://www.dailymotion.com/video/dm-video-1")

        val source = server.takeRequest()
        assertThat(source.method).isEqualTo("GET")
        assertThat(source.path).isEqualTo("/source/video.mp4")

        val session = server.takeRequest()
        assertThat(session.method).isEqualTo("POST")
        assertThat(session.path).isEqualTo("/v2/files/upload_sessions")
        assertThat(session.getHeader("Authorization")).isEqualTo("Bearer dm-token")

        val upload = server.takeRequest()
        assertThat(upload.method).isEqualTo("POST")
        assertThat(upload.path).isEqualTo("/upload?uuid=1&seal=signed")
        assertThat(upload.getHeader("Authorization")).isNull()
        assertThat(upload.getHeader("Content-Type")).startsWith("multipart/form-data")
        assertThat(upload.body.readUtf8()).contains("name=\"file\"").contains("video-bytes")

        val create = server.takeRequest()
        assertThat(create.method).isEqualTo("POST")
        assertThat(create.path).isEqualTo("/v2/profiles/profile-1/videos")
        assertThat(create.getHeader("Authorization")).isEqualTo("Bearer dm-token")
        assertThat(create.body.readUtf8())
            .contains("\"title\":\"제목\"")
            .contains("\"visibility\":\"public\"")
            .contains("\"is_for_kids\":false")
            .contains("\"file_url\":\"https://upload.dm.test/files/file-1\"")
    }

    @Test
    fun `Dailymotion OAuth exchanges code as form data`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"access_token\":\"dm-token\",\"refresh_token\":\"dm-refresh\",\"expires_in\":3600}"),
        )
        val config = mockk<DailymotionConfig>()
        every { config.getApiKey() } returns "api-key"
        every { config.getApiSecret() } returns "api-secret"
        val client = DailymotionClient(
            dailymotionApi = mockk(),
            dailymotionOAuthApi = oauthProxy(),
            dailymotionConfig = config,
            objectMapper = ObjectMapper(),
            fileTransferHelper = mockk(),
        )

        val result = client.exchangeCodeForTokens("auth-code", "https://ongo.test/callback")

        assertThat(result.accessToken).isEqualTo("dm-token")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/oauth/token")
        assertThat(request.getHeader("Content-Type")).startsWith("application/x-www-form-urlencoded")
        assertThat(request.body.readUtf8())
            .contains("grant_type=authorization_code")
            .contains("client_id=api-key")
            .contains("client_secret=api-secret")
            .contains("code=auth-code")
            .contains("redirect_uri=https%3A%2F%2Fongo.test%2Fcallback")
    }

    private fun proxy(): DailymotionApi = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(PlatformRestClientSupport.builder(server.url("").toString().removeSuffix("/")).build()))
        .build()
        .createClient(DailymotionApi::class.java)

    private fun oauthProxy(): DailymotionOAuthApi = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(PlatformRestClientSupport.builder(server.url("").toString().removeSuffix("/")).build()))
        .build()
        .createClient(DailymotionOAuthApi::class.java)
}
