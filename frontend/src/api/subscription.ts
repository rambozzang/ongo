import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { Subscription, ChangePlanRequest, ChangePlanResponse } from '@/types/subscription'

export const subscriptionApi = {
  getCurrent() {
    return apiClient
      .get<ResData<Subscription>>('/subscriptions/current')
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
}
