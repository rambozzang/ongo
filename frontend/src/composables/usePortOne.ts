import { ref } from 'vue'
import * as PortOne from '@portone/browser-sdk/v2'
import { portoneApi } from '@/api/portone'

const initialized = ref(false)
const loading = ref(false)

/** PortOne은 실패 응답에만 optional `code`를 넣는다. 빈 문자열도 오류 코드로 취급한다. */
export function isPortOnePaymentError(
  result: Awaited<ReturnType<typeof PortOne.requestPayment>>,
): boolean {
  return result != null && result.code !== undefined
}

export function usePortOne() {
  async function ensureInitialized() {
    initialized.value = true
  }

  async function openSubscriptionCheckout(
    planType: string,
    callbacks?: { onSuccess?: () => void; onClose?: () => void },
    billingCycle: 'MONTHLY' | 'YEARLY' = 'MONTHLY',
  ) {
    loading.value = true
    try {
      const intent = await portoneApi.createSubscriptionCheckout(planType, billingCycle)
      const result = await PortOne.requestPayment({
        storeId: intent.storeId,
        channelKey: intent.channelKey,
        paymentId: intent.paymentId,
        orderName: intent.orderName,
        totalAmount: intent.amount,
        currency: intent.currency,
        payMethod: 'CARD',
        customer: { email: intent.customerEmail, fullName: intent.customerName },
      })
      await completeResult(result, callbacks)
    } finally {
      loading.value = false
    }
  }

  async function openCreditCheckout(
    packageName: string,
    callbacks?: { onSuccess?: () => void; onClose?: () => void },
  ) {
    loading.value = true
    try {
      const intent = await portoneApi.createCreditCheckout(packageName)
      const result = await PortOne.requestPayment({
        storeId: intent.storeId,
        channelKey: intent.channelKey,
        paymentId: intent.paymentId,
        orderName: intent.orderName,
        totalAmount: intent.amount,
        currency: intent.currency,
        payMethod: 'CARD',
        customer: { email: intent.customerEmail, fullName: intent.customerName },
      })
      await completeResult(result, callbacks)
    } finally {
      loading.value = false
    }
  }

  async function completeResult(
    result: Awaited<ReturnType<typeof PortOne.requestPayment>>,
    callbacks?: { onSuccess?: () => void; onClose?: () => void },
  ) {
    if (!result) {
      callbacks?.onClose?.()
      return
    }
    if (isPortOnePaymentError(result)) {
      throw new Error(result.message ?? '포트원 결제가 취소되거나 실패했습니다.')
    }
    await portoneApi.complete(result.paymentId)
    callbacks?.onSuccess?.()
  }

  return { initialized, loading, ensureInitialized, openSubscriptionCheckout, openCreditCheckout }
}
