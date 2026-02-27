import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface PaddleConfigResponse {
  clientToken: string
  environment: string
  paddleCustomerId: string | null
}

export interface PaddleCheckoutData {
  priceId: string
  customData: Record<string, unknown>
  customerEmail: string
  paddleCustomerId: string | null
}

export const paddleApi = {
  getConfig() {
    return apiClient
      .get<ResData<PaddleConfigResponse>>('/paddle/config')
      .then(unwrapResponse)
  },

  createSubscriptionCheckout(planType: string) {
    return apiClient
      .post<ResData<PaddleCheckoutData>>('/paddle/checkout/subscription', { planType })
      .then(unwrapResponse)
  },

  createCreditCheckout(packageName: string) {
    return apiClient
      .post<ResData<PaddleCheckoutData>>('/paddle/checkout/credit', { packageName })
      .then(unwrapResponse)
  },
}
