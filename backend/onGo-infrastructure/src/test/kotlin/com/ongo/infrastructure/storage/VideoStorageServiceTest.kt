package com.ongo.infrastructure.storage

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
}
