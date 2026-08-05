import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface PortOneCheckoutIntent {
  paymentId: string
  storeId: string
  channelKey: string
  amount: number
  currency: 'KRW'
  orderName: string
  customerEmail: string
  customerName: string
}

export interface PortOnePaymentResult {
  id: number
  status: string
}

export const portoneApi = {
  createSubscriptionCheckout(planType: string, billingCycle: 'MONTHLY' | 'YEARLY' = 'MONTHLY') {
    return apiClient
      .post<ResData<PortOneCheckoutIntent>>('/portone/checkout/subscription', { planType, billingCycle })
      .then(unwrapResponse)
  },

  createCreditCheckout(packageName: string) {
    return apiClient
      .post<ResData<PortOneCheckoutIntent>>('/portone/checkout/credit', { packageName })
      .then(unwrapResponse)
  },

  complete(paymentId: string) {
    return apiClient
      .post<ResData<PortOnePaymentResult>>(`/portone/payments/${encodeURIComponent(paymentId)}/complete`)
      .then(unwrapResponse)
  },
}
