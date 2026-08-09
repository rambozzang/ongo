package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Visibility
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.infrastructure.external.instagram.InstagramApi
import com.ongo.infrastructure.external.instagram.InstagramClient
import com.ongo.infrastructure.external.instagram.InstagramConfig
import com.ongo.infrastructure.external.storage.StorageClient
import com.ongo.infrastructure.external.threads.ThreadsApi
import com.ongo.infrastructure.external.threads.ThreadsClient
import com.ongo.infrastructure.external.threads.ThreadsConfig
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

/**
 * MockK만으로는 Graph API의 실제 HTTP 계약(path/query/auth)을 검증할 수 없다.
 * 이 테스트는 writer -> client -> RestClient 경계를 실제 HTTP 서버로 고정한다.
 */
class InstagramThreadsStreamWriterHttpContractTest {
    private lateinit var server: MockWebServer
    private val storageClient = mockk<StorageClient>()
    private val json = "application/json"

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        every { storageClient.uploadFile(any(), any(), any(), any()) } returns "https://cdn.test/video.mp4"
        justRun { storageClient.deleteFile(any()) }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Instagram writer sends the complete Graph Reels flow`() {
        server.enqueue(MockResponse().setHeader("Content-Type", json).setBody("""{"id":"container-1"}"""))
        server.enqueue(MockResponse().setHeader("Content-Type", json).setBody("""{"id":"container-1","status_code":"FINISHED"}"""))
        server.enqueue(MockResponse().setHeader("Content-Type", json).setBody("""{"id":"media-1"}"""))
        server.enqueue(MockResponse().setHeader("Content-Type", json).setBody("""{"id":"media-1","permalink":"https://instagram.test/reel/media-1"}"""))

        val api = proxy<InstagramApi>()
        val client = InstagramClient(api, mockk(), mockk<InstagramConfig>(relaxed = true))
        val writer = InstagramStreamWriter(client, storageClient)

        writer.initSession(meta(), PlainToken("ig-token"), "ig-user-1", 4, null)
        writer.writeChunk("test".toByteArray(), 0, 4)
        val result = writer.complete()

        assertThat(result.published).isTrue()
        assertThat(result.platformUrl).isEqualTo("https://instagram.test/reel/media-1")
        val create = server.takeRequest()
        assertThat(create.method).isEqualTo("POST")
        assertThat(create.path).contains("/ig-user-1/media")
        assertThat(create.path).contains("media_type=REELS")
        assertThat(create.path).contains("access_token=ig-token")
        assertThat(create.requestUrl?.queryParameter("caption"))
            .contains("테스트 영상")
            .contains("설명")
            .contains("#tag")
        assertThat(server.takeRequest().path).contains("/container-1")
        assertThat(server.takeRequest().path).contains("creation_id=container-1")
        assertThat(server.takeRequest().path).contains("/media-1")
    }

    @Test
    fun `Threads writer sends the complete Graph video flow`() {
        server.enqueue(MockResponse().setHeader("Content-Type", json).setBody("""{"id":"container-2"}"""))
        server.enqueue(MockResponse().setHeader("Content-Type", json).setBody("""{"id":"container-2","status":"FINISHED"}"""))
        server.enqueue(MockResponse().setHeader("Content-Type", json).setBody("""{"id":"thread-2"}"""))
        server.enqueue(MockResponse().setHeader("Content-Type", json).setBody("""{"id":"thread-2","permalink":"https://threads.test/post/thread-2"}"""))

        val api = proxy<ThreadsApi>()
        val client = ThreadsClient(api, mockk(), mockk<ThreadsConfig>(relaxed = true))
        val writer = ThreadsStreamWriter(client, storageClient)

        writer.initSession(meta(), PlainToken("threads-token"), "threads-user-1", 4, null)
        writer.writeChunk("test".toByteArray(), 0, 4)
        val result = writer.complete()

        assertThat(result.published).isTrue()
        assertThat(result.platformUrl).isEqualTo("https://threads.test/post/thread-2")
        val create = server.takeRequest()
        assertThat(create.method).isEqualTo("POST")
        assertThat(create.path).contains("/threads-user-1/threads")
        assertThat(create.path).contains("media_type=VIDEO")
        assertThat(create.path).contains("access_token=threads-token")
        assertThat(create.requestUrl?.queryParameter("text"))
            .contains("테스트 영상")
            .contains("설명")
            .contains("#tag")
        assertThat(server.takeRequest().path).contains("/container-2")
        assertThat(server.takeRequest().path).contains("creation_id=container-2")
        assertThat(server.takeRequest().path).contains("/thread-2")
    }

    private inline fun <reified T : Any> proxy(): T {
        val restClient = PlatformRestClientSupport
            .builder(server.url("/v1").toString().removeSuffix("/"))
            .build()
        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(T::class.java)
    }

    private fun meta() = VideoPlatformMeta(
        videoUploadId = 1L,
        title = "테스트 영상",
        description = "설명",
        tags = listOf("tag"),
        visibility = Visibility.PUBLIC,
    )
}
