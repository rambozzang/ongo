package com.ongo.infrastructure.external.linkedin

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

class LinkedInClientHttpContractTest {
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
    fun `LinkedIn uses Videos API multipart parts finalize and waits before UGC post`() {
        server.enqueue(MockResponse().setBody("test"))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"value":{"video":"urn:li:video:1","uploadToken":"token-1","uploadInstructions":[{"uploadUrl":"${server.url("/upload/1")}","firstByte":0,"lastByte":3}]}}""",
                ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"etag-1\""))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"urn:li:video:1","status":"AVAILABLE"}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"urn:li:ugcPost:1"}"""),
        )

        val config = mockk<LinkedInConfig>()
        every { config.getApiVersion() } returns "202607"
        every { config.getVideoPollAttempts() } returns 1
        every { config.getVideoPollIntervalMillis() } returns 0
        every { config.getClientId() } returns "client-id"
        every { config.getClientSecret() } returns "client-secret"

        val client = LinkedInClient(
            linkedInApi = proxy(),
            linkedInVideosApi = videosProxy(),
            linkedInOAuthApi = mockk(),
            linkedInConfig = config,
        )

        val result = client.uploadVideo(
            PlatformUploadRequest(
                fileUrl = server.url("/source/video.mp4").toString(),
                title = "제목",
                description = "설명",
                tags = emptyList(),
                visibility = Visibility.PUBLIC.name,
                thumbnailUrl = null,
                accessToken = "linkedin-token",
                platformChannelId = "person-1",
            ),
        )

        assertThat(result.platformVideoId).isEqualTo("urn:li:ugcPost:1")
        assertThat(result.platformUrl).isEqualTo("https://www.linkedin.com/feed/update/urn:li:ugcPost:1")

        assertThat(server.takeRequest().path).isEqualTo("/source/video.mp4")

        val initialize = server.takeRequest()
        assertThat(initialize.path).isEqualTo("/rest/videos?action=initializeUpload")
        assertThat(initialize.getHeader("Authorization")).isEqualTo("Bearer linkedin-token")
        assertThat(initialize.getHeader("Linkedin-Version")).isEqualTo("202607")
        assertThat(initialize.getHeader("X-Restli-Protocol-Version")).isEqualTo("2.0.0")
        assertThat(initialize.body.readUtf8()).contains("\"fileSizeBytes\":4")

        val upload = server.takeRequest()
        assertThat(upload.method).isEqualTo("PUT")
        assertThat(upload.path).isEqualTo("/upload/1")
        assertThat(upload.getHeader("Authorization")).isEqualTo("Bearer linkedin-token")
        assertThat(upload.getHeader("Content-Length")).isEqualTo("4")
        assertThat(upload.body.readByteArray()).containsExactly(*"test".toByteArray())

        val finalize = server.takeRequest()
        assertThat(finalize.path).isEqualTo("/rest/videos?action=finalizeUpload")
        assertThat(finalize.body.readUtf8())
            .contains("\"video\":\"urn:li:video:1\"")
            .contains("\"uploadToken\":\"token-1\"")
            .contains("\"uploadedPartIds\":[\"etag-1\"]")

        assertThat(server.takeRequest().path).contains("/rest/videos/")
        assertThat(server.takeRequest().path).isEqualTo("/v2/ugcPosts")
    }

    @Test
    fun `LinkedIn OAuth uses form encoded client credentials`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"access_token":"access-1","refresh_token":"refresh-1","expires_in":3600}"""),
        )
        val config = mockk<LinkedInConfig>()
        every { config.getClientId() } returns "client-id"
        every { config.getClientSecret() } returns "client-secret"

        val client = LinkedInClient(
            linkedInApi = mockk(),
            linkedInVideosApi = mockk(),
            linkedInOAuthApi = oauthProxy(),
            linkedInConfig = config,
        )

        val result = client.exchangeCodeForTokens("code-1", "https://ongo.test/callback")

        assertThat(result.accessToken).isEqualTo("access-1")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/oauth/v2/accessToken")
        assertThat(request.getHeader("Content-Type")).startsWith("application/x-www-form-urlencoded")
        assertThat(request.body.readUtf8())
            .contains("grant_type=authorization_code")
            .contains("client_id=client-id")
            .contains("client_secret=client-secret")
    }

    private fun proxy(): LinkedInApi = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(PlatformRestClientSupport.builder(server.url("").toString().removeSuffix("/")).build()))
        .build()
        .createClient(LinkedInApi::class.java)

    private fun videosProxy(): LinkedInVideosApi = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(PlatformRestClientSupport.builder(server.url("").toString().removeSuffix("/")).build()))
        .build()
        .createClient(LinkedInVideosApi::class.java)

    private fun oauthProxy(): LinkedInOAuthApi = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(PlatformRestClientSupport.builder(server.url("").toString().removeSuffix("/")).build()))
        .build()
        .createClient(LinkedInOAuthApi::class.java)
}
