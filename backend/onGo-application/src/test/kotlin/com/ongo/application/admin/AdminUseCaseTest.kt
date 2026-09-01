package com.ongo.application.admin

import com.ongo.application.storage.StorageQuotaUseCase
import com.ongo.common.enums.AuthProvider
import com.ongo.common.enums.Platform
import com.ongo.common.enums.PlanType
import com.ongo.common.enums.SubscriptionStatus
import com.ongo.common.enums.UploadStatus
import com.ongo.common.exception.BusinessException
import com.ongo.domain.channel.ChannelRepository
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import com.ongo.domain.user.User
import com.ongo.domain.user.UserRepository
import com.ongo.domain.video.VideoRepository
import com.ongo.domain.video.VideoUpload
import com.ongo.domain.video.VideoUploadRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class AdminUseCaseTest {
    private val uploads = mockk<VideoUploadRepository>()
    private val useCase = AdminUseCase(
        userRepository = mockk<UserRepository>(relaxed = true),
        subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true),
        videoRepository = mockk<VideoRepository>(relaxed = true),
        videoUploadRepository = uploads,
        channelRepository = mockk<ChannelRepository>(relaxed = true),
        storageQuotaUseCase = mockk<StorageQuotaUseCase>(relaxed = true),
    )

    @Test
    fun `publish queue summary exposes leases retries and unconfirmed work`() {
        val now = LocalDateTime.now()
        every { uploads.findPendingUploads() } returns listOf(
            VideoUpload(
                id = 1L,
                videoId = 10L,
                platform = Platform.YOUTUBE,
                status = UploadStatus.PROCESSING,
                attemptCount = 2,
                leaseUntil = now.plusMinutes(5),
                createdAt = now.minusMinutes(4),
            ),
            VideoUpload(
                id = 3L,
                videoId = 10L,
                platform = Platform.INSTAGRAM,
                status = UploadStatus.UPLOADING,
                attemptCount = 1,
                createdAt = now.minusMinutes(3),
            ),
            VideoUpload(
                id = 2L,
                videoId = 10L,
                platform = Platform.TIKTOK,
                status = UploadStatus.UNCONFIRMED,
                attemptCount = 3,
                nextRetryAt = now.minusMinutes(1),
                lastError = "외부 응답이 끊겼습니다.",
                createdAt = now.minusMinutes(2),
            ),
        )

        val summary = useCase.getPublishQueue()

        assertEquals(3, summary.totalPending)
        assertEquals(1, summary.statusCounts[UploadStatus.PROCESSING.name])
        assertEquals(1, summary.activeLeases)
        assertEquals(1, summary.dueRetries)
        assertEquals(1, summary.unconfirmed)
        assertEquals(3, summary.items.size)
        assertTrue(summary.items.any { it.lastError == "외부 응답이 끊겼습니다." })
    }

    // ── 관리자 활성화: 기간 없는 유료 구독 복원 차단 ──────────────────────────
    //
    // 이 경로는 **결제를 거치지 않고** 상태만 바꾼다. 청구창이 빈 채로 ACTIVE 가 되면 그
    // 구독은 findDueForBilling(`next_billing_date <= now`) 에도 findTrialExpired 에도 걸리지
    // 않아(NULL 비교가 UNKNOWN) 결제 없이 유료 권한이 무기한 유지된다. 운영에서 실제로
    // 그런 구독이 발견됐다.

    private class ActivateFixture(subscription: Subscription?) {
        val users = mockk<UserRepository>(relaxed = true)
        val subscriptions = mockk<SubscriptionRepository>(relaxed = true)
        val useCase = AdminUseCase(
            userRepository = users,
            subscriptionRepository = subscriptions,
            videoRepository = mockk<VideoRepository>(relaxed = true),
            videoUploadRepository = mockk<VideoUploadRepository>(relaxed = true),
            channelRepository = mockk<ChannelRepository>(relaxed = true),
            storageQuotaUseCase = mockk<StorageQuotaUseCase>(relaxed = true),
        )

        init {
            every { users.findById(1L) } returns User(
                id = 1L,
                email = "u1@test.com",
                name = "U1",
                provider = AuthProvider.GOOGLE,
                providerId = "google_1",
                planType = PlanType.BUSINESS,
            )
            every { subscriptions.findByUserId(1L) } returns subscription
            every { subscriptions.update(any()) } answers { firstArg() }
        }
    }

    private fun suspendedSubscription(
        planType: PlanType,
        currentPeriodStart: LocalDateTime? = null,
        currentPeriodEnd: LocalDateTime? = null,
        nextBillingDate: LocalDateTime? = null,
    ) = Subscription(
        id = 1L,
        userId = 1L,
        planType = planType,
        status = SubscriptionStatus.SUSPENDED,
        currentPeriodStart = currentPeriodStart,
        currentPeriodEnd = currentPeriodEnd,
        nextBillingDate = nextBillingDate,
    )

    @Test
    fun `activateUser 는 청구 기간이 없는 유료 구독을 복원하지 않는다`() {
        val fixture = ActivateFixture(suspendedSubscription(PlanType.BUSINESS))

        val error = assertFailsWith<BusinessException> { fixture.useCase.activateUser(1L) }

        assertEquals("SUBSCRIPTION_BILLING_WINDOW_MISSING", error.code)
        // 운영자가 무엇이 비었는지 알아야 고칠 수 있다.
        assertTrue("current_period_start" in error.message, error.message)
        assertTrue("current_period_end" in error.message, error.message)
        assertTrue("next_billing_date" in error.message, error.message)
        verify(exactly = 0) { fixture.subscriptions.update(any()) }
    }

    /** 셋 중 하나만 비어도 막아야 한다 — 부분 확보를 정상으로 보면 결함이 그대로 남는다. */
    @Test
    fun `activateUser 는 청구 기간이 일부만 있어도 복원하지 않는다`() {
        val now = LocalDateTime.now()
        val fixture = ActivateFixture(
            suspendedSubscription(
                PlanType.STARTER,
                currentPeriodStart = now.minusDays(10),
                currentPeriodEnd = now.plusDays(20),
                // nextBillingDate 만 비어 있다.
            ),
        )

        val error = assertFailsWith<BusinessException> { fixture.useCase.activateUser(1L) }

        assertTrue("next_billing_date" in error.message, error.message)
        assertTrue("current_period_start" !in error.message, "채워진 값까지 빠졌다고 알립니다: ${error.message}")
        verify(exactly = 0) { fixture.subscriptions.update(any()) }
    }

    /** 정상 결제 경로는 그대로 복원돼야 한다 — 차단이 과하지 않은지 본다. */
    @Test
    fun `activateUser 는 청구 기간이 온전한 유료 구독을 복원한다`() {
        val now = LocalDateTime.now()
        val fixture = ActivateFixture(
            suspendedSubscription(
                PlanType.STARTER,
                currentPeriodStart = now.minusDays(10),
                currentPeriodEnd = now.plusDays(20),
                nextBillingDate = now.plusDays(20),
            ),
        )

        fixture.useCase.activateUser(1L)

        verify { fixture.subscriptions.update(match { it.status == SubscriptionStatus.ACTIVE }) }
    }

    /** FREE 구독에는 청구창이 없다. 같은 조건을 걸면 무료 사용자가 영영 SUSPENDED 로 남는다. */
    @Test
    fun `activateUser 는 FREE 구독을 기간 없이도 복원한다`() {
        val fixture = ActivateFixture(suspendedSubscription(PlanType.FREE))

        fixture.useCase.activateUser(1L)

        verify { fixture.subscriptions.update(match { it.status == SubscriptionStatus.ACTIVE }) }
    }
}
