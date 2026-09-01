import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Subscription, Payment, PlanType, Plan } from '@/types/subscription'
import type { PageResponse } from '@/types/api'
import { subscriptionApi } from '@/api/subscription'
import { paymentApi } from '@/api/payment'

/**
 * 백엔드의 `Int.MAX_VALUE` 무제한 표기를 화면 계약의 `-1`로 바꾼다.
 *
 * 서버 플랜 enum은 Kotlin Int 로 무제한을 표현하고, 프론트의 플랜 컴포넌트는 `-1`을
 * 무제한으로 표시한다. 변환하지 않으면 Business가 "월 2,147,483,647회"로 보여서
 * 상품 정보가 가짜처럼 보이고, 결제 전환 화면의 신뢰를 해친다.
 */
const JAVA_INT_MAX = 2_147_483_647

function normalizeUnlimitedLimit(value: number): number {
  return value === JAVA_INT_MAX ? -1 : value
}

export const useSubscriptionStore = defineStore('subscription', () => {
  const subscription = ref<Subscription | null>(null)
  const payments = ref<PageResponse<Payment> | null>(null)
  const plans = ref<Plan[]>([])
  const currentPlan = ref<PlanType | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchSubscription() {
    loading.value = true
    error.value = null
    try {
      subscription.value = await subscriptionApi.getCurrent()
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '구독 정보를 불러오지 못했습니다'
      throw cause
    } finally {
      loading.value = false
    }
  }

  async function fetchPlans() {
    error.value = null
    try {
      const data = await subscriptionApi.getPlans()
      plans.value = data.plans.map(p => ({
        type: p.planType,
        name: p.planType,
        price: p.price,
        yearlyPrice: p.yearlyPrice ?? p.price * 10,
        maxPlatforms: p.features.maxPlatforms,
        maxUploadsPerMonth: normalizeUnlimitedLimit(p.features.monthlyUploads),
        maxScheduleDays: p.features.scheduleDays,
        analyticsPeriodDays: normalizeUnlimitedLimit(p.features.analyticsDays),
        storageMb: p.features.storageGB * 1024,
        // 댓글 관리 API는 서버에서 PRO/BUSINESS만 허용한다. 예약 게시 기간이 있다고
        // 댓글 관리 권한이 생기는 것은 아니므로 서로 다른 서버 게이트를 섞지 않는다.
        commentManagement: ['PRO', 'BUSINESS'].includes(p.planType),
        teamMembers: p.features.maxTeamMembers,
        freeAiCredits: p.features.freeCredits,
        support: getSupportLevel(p.planType),
      }))
      currentPlan.value = data.currentPlan
    } catch (cause) {
      plans.value = []
      error.value = cause instanceof Error ? cause.message : '플랜 정보를 불러오지 못했습니다'
      throw cause
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
    error.value = null
    try {
      payments.value = await paymentApi.getHistory({ page, size })
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '결제 내역을 불러오지 못했습니다'
      throw cause
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

  /*
   * validateCoupon / applyCoupon 은 제거했다.
   * 쿠폰 할인을 반영하는 결제 경로가 없어 서버가 두 엔드포인트를 거절한다.
   * 스토어에 남겨두면 다른 화면이 "이미 있는 기능"으로 믿고 다시 붙인다.
   */

  return {
    subscription,
    payments,
    plans,
    currentPlan,
    loading,
    error,
    fetchSubscription,
    fetchPlans,
    changePlan,
    cancelSubscription,
    fetchPayments,
    startTrial,
    pauseSubscription,
    resumeSubscription,
  }
})
