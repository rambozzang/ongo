import { ref } from 'vue'
import { paddleApi } from '@/api/paddle'

const initialized = ref(false)
const loading = ref(false)
let cachedConfig: { clientToken: string; environment: string; paddleCustomerId: string | null } | null = null

export function usePaddle() {
  async function waitForPaddle(timeoutMs = 5000): Promise<boolean> {
    if (window.Paddle) return true
    return new Promise((resolve) => {
      const start = Date.now()
      const check = () => {
        if (window.Paddle) return resolve(true)
        if (Date.now() - start > timeoutMs) return resolve(false)
        setTimeout(check, 100)
      }
      check()
    })
  }

  async function ensureInitialized() {
    if (initialized.value) return
    const loaded = await waitForPaddle()
    if (!loaded) {
      console.warn('Paddle.js 로드 타임아웃')
      return
    }

    try {
      cachedConfig = await paddleApi.getConfig()
      window.Paddle!.Initialize({
        token: cachedConfig.clientToken,
        environment: cachedConfig.environment as 'sandbox' | 'production',
      })
      initialized.value = true
    } catch (e) {
      console.error('Paddle 초기화 실패:', e)
    }
  }

  async function openSubscriptionCheckout(
    planType: string,
    callbacks?: {
      onSuccess?: () => void
      onClose?: () => void
    },
  ) {
    if (!window.Paddle) throw new Error('Paddle.js가 로드되지 않았습니다')

    loading.value = true
    try {
      await ensureInitialized()
      const data = await paddleApi.createSubscriptionCheckout(planType)

      // Re-initialize with event callback for this checkout
      window.Paddle.Initialize({
        token: cachedConfig!.clientToken,
        environment: cachedConfig!.environment as 'sandbox' | 'production',
        eventCallback: (event) => {
          if (event.name === 'checkout.completed') callbacks?.onSuccess?.()
          if (event.name === 'checkout.closed') callbacks?.onClose?.()
        },
      })

      window.Paddle.Checkout.open({
        items: [{ priceId: data.priceId, quantity: 1 }],
        customer: data.paddleCustomerId
          ? { id: data.paddleCustomerId }
          : { email: data.customerEmail },
        customData: data.customData,
        settings: {
          displayMode: 'overlay',
          theme: document.documentElement.classList.contains('dark') ? 'dark' : 'light',
          locale: 'ko',
        },
      })
    } catch (e) {
      console.error('체크아웃 열기 실패:', e)
      throw e
    } finally {
      loading.value = false
    }
  }

  async function openCreditCheckout(
    packageName: string,
    callbacks?: {
      onSuccess?: () => void
      onClose?: () => void
    },
  ) {
    if (!window.Paddle) throw new Error('Paddle.js가 로드되지 않았습니다')

    loading.value = true
    try {
      await ensureInitialized()
      const data = await paddleApi.createCreditCheckout(packageName)

      window.Paddle.Initialize({
        token: cachedConfig!.clientToken,
        environment: cachedConfig!.environment as 'sandbox' | 'production',
        eventCallback: (event) => {
          if (event.name === 'checkout.completed') callbacks?.onSuccess?.()
          if (event.name === 'checkout.closed') callbacks?.onClose?.()
        },
      })

      window.Paddle.Checkout.open({
        items: [{ priceId: data.priceId, quantity: 1 }],
        customer: data.paddleCustomerId
          ? { id: data.paddleCustomerId }
          : { email: data.customerEmail },
        customData: data.customData,
        settings: {
          displayMode: 'overlay',
          theme: document.documentElement.classList.contains('dark') ? 'dark' : 'light',
          locale: 'ko',
        },
      })
    } catch (e) {
      console.error('크레딧 체크아웃 열기 실패:', e)
      throw e
    } finally {
      loading.value = false
    }
  }

  return {
    initialized,
    loading,
    ensureInitialized,
    openSubscriptionCheckout,
    openCreditCheckout,
  }
}
