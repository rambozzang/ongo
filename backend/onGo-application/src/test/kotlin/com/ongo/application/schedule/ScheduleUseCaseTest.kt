package com.ongo.application.schedule

import com.ongo.common.enums.Platform
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.ScheduleStatus
import com.ongo.application.schedule.dto.CreateScheduleRequest
import com.ongo.application.schedule.dto.PlatformScheduleConfig
import com.ongo.domain.user.User
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScheduleUseCaseTest {

    private val schedules = mockk<ScheduleRepository>()
    private val videos = mockk<VideoRepository>()
    private val users = mockk<UserRepository>()
    private val useCase = ScheduleUseCase(schedules, videos, users)

    @Test
    fun `schedule response preserves each platform wall clock time`() {
        val global = LocalDateTime.of(2026, 8, 10, 9, 0)
        val instagram = LocalDateTime.of(2026, 8, 10, 13, 30)
        every { schedules.findByUserIdAndDateRange(7L, global, global.plusDays(7)) } returns listOf(
            Schedule(
                id = 11L,
                videoId = 22L,
                userId = 7L,
                scheduledAt = global,
                status = ScheduleStatus.SCHEDULED,
                platforms = mapOf(
                    Platform.YOUTUBE.name to mapOf("scheduledAt" to global.toString()),
                    Platform.INSTAGRAM.name to mapOf("scheduledAt" to instagram.toString()),
                ),
            ),
        )
        every { videos.findById(22L) } returns Video(id = 22L, userId = 7L, title = "영상")

        val response = useCase.getSchedules(7L, global, global.plusDays(7))

        assertEquals(global, response.schedules.single().platforms[0].scheduledAt)
        assertEquals(instagram, response.schedules.single().platforms[1].scheduledAt)
    }

    @Test
    fun `schedule creation rejects empty duplicate and stale platform configurations`() {
        val owner = User(
            id = 7L,
            email = "creator@example.com",
            name = "Creator",
            provider = AuthProvider.GOOGLE,
            providerId = "google-7",
            planType = PlanType.PRO,
        )
        every { users.findById(7L) } returns owner
        every { videos.findById(22L) } returns Video(id = 22L, userId = 7L, title = "영상")
        val future = LocalDateTime.now(ScheduleUseCase.KST).plusDays(1)

        fun request(platforms: List<PlatformScheduleConfig>) = CreateScheduleRequest(
            videoId = 22L,
            scheduledAt = future,
            platforms = platforms,
        )

        assertFailsWith<IllegalArgumentException> {
            useCase.createSchedule(7L, request(emptyList()))
        }
        assertFailsWith<IllegalArgumentException> {
            useCase.createSchedule(
                7L,
                request(listOf(PlatformScheduleConfig(Platform.YOUTUBE), PlatformScheduleConfig(Platform.YOUTUBE))),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            useCase.createSchedule(
                7L,
                request(listOf(PlatformScheduleConfig(Platform.YOUTUBE, LocalDateTime.now(ScheduleUseCase.KST).minusMinutes(1)))),
            )
        }
    }
}
