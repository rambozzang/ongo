package com.ongo.infrastructure.external.wordpress

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

class WordPressClientHttpContractTest {
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
    fun `WordPress uploads source URL as form media and creates video post`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"ID":12,"URL":"https://cdn.wp.test/video.mp4","mime_type":"video/mp4"}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"ID":99,"URL":"https://creator.wordpress.com/2026/08/09/title/","status":"publish"}"""),
        )

        val config = mockk<WordPressConfig>()
        every { config.getClientId() } returns "client-id"
        every { config.getClientSecret() } returns "client-secret"
        val client = WordPressClient(
            wordPressApi = proxy(),
            wordPressOAuthApi = mockk(),
            wordPressConfig = config,
        )

        val result = client.uploadVideo(
            PlatformUploadRequest(
                fileUrl = "https://cdn.test/video.mp4",
                title = "제목",
                description = "설명",
                tags = listOf("tag"),
                visibility = Visibility.PUBLIC.name,
                thumbnailUrl = null,
                accessToken = "wp-token",
                platformChannelId = "site-1",
            ),
        )

        assertThat(result.platformVideoId).isEqualTo("99")
        assertThat(result.platformUrl).isEqualTo("https://creator.wordpress.com/2026/08/09/title/")

        val media = server.takeRequest()
        assertThat(media.path).isEqualTo("/rest/v1.1/sites/site-1/media/new")
        assertThat(media.getHeader("Authorization")).isEqualTo("Bearer wp-token")
        assertThat(media.getHeader("Content-Type")).startsWith("application/x-www-form-urlencoded")
        assertThat(media.body.readUtf8()).contains("media_urls%5B%5D=https%3A%2F%2Fcdn.test%2Fvideo.mp4")

        val post = server.takeRequest()
        assertThat(post.path).isEqualTo("/rest/v1.1/sites/site-1/posts/new")
        assertThat(post.body.readUtf8())
            .contains("https://cdn.wp.test/video.mp4")
            .contains("\"title\":\"제목\"")
            .contains("\"format\":\"video\"")
    }

    @Test
    fun `WordPress uploads Postiz main image and sends featured image id`() {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"ID":12,"URL":"https://cdn.wp.test/video.mp4"}"""),
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"ID":13,"URL":"https://cdn.wp.test/cover.jpg"}"""),
        )
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"ID":99,"URL":"https://creator.wordpress.com/post/99/","status":"publish"}"""),
        )

        val config = mockk<WordPressConfig>()
        every { config.getClientId() } returns "client-id"
        every { config.getClientSecret() } returns "client-secret"
        val client = WordPressClient(proxy(), mockk(), config)

        client.uploadVideo(
            PlatformUploadRequest(
                fileUrl = "https://cdn.test/video.mp4",
                title = "제목",
                description = "설명",
                tags = emptyList(),
                visibility = Visibility.PUBLIC.name,
                thumbnailUrl = null,
                accessToken = "wp-token",
                platformChannelId = "site-1",
                customSettingsJson = """{"__type":"wordpress","title":"제목","type":"post","main_image":{"id":"cover-1","path":"https://cdn.test/cover.jpg"}}""",
            ),
        )

        server.takeRequest()
        val cover = server.takeRequest()
        assertThat(cover.body.readUtf8()).contains("media_urls%5B%5D=https%3A%2F%2Fcdn.test%2Fcover.jpg")
        val post = server.takeRequest()
        assertThat(post.body.readUtf8()).contains("\"featured_image\":13")
    }

    @Test
    fun `WordPress OAuth uses official oauth2 token endpoint and form body`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"access_token":"wp-token","token_type":"bearer","blog_id":"1"}"""),
        )
        val config = mockk<WordPressConfig>()
        every { config.getClientId() } returns "client-id"
        every { config.getClientSecret() } returns "client-secret"
        val client = WordPressClient(
            wordPressApi = mockk(),
            wordPressOAuthApi = oauthProxy(),
            wordPressConfig = config,
        )

        val result = client.exchangeCodeForTokens("code-1", "https://ongo.test/callback")

        assertThat(result.accessToken).isEqualTo("wp-token")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/oauth2/token")
        assertThat(request.body.readUtf8())
            .contains("grant_type=authorization_code")
            .contains("client_id=client-id")
            .contains("client_secret=client-secret")
    }

    private fun proxy(): WordPressApi = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(PlatformRestClientSupport.builder(server.url("").toString().removeSuffix("/")).build()))
        .build()
        .createClient(WordPressApi::class.java)

    private fun oauthProxy(): WordPressOAuthApi = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(PlatformRestClientSupport.builder(server.url("").toString().removeSuffix("/")).build()))
        .build()
        .createClient(WordPressOAuthApi::class.java)
}
