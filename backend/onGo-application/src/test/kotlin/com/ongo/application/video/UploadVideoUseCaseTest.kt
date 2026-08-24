package com.ongo.application.video

import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.enums.MediaType
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.FileValidationException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.StorageQuotaExceededException
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.accountdeletion.UserWriteGuard
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UploadVideoUseCaseTest {

    private val videoRepository = mockk<VideoRepository>()
    private val storageService = mockk<StorageService>(relaxed = true)
    private val userWriteGuard = mockk<UserWriteGuard>(relaxed = true)
    private val storageQuotaUseCase = mockk<StorageQuotaUseCase>(relaxed = true)

    private lateinit var useCase: UploadVideoUseCase

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = UploadVideoUseCase(videoRepository, storageService, userWriteGuard, storageQuotaUseCase)
    }

    private fun uploadingVideo(
        id: Long = 1L,
        userId: Long = 100L,
        declaredSize: Long = 1_000L,
    ) = Video(
        id = id,
        userId = userId,
        title = "업로드 중",
        fileSizeBytes = declaredSize,
        mediaType = MediaType.VIDEO,
        status = UploadStatus.UPLOADING,
    )

    @Test
    fun `createVideo should save video with DRAFT status`() {
        val savedVideo = Video(
            id = 1L,
            userId = 100L,
            title = "테스트 영상",
            status = UploadStatus.DRAFT,
            mediaType = MediaType.VIDEO,
        )
        every { videoRepository.save(any()) } returns savedVideo

        val result = useCase.createVideo(
            userId = 100L,
            title = "테스트 영상",
        )

        assertEquals(1L, result.id)
        assertEquals(UploadStatus.DRAFT, result.status)
        verify { videoRepository.save(match { it.status == UploadStatus.DRAFT && it.title == "테스트 영상" }) }
    }

    // ---- init: 신고 크기 기준 1차 방어 ----

    /*
     * 한도를 넘는 업로드는 서명 URL 자체를 받지 못해야 한다. URL 이 나간 뒤에 막으면
     * 오브젝트는 이미 올라가 과금되고, 우리는 그걸 지우는 일만 남는다.
     */
    @Test
    fun `initiate rejects an upload that exceeds the plan quota`() {
        every { storageQuotaUseCase.checkQuota(100L, 5_000L, null) } throws
            StorageQuotaExceededException(limitBytes = 1_000L, usedBytes = 0L, requiredBytes = 5_000L)

        assertFailsWith<StorageQuotaExceededException> {
            useCase.initiatePresignedUpload(100L, "big.mp4", "video/mp4", 5_000L)
        }

        // 행도 URL 도 만들어지면 안 된다.
        verify(exactly = 0) { videoRepository.save(any()) }
        verify(exactly = 0) { storageService.generateUploadUrl(any(), any(), any(), any()) }
    }

    /*
     * 선언 크기는 URL 계약까지 전달돼야 한다. S3/R2 는 이 값을 서명에 넣어 다른 크기의 PUT 을
     * 스토리지 단계에서 거부한다 — 값이 흘러가지 않으면 그 방어가 통째로 사라진다.
     */
    @Test
    fun `initiate passes the declared size into the upload url contract`() {
        every { videoRepository.save(any()) } returns uploadingVideo(declaredSize = 2_048L)
        every { storageService.generateUploadUrl(1L, "clip.mp4", "video/mp4", 2_048L) } returns "https://s3/put"

        val result = useCase.initiatePresignedUpload(100L, "clip.mp4", "video/mp4", 2_048L)

        assertEquals("https://s3/put", result.uploadUrl)
        verify(exactly = 1) { storageQuotaUseCase.checkQuota(100L, 2_048L, null) }
        verify(exactly = 1) { storageService.generateUploadUrl(1L, "clip.mp4", "video/mp4", 2_048L) }
    }

    /*
     * 예약 저장은 한도 검사 **뒤에** 와야 한다. 순서가 뒤집히면 검사에 걸린 요청도 이미 행을
     * 만들어 둔 상태가 되고, 그 행이 다른 요청의 사용량에 예약으로 잡혀 한도가 어긋난다.
     * (검사 안에서 사용자 행을 잠그므로, 같은 트랜잭션의 이 순서가 곧 동시성 보호다.)
     */
    @Test
    fun `initiate checks the quota before creating the reservation row`() {
        every { videoRepository.save(any()) } returns uploadingVideo(declaredSize = 2_048L)

        useCase.initiatePresignedUpload(100L, "clip.mp4", "video/mp4", 2_048L)

        verifyOrder {
            storageQuotaUseCase.checkQuota(100L, 2_048L, null)
            videoRepository.save(any())
        }
    }

    // ---- confirm: 실제 크기가 유일한 근거 ----

    @Test
    fun `confirm records the actual object size instead of the declared one`() {
        val video = uploadingVideo(declaredSize = 1L) // 1바이트로 신고한 우회 시도
        every { videoRepository.findById(1L) } returns video
        every { storageService.getUploadedSize(1L) } returns 900L
        every { storageService.getFileUrl(1L, null) } returns "https://storage/videos/1/v.mp4"
        every { videoRepository.update(any()) } returns video

        useCase.confirmPresignedUpload(100L, 1L)

        // 신고치(1)가 아니라 실제 크기(900)가 기록돼야 사용량 계산이 진실해진다.
        verify {
            videoRepository.update(
                match { it.fileSizeBytes == 900L && it.status == UploadStatus.DRAFT && it.fileUrl != null },
            )
        }
        verify(exactly = 1) { storageQuotaUseCase.checkQuota(100L, 900L, 1L) }
    }

    @Test
    fun `confirm re-checks quota with the actual size and cleans up when it exceeds`() {
        val video = uploadingVideo(declaredSize = 1L)
        every { videoRepository.findById(1L) } returns video
        every { storageService.getUploadedSize(1L) } returns 10_000L
        every { storageQuotaUseCase.checkQuota(100L, 10_000L, 1L) } throws
            StorageQuotaExceededException(limitBytes = 1_000L, usedBytes = 0L, requiredBytes = 10_000L)
        every { videoRepository.delete(1L) } just runs

        assertFailsWith<StorageQuotaExceededException> { useCase.confirmPresignedUpload(100L, 1L) }

        // 한도를 넘긴 오브젝트를 남기면 과금은 되는데 사용자에겐 안 보이는 고아가 된다.
        verify(exactly = 1) { storageService.deleteFile(1L) }
        verify(exactly = 1) { videoRepository.delete(1L) }
        verify(exactly = 0) { videoRepository.update(any()) }
    }

    @Test
    fun `confirm cleans up when the actual size is not a valid file size`() {
        val video = uploadingVideo()
        every { videoRepository.findById(1L) } returns video
        every { storageService.getUploadedSize(1L) } returns 3L * 1024 * 1024 * 1024 // 2GB 상한 초과
        every { videoRepository.delete(1L) } just runs

        assertFailsWith<FileValidationException> { useCase.confirmPresignedUpload(100L, 1L) }

        verify(exactly = 1) { storageService.deleteFile(1L) }
        verify(exactly = 1) { videoRepository.delete(1L) }
        verify(exactly = 0) { videoRepository.update(any()) }
    }

    @Test
    fun `confirm cleans up when the object metadata is missing`() {
        val video = uploadingVideo()
        every { videoRepository.findById(1L) } returns video
        every { storageService.getUploadedSize(1L) } returns null
        every { videoRepository.delete(1L) } just runs

        assertFailsWith<NotFoundException> { useCase.confirmPresignedUpload(100L, 1L) }

        verify(exactly = 1) { storageService.deleteFile(1L) }
        verify(exactly = 0) { videoRepository.update(any()) }
    }

    @Test
    fun `confirm cleans up when metadata lookup throws`() {
        val video = uploadingVideo()
        every { videoRepository.findById(1L) } returns video
        every { storageService.getUploadedSize(1L) } throws IllegalStateException("스토리지 장애")
        every { videoRepository.delete(1L) } just runs

        assertFailsWith<NotFoundException> { useCase.confirmPresignedUpload(100L, 1L) }

        verify(exactly = 1) { storageService.deleteFile(1L) }
        verify(exactly = 0) { videoRepository.update(any()) }
    }

    /*
     * 스토리지 삭제가 실패했는데 행까지 지우면, 과금되는 오브젝트를 가리키는 유일한 단서가
     * 사라진다. 행은 UPLOADING 으로 남아 stale 정리가 다시 시도해야 한다.
     */
    @Test
    fun `confirm keeps the row for tracking when storage cleanup fails`() {
        val video = uploadingVideo()
        every { videoRepository.findById(1L) } returns video
        every { storageService.getUploadedSize(1L) } returns null
        every { storageService.deleteFile(1L) } throws IllegalStateException("스토리지 장애")

        assertFailsWith<NotFoundException> { useCase.confirmPresignedUpload(100L, 1L) }

        verify(exactly = 0) { videoRepository.delete(any()) }
        verify(exactly = 0) { videoRepository.update(any()) }
    }

    /*
     * 재시도가 같은 오브젝트를 두 번 세면 안 된다. 이미 확정된 행은 손대지 않고 끝낸다.
     */
    @Test
    fun `confirm is idempotent for an already confirmed upload`() {
        val confirmed = uploadingVideo().copy(
            status = UploadStatus.DRAFT,
            fileUrl = "https://storage/videos/1/v.mp4",
            fileSizeBytes = 900L,
        )
        every { videoRepository.findById(1L) } returns confirmed

        useCase.confirmPresignedUpload(100L, 1L)

        verify(exactly = 0) { storageService.getUploadedSize(any()) }
        verify(exactly = 0) { storageQuotaUseCase.checkQuota(any(), any(), any()) }
        verify(exactly = 0) { videoRepository.update(any()) }
        verify(exactly = 0) { videoRepository.delete(any()) }
    }
}
