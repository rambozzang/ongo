package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Visibility
import com.ongo.infrastructure.external.instagram.InstagramApi
import com.ongo.infrastructure.external.instagram.InstagramClient
import com.ongo.infrastructure.external.instagram.InstagramConfig
import com.ongo.infrastructure.external.instagram.InstagramOAuthApi
import com.ongo.infrastructure.external.threads.ThreadsApi
import com.ongo.infrastructure.external.threads.ThreadsClient
import com.ongo.infrastructure.external.threads.ThreadsConfig
import com.ongo.infrastructure.external.threads.ThreadsOAuthApi
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

class InstagramThreadsClientHttpContractTest {
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
    fun `Instagram 업로드는 실제 Graph API 요청에 영상 URL과 캡션을 전달한다`() {
        server.enqueue(json("""{"id":"container-1"}"""))
        server.enqueue(json("""{"id":"container-1","status_code":"FINISHED"}"""))
        server.enqueue(json("""{"id":"media-1"}"""))
        server.enqueue(json("""{"id":"media-1","permalink":"https://instagram.test/reel/media-1"}"""))

        val result = InstagramClient(
            instagramApi = proxy<InstagramApi>(),
            instagramOAuthApi = mockk(),
            instagramConfig = mockk<InstagramConfig>(relaxed = true),
        ).uploadVideo(
            PlatformUploadRequest(
                fileUrl = "https://cdn.test/source.mp4",
                title = "릴스 제목",
                description = "설명",
                tags = listOf("#태그"),
                visibility = Visibility.PUBLIC.name,
                thumbnailUrl = null,
                accessToken = "instagram-token",
                platformChannelId = "ig-user-1",
            ),
        )

        assertThat(result.platformUrl).isEqualTo("https://instagram.test/reel/media-1")
        val create = server.takeRequest()
        assertThat(create.path).startsWith("/ig-user-1/media?")
        assertThat(create.requestUrl?.queryParameter("media_type")).isEqualTo("REELS")
        assertThat(create.requestUrl?.queryParameter("video_url")).isEqualTo("https://cdn.test/source.mp4")
        assertThat(create.requestUrl?.queryParameter("caption")).isEqualTo("릴스 제목\n\n설명\n\n#태그")
        assertThat(create.requestUrl?.queryParameter("share_to_feed")).isEqualTo("true")
        assertThat(create.requestUrl?.queryParameter("access_token")).isEqualTo("instagram-token")

        val status = server.takeRequest()
        assertThat(status.requestUrl?.encodedPath).isEqualTo("/container-1")
        assertThat(status.requestUrl?.queryParameter("fields")).isEqualTo("id,status_code")

        val publish = server.takeRequest()
        assertThat(publish.requestUrl?.encodedPath).isEqualTo("/ig-user-1/media_publish")
        assertThat(publish.requestUrl?.queryParameter("creation_id")).isEqualTo("container-1")
    }

    @Test
    fun `Threads 업로드는 실제 Graph API 요청에 영상 URL과 텍스트를 전달한다`() {
        server.enqueue(json("""{"id":"container-1"}"""))
        server.enqueue(json("""{"id":"container-1","status":"FINISHED"}"""))
        server.enqueue(json("""{"id":"thread-1"}"""))
        server.enqueue(json("""{"id":"thread-1","permalink":"https://threads.test/post/thread-1"}"""))

        val result = ThreadsClient(
            threadsApi = proxy<ThreadsApi>(),
            threadsOAuthApi = mockk(),
            threadsConfig = mockk<ThreadsConfig>(relaxed = true),
        ).uploadVideo(
            PlatformUploadRequest(
                fileUrl = "https://cdn.test/source.mp4",
                title = "Threads 제목",
                description = "설명",
                tags = listOf("#태그"),
                visibility = Visibility.PUBLIC.name,
                thumbnailUrl = null,
                accessToken = "threads-token",
                platformChannelId = "threads-user-1",
            ),
        )

        assertThat(result.platformUrl).isEqualTo("https://threads.test/post/thread-1")
        val create = server.takeRequest()
        assertThat(create.path).startsWith("/threads-user-1/threads?")
        assertThat(create.requestUrl?.queryParameter("media_type")).isEqualTo("VIDEO")
        assertThat(create.requestUrl?.queryParameter("video_url")).isEqualTo("https://cdn.test/source.mp4")
        assertThat(create.requestUrl?.queryParameter("text")).isEqualTo("Threads 제목\n\n설명\n\n#태그")
        assertThat(create.requestUrl?.queryParameter("access_token")).isEqualTo("threads-token")

        val status = server.takeRequest()
        assertThat(status.requestUrl?.encodedPath).isEqualTo("/container-1")
        assertThat(status.requestUrl?.queryParameter("fields")).isEqualTo("id,status,error_message")

        val publish = server.takeRequest()
        assertThat(publish.requestUrl?.encodedPath).isEqualTo("/threads-user-1/threads_publish")
        assertThat(publish.requestUrl?.queryParameter("creation_id")).isEqualTo("container-1")
    }

    @Test
    fun `Instagram story 설정은 실제 Graph API media type으로 전달되고 feed 공유를 보내지 않는다`() {
        server.enqueue(json("""{"id":"container-story"}"""))
        server.enqueue(json("""{"id":"container-story","status_code":"FINISHED"}"""))
        server.enqueue(json("""{"id":"media-story"}"""))
        server.enqueue(json("""{"id":"media-story","permalink":"https://instagram.test/story/media-story"}"""))

        InstagramClient(
            instagramApi = proxy<InstagramApi>(),
            instagramOAuthApi = mockk(),
            instagramConfig = mockk<InstagramConfig>(relaxed = true),
        ).uploadVideo(
            PlatformUploadRequest(
                fileUrl = "https://cdn.test/story.mp4",
                title = "스토리",
                description = "설명",
                tags = emptyList(),
                visibility = Visibility.PUBLIC.name,
                thumbnailUrl = null,
                accessToken = "instagram-token",
                platformChannelId = "ig-user-1",
                customSettingsJson = """{"__type":"instagram","post_type":"story"}""",
            ),
        )

        val create = server.takeRequest()
        assertThat(create.requestUrl?.queryParameter("media_type")).isEqualTo("STORIES")
        assertThat(create.requestUrl?.queryParameter("share_to_feed")).isNull()
    }

    private fun json(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private inline fun <reified T : Any> proxy(): T = HttpServiceProxyFactory
        .builderFor(
            RestClientAdapter.create(
                PlatformRestClientSupport.builder(server.url("").toString().removeSuffix("/")).build(),
            ),
        )
        .build()
        .createClient(T::class.java)
}
