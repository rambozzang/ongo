package com.ongo.application.schedule

import com.ongo.common.enums.Platform
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.exception.AccountFrozenException
import com.ongo.application.schedule.dto.CreateScheduleRequest
import com.ongo.application.schedule.dto.PlatformScheduleConfig
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.user.User
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import io.mockk.verify

class ScheduleUseCaseTest {

    private val schedules = mockk<ScheduleRepository>()
    private val videos = mockk<VideoRepository>()
    private val videoUploads = mockk<VideoUploadRepository>()
    private val users = mockk<UserRepository>()
    private val userWriteGuard = mockk<UserWriteGuard>(relaxed = true)
    private val useCase = ScheduleUseCase(schedules, videos, videoUploads, users, userWriteGuard)

    @Test
    fun `동결된 계정은 예약 쓰기를 시작할 수 없다`() {
        every { userWriteGuard.requireWritable(7L) } throws AccountFrozenException()

        assertFailsWith<AccountFrozenException> {
            useCase.createSchedule(7L, CreateScheduleRequest(22L, LocalDateTime.now(ScheduleUseCase.KST).plusDays(1), listOf(PlatformScheduleConfig(Platform.YOUTUBE))))
        }

        verify(exactly = 0) { users.findById(any()) }
        verify(exactly = 0) { schedules.save(any()) }
    }

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
        every { videos.findByIds(listOf(22L)) } returns listOf(Video(id = 22L, userId = 7L, title = "영상"))
        every { videoUploads.findByVideoIds(listOf(22L)) } returns mapOf(
            22L to listOf(
                VideoUpload(
                    id = 101L,
                    videoId = 22L,
                    platform = Platform.YOUTUBE,
                    status = com.ongo.common.enums.UploadStatus.PUBLISHED,
                    platformUrl = "https://youtube.test/watch/101",
                    scheduledAt = global,
                ),
                VideoUpload(
                    id = 102L,
                    videoId = 22L,
                    platform = Platform.INSTAGRAM,
                    status = com.ongo.common.enums.UploadStatus.FAILED,
                    scheduledAt = instagram,
                ),
            ),
        )

        val response = useCase.getSchedules(7L, global, global.plusDays(7))

        assertEquals(global, response.schedules.single().platforms[0].scheduledAt)
        assertEquals(instagram, response.schedules.single().platforms[1].scheduledAt)
        assertEquals(com.ongo.common.enums.ScheduleStatus.PUBLISHED, response.schedules.single().platforms[0].status)
        assertEquals("https://youtube.test/watch/101", response.schedules.single().platforms[0].platformUrl)
        assertEquals(com.ongo.common.enums.ScheduleStatus.FAILED, response.schedules.single().platforms[1].status)
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

    @Test
    fun `schedule list applies the requested status filter`() {
        val global = LocalDateTime.of(2026, 8, 10, 9, 0)
        val published = Schedule(
            id = 11L,
            videoId = 22L,
            userId = 7L,
            scheduledAt = global,
            status = ScheduleStatus.PUBLISHED,
            platforms = mapOf(Platform.YOUTUBE.name to mapOf("scheduledAt" to global.toString())),
        )
        val failed = published.copy(id = 12L, status = ScheduleStatus.FAILED)
        every { schedules.findByUserIdAndDateRange(7L, global, global.plusDays(7)) } returns listOf(published, failed)
        every { videos.findByIds(listOf(22L)) } returns listOf(Video(id = 22L, userId = 7L, title = "영상"))
        every { videoUploads.findByVideoIds(listOf(22L)) } returns emptyMap()

        val response = useCase.getSchedules(7L, global, global.plusDays(7), "PUBLISHED")

        assertEquals(listOf(11L), response.schedules.map { it.id })
    }

    @Test
    fun `schedule list rejects unknown status filters`() {
        assertFailsWith<IllegalArgumentException> {
            useCase.getSchedules(7L, null, null, "NOT_A_STATUS")
        }
    }

    @Test
    fun `이미 실행 중인 예약은 취소된 것으로 덮어쓸 수 없다`() {
        val processing = Schedule(
            id = 31L,
            videoId = 22L,
            userId = 7L,
            scheduledAt = LocalDateTime.now(ScheduleUseCase.KST).plusHours(1),
            status = ScheduleStatus.PROCESSING,
            platforms = mapOf(Platform.YOUTUBE.name to emptyMap<String, Any>()),
        )
        every { schedules.findById(31L) } returns processing

        assertFailsWith<IllegalStateException> { useCase.cancelSchedule(7L, 31L) }
        verify(exactly = 0) { schedules.update(any()) }
    }
}
