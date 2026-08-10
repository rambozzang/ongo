package com.ongo.infrastructure.external.platform

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ongo.common.enums.Visibility
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.infrastructure.external.youtube.YouTubeConfig
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * YouTube resumable upload의 세션 생성과 실제 파일 전송 경계를 고정한다.
 * 단순 helper 테스트만으로는 writer가 예약 메타데이터를 올바르게 조립하는지 보장할 수 없다.
 */
class YouTubeStreamWriterHttpContractTest {
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
    fun `YouTube sends resumable metadata and file with scheduled publish time`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Location", server.url("/upload/session-1").toString()),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"id":"youtube-video-1"}"""),
        )

        val config = mockk<YouTubeConfig>()
        every { config.getUploadBaseUrl() } returns server.url("/").toString().removeSuffix("/")
        val writer = YouTubeStreamWriter(config, PlatformFileTransferHelper(jacksonObjectMapper()))
        val scheduledAt = LocalDateTime.of(2026, 8, 10, 12, 30)

        writer.initSession(meta(), PlainToken("youtube-token"), null, 4, scheduledAt)
        writer.writeChunk("test".toByteArray(), 0, 4)
        val result = writer.complete()

        assertThat(result.published).isFalse()
        assertThat(result.pollToken).isEqualTo("youtube-video-1")
        assertThat(result.platformVideoId).isEqualTo("youtube-video-1")
        assertThat(result.platformUrl).isEqualTo("https://www.youtube.com/watch?v=youtube-video-1")

        val metadata = server.takeRequest()
        assertThat(metadata.method).isEqualTo("POST")
        assertThat(metadata.path).contains("/upload/youtube/v3/videos")
        assertThat(metadata.path).contains("uploadType=resumable")
        assertThat(metadata.getHeader("Authorization")).isEqualTo("Bearer youtube-token")
        assertThat(metadata.getHeader("X-Upload-Content-Type")).isEqualTo("video/*")
        assertThat(metadata.getHeader("X-Upload-Content-Length")).isEqualTo("4")
        assertThat(metadata.body.readUtf8())
            .contains("테스트 제목")
            .contains("\"privacyStatus\":\"private\"")
            .contains("\"selfDeclaredMadeForKids\":true")
            .contains("2026-08-10T03:30:00Z")

        val upload = server.takeRequest()
        assertThat(upload.method).isEqualTo("PUT")
        assertThat(upload.path).isEqualTo("/upload/session-1")
        assertThat(upload.getHeader("Content-Type")).isEqualTo("video/*")
        assertThat(upload.getHeader("Content-Length")).isEqualTo("4")
        assertThat(upload.body.readByteArray()).containsExactly(*"test".toByteArray())
    }

    @Test
    fun `YouTube sends configured thumbnail to thumbnails set endpoint`() {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Location", server.url("/upload/session-2").toString()))
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"id":"youtube-video-2"}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("thumbnail-bytes"))
        server.enqueue(MockResponse().setResponseCode(200))

        val config = mockk<YouTubeConfig>()
        every { config.getUploadBaseUrl() } returns server.url("/").toString().removeSuffix("/")
        val writer = YouTubeStreamWriter( config, PlatformFileTransferHelper(jacksonObjectMapper()))

        writer.initSession(
            meta().copy(customThumbnailUrl = server.url("/cover.jpg").toString()),
            PlainToken("youtube-token"),
            null,
            4,
            null,
        )
        writer.writeChunk("test".toByteArray(), 0, 4)
        writer.complete()

        server.takeRequest()
        server.takeRequest()
        val download = server.takeRequest()
        assertThat(download.method).isEqualTo("GET")
        assertThat(download.path).isEqualTo("/cover.jpg")
        val thumbnail = server.takeRequest()
        assertThat(thumbnail.method).isEqualTo("POST")
        assertThat(thumbnail.path).isEqualTo("/youtube/v3/thumbnails/set?videoId=youtube-video-2")
        assertThat(thumbnail.getHeader("Authorization")).isEqualTo("Bearer youtube-token")
        assertThat(thumbnail.getHeader("Content-Type")).startsWith("multipart/form-data")
        assertThat(thumbnail.body.readUtf8()).contains("thumbnail-bytes")
    }

    private fun meta() = VideoPlatformMeta(
        videoUploadId = 1L,
        title = "테스트 제목",
        description = "테스트 설명",
        tags = listOf("tag"),
        visibility = Visibility.PUBLIC,
        customSettingsJson = """{"selfDeclaredMadeForKids":"yes"}""",
    )
}
