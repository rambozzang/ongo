import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { Subscription, ChangePlanRequest, ChangePlanResponse, PlanInfo, PlanType, CouponValidation, UsageAlertConfig } from '@/types/subscription'

export const subscriptionApi = {
  getCurrent() {
    return apiClient
      .get<ResData<Subscription>>('/subscriptions/current')
      .then(unwrapResponse)
  },

  getPlans(): Promise<{ plans: PlanInfo[]; currentPlan: PlanType }> {
    return apiClient
      .get<ResData<{ plans: PlanInfo[]; currentPlan: PlanType }>>('/subscriptions/plans')
      .then(unwrapResponse)
  },

  changePlan(request: ChangePlanRequest) {
    return apiClient
      .post<ResData<ChangePlanResponse>>('/subscriptions/change', request)
      .then(unwrapResponse)
      .then((res) => res.subscription)
  },

  cancel() {
    return apiClient
      .post<ResData<Subscription>>('/subscriptions/cancel')
      .then(unwrapResponse)
  },

  getUsage() {
    return apiClient
      .get<ResData<{ uploadsThisMonth: number; storageUsedMb: number }>>('/subscriptions/usage')
      .then(unwrapResponse)
  },

  startTrial(targetPlan: string) {
    return apiClient
      .post<ResData<Subscription>>('/subscriptions/trial', { targetPlan })
      .then(unwrapResponse)
  },

  pauseSubscription() {
    return apiClient
      .post<ResData<Subscription>>('/subscriptions/pause')
      .then(unwrapResponse)
  },

  resumeSubscription() {
    return apiClient
      .post<ResData<Subscription>>('/subscriptions/resume')
      .then(unwrapResponse)
  },

  validateCoupon(code: string, targetPlan?: string, billingCycle?: string) {
    return apiClient
      .post<ResData<CouponValidation>>('/coupons/validate', { code, targetPlan, billingCycle })
      .then(unwrapResponse)
  },

  applyCoupon(code: string, subscriptionId?: number) {
    return apiClient
      .post<ResData<CouponValidation>>('/coupons/apply', { code, subscriptionId })
      .then(unwrapResponse)
  },

  getUsageAlerts() {
    return apiClient
      .get<ResData<UsageAlertConfig[]>>('/usage/alerts')
      .then(unwrapResponse)
  },

  updateUsageAlert(config: { alertType: string; thresholdPercent: number; enabled: boolean }) {
    return apiClient
      .put<ResData<UsageAlertConfig>>('/usage/alerts', config)
      .then(unwrapResponse)
  },

  deleteUsageAlert(alertType: string) {
    return apiClient
      .delete<ResData<void>>(`/usage/alerts/${alertType}`)
      .then(unwrapResponse)
  },
}
