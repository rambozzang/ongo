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
import java.time.LocalDateTime

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

    @Test
    fun `Instagram과 Threads writer는 dispatcher가 전달한 예약 메타데이터를 거부하지 않는다`() {
        val instagramClient = mockk<InstagramClient>()
        val threadsClient = mockk<ThreadsClient>()
        val storageClient = mockk<StorageClient>()
        every { storageClient.uploadFile(any(), any(), any(), any()) } returnsMany listOf(
            "https://storage.test/instagram-scheduled.mp4",
            "https://storage.test/threads-scheduled.mp4",
        )
        justRun { storageClient.deleteFile(any()) }
        every { instagramClient.uploadVideo(any()) } returns PlatformUploadResult(
            platformVideoId = "ig-scheduled",
            platformUrl = "https://instagram.com/reel/ig-scheduled",
            status = "PUBLISHED",
        )
        every { threadsClient.uploadVideo(any()) } returns PlatformUploadResult(
            platformVideoId = "threads-scheduled",
            platformUrl = "https://threads.net/post/threads-scheduled",
            status = "PUBLISHED",
        )

        val scheduledAt = LocalDateTime.now().minusMinutes(1)
        val instagram = InstagramStreamWriter(instagramClient, storageClient)
        instagram.initSession(meta(), PlainToken("ig-token"), "ig-user", 4, scheduledAt)
        instagram.writeChunk("test".toByteArray(), 0, 4)
        val instagramResult = instagram.complete()

        val threads = ThreadsStreamWriter(threadsClient, storageClient)
        threads.initSession(meta(), PlainToken("threads-token"), "threads-user", 4, scheduledAt)
        threads.writeChunk("test".toByteArray(), 0, 4)
        val threadsResult = threads.complete()

        assertThat(instagramResult.published).isTrue()
        assertThat(threadsResult.published).isTrue()
    }

    private fun meta() = VideoPlatformMeta(
        videoUploadId = 1L,
        title = "테스트 영상",
        description = "설명",
        tags = listOf("tag"),
        visibility = Visibility.PUBLIC,
    )
}
