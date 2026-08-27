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

  /**
   * 발급받은 정기결제 수단을 서버에 등록한다.
   *
   * billingKey 는 **본문으로만** 보낸다. 경로·쿼리에 실리면 접근 로그와 브라우저 기록에
   * 평문으로 남는데, 이 값 하나로 반복 청구가 가능하다. 응답도 비어 있다.
   */
  registerBillingKey(billingKey: string) {
    return apiClient
      .post<ResData<void>>('/portone/billing-key', { billingKey })
      .then(unwrapResponse)
  },

  complete(paymentId: string) {
    return apiClient
      .post<ResData<PortOnePaymentResult>>(`/portone/payments/${encodeURIComponent(paymentId)}/complete`)
      .then(unwrapResponse)
  },
}
