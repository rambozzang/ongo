package com.ongo.application.videodownload

import com.ongo.common.exception.BusinessException
import com.ongo.domain.videodownload.VideoDownloadProvider
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class VideoDownloadUrlTest {
    @Test
    fun `classifies supported social hosts`() {
        assertEquals(VideoDownloadProvider.YOUTUBE, VideoDownloadUrl.parse("https://youtu.be/abc").provider)
        assertEquals(VideoDownloadProvider.TIKTOK, VideoDownloadUrl.parse("https://www.tiktok.com/@creator/video/1").provider)
        assertEquals(VideoDownloadProvider.INSTAGRAM, VideoDownloadUrl.parse("https://www.instagram.com/reel/abc/").provider)
    }

    @Test
    fun `removes fragment from canonical extractor URL`() {
        val parsed = VideoDownloadUrl.parse("https://www.youtube.com/watch?v=abc#t=10")
        assertEquals("https://www.youtube.com/watch?v=abc", parsed.canonical)
    }

    @Test
    fun `rejects non HTTPS and non social URLs`() {
        assertFailsWith<BusinessException> { VideoDownloadUrl.parse("http://youtu.be/abc") }
        assertFailsWith<BusinessException> { VideoDownloadUrl.parse("https://example.com/video.mp4") }
        assertFailsWith<BusinessException> { VideoDownloadUrl.parse("https://youtube.com.evil.example/abc") }
    }

    @Test
    fun `rejects credentials and non standard ports`() {
        assertFailsWith<BusinessException> { VideoDownloadUrl.parse("https://user:pass@youtu.be/abc") }
        assertFailsWith<BusinessException> { VideoDownloadUrl.parse("https://youtu.be:8443/abc") }
    }
}
