package com.ongo.infrastructure.external.platform

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * The upload helper is the last boundary before a platform receives bytes.
 * Keep the wire contract here so a refactor cannot silently drop auth, ranges,
 * metadata, or the platform id returned by the provider.
 */
class PlatformFileTransferHelperContractTest {
    private lateinit var server: MockWebServer
    private lateinit var helper: PlatformFileTransferHelper

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        helper = PlatformFileTransferHelper(ObjectMapper())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `YouTube resumable session sends authenticated metadata and file size`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Location", server.url("/session/yt-1").toString()),
        )

        val session = helper.initiateYouTubeResumableUpload(
            uploadBaseUrl = server.url("/api").toString().removeSuffix("/"),
            metadata = mapOf("snippet" to mapOf("title" to "테스트")),
            accessToken = "secret-token",
            fileSize = 1234,
        )

        val request = server.takeRequest()
        assertThat(session).isEqualTo(server.url("/session/yt-1").toString())
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).contains("/api/upload/youtube/v3/videos?uploadType=resumable")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer secret-token")
        assertThat(request.getHeader("X-Upload-Content-Length")).isEqualTo("1234")
        assertThat(request.body.readUtf8()).contains("\"title\":\"테스트\"")
    }

    @Test
    fun `YouTube session uploads the exact file and extracts provider id`() {
        server.enqueue(MockResponse().setBody("""{"id":"yt-1"}"""))
        val file = Files.createTempFile("ongo-transfer-", ".mp4").toFile()
        file.writeBytes(byteArrayOf(1, 2, 3, 4))

        try {
            val id = helper.uploadToYouTubeSession(server.url("/session/yt-1").toString(), file)
            val request = server.takeRequest()

            assertThat(id).isEqualTo("yt-1")
            assertThat(request.method).isEqualTo("PUT")
            assertThat(request.getHeader("Content-Type")).isEqualTo("video/*")
            assertThat(request.body.readByteArray()).containsExactly(1, 2, 3, 4)
        } finally {
            Files.deleteIfExists(file.toPath())
        }
    }

    @Test
    fun `TikTok sends contiguous Content-Range chunks`() {
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))
        val file = Files.createTempFile("ongo-transfer-", ".mp4").toFile()
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        try {
            helper.uploadChunkedToTikTok(server.url("/upload/tiktok").toString(), file, chunkSize = 3)

            val first = server.takeRequest()
            val second = server.takeRequest()
            assertThat(first.getHeader("Content-Range")).isEqualTo("bytes 0-2/5")
            assertThat(second.getHeader("Content-Range")).isEqualTo("bytes 3-4/5")
            assertThat(first.body.readByteArray()).containsExactly(1, 2, 3)
            assertThat(second.body.readByteArray()).containsExactly(4, 5)
        } finally {
            Files.deleteIfExists(file.toPath())
        }
    }
}
