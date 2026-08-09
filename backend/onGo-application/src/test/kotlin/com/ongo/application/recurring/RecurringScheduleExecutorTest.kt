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
    private val transactionManager = mockk<PlatformTransactionManager>()

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
        every { userWriteGuard.requireWritable(7L, any(), any()) } returns Unit
        executor = RecurringScheduleExecutor(
            recurringRepository,
            recurringUseCase,
            scheduleRepository,
            videoRepository,
            distributedLockPort,
            userWriteGuard,
            transactionManager,
        )
    }

    @Test
    fun `회차 생성 중 DB 오류가 나면 markRun과 회차 생성이 함께 롤백되어 다음 실행이 재시도된다`() {
        val definition = definition()
        every { recurringRepository.findDue(any()) } returns listOf(definition)
        every { recurringRepository.markRun(11L, occurrence, occurrence, nextOccurrence) } returns true
        every { videoRepository.findById(10L) } returns sourceVideo()
        every { videoRepository.save(any()) } throws IllegalStateException("database unavailable")

        executor.executeDueSchedules()

        verify(exactly = 1) { recurringRepository.markRun(11L, occurrence, occurrence, nextOccurrence) }
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
        every { scheduleRepository.save(any()) } answers { firstArg() }

        executor.executeDueSchedules()

        verify(exactly = 1) { videoRepository.save(match { it.id == null && it.userId == 7L }) }
        verify(exactly = 1) {
            scheduleRepository.save(match { it.videoId == 20L && it.platforms.keys == setOf(Platform.YOUTUBE.name) })
        }
        verify(exactly = 1) { transactionManager.commit(any<TransactionStatus>()) }
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
