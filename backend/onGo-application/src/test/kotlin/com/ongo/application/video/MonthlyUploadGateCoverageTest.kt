package com.ongo.application.video

import com.fasterxml.jackson.databind.ObjectMapper

import com.ongo.application.csv.CsvImportUseCase
import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.application.videodownload.ImportedVideoPersister
import com.ongo.application.videodownload.VideoDownloadRequest
import com.ongo.application.videodownload.VideoDownloadUseCase
import com.ongo.application.videodownload.VideoSourceDownloader
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * **셈에 들어가는 행을 만드는 모든 진입점이 월 한도 검사를 거치는지** 고정한다.
 *
 * 한도를 한 곳에만 두면 나머지 경로가 우회로가 된다 — 실제로 그 상태였다(검사는 옛 스트리밍
 * 경로 한 곳뿐). 반대로 세면서 검사하지 않는 경로가 있으면, 사용자는 그 경로로 한도를 채운 뒤
 * 영문도 모른 채 다른 경로에서 막힌다.
 *
 * 각 진입점에서 한도가 차 있으면 **영상 행이 저장되지 않아야** 한다. 새 진입점을 추가하면
 * 여기에도 추가할 것.
 */
class MonthlyUploadGateCoverageTest {

    private val videoRepository = mockk<VideoRepository>(relaxed = true)
    private val storageService = mockk<StorageService>(relaxed = true)
    private val userWriteGuard = mockk<UserWriteGuard>(relaxed = true)
    private val storageQuotaUseCase = mockk<StorageQuotaUseCase>(relaxed = true)
    private val quota = mockk<MonthlyUploadQuotaUseCase>()
    private val userId = 7L
    private val full = PlanLimitExceededException("월간 업로드", 5)

    @BeforeEach
    fun limitReached() {
        every { quota.check(userId) } throws full
    }

    private fun uploadUseCase() =
        UploadVideoUseCase(videoRepository, storageService, userWriteGuard, storageQuotaUseCase, quota)

    @Test
    fun `단일 PUT 업로드 시작`() {
        assertFailsWith<PlanLimitExceededException> {
            uploadUseCase().initiatePresignedUpload(userId, "a.mp4", "video/mp4", 1_000)
        }
        verify(exactly = 0) { videoRepository.save(any()) }
    }

    @Test
    fun `멀티파트 업로드 시작`() {
        every { storageService.supportsMultipartUpload() } returns true
        assertFailsWith<PlanLimitExceededException> {
            uploadUseCase().initiateUpload(userId, "a.mp4", "video/mp4", 100L * 1024 * 1024)
        }
        verify(exactly = 0) { videoRepository.save(any()) }
        verify(exactly = 0) { storageService.startMultipartUpload(any(), any(), any()) }
    }

    /** 초안도 센다(출처 UPLOAD_PC). 세면서 막지 않으면 영문 모를 차단이 생긴다. */
    @Test
    fun `초안 생성 - 글과 이미지 게시물`() {
        assertFailsWith<PlanLimitExceededException> { uploadUseCase().createVideo(userId, "제목") }
        verify(exactly = 0) { videoRepository.save(any()) }
    }

    /** 에셋 업로드에는 월 한도가 없다. 여기를 막지 않으면 "에셋으로 올린 뒤 전환" 이 우회로다. */
    @Test
    fun `에셋을 영상으로 전환`() {
        val assetRepository = mockk<com.ongo.domain.asset.AssetRepository>()
        every { assetRepository.findById(any()) } returns com.ongo.domain.asset.Asset(
            id = 3L, userId = userId, filename = "a.mp4", fileUrl = "https://r2/a.mp4",
            fileType = "VIDEO", storageObjectKey = "assets/7/a.mp4", fileSizeBytes = 10,
        )
        val useCase = AssetToVideoUseCase(
            assetRepository, videoRepository, storageQuotaUseCase,
            mockk(relaxed = true), userWriteGuard, quota,
        )
        assertFailsWith<PlanLimitExceededException> { useCase.promote(userId, 3L) }
        verify(exactly = 0) { videoRepository.save(any()) }
    }

    /** 막힐 사용자에게 최대 2 GB 를 내려받을 이유가 없다 — 다운로드 **전에** 막는다. */
    @Test
    fun `URL 가져오기 - 다운로드 전에 막는다`() {
        val downloader = mockk<VideoSourceDownloader>(relaxed = true)
        val useCase = VideoDownloadUseCase(downloader, mockk(relaxed = true), ObjectMapper(), quota)

        assertFailsWith<PlanLimitExceededException> {
            useCase.importVideo(userId, VideoDownloadRequest(url = "https://youtu.be/abcdefghijk"))
        }
        verify(exactly = 0) { downloader.download(any(), any()) }
    }

    /** 다운로드 중에 다른 업로드가 한도를 채웠을 수 있다. 잠금 안에서 다시 본다. */
    @Test
    fun `URL 가져오기 - 저장 직전에 다시 본다`() {
        val persister = ImportedVideoPersister(videoRepository, mockk(relaxed = true), storageQuotaUseCase, quota)
        assertFailsWith<PlanLimitExceededException> {
            persister.persist(
                userId = userId,
                downloaded = mockk(relaxed = true),
                objectKey = "videos/7/imports/x.mp4",
                title = "t",
                originalFilename = "x.mp4",
                sourceReference = ObjectMapper().createObjectNode(),
                openStream = { "x".byteInputStream() },
            )
        }
        verify(exactly = 0) { videoRepository.save(any()) }
    }

    /** CSV 는 한도를 넘는 줄만 실패로 보고한다. 앞의 줄까지 되돌리면 무엇이 들어갔는지 알 수 없다. */
    @Test
    fun `CSV 가져오기 - 넘는 줄만 실패로 보고한다`() {
        var calls = 0
        every { quota.check(userId) } answers { if (++calls > 1) throw full }
        val csv = "title,description,tags,category,platforms,scheduledAt\n" +
            "첫째,,,,YOUTUBE,\n" +
            "둘째,,,,YOUTUBE,\n"

        val result = CsvImportUseCase(videoRepository, quota)
            .importCsv(userId, MockMultipartFile("file", "a.csv", "text/csv", csv.toByteArray()))

        assertEquals(1, result.successCount)
        assertEquals(1, result.errorCount)
        assertTrue(result.errors.single().message.contains("월간 업로드"), result.errors.toString())
        verify(exactly = 1) { videoRepository.save(any()) }
    }
}
