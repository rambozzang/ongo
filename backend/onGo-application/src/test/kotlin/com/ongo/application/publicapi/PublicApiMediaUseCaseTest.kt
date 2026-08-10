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

    @Test
    fun `Postiz 문서 업로드는 허용된 시그니처를 확인한 뒤 파일 에셋으로 저장한다`() {
        val path = Files.createTempFile("public-api-test-", ".pdf")
        Files.write(path, "%PDF-1.7\n".encodeToByteArray())
        try {
            every { downloader.download("https://cdn.example/guide.pdf") } returns PublicDownloadedMedia(
                path, "guide.pdf", "APPLICATION/PDF; charset=binary", Files.size(path),
            )
            every {
                assets.createAsset(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                )
            } returns AssetResponse(
                id = 9,
                filename = "stored.pdf",
                originalFilename = "guide.pdf",
                fileUrl = "https://storage/guide.pdf",
                fileType = "FILE",
                fileSizeBytes = Files.size(path),
                mimeType = "application/pdf",
                tags = emptyList(),
                folder = "public-api",
                width = null,
                height = null,
                durationSeconds = null,
                createdAt = LocalDateTime.now(),
            )

            val result = useCase.uploadFromUrl(1, "https://cdn.example/guide.pdf", null)

            assertEquals("9", result.id)
            assertEquals("application/pdf", result.mimeType)
            assertEquals("guide.pdf", result.name)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `Postiz DOCX 업로드는 ZIP 컨테이너 시그니처를 확인한다`() {
        val path = Files.createTempFile("public-api-test-", ".docx")
        Files.write(path, byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00))
        try {
            every { downloader.download("https://cdn.example/guide.docx") } returns PublicDownloadedMedia(
                path,
                "guide.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                Files.size(path),
            )
            every {
                assets.createAsset(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                )
            } returns AssetResponse(
                id = 10,
                filename = "stored.docx",
                originalFilename = "guide.docx",
                fileUrl = "https://storage/guide.docx",
                fileType = "FILE",
                fileSizeBytes = Files.size(path),
                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                tags = emptyList(),
                folder = "public-api",
                width = null,
                height = null,
                durationSeconds = null,
                createdAt = LocalDateTime.now(),
            )

            val result = useCase.uploadFromUrl(1, "https://cdn.example/guide.docx", null)

            assertEquals("10", result.id)
            assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", result.mimeType)
            assertEquals("guide.docx", result.name)
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
