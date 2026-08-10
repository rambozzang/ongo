package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Visibility
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.video.VideoPlatformMeta
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class NaverClipStreamWriterHttpContractTest {
    private lateinit var server: MockWebServer
    private lateinit var writer: NaverClipStreamWriter

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        writer = NaverClipStreamWriter()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Naver Clip fails closed instead of calling a fabricated API`() {
        assertFailsWith<UnsupportedOperationException> {
            writer.initSession(meta(), PlainToken("naver-token"), "channel-1", 4, null)
        }
        assertThat(server.requestCount).isEqualTo(0)
    }

    private fun meta() = VideoPlatformMeta(
        videoUploadId = 1L,
        title = "테스트 영상",
        description = "설명",
        tags = listOf("tag"),
        visibility = Visibility.PUBLIC,
    )
}
