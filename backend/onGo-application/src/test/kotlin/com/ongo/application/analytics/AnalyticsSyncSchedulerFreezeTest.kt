package com.ongo.application.analytics

import com.ongo.common.enums.Platform
import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.analytics.AnalyticsRepository
import com.ongo.domain.channel.Channel
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.lock.DistributedLockPort
import com.ongo.domain.channel.PlatformClientPort
import com.ongo.domain.channel.TokenEncryptionPort
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 분석 동기화 배치가 동결 계정을 건드리지 않는지 고정한다.
 *
 * 이 배치는 앞의 셋보다 층이 깊다. 채널마다 가상 스레드를 띄우고, 그 안에서 업로드별로
 * 날짜를 돌며 **플랫폼 API 를 호출한 뒤** 저장한다. 그래서 검사 지점이 둘이다.
 *
 * - 채널 단위 사전 검사: 동결이면 토큰 복호화도 API 호출도 하지 않는다
 * - 저장 직전 재확인: API 호출 동안 탈퇴 요청이 들어올 수 있다
 *
 * 여기서는 사전 검사를 본다. 동결 계정이면 **토큰 복호화조차 일어나지 않아야** 한다.
 */
class AnalyticsSyncSchedulerFreezeTest {

    private val channelRepository = mockk<ChannelRepository>()
    private val videoUploadRepository = mockk<VideoUploadRepository>(relaxed = true)
    private val analyticsRepository = mockk<AnalyticsRepository>(relaxed = true)
    private val platformClientPort = mockk<PlatformClientPort>(relaxed = true)
    private val tokenEncryptionPort = mockk<TokenEncryptionPort>()
    private val lockPort = mockk<DistributedLockPort>()
    private val guard = mockk<UserWriteGuard>()

    private fun scheduler() = AnalyticsSyncScheduler(
        channelRepository = channelRepository,
        videoUploadRepository = videoUploadRepository,
        analyticsRepository = analyticsRepository,
        platformClientPort = platformClientPort,
        tokenEncryptionPort = tokenEncryptionPort,
        distributedLockPort = lockPort,
        userWriteGuard = guard,
    ).also {
        every { lockPort.withLock(any(), any<() -> Unit>()) } answers {
            secondArg<() -> Unit>().invoke()
            true
        }
    }

    private fun channel(id: Long, userId: Long) = Channel(
        id = id,
        userId = userId,
        platform = Platform.YOUTUBE,
        platformChannelId = "ch-$id",
        channelName = "채널$id",
        accessToken = "enc-token-$id",
    )

    @Test
    @DisplayName("동결 계정의 채널은 토큰 복호화조차 하지 않는다")
    fun frozenAccountChannelIsSkippedBeforeTokenDecryption() {
        every { channelRepository.findAllActive() } returns listOf(channel(1, 100L))
        every { guard.requireWritable(100L, any(), any()) } throws AccountFrozenException()

        scheduler().syncAnalytics()

        // 토큰 복호화와 플랫폼 API 호출은 비용이다. 어차피 쓸 수 없으면 하지 않는다.
        verify(exactly = 0) { tokenEncryptionPort.decrypt(any()) }
        verify(exactly = 0) { analyticsRepository.upsert(any()) }
    }

    @Test
    @DisplayName("정상 계정의 채널은 평소대로 처리한다")
    fun activeAccountChannelIsProcessed() {
        every { channelRepository.findAllActive() } returns listOf(channel(1, 100L))
        every { guard.requireWritable(100L, any(), any()) } returns Unit
        every { tokenEncryptionPort.decrypt("enc-token-1") } returns "token"
        every { videoUploadRepository.findByPlatformAndUserId(any(), any()) } returns emptyList()

        scheduler().syncAnalytics()

        verify(exactly = 1) { tokenEncryptionPort.decrypt("enc-token-1") }
    }

    @Test
    @DisplayName("게이트 조회가 실패해도 건너뛴다 — fail-closed")
    fun gateLookupFailureSkipsTheChannel() {
        every { channelRepository.findAllActive() } returns listOf(channel(1, 100L))
        // canWrite 는 예외 종류를 가리지 않고 false 로 만든다.
        every { guard.requireWritable(100L, any(), any()) } throws IllegalStateException("DB 오류")

        scheduler().syncAnalytics()

        verify(exactly = 0) { tokenEncryptionPort.decrypt(any()) }
        verify(exactly = 0) { analyticsRepository.upsert(any()) }
    }

    @Test
    @DisplayName("동결 채널이 섞여 있어도 나머지는 계속 처리한다")
    fun frozenChannelDoesNotStopTheBatch() {
        every { channelRepository.findAllActive() } returns
            listOf(channel(1, 100L), channel(2, 200L))
        every { guard.requireWritable(100L, any(), any()) } throws AccountFrozenException()
        every { guard.requireWritable(200L, any(), any()) } returns Unit
        every { tokenEncryptionPort.decrypt("enc-token-2") } returns "token"
        every { videoUploadRepository.findByPlatformAndUserId(any(), any()) } returns emptyList()

        scheduler().syncAnalytics()

        verify(exactly = 0) { tokenEncryptionPort.decrypt("enc-token-1") }
        verify(exactly = 1) { tokenEncryptionPort.decrypt("enc-token-2") }
    }

    @Test
    @DisplayName("가상 스레드 안에서 예외가 나도 배치 전체가 죽지 않는다")
    fun exceptionInVirtualThreadDoesNotKillTheBatch() {
        every { channelRepository.findAllActive() } returns
            listOf(channel(1, 100L), channel(2, 200L))
        every { guard.requireWritable(any(), any(), any()) } returns Unit
        // 1번 채널의 토큰 복호화가 터진다. 스레드 안에서 나는 예외다.
        every { tokenEncryptionPort.decrypt("enc-token-1") } throws IllegalStateException("복호화 실패")
        every { tokenEncryptionPort.decrypt("enc-token-2") } returns "token"
        every { videoUploadRepository.findByPlatformAndUserId(any(), any()) } returns emptyList()

        // 예외가 밖으로 새면 여기서 터진다. 채널 하나 때문에 배치 전체가 죽으면 안 된다.
        scheduler().syncAnalytics()

        // 2번 채널은 정상 처리된다.
        verify(exactly = 1) { tokenEncryptionPort.decrypt("enc-token-2") }
    }

    @Test
    @DisplayName("락을 못 잡으면 아무것도 하지 않는다")
    fun skipsWhenLockIsHeldElsewhere() {
        every { channelRepository.findAllActive() } returns listOf(channel(1, 100L))

        val s = AnalyticsSyncScheduler(
            channelRepository = channelRepository,
            videoUploadRepository = videoUploadRepository,
            analyticsRepository = analyticsRepository,
            platformClientPort = platformClientPort,
            tokenEncryptionPort = tokenEncryptionPort,
            distributedLockPort = lockPort,
            userWriteGuard = guard,
        )
        // 다른 인스턴스가 이미 돌고 있다.
        every { lockPort.withLock(any(), any<() -> Unit>()) } returns false

        s.syncAnalytics()

        verify(exactly = 0) { channelRepository.findAllActive() }
        verify(exactly = 0) { guard.requireWritable(any(), any(), any()) }
    }
}
