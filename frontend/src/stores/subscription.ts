import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Subscription, Payment, PlanType, Plan } from '@/types/subscription'
import type { PageResponse } from '@/types/api'
import { subscriptionApi } from '@/api/subscription'
import { paymentApi } from '@/api/payment'

export const useSubscriptionStore = defineStore('subscription', () => {
  const subscription = ref<Subscription | null>(null)
  const payments = ref<PageResponse<Payment> | null>(null)
  const plans = ref<Plan[]>([])
  const currentPlan = ref<PlanType | null>(null)
  const loading = ref(false)

  async function fetchSubscription() {
    loading.value = true
    try {
      subscription.value = await subscriptionApi.getCurrent()
    } catch {
      // silently fail
    } finally {
      loading.value = false
    }
  }

  async function fetchPlans() {
    try {
      const data = await subscriptionApi.getPlans()
      plans.value = data.plans.map(p => ({
        type: p.planType,
        name: p.planType,
        price: p.price,
        yearlyPrice: p.yearlyPrice ?? p.price * 10,
        maxPlatforms: p.features.maxPlatforms,
        maxUploadsPerMonth: p.features.monthlyUploads,
        maxScheduleDays: p.features.scheduleDays,
        analyticsPeriodDays: p.features.analyticsDays,
        storageMb: p.features.storageGB * 1024,
        commentManagement: p.features.scheduleDays > 0,
        teamMembers: p.features.maxTeamMembers,
        freeAiCredits: p.features.freeCredits,
        support: getSupportLevel(p.planType),
      }))
      currentPlan.value = data.currentPlan
    } catch {
      // fallback to hardcoded PLANS
    }
  }

  function getSupportLevel(planType: string): string {
    const map: Record<string, string> = {
      FREE: '커뮤니티',
      STARTER: '이메일',
      PRO: '우선 이메일',
      BUSINESS: '전담 매니저',
    }
    return map[planType] ?? '커뮤니티'
  }

  async function changePlan(targetPlan: PlanType, billingCycle: 'MONTHLY' | 'YEARLY' = 'MONTHLY') {
    loading.value = true
    try {
      subscription.value = await subscriptionApi.changePlan({ targetPlan, billingCycle })
    } catch {
      throw new Error('플랜 변경에 실패했습니다')
    } finally {
      loading.value = false
    }
  }

  async function cancelSubscription() {
    loading.value = true
    try {
      subscription.value = await subscriptionApi.cancel()
    } catch {
      throw new Error('구독 취소에 실패했습니다')
    } finally {
      loading.value = false
    }
  }

  async function fetchPayments(page = 0, size = 20) {
    loading.value = true
    try {
      payments.value = await paymentApi.getHistory({ page, size })
    } catch {
      // silently fail
    } finally {
      loading.value = false
    }
  }

  async function startTrial(targetPlan: string) {
    loading.value = true
    try {
      subscription.value = await subscriptionApi.startTrial(targetPlan)
    } catch {
      throw new Error('트라이얼 시작에 실패했습니다')
    } finally {
      loading.value = false
    }
  }

  async function pauseSubscription() {
    loading.value = true
    try {
      subscription.value = await subscriptionApi.pauseSubscription()
    } catch {
      throw new Error('구독 일시정지에 실패했습니다')
    } finally {
      loading.value = false
    }
  }

  async function resumeSubscription() {
    loading.value = true
    try {
      subscription.value = await subscriptionApi.resumeSubscription()
    } catch {
      throw new Error('구독 재개에 실패했습니다')
    } finally {
      loading.value = false
    }
  }

  async function validateCoupon(code: string) {
    try {
      return await subscriptionApi.validateCoupon(code)
    } catch {
      throw new Error('쿠폰 검증에 실패했습니다')
    }
  }

  async function applyCoupon(code: string) {
    try {
      return await subscriptionApi.applyCoupon(code)
    } catch {
      throw new Error('쿠폰 적용에 실패했습니다')
    }
  }

  return {
    subscription,
    payments,
    plans,
    currentPlan,
    loading,
    fetchSubscription,
    fetchPlans,
    changePlan,
    cancelSubscription,
    fetchPayments,
    startTrial,
    pauseSubscription,
    resumeSubscription,
    validateCoupon,
    applyCoupon,
  }
})
