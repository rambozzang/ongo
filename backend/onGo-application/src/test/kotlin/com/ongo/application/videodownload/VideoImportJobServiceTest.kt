package com.ongo.application.videodownload

import com.ongo.common.exception.BusinessException
import com.ongo.common.exception.NotFoundException
import com.ongo.common.exception.PlanLimitExceededException
import com.ongo.domain.videodownload.VideoDownloadProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class VideoImportJobServiceTest {

    private val useCase = mockk<VideoDownloadUseCase>(relaxUnitFun = true)
    private var service = VideoImportJobService(useCase, maxConcurrent = 2)
    private val request = VideoDownloadRequest(url = "https://www.youtube.com/watch?v=abc123DEF45")
    private val result = VideoDownloadResult(videoId = 7L, title = "라이브", provider = VideoDownloadProvider.YOUTUBE, fileUrl = "u")

    @AfterEach
    fun tearDown() = service.destroy()

    private fun awaitStatus(userId: Long, jobId: String, vararg wanted: VideoImportJobService.Status): VideoImportJobService.Job {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val job = service.get(userId, jobId)
            if (job.status in wanted) return job
            Thread.sleep(10)
        }
        error("작업이 ${wanted.toList()} 에 도달하지 않았다: ${service.get(userId, jobId)}")
    }

    @Test
    @DisplayName("작업은 바로 돌려주고, 끝나면 결과를 담는다")
    fun startsAndCompletes() {
        every { useCase.importVideo(1L, request) } returns result

        val job = service.start(1L, request)

        val done = awaitStatus(1L, job.id, VideoImportJobService.Status.SUCCEEDED)
        assertEquals(7L, done.result?.videoId)
    }

    @Test
    @DisplayName("사용자 오류는 코드와 문구를 그대로 남긴다")
    fun businessFailureKeepsCode() {
        every { useCase.importVideo(1L, request) } throws BusinessException("VIDEO_DOWNLOAD_SIZE_INVALID", "최대 10GB")

        val done = awaitStatus(1L, service.start(1L, request).id, VideoImportJobService.Status.FAILED)

        assertEquals("VIDEO_DOWNLOAD_SIZE_INVALID", done.errorCode)
        assertEquals("최대 10GB", done.errorMessage)
    }

    @Test
    @DisplayName("예상 못 한 오류는 내부 사정을 드러내지 않는 문구로 바꾼다")
    fun unexpectedFailureIsGeneric() {
        every { useCase.importVideo(1L, request) } throws IllegalStateException("/tmp/ongo-video-download-123 disk full")

        val done = awaitStatus(1L, service.start(1L, request).id, VideoImportJobService.Status.FAILED)

        assertEquals("VIDEO_DOWNLOAD_FAILED", done.errorCode)
        assertEquals("소스 영상을 가져오지 못했습니다.", done.errorMessage)
    }

    /** 몇 분 기다린 뒤에 "지원하지 않는 URL" · "한도 초과" 를 알리면 안 된다. */
    @Test
    @DisplayName("URL 형식과 월 한도는 작업을 만들기 전에 즉시 거른다")
    fun rejectsImmediately() {
        assertThrows(Exception::class.java) { service.start(1L, VideoDownloadRequest(url = "https://evil.example/x.mp4")) }
        every { useCase.precheck(2L) } throws PlanLimitExceededException("월간 업로드", 5)
        assertThrows(PlanLimitExceededException::class.java) { service.start(2L, request) }

        verify(exactly = 0) { useCase.importVideo(any(), any()) }
    }

    @Test
    @DisplayName("남의 작업은 없는 것처럼 보인다")
    fun otherUsersJobIsHidden() {
        every { useCase.importVideo(1L, request) } returns result
        val job = service.start(1L, request)

        assertThrows(NotFoundException::class.java) { service.get(99L, job.id) }
    }

    /** 다운로드는 디스크·대역폭을 크게 쓴다. 한도를 넘는 작업은 앞 작업이 끝날 때까지 기다린다. */
    @Test
    @DisplayName("동시 실행 한도를 넘는 작업은 QUEUED 로 기다린다")
    fun limitsConcurrency() {
        service.destroy()
        service = VideoImportJobService(useCase, maxConcurrent = 1)
        val release = CountDownLatch(1)
        every { useCase.importVideo(1L, request) } answers { release.await(5, TimeUnit.SECONDS); result }

        val first = service.start(1L, request)
        awaitStatus(1L, first.id, VideoImportJobService.Status.RUNNING)
        val second = service.start(1L, request)
        Thread.sleep(100)
        assertEquals(VideoImportJobService.Status.QUEUED, service.get(1L, second.id).status)

        release.countDown()
        awaitStatus(1L, second.id, VideoImportJobService.Status.SUCCEEDED)
    }

    @Test
    @DisplayName("끝난 작업은 한 시간 뒤 지운다")
    fun evictsFinishedJobs() {
        every { useCase.importVideo(1L, request) } returns result
        val job = service.start(1L, request)
        awaitStatus(1L, job.id, VideoImportJobService.Status.SUCCEEDED)

        service.clock = Clock.fixed(Instant.now().plus(VideoImportJobService.RETENTION).plusSeconds(60), ZoneOffset.UTC)

        assertThrows(NotFoundException::class.java) { service.get(1L, job.id) }
    }
}
