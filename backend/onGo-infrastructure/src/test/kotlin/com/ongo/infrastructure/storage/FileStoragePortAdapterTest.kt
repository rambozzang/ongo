package com.ongo.infrastructure.storage

import com.ongo.infrastructure.external.storage.StorageClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * 포트가 **실제 스토리지 어댑터에 위임하는지** 고정한다.
 *
 * URL을 문자열로 조립해 돌려주면 컴파일도 되고 화면에도 그럴듯하게 찍힌다. 틀렸다는 것은
 * 사용자가 눌러 본 뒤에야 드러나고, 그때는 "파일이 사라졌다"는 문의로 온다. 서명이 없거나
 * 엔드포인트가 다른 URL은 진짜와 눈으로 구분되지 않으므로 여기서 호출로 못 박는다.
 *
 * 어떤 URL이 나오는지는 어댑터(S3/R2·MinIO)가 정한다. 이 테스트가 지키는 것은
 * **우리가 그 판단을 가로채지 않는다**는 것뿐이다.
 */
class FileStoragePortAdapterTest {

    private val storageClient = mockk<StorageClient>()
    private val adapter = FileStoragePortAdapter(storageClient)

    @Test
    @DisplayName("다운로드 URL은 스토리지 어댑터가 만든 값을 그대로 돌려준다")
    fun downloadUrlDelegatesToTheRealStorageClient() {
        every { storageClient.getFileUrl("assets/100/uuid_a.png") } returns
            "https://r2.test/assets/100/uuid_a.png?X-Amz-Signature=real"

        val url = adapter.downloadUrlByKey("assets/100/uuid_a.png")

        assertEquals(
            "https://r2.test/assets/100/uuid_a.png?X-Amz-Signature=real",
            url,
            "어댑터가 만든 URL을 쓰지 않고 다른 값을 돌려줬다",
        )
        verify(exactly = 1) { storageClient.getFileUrl("assets/100/uuid_a.png") }
    }

    /** 키를 손대면 다른 오브젝트를 가리킨다. 정규화·접두사 추가 없이 그대로 넘겨야 한다. */
    @Test
    @DisplayName("키를 변형하지 않고 그대로 넘긴다")
    fun keyIsPassedThroughUnchanged() {
        every { storageClient.getFileUrl(any()) } returns "https://r2.test/x"

        adapter.downloadUrlByKey("assets/100/한글 이름.png")

        verify(exactly = 1) { storageClient.getFileUrl("assets/100/한글 이름.png") }
    }

    /* ── 복사 ─────────────────────────────────────────────────────────── */

    /**
     * 복사와 URL 발급 **둘 다 어댑터가 한다.** 여기서 하는 일은 순서를 정하는 것뿐이다.
     */
    @Test
    @DisplayName("복사는 어댑터에 위임하고 사본 키로 발급한 URL을 돌려준다")
    fun copyDelegatesAndReturnsTheTargetUrl() {
        every { storageClient.copyObject("assets/100/a.mp4", "videos/42/a.mp4") } returns Unit
        every { storageClient.getFileUrl("videos/42/a.mp4") } returns
            "https://r2.test/videos/42/a.mp4?X-Amz-Signature=real"

        val url = adapter.copyByKey("assets/100/a.mp4", "videos/42/a.mp4")

        assertEquals("https://r2.test/videos/42/a.mp4?X-Amz-Signature=real", url)
        verify(exactly = 1) { storageClient.copyObject("assets/100/a.mp4", "videos/42/a.mp4") }
        // 원본이 아니라 **사본** 키로 발급해야 한다. 원본 URL을 주면 두 행이 같은 파일을 가리킨다.
        verify(exactly = 1) { storageClient.getFileUrl("videos/42/a.mp4") }
        verify(exactly = 0) { storageClient.getFileUrl("assets/100/a.mp4") }
    }

    /** 키를 손대면 다른 오브젝트를 복사하거나 가리킨다. 양쪽 모두 그대로 넘겨야 한다. */
    @Test
    @DisplayName("복사도 키를 변형하지 않는다")
    fun copyKeysArePassedThroughUnchanged() {
        every { storageClient.copyObject(any(), any()) } returns Unit
        every { storageClient.getFileUrl(any()) } returns "https://r2.test/x"

        adapter.copyByKey("assets/100/원본.mp4", "videos/42/copy_.mp4")

        verify(exactly = 1) { storageClient.copyObject("assets/100/원본.mp4", "videos/42/copy_.mp4") }
    }

    /**
     * **복사가 실패하면 URL을 만들지 않는다.** 사본이 없는데 URL을 주면 호출부는 성공으로
     * 읽고, 깨진 링크를 가진 행이 저장된다.
     */
    @Test
    @DisplayName("복사가 실패하면 URL을 발급하지 않고 그대로 올린다")
    fun copyFailureStopsBeforeIssuingAUrl() {
        every { storageClient.copyObject(any(), any()) } throws IllegalStateException("복사 실패")

        val error = runCatching { adapter.copyByKey("assets/100/a.mp4", "videos/42/a.mp4") }.exceptionOrNull()

        assertEquals("복사 실패", error?.message)
        verify(exactly = 0) { storageClient.getFileUrl(any()) }
    }

    /** 발급 실패를 삼켜 가짜 URL로 바꾸지 않는다 — 판단은 호출부가 한다. */
    @Test
    @DisplayName("발급 실패를 URL로 바꾸지 않고 그대로 올린다")
    fun failuresPropagateInsteadOfBecomingAUrl() {
        every { storageClient.getFileUrl(any()) } throws IllegalStateException("스토리지 장애")

        val error = runCatching { adapter.downloadUrlByKey("assets/100/a.png") }.exceptionOrNull()

        assertEquals("스토리지 장애", error?.message, "실패가 문자열로 둔갑했다")
    }
}
