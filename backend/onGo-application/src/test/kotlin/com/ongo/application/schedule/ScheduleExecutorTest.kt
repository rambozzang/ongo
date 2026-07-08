package com.ongo.application.schedule

import com.ongo.common.enums.Platform
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.application.video.StreamPublishUseCase
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class ScheduleExecutorTest {

    @MockK
    private lateinit var scheduleRepository: ScheduleRepository

    @MockK
    private lateinit var videoUploadRepository: VideoUploadRepository

    @MockK
    private lateinit var distributedLockPort: DistributedLockPort

    @MockK(relaxed = true)
    private lateinit var streamPublishUseCase: StreamPublishUseCase

    private lateinit var scheduleExecutor: ScheduleExecutor

    // KST 기준 현재 시각으로 간주하는 기준점 (테스트용 고정값)
    private val now = LocalDateTime.now()

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { distributedLockPort.tryLock(any()) } returns true
        every { distributedLockPort.releaseLock(any()) } returns Unit
        scheduleExecutor = ScheduleExecutor(scheduleRepository, videoUploadRepository, distributedLockPort, streamPublishUseCase)
    }

    // ==================== 헬퍼 ====================

    private fun createSchedule(
        id: Long = 1L,
        videoId: Long = 100L,
        userId: Long = 10L,
        scheduledAt: LocalDateTime = now.minusHours(1),
        status: ScheduleStatus = ScheduleStatus.SCHEDULED,
    ): Schedule = Schedule(
        id = id,
        videoId = videoId,
        userId = userId,
        scheduledAt = scheduledAt,
        status = status,
    )

    private fun createUpload(
        id: Long = 1L,
        videoId: Long = 100L,
        platform: Platform = Platform.YOUTUBE,
        status: UploadStatus = UploadStatus.UPLOADING,
    ): VideoUpload = VideoUpload(
        id = id,
        videoId = videoId,
        platform = platform,
        status = status,
    )

    // ==================== 테스트 케이스 ====================

    @Test
    @DisplayName("예약 없으면 아무것도 하지 않음")
    fun `예약 없으면 아무것도 하지 않음`() {
        // given
        every { scheduleRepository.findDueSchedules(any()) } returns emptyList()

        // when
        scheduleExecutor.syncScheduleStatuses()

        // then
        verify(exactly = 0) { videoUploadRepository.findByVideoId(any()) }
        verify(exactly = 0) { scheduleRepository.update(any()) }
    }

    @Test
    @DisplayName("모든 업로드 PUBLISHED면 Schedule도 PUBLISHED로 갱신")
    fun `모든 업로드 PUBLISHED면 Schedule도 PUBLISHED로 갱신`() {
        // given
        val schedule = createSchedule(status = ScheduleStatus.SCHEDULED)
        val uploads = listOf(
            createUpload(id = 1L, platform = Platform.YOUTUBE, status = UploadStatus.PUBLISHED),
            createUpload(id = 2L, platform = Platform.TIKTOK, status = UploadStatus.PUBLISHED),
        )

        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule)
        every { videoUploadRepository.findByVideoId(schedule.videoId) } returns uploads
        every { scheduleRepository.update(any()) } answers { firstArg() }

        // when
        scheduleExecutor.syncScheduleStatuses()

        // then
        val slot = slot<Schedule>()
        verify(exactly = 1) { scheduleRepository.update(capture(slot)) }
        assertEquals(ScheduleStatus.PUBLISHED, slot.captured.status)
    }

    @Test
    @DisplayName("모든 업로드 FAILED면 Schedule도 FAILED로 갱신")
    fun `모든 업로드 FAILED면 Schedule도 FAILED로 갱신`() {
        // given
        val schedule = createSchedule(status = ScheduleStatus.SCHEDULED)
        val uploads = listOf(
            createUpload(id = 1L, platform = Platform.YOUTUBE, status = UploadStatus.FAILED),
            createUpload(id = 2L, platform = Platform.TIKTOK, status = UploadStatus.FAILED),
        )

        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule)
        every { videoUploadRepository.findByVideoId(schedule.videoId) } returns uploads
        every { scheduleRepository.update(any()) } answers { firstArg() }

        // when
        scheduleExecutor.syncScheduleStatuses()

        // then
        val slot = slot<Schedule>()
        verify(exactly = 1) { scheduleRepository.update(capture(slot)) }
        assertEquals(ScheduleStatus.FAILED, slot.captured.status)
    }

    @Test
    @DisplayName("모든 업로드 REJECTED면 Schedule도 FAILED로 갱신")
    fun `모든 업로드 REJECTED면 Schedule도 FAILED로 갱신`() {
        // given
        val schedule = createSchedule(status = ScheduleStatus.SCHEDULED)
        val uploads = listOf(
            createUpload(id = 1L, platform = Platform.YOUTUBE, status = UploadStatus.REJECTED),
            createUpload(id = 2L, platform = Platform.TIKTOK, status = UploadStatus.REJECTED),
        )

        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule)
        every { videoUploadRepository.findByVideoId(schedule.videoId) } returns uploads
        every { scheduleRepository.update(any()) } answers { firstArg() }

        // when
        scheduleExecutor.syncScheduleStatuses()

        // then
        val slot = slot<Schedule>()
        verify(exactly = 1) { scheduleRepository.update(capture(slot)) }
        assertEquals(ScheduleStatus.FAILED, slot.captured.status)
    }

    @Test
    @DisplayName("업로드 PROCESSING 있으면 Schedule PROCESSING으로 갱신")
    fun `업로드 PROCESSING 있으면 Schedule PROCESSING으로 갱신`() {
        // given
        val schedule = createSchedule(status = ScheduleStatus.SCHEDULED)
        val uploads = listOf(
            createUpload(id = 1L, platform = Platform.YOUTUBE, status = UploadStatus.PROCESSING),
            createUpload(id = 2L, platform = Platform.TIKTOK, status = UploadStatus.UPLOADING),
        )

        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule)
        every { videoUploadRepository.findByVideoId(schedule.videoId) } returns uploads
        every { scheduleRepository.update(any()) } answers { firstArg() }

        // when
        scheduleExecutor.syncScheduleStatuses()

        // then
        val slot = slot<Schedule>()
        verify(exactly = 1) { scheduleRepository.update(capture(slot)) }
        assertEquals(ScheduleStatus.PROCESSING, slot.captured.status)
    }

    @Test
    @DisplayName("업로드 REVIEW 상태가 있으면 Schedule PROCESSING으로 갱신")
    fun `업로드 REVIEW 상태가 있으면 Schedule PROCESSING으로 갱신`() {
        // given
        val schedule = createSchedule(status = ScheduleStatus.SCHEDULED)
        val uploads = listOf(
            createUpload(id = 1L, platform = Platform.YOUTUBE, status = UploadStatus.REVIEW),
            createUpload(id = 2L, platform = Platform.TIKTOK, status = UploadStatus.UPLOADING),
        )

        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule)
        every { videoUploadRepository.findByVideoId(schedule.videoId) } returns uploads
        every { scheduleRepository.update(any()) } answers { firstArg() }

        // when
        scheduleExecutor.syncScheduleStatuses()

        // then
        val slot = slot<Schedule>()
        verify(exactly = 1) { scheduleRepository.update(capture(slot)) }
        assertEquals(ScheduleStatus.PROCESSING, slot.captured.status)
    }

    @Test
    @DisplayName("업로드 레코드 없고 24시간 초과된 SCHEDULED도 executeScheduledUpload 호출")
    fun `업로드 레코드 없고 24시간 초과된 SCHEDULED도 executeScheduledUpload 호출`() {
        // given: scheduledAt이 25시간 전 → 24시간 타임아웃 초과, SCHEDULED 상태
        val schedule = createSchedule(scheduledAt = now.minusHours(25), status = ScheduleStatus.SCHEDULED)

        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule)
        every { videoUploadRepository.findByVideoId(schedule.videoId) } returns emptyList()

        // when
        scheduleExecutor.syncScheduleStatuses()

        // then: SCHEDULED 상태이면 먼저 업로드 시도
        verify(exactly = 1) { streamPublishUseCase.executeScheduledUpload(schedule) }
        verify(exactly = 0) { scheduleRepository.update(any()) }
    }

    @Test
    @DisplayName("업로드 레코드 없고 24시간 초과 시 FAILED 처리 — PROCESSING 상태")
    fun `업로드 레코드 없고 24시간 초과 시 FAILED 처리`() {
        // given: scheduledAt이 25시간 전 → 24시간 타임아웃 초과, PROCESSING 상태
        val schedule = createSchedule(scheduledAt = now.minusHours(25), status = ScheduleStatus.PROCESSING)

        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule)
        every { videoUploadRepository.findByVideoId(schedule.videoId) } returns emptyList()
        every { scheduleRepository.update(any()) } answers { firstArg() }

        // when
        scheduleExecutor.syncScheduleStatuses()

        // then
        val slot = slot<Schedule>()
        verify(exactly = 1) { scheduleRepository.update(capture(slot)) }
        assertEquals(ScheduleStatus.FAILED, slot.captured.status)
    }

    @Test
    @DisplayName("업로드 레코드 없고 SCHEDULED 상태면 executeScheduledUpload 호출")
    fun `업로드 레코드 없고 SCHEDULED 상태면 executeScheduledUpload 호출`() {
        // given
        val schedule = createSchedule(scheduledAt = now.minusHours(1), status = ScheduleStatus.SCHEDULED)

        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule)
        every { videoUploadRepository.findByVideoId(schedule.videoId) } returns emptyList()

        // when
        scheduleExecutor.syncScheduleStatuses()

        // then
        verify(exactly = 1) { streamPublishUseCase.executeScheduledUpload(schedule) }
        verify(exactly = 0) { scheduleRepository.update(any()) }
    }

    @Test
    @DisplayName("업로드 레코드 없고 PROCESSING 상태면 24시간 미만이면 상태 유지")
    fun `업로드 레코드 없고 PROCESSING 상태면 24시간 미만이면 상태 유지`() {
        // given: scheduledAt이 1시간 전 → 24시간 이내, PROCESSING 상태
        val schedule = createSchedule(scheduledAt = now.minusHours(1), status = ScheduleStatus.PROCESSING)

        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule)
        every { videoUploadRepository.findByVideoId(schedule.videoId) } returns emptyList()

        // when
        scheduleExecutor.syncScheduleStatuses()

        // then: 업데이트 호출 없어야 함
        verify(exactly = 0) { streamPublishUseCase.executeScheduledUpload(any()) }
        verify(exactly = 0) { scheduleRepository.update(any()) }
    }

    @Test
    @DisplayName("24시간 타임아웃 시 FAILED 처리 — 업로드는 있지만 여전히 UPLOADING")
    fun `24시간 타임아웃 시 FAILED 처리 — 업로드는 있지만 여전히 UPLOADING`() {
        // given: scheduledAt이 25시간 전 + 업로드가 UPLOADING 상태로 멈춰있음
        val schedule = createSchedule(scheduledAt = now.minusHours(25), status = ScheduleStatus.SCHEDULED)
        val uploads = listOf(
            createUpload(id = 1L, platform = Platform.YOUTUBE, status = UploadStatus.UPLOADING),
        )

        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule)
        every { videoUploadRepository.findByVideoId(schedule.videoId) } returns uploads
        every { scheduleRepository.update(any()) } answers { firstArg() }

        // when
        scheduleExecutor.syncScheduleStatuses()

        // then
        val slot = slot<Schedule>()
        verify(exactly = 1) { scheduleRepository.update(capture(slot)) }
        assertEquals(ScheduleStatus.FAILED, slot.captured.status)
    }

    @Test
    @DisplayName("이미 같은 상태면 update 호출하지 않음 — 이미 PROCESSING 상태")
    fun `이미 같은 상태면 update 호출하지 않음 — 이미 PROCESSING 상태`() {
        // given: Schedule이 이미 PROCESSING, 업로드도 PROCESSING
        val schedule = createSchedule(status = ScheduleStatus.PROCESSING)
        val uploads = listOf(
            createUpload(id = 1L, platform = Platform.YOUTUBE, status = UploadStatus.PROCESSING),
        )

        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule)
        every { videoUploadRepository.findByVideoId(schedule.videoId) } returns uploads

        // when
        scheduleExecutor.syncScheduleStatuses()

        // then: newStatus == schedule.status 이므로 update 호출 없어야 함
        verify(exactly = 0) { scheduleRepository.update(any()) }
    }

    @Test
    @DisplayName("여러 예약 중 일부만 상태 변경 대상이면 해당 예약만 update 호출")
    fun `여러 예약 중 일부만 상태 변경 대상이면 해당 예약만 update 호출`() {
        // given
        val scheduleToUpdate = createSchedule(id = 1L, videoId = 101L, status = ScheduleStatus.SCHEDULED)
        val scheduleNoChange = createSchedule(id = 2L, videoId = 102L, status = ScheduleStatus.PROCESSING)

        val uploadsPublished = listOf(
            createUpload(id = 1L, videoId = 101L, platform = Platform.YOUTUBE, status = UploadStatus.PUBLISHED),
        )
        val uploadsProcessing = listOf(
            createUpload(id = 2L, videoId = 102L, platform = Platform.TIKTOK, status = UploadStatus.PROCESSING),
        )

        every { scheduleRepository.findDueSchedules(any()) } returns listOf(scheduleToUpdate, scheduleNoChange)
        every { videoUploadRepository.findByVideoId(101L) } returns uploadsPublished
        every { videoUploadRepository.findByVideoId(102L) } returns uploadsProcessing
        every { scheduleRepository.update(any()) } answers { firstArg() }

        // when
        scheduleExecutor.syncScheduleStatuses()

        // then: scheduleToUpdate만 update 호출
        val slot = slot<Schedule>()
        verify(exactly = 1) { scheduleRepository.update(capture(slot)) }
        assertEquals(1L, slot.captured.id)
        assertEquals(ScheduleStatus.PUBLISHED, slot.captured.status)
    }

    @Test
    @DisplayName("FAILED와 REJECTED 혼합이면 Schedule FAILED로 갱신")
    fun `FAILED와 REJECTED 혼합이면 Schedule FAILED로 갱신`() {
        // given
        val schedule = createSchedule(status = ScheduleStatus.SCHEDULED)
        val uploads = listOf(
            createUpload(id = 1L, platform = Platform.YOUTUBE, status = UploadStatus.FAILED),
            createUpload(id = 2L, platform = Platform.TIKTOK, status = UploadStatus.REJECTED),
        )

        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule)
        every { videoUploadRepository.findByVideoId(schedule.videoId) } returns uploads
        every { scheduleRepository.update(any()) } answers { firstArg() }

        // when
        scheduleExecutor.syncScheduleStatuses()

        // then
        val slot = slot<Schedule>()
        verify(exactly = 1) { scheduleRepository.update(capture(slot)) }
        assertEquals(ScheduleStatus.FAILED, slot.captured.status)
    }
}
