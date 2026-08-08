package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Visibility
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.infrastructure.external.naverclip.NaverClipApi
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

class NaverClipStreamWriterHttpContractTest {
    private lateinit var server: MockWebServer
    private lateinit var writer: NaverClipStreamWriter

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = proxy<NaverClipApi>()
        writer = NaverClipStreamWriter(api, PlatformFileTransferHelper(com.fasterxml.jackson.databind.ObjectMapper()))
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Naver Clip sends init, binary upload, and complete contracts`() {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"upload_id":"upload-1","upload_url":"${server.url("/upload/1")}"}"""))
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"clip_id":"clip-1","clip_url":"https://naver.test/clip/clip-1","status":"PUBLISHED"}"""))

        writer.initSession(meta(), PlainToken("naver-token"), "channel-1", 4, null)
        writer.writeChunk("test".toByteArray(), 0, 4)
        val result = writer.complete()

        assertThat(result.published).isTrue()
        assertThat(result.platformUrl).isEqualTo("https://naver.test/clip/clip-1")

        val init = server.takeRequest()
        assertThat(init.method).isEqualTo("POST")
        assertThat(init.path).isEqualTo("/api/v1/clips/upload/init")
        assertThat(init.getHeader("Authorization")).isEqualTo("Bearer naver-token")
        assertThat(init.body.readUtf8()).contains("\"title\":\"테스트 영상\"")

        val upload = server.takeRequest()
        assertThat(upload.method).isEqualTo("PUT")
        assertThat(upload.path).isEqualTo("/upload/1")
        assertThat(upload.getHeader("Authorization")).isEqualTo("Bearer naver-token")
        assertThat(upload.body.readByteArray()).containsExactly(*"test".toByteArray())

        val complete = server.takeRequest()
        assertThat(complete.method).isEqualTo("POST")
        assertThat(complete.path).isEqualTo("/api/v1/clips/upload/complete")
        assertThat(complete.body.readUtf8()).contains("\"upload_id\":\"upload-1\"")
    }

    private inline fun <reified T : Any> proxy(): T {
        val restClient = PlatformRestClientSupport
            .builder(server.url("/api").toString().removeSuffix("/"))
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
