package com.ongo.infrastructure.external.pinterest

import com.fasterxml.jackson.databind.ObjectMapper
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

class PinterestClientHttpContractTest {
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
    fun `Pinterest uploads binary, waits for media readiness, and creates video pin`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"media_id":"media-1","status":"registered","upload_url":"${server.url("/media-upload")}","upload_parameters":{"key":"uploads/video.mp4","policy":"signed-policy"}}""",
                ),
        )
        server.enqueue(MockResponse().setBody("video-bytes"))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"media_id":"media-1","status":"succeeded"}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setResponseCode(201)
                .setBody("""{"id":"pin-1","title":"제목","description":"설명"}"""),
        )

        val config = mockk<PinterestConfig>()
        every { config.getMediaPollAttempts() } returns 1
        every { config.getMediaPollIntervalMillis() } returns 0

        val client = PinterestClient(
            pinterestApi = proxy(),
            pinterestOAuthApi = mockk(),
            pinterestConfig = config,
            fileTransferHelper = PlatformFileTransferHelper(ObjectMapper()),
        )

        val result = client.uploadVideo(
            PlatformUploadRequest(
                fileUrl = server.url("/source/video.mp4").toString(),
                title = "제목",
                description = "설명",
                tags = emptyList(),
                visibility = Visibility.PUBLIC.name,
                thumbnailUrl = server.url("/source/cover.jpg").toString(),
                accessToken = "pinterest-token",
                platformChannelId = "board-1",
                customSettingsJson = """
                    {
                      "__type":"pinterest",
                      "board":"board-9",
                      "title":"설정 핀 제목",
                      "link":"https://ongo.test/video/1",
                      "dominant_color":"#6E7874"
                    }
                """.trimIndent(),
            ),
        )

        assertThat(result.platformVideoId).isEqualTo("pin-1")
        assertThat(result.platformUrl).isEqualTo("https://www.pinterest.com/pin/pin-1/")

        val register = server.takeRequest()
        assertThat(register.method).isEqualTo("POST")
        assertThat(register.path).isEqualTo("/v5/media")
        assertThat(register.getHeader("Authorization")).isEqualTo("Bearer pinterest-token")
        assertThat(register.body.readUtf8()).contains("\"media_type\":\"video\"")

        val source = server.takeRequest()
        assertThat(source.method).isEqualTo("GET")
        assertThat(source.path).isEqualTo("/source/video.mp4")

        val upload = server.takeRequest()
        assertThat(upload.method).isEqualTo("POST")
        assertThat(upload.path).isEqualTo("/media-upload")
        assertThat(upload.getHeader("Authorization")).isNull()
        assertThat(upload.getHeader("Content-Type")).startsWith("multipart/form-data")
        val uploadBody = upload.body.readUtf8()
        assertThat(uploadBody).contains("name=\"key\"")
        assertThat(uploadBody).contains("uploads/video.mp4")

        val status = server.takeRequest()
        assertThat(status.method).isEqualTo("GET")
        assertThat(status.path).isEqualTo("/v5/media/media-1")
        assertThat(status.getHeader("Authorization")).isEqualTo("Bearer pinterest-token")

        val pin = server.takeRequest()
        assertThat(pin.method).isEqualTo("POST")
        assertThat(pin.path).isEqualTo("/v5/pins")
        assertThat(pin.body.readUtf8())
            .contains("\"board_id\":\"board-9\"")
            .contains("\"title\":\"설정 핀 제목\"")
            .contains("\"link\":\"https://ongo.test/video/1\"")
            .contains("\"dominant_color\":\"#6E7874\"")
            .contains("\"source_type\":\"video_id\"")
            .contains("\"media_id\":\"media-1\"")
            .contains("\"cover_image_url\":\"${server.url("/source/cover.jpg")}\"")
    }

    @Test
    fun `Pinterest OAuth exchanges code with basic auth and form body`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"access_token":"pina-token","refresh_token":"pina-refresh","expires_in":3600}"""),
        )

        val config = mockk<PinterestConfig>()
        every { config.getAppId() } returns "app-id"
        every { config.getAppSecret() } returns "app-secret"
        val client = PinterestClient(
            pinterestApi = mockk(),
            pinterestOAuthApi = oauthProxy(),
            pinterestConfig = config,
            fileTransferHelper = mockk(),
        )

        val result = client.exchangeCodeForTokens("auth-code", "https://ongo.test/callback")

        assertThat(result.accessToken).isEqualTo("pina-token")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/v5/oauth/token")
        assertThat(request.getHeader("Authorization")).isEqualTo("Basic YXBwLWlkOmFwcC1zZWNyZXQ=")
        assertThat(request.getHeader("Content-Type")).startsWith("application/x-www-form-urlencoded")
        assertThat(request.body.readUtf8())
            .contains("grant_type=authorization_code")
            .contains("code=auth-code")
            .contains("redirect_uri=https%3A%2F%2Fongo.test%2Fcallback")
            .contains("continuous_refresh=true")
    }

    @Test
    fun `Pinterest channel connection stores an owned board as publish target`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"username":"creator","follower_count":12}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"items":[{"id":"board-7","name":"My videos","url":"https://www.pinterest.com/creator/my-videos/"}]}"""),
        )

        val client = PinterestClient(
            pinterestApi = proxy(),
            pinterestOAuthApi = mockk(),
            pinterestConfig = mockk(),
            fileTransferHelper = mockk(),
        )

        val channel = client.getChannelInfo("pinterest-token")

        assertThat(channel.channelId).isEqualTo("board-7")
        assertThat(channel.channelName).isEqualTo("creator · My videos")
        assertThat(channel.channelUrl).isEqualTo("https://www.pinterest.com/creator/my-videos/")
        assertThat(server.takeRequest().path).isEqualTo("/v5/user_account")
        assertThat(server.takeRequest().path).isEqualTo("/v5/boards?page_size=1")
    }

    private fun proxy(): PinterestApi {
        val restClient = PlatformRestClientSupport
            .builder(server.url("").toString().removeSuffix("/"))
            .build()
        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(PinterestApi::class.java)
    }

    private fun oauthProxy(): PinterestOAuthApi {
        val restClient = PlatformRestClientSupport
            .builder(server.url("").toString().removeSuffix("/"))
            .build()
        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(PinterestOAuthApi::class.java)
    }
}
