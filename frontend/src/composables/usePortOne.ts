import { ref } from 'vue'
import * as PortOne from '@portone/browser-sdk/v2'
import { portoneApi, type PortOneCheckoutIntent } from '@/api/portone'

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

  /**
   * 정기결제 수단을 발급받아 서버에 등록한다.
   *
   * ## 왜 결제보다 먼저인가
   *
   * 등록에 실패한 채로 결제만 받으면 첫 달 요금은 걷히지만 다음 달 갱신이 불가능해진다.
   * 고객은 결제한 줄 알고, 한 달 뒤 아무 안내 없이 PAST_DUE 로 내려간다. 그래서 수단
   * 등록이 성립하지 않으면 **결제창을 아예 열지 않는다.**
   *
   * ## billingKey 를 어떻게 다루는가
   *
   * 이 함수 안에서만 존재한다. localStorage·store·URL·로그 어디에도 남기지 않고,
   * 곧바로 HTTPS POST 본문으로 서버에 넘긴 뒤 버린다.
   *
   * @returns 등록에 성공하면 true. 사용자가 창을 닫았으면 false.
   * @throws 발급 실패·서버 저장 실패. 호출자가 결제를 중단해야 한다.
   */
  async function registerBillingMethod(intent: PortOneCheckoutIntent): Promise<boolean> {
    const issued = await PortOne.requestIssueBillingKey({
      storeId: intent.storeId,
      channelKey: intent.channelKey,
      billingKeyMethod: 'CARD',
      issueName: intent.orderName,
      customer: { email: intent.customerEmail, fullName: intent.customerName },
    })

    // undefined = 사용자가 발급 창을 닫았다. 오류가 아니므로 조용히 중단한다.
    if (!issued) return false

    if (issued.code !== undefined) {
      throw new Error(issued.message ?? '정기결제 수단 등록이 취소되었거나 실패했습니다.')
    }

    /*
     * code 는 없는데 billingKey 가 비어 있는 응답. SDK 타입상 일어나지 않아야 하지만
     * 실제로 오면 빈 문자열을 서버에 보내게 되고, 서버는 그걸 거절한다 — 그 왕복을
     * 하는 동안 사용자는 카드 등록이 된 줄 안다. 더 나쁘게는 서버가 통과시키면 청구
     * 불가능한 구독이 만들어진다. 여기서 끊는다.
     */
    if (!issued.billingKey) {
      throw new Error('정기결제 수단 정보를 받지 못했습니다. 다시 시도해 주세요.')
    }

    await portoneApi.registerBillingKey(issued.billingKey)
    return true
  }

  async function openSubscriptionCheckout(
    planType: string,
    callbacks?: { onSuccess?: () => void; onClose?: () => void },
    billingCycle: 'MONTHLY' | 'YEARLY' = 'MONTHLY',
  ) {
    loading.value = true
    try {
      const intent = await portoneApi.createSubscriptionCheckout(planType, billingCycle)

      /*
       * 정기결제 수단 등록이 먼저다. 발급 취소·실패·서버 저장 실패면 여기서 끝나고
       * requestPayment 는 호출되지 않는다 — 갱신 불가능한 구독을 만들지 않기 위해서다.
       */
      const registered = await registerBillingMethod(intent)
      if (!registered) {
        callbacks?.onClose?.()
        return
      }

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
