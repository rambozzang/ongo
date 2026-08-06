package com.ongo.application.schedule

import com.ongo.application.video.StreamPublishUseCase
import com.ongo.common.enums.Platform
import com.ongo.common.enums.ScheduleStatus
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.schedule.Schedule
import com.ongo.domain.schedule.ScheduleRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 예약 실행기에서 **무엇을 막고 무엇을 막지 않는지** 고정한다.
 *
 * 이 스케줄러는 성격이 다른 두 가지를 한다.
 * - 아직 시작되지 않은 예약을 **외부 플랫폼에 실제로 게시**한다
 * - 이미 시작된 업로드의 결과를 읽어 예약 **상태를 정합화**한다
 *
 * 동결 계정에 대해 전자는 막아야 한다. 삭제를 요청한 계정으로 새 콘텐츠를 외부에 올리는
 * 것은 되돌릴 수 없다.
 *
 * 후자는 막으면 안 된다. 새 콘텐츠를 만들지 않고, 막으면 예약이 잘못된 상태로 영원히
 * 남는다. 결제 정합성과 같은 논리다 — 멈추는 쪽이 더 나쁘다.
 */
class ScheduleExecutorFreezeTest {

    private val kst = ZoneId.of("Asia/Seoul")

    private val scheduleRepository = mockk<ScheduleRepository>(relaxed = true)
    private val videoUploadRepository = mockk<VideoUploadRepository>()
    private val lockPort = mockk<DistributedLockPort>()
    private val publishUseCase = mockk<StreamPublishUseCase>(relaxed = true)
    private val guard = mockk<UserWriteGuard>()

    private fun executor(): ScheduleExecutor {
        val txManager = mockk<PlatformTransactionManager>()
        every { txManager.getTransaction(any()) } returns SimpleTransactionStatus()
        every { txManager.commit(any<TransactionStatus>()) } returns Unit
        every { txManager.rollback(any<TransactionStatus>()) } returns Unit

        every { lockPort.withLock(any(), any<() -> Unit>()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }

        return ScheduleExecutor(
            scheduleRepository = scheduleRepository,
            videoUploadRepository = videoUploadRepository,
            distributedLockPort = lockPort,
            streamPublishUseCase = publishUseCase,
            transactionManager = txManager,
            userWriteGuard = guard,
        )
    }

    private fun schedule(id: Long, status: ScheduleStatus = ScheduleStatus.SCHEDULED) = Schedule(
        id = id,
        videoId = id * 10,
        userId = 1L,
        scheduledAt = LocalDateTime.now(kst).minusMinutes(5),
        status = status,
    )

    private fun upload(status: UploadStatus) = VideoUpload(
        id = 1L,
        videoId = 10L,
        platform = Platform.YOUTUBE,
        status = status,
    )

    @Test
    @DisplayName("동결 계정의 예약은 외부 플랫폼에 게시하지 않는다")
    fun frozenAccountIsNotPublished() {
        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule(1))
        every { videoUploadRepository.findByVideoId(10L) } returns emptyList()
        every { guard.requireWritable(1L, any(), any()) } throws AccountFrozenException()

        executor().syncScheduleStatuses()

        // 외부 게시는 되돌릴 수 없다. 삭제를 요청한 계정으로 새 콘텐츠를 올리면 안 된다.
        verify(exactly = 0) { publishUseCase.executeScheduledUpload(any()) }
    }

    @Test
    @DisplayName("정상 계정의 예약은 평소대로 게시한다")
    fun activeAccountIsPublished() {
        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule(1))
        every { videoUploadRepository.findByVideoId(10L) } returns emptyList()
        every { guard.requireWritable(1L, any(), any()) } returns Unit

        executor().syncScheduleStatuses()

        verify(exactly = 1) { publishUseCase.executeScheduledUpload(any()) }
    }

    @Test
    @DisplayName("동결이어도 이미 시작된 업로드의 상태 정합화는 계속한다")
    fun statusReconciliationIsNotGated() {
        // 업로드가 이미 존재한다 = 게시는 이미 일어났다. 그 결과를 반영하는 것뿐이다.
        every { scheduleRepository.findDueSchedules(any()) } returns listOf(schedule(1))
        every { videoUploadRepository.findByVideoId(10L) } returns listOf(upload(UploadStatus.PUBLISHED))
        every { guard.requireWritable(any(), any(), any()) } throws AccountFrozenException()

        executor().syncScheduleStatuses()

        // 막으면 예약이 SCHEDULED 로 영원히 남는다. 상태를 못 따라잡는 쪽이 더 나쁘다.
        verify(exactly = 1) {
            scheduleRepository.update(match { it.status == ScheduleStatus.PUBLISHED })
        }
        // 이 경로에서는 게이트를 보지도 않는다.
        verify(exactly = 0) { guard.requireWritable(any(), any(), any()) }
    }

    @Test
    @DisplayName("동결로 게시를 건너뛰어도 다음 예약은 계속 처리한다")
    fun frozenScheduleDoesNotStopTheBatch() {
        every { scheduleRepository.findDueSchedules(any()) } returns
            listOf(schedule(1), schedule(2))
        every { videoUploadRepository.findByVideoId(10L) } returns emptyList()
        every { videoUploadRepository.findByVideoId(20L) } returns
            listOf(upload(UploadStatus.PUBLISHED))
        every { guard.requireWritable(1L, any(), any()) } throws AccountFrozenException()

        executor().syncScheduleStatuses()

        verify(exactly = 0) { publishUseCase.executeScheduledUpload(any()) }
        // 두 번째 예약은 상태 정합화 경로라 게이트와 무관하게 처리된다.
        verify(exactly = 1) { scheduleRepository.update(any()) }
    }
}
