package com.ongo.application.publicapi

import com.ongo.application.asset.AssetUseCase
import com.ongo.application.asset.dto.AssetResponse
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class PublicApiMediaUseCaseTest {
    private val assets = mockk<AssetUseCase>()
    private val downloader = mockk<PublicRemoteMediaDownloader>()
    private val useCase = PublicApiMediaUseCase(assets, downloader)

    @Test
    fun `URL 미디어는 시그니처 검증 후 에셋으로 저장한다`() {
        val path = Files.createTempFile("public-api-test-", ".mp4")
        Files.write(path, byteArrayOf(0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6f, 0x6d))
        try {
            every { downloader.download("https://cdn.example/video.mp4") } returns PublicDownloadedMedia(
                path, "video.mp4", "video/mp4", Files.size(path),
            )
            every {
                assets.createAsset(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                )
            } returns AssetResponse(
                id = 8,
                filename = "stored.mp4",
                originalFilename = "video.mp4",
                fileUrl = "https://storage/video.mp4",
                fileType = "VIDEO",
                fileSizeBytes = 12,
                mimeType = "video/mp4",
                tags = emptyList(),
                folder = "public-api",
                width = null,
                height = null,
                durationSeconds = null,
                createdAt = LocalDateTime.now(),
            )

            val result = useCase.uploadFromUrl(1, "https://cdn.example/video.mp4", null)

            assertEquals("8", result.id)
            assertEquals("https://storage/video.mp4", result.path)
            assertEquals("video/mp4", result.mimeType)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `URL 미디어의 잘못된 파일 내용은 저장하지 않는다`() {
        val path = Files.createTempFile("public-api-test-", ".mp4")
        Files.write(path, "not a video".encodeToByteArray())
        try {
            every { downloader.download("https://cdn.example/video.mp4") } returns PublicDownloadedMedia(
                path, "video.mp4", "video/mp4", Files.size(path),
            )

            assertFailsWith<RuntimeException> {
                useCase.uploadFromUrl(1, "https://cdn.example/video.mp4", null)
            }
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
