package com.ongo.application.videodownload

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 가용성 조회가 **예외를 던지지 않고 상태를 돌려주는지** 고정한다.
 *
 * 추출기 바이너리는 배포 전제라 JVM 밖에 있다. 즉 코드가 멀쩡해도 호스트에 없으면
 * 동작하지 않는다. 그걸 사용자가 임포트를 눌러본 뒤에야 알게 하지 않으려는 것이 이 조회의
 * 목적이다. 그러니 "쓸 수 없다"는 **정상적인 답**이어야 하고 예외로 터지면 안 된다.
 */
class VideoDownloadAvailabilityTest {

    private val downloader = mockk<VideoSourceDownloader>()

    private fun useCase() = VideoDownloadUseCase(
        sourceDownloader = downloader,
        importedVideoPersister = mockk<ImportedVideoPersister>(relaxed = true),
        objectMapper = ObjectMapper(),
    )

    @Test
    @DisplayName("추출기를 쓸 수 있으면 available=true")
    fun availableWhenExtractorIsPresent() {
        every { downloader.checkAvailability() } returns DownloaderAvailability(available = true)

        val result = useCase().checkAvailability()

        assertTrue(result.available)
        assertEquals(null, result.reason)
    }

    @Test
    @DisplayName("추출기가 없으면 예외가 아니라 available=false 로 돌아온다")
    fun unavailableIsAnAnswerNotAnError() {
        every { downloader.checkAvailability() } returns DownloaderAvailability(
            available = false,
            reason = "영상 URL 가져오기를 지금 사용할 수 없습니다. 관리자에게 문의해 주세요.",
        )

        // 여기서 예외가 나면 화면이 진입점 노출 여부를 판단할 수 없다.
        val result = useCase().checkAvailability()

        assertFalse(result.available)
        assertTrue(!result.reason.isNullOrBlank())
    }

    @Test
    @DisplayName("사용자에게 보이는 사유에 경로나 내부 구조가 없다")
    fun reasonNeverLeaksInternals() {
        every { downloader.checkAvailability() } returns DownloaderAvailability(
            available = false,
            reason = "영상 URL 가져오기를 지금 사용할 수 없습니다. 관리자에게 문의해 주세요.",
        )

        val reason = useCase().checkAvailability().reason ?: ""

        // 경로·실행파일명·예외 클래스명이 새면 내부 구조가 노출되고, 사용자가 그걸 보고
        // 할 수 있는 일도 없다. 진단은 로그가 맡는다.
        listOf("/", "yt-dlp", "Exception", "java.", "IOException").forEach {
            assertFalse(reason.contains(it)) { "사유에 '$it' 가 들어 있다: $reason" }
        }
    }
}
