package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Visibility
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.infrastructure.external.tiktok.TikTokApi
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

class TikTokStreamWriterHttpContractTest {
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
    fun `TikTok sends creator validation init bytes and publish status`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"data":{"privacy_level_options":["PUBLIC_TO_EVERYONE"]},"error":null}"""))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"data":{"publish_id":"publish-1","upload_url":"${server.url("/upload/tiktok")}"},"error":null}"""))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"data":{"status":"PUBLISH_COMPLETE","publicaly_available_post_id":["video-1"]},"error":null}"""))

        val writer = TikTokStreamWriter(
            proxy(),
            PlatformFileTransferHelper(ObjectMapper()),
            statusPollIntervalMs = 0,
            statusPollMaxAttempts = 1,
        )
        writer.initSession(meta(), PlainToken("tiktok-token"), "creator-1", 4, null)
        writer.writeChunk("test".toByteArray(), 0, 4)
        val result = writer.complete()

        assertThat(result.published).isTrue()
        assertThat(result.platformVideoId).isEqualTo("video-1")
        assertThat(result.platformUrl).isEqualTo("https://www.tiktok.com/@creator-1/video/video-1")

        val creator = server.takeRequest()
        assertThat(creator.path).isEqualTo("/api/v2/post/publish/creator_info/query/")
        assertThat(creator.getHeader("Authorization")).isEqualTo("Bearer tiktok-token")

        val init = server.takeRequest()
        assertThat(init.path).isEqualTo("/api/v2/post/publish/video/init/")
        assertThat(init.getHeader("Authorization")).isEqualTo("Bearer tiktok-token")
        assertThat(init.body.readUtf8())
            .contains("\"title\":\"테스트 영상\\n\\n설명\\n\\n#tag\"")
            .contains("\"privacy_level\":\"PUBLIC_TO_EVERYONE\"")

        val upload = server.takeRequest()
        assertThat(upload.method).isEqualTo("PUT")
        assertThat(upload.path).isEqualTo("/upload/tiktok")
        assertThat(upload.getHeader("Content-Range")).isEqualTo("bytes 0-3/4")
        assertThat(upload.body.readByteArray()).containsExactly(*"test".toByteArray())

        val status = server.takeRequest()
        assertThat(status.path).isEqualTo("/api/v2/post/publish/status/fetch/")
        assertThat(status.getHeader("Authorization")).isEqualTo("Bearer tiktok-token")
        assertThat(status.body.readUtf8()).contains("\"publish_id\":\"publish-1\"")
    }

    private fun proxy(): TikTokApi {
        val restClient = PlatformRestClientSupport
            .builder(server.url("/api").toString().removeSuffix("/"))
            .build()
        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(TikTokApi::class.java)
    }

    private fun meta() = VideoPlatformMeta(
        videoUploadId = 1L,
        title = "테스트 영상",
        description = "설명",
        tags = listOf("tag"),
        visibility = Visibility.PUBLIC,
    )
}
