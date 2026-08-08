package com.ongo.infrastructure.external.platform

import com.ongo.common.enums.Visibility
import com.ongo.domain.channel.PlainToken
import com.ongo.domain.video.VideoPlatformMeta
import com.ongo.infrastructure.external.instagram.InstagramClient
import com.ongo.infrastructure.external.storage.StorageClient
import com.ongo.infrastructure.external.threads.ThreadsClient
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InstagramThreadsStreamWriterTest {

    @Test
    fun `Instagram writer는 임시 object URL로 Graph 업로드 후 성공 링크를 반환한다`() {
        val instagramClient = mockk<InstagramClient>()
        val storageClient = mockk<StorageClient>()
        val request = slot<PlatformUploadRequest>()
        every { storageClient.uploadFile(any(), any(), any(), any()) } returns "https://storage.test/instagram.mp4"
        justRun { storageClient.deleteFile(any()) }
        every { instagramClient.uploadVideo(capture(request)) } returns PlatformUploadResult(
            platformVideoId = "ig-1",
            platformUrl = "https://instagram.com/reel/ig-1",
            status = "PUBLISHED",
        )

        val writer = InstagramStreamWriter(instagramClient, storageClient)
        writer.initSession(meta(), PlainToken("plain-token"), "ig-user", 4, null)
        writer.writeChunk("test".toByteArray(), 0, 4)

        val result = writer.complete()

        assertThat(result.published).isTrue()
        assertThat(result.platformVideoId).isEqualTo("ig-1")
        assertThat(result.platformUrl).isEqualTo("https://instagram.com/reel/ig-1")
        assertThat(request.captured.accessToken).isEqualTo("plain-token")
        verify(exactly = 1) { storageClient.deleteFile(any()) }
    }

    @Test
    fun `Threads writer는 임시 object URL로 Graph 업로드 후 성공 링크를 반환한다`() {
        val threadsClient = mockk<ThreadsClient>()
        val storageClient = mockk<StorageClient>()
        val request = slot<PlatformUploadRequest>()
        every { storageClient.uploadFile(any(), any(), any(), any()) } returns "https://storage.test/threads.mp4"
        justRun { storageClient.deleteFile(any()) }
        every { threadsClient.uploadVideo(capture(request)) } returns PlatformUploadResult(
            platformVideoId = "thread-1",
            platformUrl = "https://threads.net/post/thread-1",
            status = "published",
        )

        val writer = ThreadsStreamWriter(threadsClient, storageClient)
        writer.initSession(meta(), PlainToken("plain-token"), "threads-user", 4, null)
        writer.writeChunk("test".toByteArray(), 0, 4)

        val result = writer.complete()

        assertThat(result.published).isTrue()
        assertThat(result.platformVideoId).isEqualTo("thread-1")
        assertThat(result.platformUrl).isEqualTo("https://threads.net/post/thread-1")
        assertThat(request.captured.accessToken).isEqualTo("plain-token")
        verify(exactly = 1) { storageClient.deleteFile(any()) }
    }

    private fun meta() = VideoPlatformMeta(
        videoUploadId = 1L,
        title = "테스트 영상",
        description = "설명",
        tags = listOf("tag"),
        visibility = Visibility.PUBLIC,
    )
}
