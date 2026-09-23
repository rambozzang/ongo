package com.ongo.application.video

import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.enums.MediaType
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.FileValidationException
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.StorageQuotaExceededException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 멀티파트 업로드의 **서버 쪽 약속**을 고정한다.
 *
 * ## 왜 필요한가
 *
 * 예전 업로드는 presigned PUT 한 번이었다. 끊기면 처음부터 다시 올려야 했고, 클라이언트의
 * 30분 제한 때문에 느린 회선에서는 2 GB 를 끝까지 보낼 수 없었다. 멀티파트는 실패한 조각만
 * 다시 보낸다.
 *
 * 그 대신 서버가 지킬 것이 늘었다.
 *
 *  - 조각 크기를 **서버가** 정한다. 클라이언트가 정하면 서명에 무엇을 넣을지 알 수 없다.
 *  - 조각이 빠진 채 완료를 요청하면 **세션을 살려 둔다.** 지우면 다 올린 조각까지 버려진다.
 *  - 검증(크기·요금제 용량)은 단일 PUT 과 **같은 자리**에서 한다. 새 경로가 우회로가 되면 안 된다.
 */
class MultipartUploadUseCaseTest {

    private val videoRepository = mockk<VideoRepository>()
    private val storageService = mockk<StorageService>(relaxed = true)
    private val userWriteGuard = mockk<UserWriteGuard>(relaxed = true)
    private val storageQuotaUseCase = mockk<StorageQuotaUseCase>(relaxed = true)

    private lateinit var useCase: UploadVideoUseCase

