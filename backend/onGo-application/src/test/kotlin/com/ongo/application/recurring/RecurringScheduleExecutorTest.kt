package com.ongo.application.recurring

import com.ongo.common.enums.Platform
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.recurring.RecurringSchedule
import com.ongo.domain.recurring.RecurringScheduleRepository
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.video.Video
import com.ongo.domain.video.VideoRepository
import com.ongo.application.video.StorageService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.LocalDateTime
import java.time.LocalTime

class RecurringScheduleExecutorTest {
    private val recurringRepository = mockk<RecurringScheduleRepository>()
    private val recurringUseCase = mockk<RecurringScheduleUseCase>()
    private val scheduleRepository = mockk<ScheduleRepository>()
    private val videoRepository = mockk<VideoRepository>()
    private val distributedLockPort = mockk<DistributedLockPort>()
    private val userWriteGuard = mockk<UserWriteGuard>()
    private val storageService = mockk<StorageService>()
    private val transactionManager = mockk<PlatformTransactionManager>()
    private val notificationRepository =
        mockk<com.ongo.domain.notification.NotificationRepository>(relaxed = true)

    private lateinit var executor: RecurringScheduleExecutor
    private val occurrence = LocalDateTime.of(2099, 3, 2, 9, 0)
    private val nextOccurrence = occurrence.plusWeeks(1)

