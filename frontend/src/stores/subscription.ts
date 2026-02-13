import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Subscription, Payment, PlanType } from '@/types/subscription'
import type { PageResponse } from '@/types/api'
import { subscriptionApi } from '@/api/subscription'
import { paymentApi } from '@/api/payment'

export const useSubscriptionStore = defineStore('subscription', () => {
  const subscription = ref<Subscription | null>(null)
  const payments = ref<PageResponse<Payment> | null>(null)
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

  async function changePlan(targetPlan: PlanType) {
    loading.value = true
    try {
      subscription.value = await subscriptionApi.changePlan({ targetPlan })
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

  return {
    subscription,
    payments,
    loading,
    fetchSubscription,
    changePlan,
    cancelSubscription,
    fetchPayments,
  }
})
