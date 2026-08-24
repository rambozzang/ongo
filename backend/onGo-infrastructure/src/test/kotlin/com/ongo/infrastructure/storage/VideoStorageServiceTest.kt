package com.ongo.infrastructure.storage

import com.ongo.infrastructure.external.storage.ObjectMetadata
import com.ongo.infrastructure.external.storage.StorageClient
import com.ongo.infrastructure.external.storage.StorageProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class VideoStorageServiceTest {
    private val storageClient = mockk<StorageClient>()
    private val service = VideoStorageService(
        storageClient = storageClient,
        storageProperties = StorageProperties(bucket = "ongo-videos"),
        tusBaseEndpoint = "http://localhost/tus",
    )

    @Test
    fun `legacy stored URL resolves an imported object and refreshes its URL`() {
        every { storageClient.listObjects("videos/20/") } returns emptyList()
        every { storageClient.objectExists("videos/10/imports/source.mp4") } returns true
        every {
            storageClient.generatePresignedDownloadUrl("videos/10/imports/source.mp4", 10080)
        } returns "https://storage.test/refreshed.mp4"

        assertEquals(
            "https://storage.test/refreshed.mp4",
            service.getFileUrl(
                videoId = 20L,
                storedFileUrl = "https://storage.test/ongo-videos/videos/10/imports/source.mp4?signature=redacted",
            ),
        )
    }

    @Test
    fun `copy uses the source object and places it under the occurrence video`() {
        every { storageClient.listObjects("videos/10/") } returns listOf("videos/10/source.mp4")
        every { storageClient.copyObject("videos/10/source.mp4", "videos/20/source.mp4") } returns Unit
        every { storageClient.getFileUrl("videos/20/source.mp4") } returns "https://storage.test/occurrence.mp4"

        assertEquals(
            "https://storage.test/occurrence.mp4",
            service.copyVideoFile(10L, 20L, "https://storage.test/expired.mp4"),
        )

        verify(exactly = 1) { storageClient.copyObject("videos/10/source.mp4", "videos/20/source.mp4") }
    }

    /*
     * 선언 크기는 서명 URL 계약까지 그대로 내려가야 한다. S3/R2 어댑터는 이 값을 서명에 넣어
     * 다른 크기의 PUT 을 스토리지가 직접 거부하게 만든다 — 값이 끊기면 그 방어가 사라진다.
     */
    @Test
    fun `upload url carries the declared content length to the client`() {
        every {
            storageClient.generatePresignedUploadUrl("videos/7/clip.mp4", "video/mp4", 2_048L, 60)
        } returns "https://storage.test/put"

        assertEquals(
            "https://storage.test/put",
            service.generateUploadUrl(7L, "clip.mp4", "video/mp4", 2_048L),
        )
        verify(exactly = 1) {
            storageClient.generatePresignedUploadUrl("videos/7/clip.mp4", "video/mp4", 2_048L, 60)
        }
    }

    @Test
    fun `uploaded size reports the real object length from storage metadata`() {
        every { storageClient.listObjects("videos/7/") } returns listOf("videos/7/clip.mp4")
        every { storageClient.getObjectMetadata("videos/7/clip.mp4") } returns
            ObjectMetadata(contentLength = 4_096L, contentType = "video/mp4", eTag = "e")

        assertEquals(4_096L, service.getUploadedSize(7L))
    }

    @Test
    fun `uploaded size is null when nothing was actually uploaded`() {
        every { storageClient.listObjects("videos/8/") } returns emptyList()

        assertEquals(null, service.getUploadedSize(8L))
    }

    @Test
    fun `uploaded size is null when metadata cannot be read`() {
        every { storageClient.listObjects("videos/9/") } returns listOf("videos/9/clip.mp4")
        every { storageClient.getObjectMetadata("videos/9/clip.mp4") } returns null

        assertEquals(null, service.getUploadedSize(9L))
    }
}
