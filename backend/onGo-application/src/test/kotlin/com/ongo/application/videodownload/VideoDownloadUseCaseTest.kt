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
import io.mockk.verifyOrder
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
        // 확정 구간은 실제 구현을 조립한다 — 다운로드부터 저장까지의 기존 계약을 그대로 검증하기 위해서다.
        useCase = VideoDownloadUseCase(
            sourceDownloader,
            ImportedVideoPersister(videoRepository, fileStoragePort, storageQuotaUseCase),
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

    /*
     * 다운로드는 트랜잭션 밖에서 끝나야 한다. 2GB 를 몇 분에 걸쳐 받는 동안 사용자 행 잠금과
     * DB 커넥션을 붙들면 같은 사용자의 다른 요청이 전부 막히고 커넥션 풀이 마른다.
     * 확정(persist)은 다운로드가 끝난 **뒤에** 한 번만 불려야 한다.
     */
    @Test
    fun `downloads outside the transaction and persists only after the download completes`() {
        val persister = mockk<ImportedVideoPersister>()
        val useCaseWithMockPersister = VideoDownloadUseCase(sourceDownloader, persister, objectMapper)
        val path = Files.createTempFile("ongo-video-download-", ".mp4").also { temporaryFiles.add(it) }
        Files.write(path, ByteArray(10))
        every { sourceDownloader.download(any(), any()) } returns DownloadedVideo(
            path = path, title = "제목", originalFilename = "a.mp4", contentType = "video/mp4", size = 10L,
        )
        every { persister.persist(any(), any(), any(), any(), any(), any(), any()) } returns
            Video(id = 3L, userId = 100L, title = "제목", fileUrl = "https://storage/a.mp4")

        val result = useCaseWithMockPersister.importVideo(
            100L, VideoDownloadRequest(url = "https://www.youtube.com/watch?v=abcdefghijk"),
        )

        assertEquals(3L, result.videoId)
        verifyOrder {
            sourceDownloader.download(any(), any())
            persister.persist(any(), any(), any(), any(), any(), any(), any())
        }
        verify(exactly = 1) { persister.persist(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `never reaches the transaction when the download fails`() {
        val persister = mockk<ImportedVideoPersister>()
        val useCaseWithMockPersister = VideoDownloadUseCase(sourceDownloader, persister, objectMapper)
        every { sourceDownloader.download(any(), any()) } throws IllegalStateException("추출기 실패")

        assertFailsWith<com.ongo.common.exception.BusinessException> {
            useCaseWithMockPersister.importVideo(
                100L, VideoDownloadRequest(url = "https://www.youtube.com/watch?v=abcdefghijk"),
            )
        }

        verify(exactly = 0) { persister.persist(any(), any(), any(), any(), any(), any(), any()) }
    }
}