    private val userId = 100L
    private val videoId = 7L
    private val mib = 1024L * 1024
    private val objectKey = "videos/7/clip.mp4"
    private val uploadId = "upload-abc"

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        useCase = UploadVideoUseCase(videoRepository, storageService, userWriteGuard, storageQuotaUseCase, mockk<com.ongo.application.video.MonthlyUploadQuotaUseCase>(relaxed = true))
        every { storageService.supportsMultipartUpload() } returns true
    }

    private fun uploading(declared: Long = 40 * mib, owner: Long = userId) = Video(
        id = videoId,
        userId = owner,
        title = "업로드 중",
        fileSizeBytes = declared,
        mediaType = MediaType.VIDEO,
        status = UploadStatus.UPLOADING,
    )

    // ── 시작 ────────────────────────────────────────────────────────────

    @Test
    fun `시작하면 서버가 정한 조각 계획과 세션을 돌려준다`() {
        every { videoRepository.save(any()) } answers { firstArg<Video>().copy(id = videoId) }
        every { storageService.startMultipartUpload(videoId, "clip.mp4", "video/mp4") } returns
            MultipartUploadSession(objectKey, uploadId)

        val r = useCase.initiateUpload(userId, "clip.mp4", "video/mp4", 40 * mib)

        assertTrue(r.multipart)
        assertEquals(uploadId, r.uploadId)
        assertEquals(objectKey, r.objectKey)
        assertEquals(16 * mib, r.partSize)
        assertEquals(3, r.partCount, "40 MiB 는 16+16+8 세 조각이다")
    }

    /**
     * **새 경로가 우회로가 되면 안 된다.** 용량 초과는 세션을 열기 전에 막아야 한다 —
     * 세션이 열리면 조각이 올라가 과금되고, 우리는 그것을 지우는 일만 남는다.
     */
    @Test
    fun `요금제 용량을 넘으면 세션을 열지 않는다`() {
        every { storageQuotaUseCase.checkQuota(userId, 40 * mib, null) } throws
            StorageQuotaExceededException(0, 0, 40 * mib)

        assertFailsWith<StorageQuotaExceededException> {
            useCase.initiateUpload(userId, "clip.mp4", "video/mp4", 40 * mib)
        }
        verify(exactly = 0) { storageService.startMultipartUpload(any(), any(), any()) }
        verify(exactly = 0) { videoRepository.save(any()) }
    }

    @Test
    fun `2GB 를 넘으면 세션을 열지 않는다`() {
        assertFailsWith<FileValidationException> {
            useCase.initiateUpload(userId, "clip.mp4", "video/mp4", 3L * 1024 * mib)
        }
        verify(exactly = 0) { storageService.startMultipartUpload(any(), any(), any()) }
    }

    /** 세션을 못 열었는데 행만 남으면 사용자에게 "업로드 중" 인 유령 영상이 보인다. */
    @Test
    fun `세션을 열지 못하면 방금 만든 행을 지운다`() {
        every { videoRepository.save(any()) } answers { firstArg<Video>().copy(id = videoId) }
        every { videoRepository.delete(videoId) } just Runs
        every { storageService.startMultipartUpload(any(), any(), any()) } throws RuntimeException("R2 down")

        assertFailsWith<RuntimeException> { useCase.initiateUpload(userId, "clip.mp4", "video/mp4", 40 * mib) }
        verify { videoRepository.delete(videoId) }
    }

    /** 로컬 MinIO 처럼 멀티파트가 없으면 기존 단일 PUT 으로 폴백한다. */
    @Test
    fun `스토리지가 멀티파트를 지원하지 않으면 단일 PUT URL 을 돌려준다`() {
        every { storageService.supportsMultipartUpload() } returns false
        every { videoRepository.save(any()) } answers { firstArg<Video>().copy(id = videoId) }
        every { storageService.generateUploadUrl(videoId, any(), any(), any()) } returns "https://put"

        val r = useCase.initiateUpload(userId, "clip.mp4", "video/mp4", 40 * mib)

        assertFalse(r.multipart)
        assertEquals("https://put", r.uploadUrl)
        verify(exactly = 0) { storageService.startMultipartUpload(any(), any(), any()) }
    }

    // ── 조각 URL ───────────────────────────────────────────────────────

    /**
     * **핵심.** 조각 크기는 행에 저장된 신고 크기로 서버가 다시 계산한다. 마지막 조각만 짧다.
     * 이 크기가 서명에 들어가 다른 크기의 조각은 스토리지가 거부한다.
     */
    @Test
    fun `조각 크기를 서버가 계산해 서명에 넘긴다`() {
        every { videoRepository.findById(videoId) } returns uploading(40 * mib)
        val sizes = slot<Map<Int, Long>>()
        every { storageService.presignUploadParts(videoId, objectKey, uploadId, capture(sizes)) } returns emptyMap()

        useCase.presignUploadParts(userId, videoId, uploadId, objectKey, listOf(1, 3, 3))

        assertEquals(mapOf(1 to 16 * mib, 3 to 8 * mib), sizes.captured, "중복은 한 번만, 마지막 조각은 나머지")
    }

    @Test
    fun `계획 밖의 조각 번호는 거부한다`() {
        every { videoRepository.findById(videoId) } returns uploading(40 * mib)

        assertFailsWith<IllegalArgumentException> {
            useCase.presignUploadParts(userId, videoId, uploadId, objectKey, listOf(4))
        }
        assertFailsWith<IllegalArgumentException> {
            useCase.presignUploadParts(userId, videoId, uploadId, objectKey, listOf(0))
        }
    }

    /** 한꺼번에 받으면 느린 회선에서 뒷조각 URL 이 만료된다. 상한을 둔다. */
    @Test
    fun `한 번에 20개를 넘는 조각 URL 은 거부한다`() {
        every { videoRepository.findById(videoId) } returns uploading(2L * 1024 * mib)

        assertFailsWith<IllegalArgumentException> {
            useCase.presignUploadParts(userId, videoId, uploadId, objectKey, (1..21).toList())
        }
    }

    @Test
    fun `남의 영상에는 조각 URL 을 주지 않는다`() {
        every { videoRepository.findById(videoId) } returns uploading(owner = 999L)

        assertFailsWith<ForbiddenException> {
            useCase.presignUploadParts(userId, videoId, uploadId, objectKey, listOf(1))
        }
        verify(exactly = 0) { storageService.presignUploadParts(any(), any(), any(), any()) }
    }

    @Test
    fun `이미 확정된 영상에는 조각 URL 을 주지 않는다`() {
        every { videoRepository.findById(videoId) } returns
            uploading().copy(status = UploadStatus.DRAFT, fileUrl = "https://done")

        assertFailsWith<IllegalStateException> {
            useCase.presignUploadParts(userId, videoId, uploadId, objectKey, listOf(1))
        }
    }

    // ── 완료 ────────────────────────────────────────────────────────────

    /** 완료 시 서버가 **계획 전체**를 기대 조각으로 넘긴다. 클라이언트가 조각 목록을 정하지 않는다. */
    @Test
    fun `완료하면 계획 전체를 기대 조각으로 넘기고 기존 확정 절차를 밟는다`() {
        val video = uploading(40 * mib)
        every { videoRepository.findById(videoId) } returns video
        val expected = slot<Map<Int, Long>>()
        every { storageService.completeMultipartUpload(videoId, objectKey, uploadId, capture(expected)) } just Runs
        every { storageService.getUploadedSize(videoId) } returns 40 * mib
        every { storageService.getFileUrl(videoId, null) } returns "https://file"
        every { storageService.getUploadedKey(videoId) } returns objectKey
        every { videoRepository.update(any()) } answers { firstArg() }

        useCase.completeMultipartUpload(userId, videoId, uploadId, objectKey)

        assertEquals(mapOf(1 to 16 * mib, 2 to 16 * mib, 3 to 8 * mib), expected.captured)
        verify {
            videoRepository.update(match {
                it.status == UploadStatus.DRAFT && it.fileSizeBytes == 40 * mib && it.storageObjectKey == objectKey
            })
        }
    }

    /**
     * **가장 비싼 회귀.** 조각이 빠졌다고 행과 오브젝트를 지우면, 이미 올린 조각까지 모두
     * 버려지고 사용자는 2 GB 를 처음부터 다시 올려야 한다. 멀티파트를 도입한 이유가 사라진다.
     */
    @Test
    fun `조각이 빠졌으면 아무것도 지우지 않고 세션을 살려 둔다`() {
        every { videoRepository.findById(videoId) } returns uploading(40 * mib)
        every { storageService.completeMultipartUpload(any(), any(), any(), any()) } throws
            IncompleteMultipartUploadException(listOf(2))

        val e = assertFailsWith<IncompleteMultipartUploadException> {
            useCase.completeMultipartUpload(userId, videoId, uploadId, objectKey)
        }
        assertEquals(IncompleteMultipartUploadException.CODE, e.code)
        verify(exactly = 0) { storageService.deleteFile(any()) }
        verify(exactly = 0) { videoRepository.delete(any()) }
        verify(exactly = 0) { videoRepository.update(any()) }
    }

    /** 새 경로도 실제 크기로 요금제 용량을 **다시** 본다 — 단일 PUT 과 같은 방어선이다. */
    @Test
    fun `완료 후 실제 크기가 용량을 넘으면 정리하고 거부한다`() {
        every { videoRepository.findById(videoId) } returns uploading(40 * mib)
        every { storageService.getUploadedSize(videoId) } returns 40 * mib
        every { storageQuotaUseCase.checkQuota(userId, 40 * mib, videoId) } throws
            StorageQuotaExceededException(0, 0, 40 * mib)
        every { videoRepository.delete(videoId) } just Runs

        assertFailsWith<StorageQuotaExceededException> {
            useCase.completeMultipartUpload(userId, videoId, uploadId, objectKey)
        }
        verify { storageService.deleteFile(videoId) }
    }

    @Test
    fun `이미 확정된 업로드를 다시 완료해도 아무것도 하지 않는다`() {
        every { videoRepository.findById(videoId) } returns
            uploading().copy(status = UploadStatus.DRAFT, fileUrl = "https://done")

        useCase.completeMultipartUpload(userId, videoId, uploadId, objectKey)

        verify(exactly = 0) { storageService.completeMultipartUpload(any(), any(), any(), any()) }
    }

    // ── 취소 ────────────────────────────────────────────────────────────

    @Test
    fun `취소하면 세션을 중단하고 행과 오브젝트를 정리한다`() {
        every { videoRepository.findById(videoId) } returns uploading()
        every { videoRepository.delete(videoId) } just Runs

        useCase.abortMultipartUpload(userId, videoId, uploadId, objectKey)

        verify { storageService.abortMultipartUpload(videoId, objectKey, uploadId) }
        verify { storageService.deleteFile(videoId) }
        verify { videoRepository.delete(videoId) }
    }

    /** 세션 중단이 실패해도 deleteFile 이 prefix 아래 미완료 업로드를 함께 회수한다. */
    @Test
    fun `세션 중단이 실패해도 정리는 계속한다`() {
        every { videoRepository.findById(videoId) } returns uploading()
        every { storageService.abortMultipartUpload(any(), any(), any()) } throws RuntimeException("R2 down")
        every { videoRepository.delete(videoId) } just Runs

        useCase.abortMultipartUpload(userId, videoId, uploadId, objectKey)

        verify { storageService.deleteFile(videoId) }
    }

    @Test
    fun `이미 확정된 영상은 취소할 수 없다`() {
        every { videoRepository.findById(videoId) } returns
            uploading().copy(status = UploadStatus.DRAFT, fileUrl = "https://done")

        assertFailsWith<IllegalStateException> {
            useCase.abortMultipartUpload(userId, videoId, uploadId, objectKey)
        }
        verify(exactly = 0) { storageService.deleteFile(any()) }
    }
}
