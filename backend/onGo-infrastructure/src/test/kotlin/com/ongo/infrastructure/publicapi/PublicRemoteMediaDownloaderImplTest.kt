package com.ongo.infrastructure.publicapi

import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class PublicRemoteMediaDownloaderImplTest {
    private val downloader = PublicRemoteMediaDownloaderImpl()

    @Test
    fun `loopback URL은 HTTP 요청 전에 차단한다`() {
        assertFailsWith<IllegalArgumentException> {
            downloader.download("http://127.0.0.1/video.mp4")
        }
    }

    @Test
    fun `URL userinfo와 임의 포트는 차단한다`() {
        assertFailsWith<IllegalArgumentException> {
            downloader.download("https://user:password@example.com:8443/video.mp4")
        }
    }
}