    @BeforeEach
    fun setUp() {
        every { transactionManager.getTransaction(any()) } returns SimpleTransactionStatus()
        every { transactionManager.commit(any<TransactionStatus>()) } returns Unit
        every { transactionManager.rollback(any<TransactionStatus>()) } returns Unit
        every { distributedLockPort.withLock(any(), any()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }
        every { recurringUseCase.nextRunAtAfter(any(), occurrence) } returns nextOccurrence
        every { recurringUseCase.deactivateIfOutsideCurrentPlan(any(), any()) } returns false
        every { userWriteGuard.requireWritable(7L, any(), any()) } returns Unit
        executor = RecurringScheduleExecutor(
            recurringRepository,
            recurringUseCase,
            scheduleRepository,
            videoRepository,
            distributedLockPort,
            userWriteGuard,
            storageService,
            transactionManager,
            notificationRepository,
        )
    }

    @Test
    fun `회차 생성 중 DB 오류가 나면 markRun을 소비하지 않아 다음 실행이 재시도된다`() {
        val definition = definition()
        every { recurringRepository.findDue(any()) } returns listOf(definition)
        every { videoRepository.findById(10L) } returns sourceVideo()
        every { videoRepository.save(any()) } throws IllegalStateException("database unavailable")

        executor.executeDueSchedules()

        verify(exactly = 0) { recurringRepository.markRun(any(), any(), any(), any()) }
        verify(exactly = 0) { scheduleRepository.save(any()) }
        verify(exactly = 1) { transactionManager.rollback(any<TransactionStatus>()) }
    }

    @Test
    fun `회차 생성 성공 시 원본 복제와 서버 예약을 한 번 만든다`() {
        val definition = definition()
        every { recurringRepository.findDue(any()) } returns listOf(definition)
        every { recurringRepository.markRun(11L, occurrence, occurrence, nextOccurrence) } returns true
        every { videoRepository.findById(10L) } returns sourceVideo()
        every { videoRepository.save(any()) } returns sourceVideo().copy(id = 20L, status = UploadStatus.DRAFT)
        every { storageService.copyVideoFile(10L, 20L, "https://storage.test/source.mp4") } returns "https://storage.test/occurrence.mp4"
        every { videoRepository.update(any()) } answers { firstArg() }
        every { scheduleRepository.save(any()) } answers { firstArg() }

        executor.executeDueSchedules()

        verify(exactly = 1) { videoRepository.save(match { it.id == null && it.userId == 7L }) }
        // 회차 사본이다. 원본 출처를 복사하면 매 회차가 새 업로드로 세어져 월 한도가 저절로 준다.
        verify { videoRepository.save(match { it.source == com.ongo.domain.contentsource.VideoSource.DERIVED }) }
        verify(exactly = 1) {
            scheduleRepository.save(match { it.videoId == 20L && it.platforms.keys == setOf(Platform.YOUTUBE.name) })
        }
        verify(exactly = 1) {
            videoRepository.update(match { it.id == 20L && it.fileUrl == "https://storage.test/occurrence.mp4" })
        }
        verify(exactly = 1) { transactionManager.commit(any<TransactionStatus>()) }
    }

    @Test
    fun `다운그레이드 후 플랜 밖 반복 예약은 비활성화하고 회차를 만들지 않는다`() {
        val definition = definition()
        every { recurringRepository.findDue(any()) } returns listOf(definition)
        every { recurringUseCase.deactivateIfOutsideCurrentPlan(7L, occurrence) } returns true
        every { recurringRepository.update(definition.copy(isActive = false)) } returns definition.copy(isActive = false)

        executor.executeDueSchedules()

        verify(exactly = 1) { recurringRepository.update(definition.copy(isActive = false)) }
        verify(exactly = 0) { videoRepository.save(any()) }
        verify(exactly = 0) { scheduleRepository.save(any()) }
        verify(exactly = 0) { recurringRepository.markRun(any(), any(), any(), any()) }
        // 조용히 멈추면 사용자는 왜 게시가 안 되는지 모른다. 멈춘 사실과 해법(업그레이드)을 알린다.
        verify(exactly = 1) {
            notificationRepository.save(match { it.userId == 7L && it.referenceType == "recurring_schedule" })
        }
    }

    /** 알림 저장이 실패해도 비활성화는 유지된다 — 한도를 넘는 게시를 막는 것이 우선이다. */
    @Test
    fun `알림 저장이 실패해도 반복 예약은 멈춘 상태로 남는다`() {
        val definition = definition()
        every { recurringRepository.findDue(any()) } returns listOf(definition)
        every { recurringUseCase.deactivateIfOutsideCurrentPlan(7L, occurrence) } returns true
        every { recurringRepository.update(definition.copy(isActive = false)) } returns definition.copy(isActive = false)
        every { notificationRepository.save(any()) } throws IllegalStateException("db down")

        executor.executeDueSchedules()

        verify(exactly = 1) { recurringRepository.update(definition.copy(isActive = false)) }
        verify(exactly = 0) { videoRepository.save(any()) }
    }

    @Test
    fun `스토리지 복제가 실패하면 대상 오브젝트를 보상 삭제하고 회차를 소비하지 않는다`() {
        val definition = definition()
        every { recurringRepository.findDue(any()) } returns listOf(definition)
        every { videoRepository.findById(10L) } returns sourceVideo()
        every { videoRepository.save(any()) } returns sourceVideo().copy(id = 20L, status = UploadStatus.DRAFT)
        every { storageService.copyVideoFile(10L, 20L, "https://storage.test/source.mp4") } throws IllegalStateException("copy failed")
        every { storageService.deleteFile(20L) } returns Unit

        executor.executeDueSchedules()

        verify(exactly = 1) { storageService.deleteFile(20L) }
        verify(exactly = 0) { videoRepository.update(any()) }
        verify(exactly = 0) { scheduleRepository.save(any()) }
        verify(exactly = 0) { recurringRepository.markRun(any(), any(), any(), any()) }
        verify(exactly = 1) { transactionManager.rollback(any<TransactionStatus>()) }
    }

    @Test
    fun `원본 영상이 사라지면 회차를 소비하지 않고 다음 실행에서 재시도한다`() {
        val definition = definition()
        every { recurringRepository.findDue(any()) } returns listOf(definition)
        every { videoRepository.findById(10L) } returns null

        executor.executeDueSchedules()

        verify(exactly = 0) { recurringRepository.markRun(any(), any(), any(), any()) }
        verify(exactly = 0) { videoRepository.save(any()) }
        verify(exactly = 0) { scheduleRepository.save(any()) }
        verify(exactly = 1) { transactionManager.rollback(any<TransactionStatus>()) }
    }

    private fun definition() = RecurringSchedule(
        id = 11L,
        userId = 7L,
        videoId = 10L,
        name = "매주 게시",
        frequency = "WEEKLY",
        dayOfWeek = 1,
        timeOfDay = LocalTime.of(9, 0),
        platforms = listOf(Platform.YOUTUBE.name),
        nextRunAt = occurrence,
    )

    private fun sourceVideo() = Video(
        id = 10L,
        userId = 7L,
        title = "원본 영상",
        fileUrl = "https://storage.test/source.mp4",
    )
}
