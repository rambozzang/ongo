package com.ongo.application.schedule

import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.application.video.StreamPublishUseCase
import com.ongo.common.enums.Platform
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.enums.UploadStatus
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 예약 상태 동기화의 **항목별 격리**와 **락 사용**을 검증한다.
 *
 * 예전에는 메서드 전체가 `@Transactional` 이고 항목별로 예외를 삼켰다. 한 건의 DB 오류가
 * 트랜잭션을 abort 시키면 이후 예약이 전부 실패하고 이미 갱신한 상태까지 롤백됐다.
 * 락도 구형 `tryLock`/`releaseLock` 이라 획득과 해제가 다른 커넥션에서 일어나
 * 해제가 무시될 수 있었다.
 *
 * 실제 트랜잭션 의미는 `SpringTransactionParticipationIT`, 락은 `DistributedLockServiceIT` 가
 * PostgreSQL 로 고정한다. 여기서는 구조를 본다.
 */
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

    // 기존 테스트는 동결을 다루지 않는다. 통과시키는 가드로 둔다.
    private val userWriteGuard = mockk<UserWriteGuard>(relaxed = true)

    private lateinit var executor: ScheduleExecutor

    private val kst = ZoneId.of("Asia/Seoul")

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        val txManager = mockk<PlatformTransactionManager>()
        every { txManager.getTransaction(any()) } returns SimpleTransactionStatus()
        every { txManager.commit(any<TransactionStatus>()) } returns Unit
        every { txManager.rollback(any<TransactionStatus>()) } returns Unit

        // withLock 은 블록을 실행하고 true 를 돌려주는 것으로 흉내 낸다
        every { distributedLockPort.withLock(any(), any()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }
        every { scheduleRepository.claimDue(any(), any()) } answers {
            schedule(firstArg<Long>())
        }

        executor = ScheduleExecutor(
            scheduleRepository = scheduleRepository,
            videoUploadRepository = videoUploadRepository,
            distributedLockPort = distributedLockPort,
            streamPublishUseCase = streamPublishUseCase,
            transactionManager = txManager,
            userWriteGuard = userWriteGuard,
        )
    }

    private fun schedule(id: Long) = Schedule(
        id = id,
        videoId = id * 10,
        userId = 1L,
        scheduledAt = LocalDateTime.now(kst).minusMinutes(5),
        status = ScheduleStatus.SCHEDULED,
    )

    private fun publishedUpload(videoId: Long) = VideoUpload(
        id = videoId,
        videoId = videoId,
        platform = Platform.YOUTUBE,
        status = UploadStatus.PUBLISHED,
    )

    @Test
    @DisplayName("한 건이 실패해도 나머지 예약은 계속 동기화된다")
    fun failureOnOneScheduleDoesNotStopTheRest() {
        every { scheduleRepository.findDueSchedules(any()) } returns
            listOf(schedule(1), schedule(2), schedule(3))
        every { videoUploadRepository.findByVideoId(10L) } returns listOf(publishedUpload(10L))
        // 2번에서 DB 오류. 예전 구조라면 여기서 트랜잭션이 죽어 3번도 못 돌았다
        every { videoUploadRepository.findByVideoId(20L) } throws IllegalStateException("DB 오류")
        every { videoUploadRepository.findByVideoId(30L) } returns listOf(publishedUpload(30L))
        every { scheduleRepository.update(any()) } answers { firstArg() }

        executor.syncScheduleStatuses()

        verify(exactly = 1) { scheduleRepository.update(match { it.id == 1L && it.status == ScheduleStatus.PUBLISHED }) }
        verify(exactly = 1) { scheduleRepository.update(match { it.id == 3L && it.status == ScheduleStatus.PUBLISHED }) }
        verify(exactly = 0) { scheduleRepository.update(match { it.id == 2L }) }
    }

    @Test
    @DisplayName("구형 tryLock 이 아니라 withLock 을 쓴다 — 락 누수 방지")
    fun usesWithLockNotLegacyApi() {
        every { scheduleRepository.findDueSchedules(any()) } returns emptyList()

        executor.syncScheduleStatuses()

        verify(exactly = 1) { distributedLockPort.withLock(any(), any()) }
        @Suppress("DEPRECATION")
        verify(exactly = 0) { distributedLockPort.tryLock(any()) }
        @Suppress("DEPRECATION")
        verify(exactly = 0) { distributedLockPort.releaseLock(any()) }
    }

    @Test
    @DisplayName("락을 못 잡으면 아무 작업도 하지 않는다")
    fun skipsWhenLockNotAcquired() {
        every { distributedLockPort.withLock(any(), any()) } returns false

        executor.syncScheduleStatuses()

        verify(exactly = 0) { scheduleRepository.findDueSchedules(any()) }
        verify(exactly = 0) { scheduleRepository.claimDue(any(), any()) }
        verify(exactly = 0) { scheduleRepository.update(any()) }
    }

    @Test
    @DisplayName("업로드 레코드가 없는 SCHEDULED 예약은 업로드를 트리거한다")
    fun triggersUploadWhenNoUploadRecordYet() {
        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule(1))
        every { videoUploadRepository.findByVideoId(10L) } returns emptyList()

        executor.syncScheduleStatuses()

        verify(exactly = 1) { streamPublishUseCase.executeScheduledUpload(match { it.id == 1L }) }
        verify(exactly = 0) { scheduleRepository.update(any()) }
    }

    @Test
    @DisplayName("이미 생성된 UPLOADING row는 외부 게시를 재발행하지 않는다")
    fun doesNotDispatchExistingUploadingRows() {
        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule(1))
        every { videoUploadRepository.findByVideoId(10L) } returns listOf(
            publishedUpload(10L).copy(
                status = UploadStatus.UPLOADING,
                scheduledAt = LocalDateTime.now(kst).minusMinutes(1),
            ),
        )

        executor.syncScheduleStatuses()

        verify(exactly = 0) { streamPublishUseCase.executeScheduledUpload(any()) }
    }

    @Test
    @DisplayName("scheduledAt이 없는 레거시 업로드 row는 예약 복구 대상으로 dispatch한다")
    fun dispatchesLegacyUploadingRowWithoutScheduledAt() {
        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule(1))
        every { videoUploadRepository.findByVideoId(10L) } returns listOf(
            publishedUpload(10L).copy(status = UploadStatus.UPLOADING, scheduledAt = null),
        )

        executor.syncScheduleStatuses()

        verify(exactly = 1) { streamPublishUseCase.executeScheduledUpload(match { it.id == 1L }) }
    }

    @Test
    @DisplayName("PROCESSING 예약은 다시 claim하지 않고 상태만 동기화한다")
    fun processingScheduleIsOnlySynchronized() {
        every { scheduleRepository.findDueSchedules(any()) } returns listOf(
            schedule(1).copy(status = ScheduleStatus.PROCESSING),
        )
        every { videoUploadRepository.findByVideoId(10L) } returns listOf(publishedUpload(10L))
        every { scheduleRepository.update(any()) } answers { firstArg() }

        executor.syncScheduleStatuses()

        verify(exactly = 0) { scheduleRepository.claimDue(any(), any()) }
        verify(exactly = 1) { scheduleRepository.update(match { it.status == ScheduleStatus.PUBLISHED }) }
    }

    @Test
    @DisplayName("다른 작업자가 먼저 claim한 예약은 외부 게시하지 않는다")
    fun skipsScheduleWhenClaimWasLost() {
        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule(1))
        every { scheduleRepository.claimDue(any(), any()) } returns null

        executor.syncScheduleStatuses()

        verify(exactly = 0) { videoUploadRepository.findByVideoId(any()) }
        verify(exactly = 0) { streamPublishUseCase.executeScheduledUpload(any()) }
    }
}
