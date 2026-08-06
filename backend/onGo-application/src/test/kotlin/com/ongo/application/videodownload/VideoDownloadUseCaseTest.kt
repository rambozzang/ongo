package com.ongo.application.videodownload

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.application.common.FileStoragePort
import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VideoDownloadUseCaseTest {
    private val videoRepository = mockk<VideoRepository>()
    private val sourceDownloader = mockk<VideoSourceDownloader>()
    private val fileStoragePort = mockk<FileStoragePort>()
    private val storageQuotaUseCase = mockk<StorageQuotaUseCase>()
    private val objectMapper = ObjectMapper()
    private lateinit var useCase: VideoDownloadUseCase
    private val temporaryFiles = mutableListOf<java.nio.file.Path>()

    @BeforeEach
    fun setUp() {
        useCase = VideoDownloadUseCase(
            videoRepository,
            sourceDownloader,
            fileStoragePort,
            storageQuotaUseCase,
            objectMapper,
        )
    }

    @AfterEach
    fun tearDown() {
        temporaryFiles.forEach { Files.deleteIfExists(it) }
        temporaryFiles.clear()
    }

    @Test
    fun `imports downloaded video into storage and creates URL_IMPORT video`() {
        val path = temporaryFile("video-bytes")
        every { sourceDownloader.download("https://youtu.be/abc", any()) } returns
            DownloadedVideo(path, "원본 제목", "original.mp4", "video/mp4", 11)
        every { storageQuotaUseCase.checkQuota(7L, 11) } just runs
        every { fileStoragePort.uploadByKey(any(), any(), "video/mp4", 11) } returns
            "https://storage.example/video.mp4"
        val saved = slot<Video>()
        every { videoRepository.save(capture(saved)) } answers { saved.captured.copy(id = 42L) }

        val result = useCase.importVideo(7L, VideoDownloadRequest("https://youtu.be/abc"))

        assertEquals(42L, result.videoId)
        assertEquals("원본 제목", result.title)
        assertEquals(UploadStatus.DRAFT, saved.captured.status)
        assertEquals("URL_IMPORT", saved.captured.source.name)
        assertEquals("YOUTUBE", saved.captured.sourceReference?.get("provider")?.asText())
        assertEquals("https://youtu.be/abc", saved.captured.sourceReference?.get("url")?.asText())
        verify { fileStoragePort.uploadByKey(any(), any(), "video/mp4", 11) }
    }

    @Test
    fun `uses explicit title and deletes uploaded object when database save fails`() {
        val path = temporaryFile("video-bytes")
        every { sourceDownloader.download(any(), any()) } returns
            DownloadedVideo(path, "원본", "original.webm", "video/webm", 11)
        every { storageQuotaUseCase.checkQuota(7L, 11) } just runs
        every { fileStoragePort.uploadByKey(any(), any(), "video/webm", 11) } returns "storage-url"
        every { videoRepository.save(any()) } throws IllegalStateException("db unavailable")
        every { fileStoragePort.deleteByKey(any()) } just runs

        assertFailsWith<IllegalStateException> {
            useCase.importVideo(7L, VideoDownloadRequest("https://www.instagram.com/reel/abc", "내 제목"))
        }

        verify(exactly = 1) { fileStoragePort.deleteByKey(any()) }
    }

    @Test
    fun `does not upload when downloaded content is not a video`() {
        val path = temporaryFile("not-video")
        every { sourceDownloader.download(any(), any()) } returns
            DownloadedVideo(path, "문서", "file.txt", "text/plain", 11)

        assertFailsWith<com.ongo.common.exception.BusinessException> {
            useCase.importVideo(7L, VideoDownloadRequest("https://www.tiktok.com/@a/video/1"))
        }
        verify(exactly = 0) { fileStoragePort.uploadByKey(any(), any(), any(), any()) }
        verify(exactly = 0) { videoRepository.save(any()) }
    }

    @Test
    fun `rejects a size mismatch before storage upload`() {
        val path = temporaryFile("actual-bytes")
        every { sourceDownloader.download(any(), any()) } returns
            DownloadedVideo(path, "영상", "video.mp4", "video/mp4", 99)

        assertFailsWith<com.ongo.common.exception.BusinessException> {
            useCase.importVideo(7L, VideoDownloadRequest("https://youtu.be/abc"))
        }
        verify(exactly = 0) { fileStoragePort.uploadByKey(any(), any(), any(), any()) }
    }

    private fun temporaryFile(content: String): java.nio.file.Path {
        val path = Files.createTempFile("video-download-test-", ".bin")
        Files.writeString(path, content)
        temporaryFiles.add(path)
        return path
    }
}
