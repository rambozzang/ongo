package com.ongo.infrastructure.storage

import com.ongo.application.video.IncompleteMultipartUploadException
import com.ongo.infrastructure.external.storage.StorageClient
import com.ongo.infrastructure.external.storage.StorageProperties
import com.ongo.infrastructure.external.storage.UploadedPart
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 멀티파트의 스토리지 쪽 약속.
 *
 * `objectKey` 와 `uploadId` 는 **클라이언트가 되돌려 보내는 값**이다. 경로를 검사하지 않으면
 * 남의 영상 경로에 조각을 올리거나 남의 업로드를 중단할 수 있다.
 */
class VideoStorageMultipartTest {

    private val storageClient = mockk<StorageClient>()
    private val service = VideoStorageService(
        storageClient = storageClient,
        storageProperties = StorageProperties(bucket = "ongo-videos"),
        tusBaseEndpoint = "http://localhost/tus",
    )

    private val key = "videos/7/clip.mp4"

    @Test
    fun `시작 시 키를 서버가 정한다 - 파일명은 정제된다`() {
        every { storageClient.createMultipartUpload("videos/7/my_clip_.mp4", "video/mp4") } returns "u1"

        val session = service.startMultipartUpload(7L, "../my clip!.mp4", "video/mp4")

        assertEquals("videos/7/my_clip_.mp4", session.objectKey)
        assertEquals("u1", session.uploadId)
    }

    /** **보안 핵심.** 다른 영상의 경로를 보내면 스토리지까지 가지 않고 거부한다. */
    @Test
    fun `다른 영상 경로의 키는 거부한다`() {
        listOf(
            "videos/8/clip.mp4",          // 남의 영상
            "videos/7/",                   // 파일명 없음
            "videos/7/sub/clip.mp4",       // 하위 경로
            "videos/7/../8/clip.mp4",      // 경로 탈출
            "videos/70/clip.mp4",          // 접두사만 같은 다른 id
            "assets/7/clip.mp4",           // 다른 버킷 영역
        ).forEach { bad ->
            assertFailsWith<IllegalArgumentException>("거부해야 한다: $bad") {
                service.presignUploadParts(7L, bad, "u1", mapOf(1 to 5L))
            }
            assertFailsWith<IllegalArgumentException>("거부해야 한다: $bad") {
                service.abortMultipartUpload(7L, bad, "u1")
            }
            assertFailsWith<IllegalArgumentException>("거부해야 한다: $bad") {
                service.completeMultipartUpload(7L, bad, "u1", mapOf(1 to 5L))
            }
        }
        verify(exactly = 0) { storageClient.presignUploadPart(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { storageClient.abortMultipartUpload(any(), any()) }
    }

    @Test
    fun `조각마다 계획된 크기로 서명한다`() {
        every { storageClient.presignUploadPart(key, "u1", 1, 100L, 60) } returns "url1"
        every { storageClient.presignUploadPart(key, "u1", 2, 40L, 60) } returns "url2"

        val urls = service.presignUploadParts(7L, key, "u1", mapOf(1 to 100L, 2 to 40L))

        assertEquals(mapOf(1 to "url1", 2 to "url2"), urls)
    }

    @Test
    fun `모든 조각이 맞으면 계획 순서대로 완료한다`() {
        every { storageClient.listUploadedParts(key, "u1") } returns listOf(
            UploadedPart(2, "e2", 40),
            UploadedPart(1, "e1", 100),
        )
        val parts = slot<List<UploadedPart>>()
        every { storageClient.completeMultipartUpload(key, "u1", capture(parts)) } just Runs

        service.completeMultipartUpload(7L, key, "u1", mapOf(1 to 100L, 2 to 40L))

        assertEquals(listOf(1, 2), parts.captured.map { it.partNumber })
    }

    @Test
    fun `빠진 조각이 있으면 완료하지 않고 번호를 알려준다`() {
        every { storageClient.listUploadedParts(key, "u1") } returns listOf(UploadedPart(1, "e1", 100))

        val e = assertFailsWith<IncompleteMultipartUploadException> {
            service.completeMultipartUpload(7L, key, "u1", mapOf(1 to 100L, 2 to 40L, 3 to 40L))
        }
        assertEquals(listOf(2, 3), e.missingParts)
        verify(exactly = 0) { storageClient.completeMultipartUpload(any(), any(), any()) }
    }

    /** 서명 강제가 없는 호환 스토리지를 대비해 크기도 다시 본다. */
    @Test
    fun `크기가 계획과 다른 조각은 빠진 것으로 본다`() {
        every { storageClient.listUploadedParts(key, "u1") } returns listOf(
            UploadedPart(1, "e1", 100),
            UploadedPart(2, "e2", 39),
        )

        val e = assertFailsWith<IncompleteMultipartUploadException> {
            service.completeMultipartUpload(7L, key, "u1", mapOf(1 to 100L, 2 to 40L))
        }
        assertEquals(listOf(2), e.missingParts)
    }

    /** 계획에 없는 조각을 합치면 원본보다 큰 파일이 된다. 완료 목록에서 뺀다. */
    @Test
    fun `계획에 없는 조각은 완료 목록에 넣지 않는다`() {
        every { storageClient.listUploadedParts(key, "u1") } returns listOf(
            UploadedPart(1, "e1", 100),
            UploadedPart(2, "e2", 40),
            UploadedPart(9, "e9", 5_000_000),
        )
        val parts = slot<List<UploadedPart>>()
        every { storageClient.completeMultipartUpload(key, "u1", capture(parts)) } just Runs

        service.completeMultipartUpload(7L, key, "u1", mapOf(1 to 100L, 2 to 40L))

        assertEquals(listOf(1, 2), parts.captured.map { it.partNumber })
    }

    /**
     * 미완료 조각은 오브젝트 목록에 안 나온다. deleteFile 이 이것까지 회수하지 않으면
     * 버려진 업로드의 조각이 보이지 않는 채로 버킷 용량을 계속 차지한다.
     */
    @Test
    fun `영상 파일 삭제 시 미완료 멀티파트 업로드도 중단한다`() {
        every { storageClient.listObjects("videos/7/") } returns emptyList()
        every { storageClient.abortMultipartUploads("videos/7/") } returns 1

        service.deleteFile(7L)

        verify { storageClient.abortMultipartUploads("videos/7/") }
    }
}
