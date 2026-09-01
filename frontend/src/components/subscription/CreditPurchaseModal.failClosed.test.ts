import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import CreditPurchaseModal from './CreditPurchaseModal.vue'
import { creditApi } from '@/api/credit'
import { capabilitiesApi } from '@/api/capabilities'

/**
 * 패키지 목록을 못 받았을 때 **살 수 있는 것처럼 보이지 않는지** 고정한다.
 *
 * 예전에는 화면이 `CREDIT_PACKAGES` 상수를 그렸다. 서버에서 가격을 바꾸면 사용자는 옛 가격을
 * 보고 결제하고, 청구는 서버 값으로 된다 — 결제창이 뜨기 전까지 어디에도 드러나지 않는 차이다.
 *
 * 그래서 목록은 서버 응답만 쓴다. 못 받았으면 **아무것도 그리지 않고 그 사실을 말한다.**
 * 오래된 숫자를 대신 보여 주는 것이 빈 화면보다 나쁘다.
 */

const openCreditCheckout = vi.fn()

vi.mock('@/api/credit', () => ({
  creditApi: { getPackages: vi.fn(), getBalance: vi.fn(), getTransactions: vi.fn() },
}))

vi.mock('@/api/capabilities', () => ({
  capabilitiesApi: { list: vi.fn(), clearCache: vi.fn() },
}))

vi.mock('@/composables/usePortOne', async () => {
  const { ref: vueRef } = await import('vue')
  return {
    usePortOne: () => ({ loading: vueRef(false), openCreditCheckout }),
  }
})

const SERVER_PACKAGES = [
  { name: 'STARTER', displayName: '스타터 팩', credits: 500, price: 4900, validDays: 30, pricePerCredit: 9.8 },
]

describe('크레딧 구매 모달 — 조회 실패', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'payment', enabled: true, reason: null },
    ] as never)
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  /** 모달은 `<Teleport to="body">` 안에 있다. 스텁하면 단언이 공허하게 통과한다. */
  function mountOpen() {
    return mount(CreditPurchaseModal, {
      props: { modelValue: true },
      attachTo: document.body,
      global: { plugins: [createPinia()], stubs: { LoadingSpinner: true } },
    })
  }

  const bodyText = () => document.body.textContent ?? ''

  /** **핵심.** 실패 시 상수 가격이 다시 나타나면 사용자가 본 금액과 청구액이 갈린다. */
  it('조회에 실패하면 어떤 가격도 그리지 않는다', async () => {
    vi.mocked(creditApi.getPackages).mockRejectedValue(new Error('서버가 응답하지 않습니다'))

    mountOpen()
    await flushPromises()

    expect(document.querySelectorAll('input[type="radio"]')).toHaveLength(0)
    // 예전 상수의 값들. 하나라도 보이면 오래된 숫자를 그리고 있다는 뜻이다.
    expect(bodyText()).not.toContain('4,900')
    expect(bodyText()).not.toContain('스타터 팩')
  })

  /** 빈 화면만 두면 사용자는 판매 중단으로 읽는다. 이유를 말해 준다. */
  it('조회 실패 사유와 재시도 수단을 보여준다', async () => {
    vi.mocked(creditApi.getPackages).mockRejectedValue(new Error('서버가 응답하지 않습니다'))

    mountOpen()
    await flushPromises()

    expect(bodyText()).toContain('서버가 응답하지 않습니다')
    const retry = Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => b.textContent?.includes('다시 시도'))
    expect(retry).toBeDefined()

    vi.mocked(creditApi.getPackages).mockResolvedValue(SERVER_PACKAGES as never)
    retry!.click()
    await flushPromises()

    expect(bodyText()).toContain('스타터 팩')
  })

  /** 못 고르면 결제도 시작되지 않아야 한다 — 서버가 검증할 식별자 자체가 없다. */
  it('조회에 실패하면 결제를 시작하지 않는다', async () => {
    vi.mocked(creditApi.getPackages).mockRejectedValue(new Error('서버가 응답하지 않습니다'))

    mountOpen()
    await flushPromises()

    const pay = Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => b.textContent?.includes('결제하기'))
    pay?.click()
    await flushPromises()

    expect(openCreditCheckout).not.toHaveBeenCalled()
  })

  /**
   * 열린 채로 마운트되는 경로도 조회한다. watch 만 두면 값이 바뀔 때만 돌아, 이 경로에서
   * 목록이 영영 비어 있다.
   */
  it('열린 채로 마운트돼도 목록을 조회한다', async () => {
    vi.mocked(creditApi.getPackages).mockResolvedValue(SERVER_PACKAGES as never)

    mountOpen()
    await flushPromises()

    expect(creditApi.getPackages).toHaveBeenCalled()
    expect(bodyText()).toContain('스타터 팩')
  })

  /** 다시 열 때마다 확인한다 — 세션 중 가격이 바뀌어도 옛 값을 들고 있지 않게 한다. */
  it('다시 열면 목록을 다시 조회한다', async () => {
    vi.mocked(creditApi.getPackages).mockResolvedValue(SERVER_PACKAGES as never)
    const wrapper = mountOpen()
    await flushPromises()

    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()

    expect(vi.mocked(creditApi.getPackages).mock.calls.length).toBeGreaterThan(1)
  })
})
