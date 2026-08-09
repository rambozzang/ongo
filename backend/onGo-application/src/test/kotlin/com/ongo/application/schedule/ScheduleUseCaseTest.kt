package com.ongo.application.schedule

import com.ongo.common.enums.Platform
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.exception.AccountFrozenException
import com.ongo.common.exception.ForbiddenException
import com.ongo.application.schedule.dto.CreateScheduleRequest
import com.ongo.application.schedule.dto.PlatformScheduleConfig
import com.ongo.application.schedule.dto.UpdateScheduleRequest
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

    @Test
    fun `다른 사용자의 예약은 시간 수정과 취소 모두 거부한다`() {
        val foreignSchedule = Schedule(
            id = 35L,
            videoId = 22L,
            userId = 99L,
            scheduledAt = LocalDateTime.now(ScheduleUseCase.KST).plusHours(1),
            status = ScheduleStatus.SCHEDULED,
            platforms = mapOf(Platform.YOUTUBE.name to emptyMap<String, Any>()),
        )
        every { schedules.findById(35L) } returns foreignSchedule

        assertFailsWith<ForbiddenException> {
            useCase.updateSchedule(
                7L,
                35L,
                UpdateScheduleRequest(scheduledAt = foreignSchedule.scheduledAt.plusHours(1)),
            )
        }
        assertFailsWith<ForbiddenException> { useCase.cancelSchedule(7L, 35L) }

        verify(exactly = 0) { schedules.update(any()) }
        verify(exactly = 0) { videoUploads.rescheduleScheduledUploads(any(), any()) }
        verify(exactly = 0) { videoUploads.cancelScheduledUploadsByIds(any(), any()) }
    }

    @Test
    fun `예약 취소는 아직 전송되지 않은 durable 업로드도 취소하고 영상을 draft로 되돌린다`() {
        val scheduled = Schedule(
            id = 41L,
            videoId = 22L,
            userId = 7L,
            scheduledAt = LocalDateTime.now(ScheduleUseCase.KST).plusHours(1),
            status = ScheduleStatus.SCHEDULED,
            platforms = mapOf(Platform.YOUTUBE.name to emptyMap<String, Any>()),
        )
        val video = Video(id = 22L, userId = 7L, title = "예약 영상", status = com.ongo.common.enums.UploadStatus.UPLOADING)
        every { schedules.findById(41L) } returns scheduled
        every { videoUploads.findByVideoId(22L) } returnsMany listOf(
            listOf(
                VideoUpload(
                    22L,
                    22L,
                    Platform.YOUTUBE,
                    status = com.ongo.common.enums.UploadStatus.UPLOADING,
                    scheduledAt = scheduled.scheduledAt,
                ),
            ),
            listOf(
                VideoUpload(22L, 22L, Platform.YOUTUBE, status = com.ongo.common.enums.UploadStatus.CANCELLED),
            ),
        )
        every { videoUploads.cancelScheduledUploadsByIds(setOf(22L), any()) } returns 1
        every { videos.findById(22L) } returns video
        every { schedules.update(any()) } answers { firstArg() }
        every { videos.update(any()) } answers { firstArg() }

        useCase.cancelSchedule(7L, 41L)

        verify { videoUploads.cancelScheduledUploadsByIds(setOf(22L), any()) }
        verify { schedules.update(match { it.status == ScheduleStatus.CANCELLED }) }
        verify { videos.update(match { it.id == 22L && it.status == com.ongo.common.enums.UploadStatus.DRAFT }) }
    }

    @Test
    fun `예약 업로드가 lease 경합으로 일부만 취소되면 예약 상태를 바꾸지 않는다`() {
        val scheduled = Schedule(
            id = 42L,
            videoId = 22L,
            userId = 7L,
            scheduledAt = LocalDateTime.now(ScheduleUseCase.KST).plusHours(1),
            status = ScheduleStatus.SCHEDULED,
            platforms = mapOf(Platform.YOUTUBE.name to emptyMap<String, Any>()),
        )
        every { schedules.findById(42L) } returns scheduled
        every { videoUploads.findByVideoId(22L) } returns listOf(
            VideoUpload(
                22L,
                22L,
                Platform.YOUTUBE,
                status = com.ongo.common.enums.UploadStatus.UPLOADING,
                scheduledAt = scheduled.scheduledAt,
            ),
        )
        every { videoUploads.cancelScheduledUploadsByIds(setOf(22L), any()) } returns 0

        assertFailsWith<com.ongo.common.exception.BusinessException> {
            useCase.cancelSchedule(7L, 42L)
        }
        verify(exactly = 0) { schedules.update(any()) }
    }

    @Test
    fun `공유 원본 영상 예약 취소는 현재 계정의 업로드만 취소한다`() {
        val scheduledAt = LocalDateTime.now(ScheduleUseCase.KST).plusHours(1)
        val scheduled = Schedule(
            id = 43L,
            videoId = 22L,
            userId = 7L,
            scheduledAt = scheduledAt,
            status = ScheduleStatus.SCHEDULED,
            platforms = mapOf("YOUTUBE#7" to mapOf("scheduledAt" to scheduledAt.toString())),
        )
        every { schedules.findById(43L) } returns scheduled
        every { videoUploads.findByVideoId(22L) } returns listOf(
            VideoUpload(
                id = 701L,
                videoId = 22L,
                platform = Platform.YOUTUBE,
                channelId = 7L,
                status = com.ongo.common.enums.UploadStatus.UPLOADING,
                scheduledAt = scheduledAt,
            ),
            VideoUpload(
                id = 702L,
                videoId = 22L,
                platform = Platform.YOUTUBE,
                channelId = 8L,
                status = com.ongo.common.enums.UploadStatus.UPLOADING,
                scheduledAt = scheduledAt,
            ),
        )
        every { videoUploads.cancelScheduledUploadsByIds(setOf(701L), any()) } returns 1
        every { schedules.update(any()) } answers { firstArg() }
        every { videos.findById(22L) } returns Video(id = 22L, userId = 7L, title = "공유 영상")
        every { videos.update(any()) } answers { firstArg() }

        useCase.cancelSchedule(7L, 43L)

        verify(exactly = 1) { videoUploads.cancelScheduledUploadsByIds(setOf(701L), any()) }
        verify(exactly = 0) { videoUploads.cancelScheduledUploadsByIds(match { it.contains(702L) }, any()) }
    }

    @Test
    fun `예약 시간 수정은 durable 업로드 큐의 플랫폼별 시간도 함께 바꾼다`() {
        val original = LocalDateTime.now(ScheduleUseCase.KST).plusHours(2)
        val moved = original.plusHours(3)
        val originalInstagram = original.plusHours(1)
        val movedInstagram = moved.plusHours(1)
        val scheduled = Schedule(
            id = 51L,
            videoId = 22L,
            userId = 7L,
            scheduledAt = original,
            status = ScheduleStatus.SCHEDULED,
            platforms = mapOf(
                Platform.YOUTUBE.name to mapOf("scheduledAt" to original.toString()),
                Platform.INSTAGRAM.name to mapOf("scheduledAt" to originalInstagram.toString()),
            ),
        )
        every { schedules.findById(51L) } returns scheduled
        every { users.findById(7L) } returns User(
            id = 7L,
            email = "creator@example.com",
            name = "Creator",
            provider = AuthProvider.GOOGLE,
            providerId = "google-7",
            planType = PlanType.PRO,
        )
        every { videoUploads.rescheduleScheduledUploads(22L, any()) } returns 2
        every { schedules.update(any()) } answers { firstArg() }
        every { videos.findById(22L) } returns Video(id = 22L, userId = 7L, title = "예약 영상")
        every { videoUploads.findByVideoId(22L) } returns listOf(
            VideoUpload(id = 1001L, videoId = 22L, platform = Platform.YOUTUBE, scheduledAt = original),
            VideoUpload(id = 1002L, videoId = 22L, platform = Platform.INSTAGRAM, scheduledAt = originalInstagram),
        )

        useCase.updateSchedule(
            7L,
            51L,
            UpdateScheduleRequest(scheduledAt = moved),
        )

        verify {
            videoUploads.rescheduleScheduledUploads(
                22L,
                match { it == mapOf(1001L to moved, 1002L to movedInstagram) },
            )
        }
        verify { schedules.update(match { it.scheduledAt == moved && it.status == ScheduleStatus.SCHEDULED }) }
    }

    @Test
    fun `lease를 보유한 예약 업로드는 서버에서 시간 수정을 거부한다`() {
        val original = LocalDateTime.now(ScheduleUseCase.KST).plusHours(2)
        val scheduled = Schedule(
            id = 53L,
            videoId = 22L,
            userId = 7L,
            scheduledAt = original,
            status = ScheduleStatus.SCHEDULED,
            platforms = mapOf("YOUTUBE#7" to mapOf("scheduledAt" to original.toString())),
        )
        every { schedules.findById(53L) } returns scheduled
        every { users.findById(7L) } returns User(
            id = 7L,
            email = "creator@example.com",
            name = "Creator",
            provider = AuthProvider.GOOGLE,
            providerId = "google-7",
            planType = PlanType.PRO,
        )
        every { videoUploads.findByVideoId(22L) } returns listOf(
            VideoUpload(
                901L,
                22L,
                Platform.YOUTUBE,
                channelId = 7L,
                status = com.ongo.common.enums.UploadStatus.UPLOADING,
                leaseUntil = LocalDateTime.now().plusMinutes(5),
                scheduledAt = original,
            ),
        )

        assertFailsWith<IllegalStateException> {
            useCase.updateSchedule(7L, 53L, UpdateScheduleRequest(scheduledAt = original.plusHours(1)))
        }
        verify(exactly = 0) { videoUploads.rescheduleScheduledUploads(any(), any()) }
        verify(exactly = 0) { schedules.update(any()) }
    }

    @Test
    fun `공유 원본 영상 예약 시간 수정은 기존 예약 시각과 계정이 일치하는 업로드만 옮긴다`() {
        val original = LocalDateTime.now(ScheduleUseCase.KST).plusHours(2)
        val moved = original.plusHours(3)
        val scheduled = Schedule(
            id = 52L,
            videoId = 22L,
            userId = 7L,
            scheduledAt = original,
            status = ScheduleStatus.SCHEDULED,
            platforms = mapOf("YOUTUBE#7" to mapOf("scheduledAt" to original.toString())),
        )
        every { schedules.findById(52L) } returns scheduled
        every { users.findById(7L) } returns User(
            id = 7L,
            email = "creator@example.com",
            name = "Creator",
            provider = AuthProvider.GOOGLE,
            providerId = "google-7",
            planType = PlanType.PRO,
        )
        every { videoUploads.findByVideoId(22L) } returns listOf(
            VideoUpload(801L, 22L, Platform.YOUTUBE, channelId = 7L, scheduledAt = original),
            VideoUpload(802L, 22L, Platform.YOUTUBE, channelId = 8L, scheduledAt = original),
        )
        every { videoUploads.rescheduleScheduledUploads(22L, mapOf(801L to moved)) } returns 1
        every { schedules.update(any()) } answers { firstArg() }
        every { videos.findById(22L) } returns Video(id = 22L, userId = 7L, title = "공유 영상")

        useCase.updateSchedule(7L, 52L, UpdateScheduleRequest(scheduledAt = moved))

        verify { videoUploads.rescheduleScheduledUploads(22L, mapOf(801L to moved)) }
    }

    @Test
    fun `예약 플랫폼 집합 변경은 큐를 조용히 깨뜨리지 않고 거부한다`() {
        val scheduled = Schedule(
            id = 61L,
            videoId = 22L,
            userId = 7L,
            scheduledAt = LocalDateTime.now(ScheduleUseCase.KST).plusHours(2),
            status = ScheduleStatus.SCHEDULED,
            platforms = mapOf(
                Platform.YOUTUBE.name to emptyMap<String, Any>(),
                Platform.INSTAGRAM.name to emptyMap<String, Any>(),
            ),
        )
        every { schedules.findById(61L) } returns scheduled
        every { users.findById(7L) } returns User(
            id = 7L,
            email = "creator@example.com",
            name = "Creator",
            provider = AuthProvider.GOOGLE,
            providerId = "google-7",
            planType = PlanType.PRO,
        )

        assertFailsWith<IllegalArgumentException> {
            useCase.updateSchedule(
                7L,
                61L,
                UpdateScheduleRequest(platforms = listOf(PlatformScheduleConfig(Platform.YOUTUBE))),
            )
        }

        verify(exactly = 0) { videoUploads.rescheduleScheduledUploads(any(), any()) }
        verify(exactly = 0) { schedules.update(any()) }
    }
}
