package com.ongo.application.ai

import com.ongo.common.enums.PlanType
import com.ongo.common.exception.AccountFrozenException
import com.ongo.domain.accountdeletion.UserWriteGuard
import com.ongo.domain.subscription.Subscription
import com.ongo.domain.subscription.SubscriptionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 주간 다이제스트 배치가 동결 계정을 건너뛰는지 고정한다.
 *
 * 여기서 게이트를 보는 것은 **AI 호출 비용을 아끼기 위한 사전 검사**다.
 * `WeeklyDigestUseCase.generateDigest` 는 AI 를 부른 뒤에 저장하는데, 그 호출이
 * 수 초 걸릴 수 있어 그 사이 탈퇴 요청이 들어올 수 있다. 그래서 실제 안전은
 * 유스케이스가 저장 직전에 다시 확인해서 담보한다. 여기 검사만으로는 부족하다.
 */
class WeeklyDigestSchedulerFreezeTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>()
    private val useCase = mockk<WeeklyDigestUseCase>(relaxed = true)
    private val guard = mockk<UserWriteGuard>()

    private val scheduler = WeeklyDigestScheduler(subscriptionRepository, useCase, guard)

    private fun subscription(userId: Long, plan: PlanType) =
        Subscription(id = userId, userId = userId, planType = plan)

    private fun subscriptions(vararg subs: Subscription) {
        every { subscriptionRepository.findByPlanType(PlanType.PRO) } returns
            subs.filter { it.planType == PlanType.PRO }
        every { subscriptionRepository.findByPlanType(PlanType.BUSINESS) } returns
            subs.filter { it.planType == PlanType.BUSINESS }
    }

    @Test
    @DisplayName("동결 계정은 AI 호출까지 가지 않는다")
    fun frozenAccountSkipsBeforeAiCall() {
        subscriptions(subscription(1L, PlanType.PRO), subscription(2L, PlanType.PRO))
        every { guard.requireWritable(1L, any(), any()) } throws AccountFrozenException()
        every { guard.requireWritable(2L, any(), any()) } returns Unit

        scheduler.generateWeeklyDigests()

        // 동결 계정은 AI 크레딧을 쓰지 않는다. 어차피 저장하지 못한다.
        verify(exactly = 0) { useCase.generateDigest(1L, any(), any()) }
        verify(exactly = 1) { useCase.generateDigest(2L, any(), any()) }
    }

    @Test
    @DisplayName("게이트 조회 실패도 건너뛴다 — fail-closed")
    fun gateFailureSkips() {
        subscriptions(subscription(1L, PlanType.PRO))
        every { guard.requireWritable(1L, any(), any()) } throws
            AccountFrozenException("계정 상태를 확인할 수 없어 요청을 처리하지 못했습니다.")

        scheduler.generateWeeklyDigests()

        verify(exactly = 0) { useCase.generateDigest(any(), any(), any()) }
    }

    @Test
    @DisplayName("동결 계정이 섞여 있어도 배치가 죽지 않는다")
    fun frozenAccountDoesNotStopTheBatch() {
        subscriptions(
            subscription(1L, PlanType.PRO),
            subscription(2L, PlanType.BUSINESS),
            subscription(3L, PlanType.PRO),
        )
        every { guard.requireWritable(2L, any(), any()) } throws AccountFrozenException()
        every { guard.requireWritable(1L, any(), any()) } returns Unit
        every { guard.requireWritable(3L, any(), any()) } returns Unit

        scheduler.generateWeeklyDigests()

        verify(exactly = 1) { useCase.generateDigest(1L, any(), any()) }
        verify(exactly = 0) { useCase.generateDigest(2L, any(), any()) }
        verify(exactly = 1) { useCase.generateDigest(3L, any(), any()) }
    }
}
