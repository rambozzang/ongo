package com.ongo.infrastructure.videodownload

import com.fasterxml.jackson.databind.ObjectMapper
import com.ongo.common.util.FileValidationUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/** 받기 **전에** 거르는 규칙. 상한을 넘는 영상을 끝까지 받은 뒤 버리면 대역폭과 디스크를 버린다. */
class YtDlpCapacityCheckTest {

    private val json = ObjectMapper()
    private val gib = 1024L * 1024 * 1024

    @Test
    @DisplayName("병합 형식은 요청된 형식 크기의 합으로 추정하고, 하나라도 모르면 모른다고 한다")
    fun estimatesMergedSize() {
        assertEquals(300L, YtDlpVideoDownloader.estimatedSize(json.readTree("""{"filesize":300}""")))
        assertEquals(250L, YtDlpVideoDownloader.estimatedSize(json.readTree("""{"filesize_approx":250}""")))
        assertEquals(
            1_100L,
            YtDlpVideoDownloader.estimatedSize(json.readTree("""{"requested_formats":[{"filesize":1000},{"filesize_approx":100}]}""")),
        )
        assertNull(YtDlpVideoDownloader.estimatedSize(json.readTree("""{"requested_formats":[{"filesize":1000},{}]}""")))
        assertNull(YtDlpVideoDownloader.estimatedSize(json.readTree("""{"title":"live"}""")))
    }

    @Test
    @DisplayName("10GB 를 넘는다고 알려진 영상은 받기 전에 한도를 알려 거절한다")
    fun rejectsOversizedBeforeDownload() {
        val error = assertThrows(VideoTooLargeException::class.java) {
            YtDlpVideoDownloader.checkCapacity(FileValidationUtil.VIDEO_DIRECT_UPLOAD_MAX_BYTES + 1, 100 * gib)
        }
        assertEquals("VIDEO_DOWNLOAD_SIZE_INVALID", error.code)
    }

    /** 영상·음성을 합치는 동안 원본 둘과 결과물이 함께 있다. */
    @Test
    @DisplayName("디스크 여유가 예상 크기의 두 배에 못 미치면 받기 전에 멈춘다")
    fun requiresTwiceTheSizeOnDisk() {
        assertThrows(IllegalStateException::class.java) { YtDlpVideoDownloader.checkCapacity(8 * gib, 16 * gib) }
        assertDoesNotThrow { YtDlpVideoDownloader.checkCapacity(8 * gib, 17 * gib) }
    }

    @Test
    @DisplayName("크기를 모르면 디스크로 막지 않는다 — --max-filesize 와 다운로드 후 검증이 맡는다")
    fun unknownSizeIsNotBlocked() {
        assertDoesNotThrow { YtDlpVideoDownloader.checkCapacity(null, 0) }
    }
}
