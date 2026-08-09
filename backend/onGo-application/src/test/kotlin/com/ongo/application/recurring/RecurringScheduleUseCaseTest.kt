package com.ongo.application.recurring

import com.ongo.application.recurring.dto.CreateRecurringScheduleRequest
import com.ongo.common.exception.ForbiddenException
import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.recurring.RecurringSchedule
import com.ongo.domain.recurring.RecurringScheduleRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.LocalTime

class RecurringScheduleUseCaseTest {

    private val repository = mockk<RecurringScheduleRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val userWriteGuard = mockk<UserWriteGuard>(relaxed = true)

    @Test
    fun `동결된 계정은 반복 예약을 생성할 수 없다`() {
        every { userWriteGuard.requireWritable(1L) } throws AccountFrozenException()

        assertThrows(AccountFrozenException::class.java) {
            useCase().createSchedule(1L, request())
        }

        verify(exactly = 0) { videoRepository.findById(any()) }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `반복 예약은 원본 영상 소유자만 생성할 수 있다`() {
        every { videoRepository.findById(10L) } returns Video(
            id = 10L,
            userId = 99L,
            title = "다른 사용자 영상",
            fileUrl = "https://storage.test/video.mp4",
        )

        assertThrows(ForbiddenException::class.java) {
            useCase().createSchedule(1L, request())
        }
    }

    @Test
    fun `반복 예약은 파일이 있는 소유 영상과 유효한 플랫폼을 저장한다`() {
        every { videoRepository.findById(10L) } returns Video(
            id = 10L,
            userId = 1L,
            title = "내 영상",
            fileUrl = "https://storage.test/video.mp4",
        )
        every { repository.save(any()) } answers {
            firstArg<RecurringSchedule>().copy(id = 7L)
        }

        val result = useCase().createSchedule(1L, request())

        assertEquals(7L, result.id)
        assertEquals(10L, result.videoId)
        assertEquals(listOf("YOUTUBE"), result.platforms)
    }

    @Test
    fun `반복 예약은 잘못된 요일과 시간대를 거부한다`() {
        every { videoRepository.findById(10L) } returns Video(
            id = 10L,
            userId = 1L,
            title = "내 영상",
            fileUrl = "https://storage.test/video.mp4",
        )

        val invalidDay = assertThrows(IllegalArgumentException::class.java) {
            useCase().createSchedule(1L, request().copy(dayOfWeek = 8))
        }
        assertTrue(invalidDay.message!!.contains("요일"))

        val invalidZone = assertThrows(IllegalArgumentException::class.java) {
            useCase().createSchedule(1L, request().copy(timezone = "Not/AZone"))
        }
        assertTrue(invalidZone.message!!.contains("시간대"))
    }

    @Test
    fun `반복 예약의 다음 실행 시각은 사용자 시간대를 KST 저장 시각으로 변환한다`() {
        val source = RecurringSchedule(
            id = 7L,
            userId = 1L,
            videoId = 10L,
            name = "뉴욕 반복 게시",
            frequency = "DAILY",
            timeOfDay = LocalTime.of(9, 0),
            timezone = "America/New_York",
            nextRunAt = LocalDateTime.of(2026, 1, 10, 23, 0),
        )

        val next = useCase().nextRunAtAfter(source, source.nextRunAt!!)

        // Jan 11 09:00 in New York is Jan 11 23:00 in KST.
        assertEquals(LocalDateTime.of(2026, 1, 11, 23, 0), next)
    }

    private fun useCase() = RecurringScheduleUseCase(repository, videoRepository, userWriteGuard)

    private fun request() = CreateRecurringScheduleRequest(
        videoId = 10L,
        name = "매주 게시",
        frequency = "WEEKLY",
        dayOfWeek = 1,
        timeOfDay = "09:00",
        platforms = listOf("YOUTUBE"),
    )
}
