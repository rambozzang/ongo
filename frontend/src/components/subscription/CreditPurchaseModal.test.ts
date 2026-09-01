import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import CreditPurchaseModal from './CreditPurchaseModal.vue'
import { creditApi } from '@/api/credit'
import { capabilitiesApi } from '@/api/capabilities'

/**
 * 결제 완료 표시가 **서버 완료 이후에만**, 그리고 **지연 없이** 일어나는지 고정한다.
 *
 * `usePortOne.completeResult` 는 `POST /portone/payments/{id}/complete` 를 await 한 뒤에야
 * `onSuccess` 를 부른다. 서버는 그 호출 안에서 PG 에 재조회해 검증하고 크레딧을 지급한다.
 * 즉 `onSuccess` 시점의 잔액은 이미 확정이며, 기다릴 대상이 없다.
 *
 * 예전 구현은 여기서 1.5 초를 기다렸다. 그동안 배경 클릭으로 모달을 닫으면 타이머가
 * 나중에 `paymentComplete` 를 되살려, 다음에 열 때 이전 결제의 "충전 완료!" 가 떴다.
 */
/*
 * 패키지 목록·가격은 **서버가 authoritative source 다.** 결제 금액을 서버가
 * `CreditPackage` enum 에서 계산하므로, 화면이 자기 상수를 그리면 본 금액과 청구액이
 * 갈린다. 그래서 이 테스트도 서버 응답을 세워 준다.
 */
vi.mock('@/api/credit', () => ({
  creditApi: { getPackages: vi.fn(), getBalance: vi.fn(), getTransactions: vi.fn() },
}))

vi.mock('@/api/capabilities', () => ({
  capabilitiesApi: { list: vi.fn(), clearCache: vi.fn() },
}))

/** 서버 `GET /credits/packages` 응답 모양 그대로. */
const SERVER_PACKAGES = [
  { name: 'STARTER', displayName: '스타터 팩', credits: 100, price: 5000, validDays: 365, pricePerCredit: 50 },
  { name: 'PRO', displayName: '프로 팩', credits: 500, price: 20000, validDays: 365, pricePerCredit: 40 },
]

/** 테스트가 직접 호출할 수 있도록 콜백을 붙잡아 두는 usePortOne 대역. */
let capturedCallbacks: { onSuccess?: () => void; onClose?: () => void } | undefined
const portone = vi.hoisted(() => ({
  openCreditCheckout: vi.fn(),
  // Vue 템플릿이 실제 Ref처럼 언랩하도록 최소한의 Ref 계약을 유지한다.
  loading: { value: false, __v_isRef: true },
}))

vi.mock('@/composables/usePortOne', () => {
  return {
    usePortOne: () => ({
      loading: portone.loading,
      openCreditCheckout: (...args: unknown[]) => {
        capturedCallbacks = args[1] as { onSuccess?: () => void; onClose?: () => void }
        return portone.openCreditCheckout(...args)
      },
    }),
  }
})

describe('CreditPurchaseModal', () => {

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(creditApi.getPackages).mockResolvedValue(SERVER_PACKAGES as never)
    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'payment', enabled: true, reason: null },
    ] as never)
    capturedCallbacks = undefined
    portone.loading.value = false
    portone.openCreditCheckout.mockReset()
    portone.openCreditCheckout.mockResolvedValue(undefined)
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  /**
   * 모달은 `<Teleport to="body">` 안에 있다. Teleport 를 스텁하면 내용이 아예 렌더되지
   * 않아 `not.toContain` 단언이 전부 공허하게 통과한다. 그래서 스텁하지 않고 실제로
   * body 에 붙은 DOM 을 본다.
   */
  function mountOpen() {
    return mount(CreditPurchaseModal, {
      props: { modelValue: true },
      attachTo: document.body,
      global: { plugins: [createPinia()], stubs: { LoadingSpinner: true } },
    })
  }

  /** 서버 목록이 도착해야 카드가 그려진다. 조회 전에는 살 수 있는 것이 없다. */
  async function mountOpenLoaded() {
    const wrapper = mountOpen()
    await flushPromises()
    return wrapper
  }

  const bodyText = () => document.body.textContent ?? ''

  async function selectFirstPackageAndPay(wrapper: ReturnType<typeof mountOpen>) {
    const radios = document.querySelectorAll<HTMLInputElement>('input[type="radio"]')
    radios[0].dispatchEvent(new Event('change'))
    await wrapper.vm.$nextTick()

    const payButton = Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => b.textContent?.includes('결제하기'))
    payButton?.click()
    await wrapper.vm.$nextTick()
    await Promise.resolve()
    await wrapper.vm.$nextTick()
  }

  it('sends the enum key, not the display name', async () => {
    const wrapper = await mountOpenLoaded()

    await selectFirstPackageAndPay(wrapper)

    // 서버가 준 enum 이름을 그대로 보낸다. 표시명('스타터 팩')을 보내면 가격 조회가 실패한다.
    expect(portone.openCreditCheckout).toHaveBeenCalledWith('STARTER', expect.anything())
  })

  it('결제 capability가 비활성이면 결제 패키지와 PG를 열지 않는다', async () => {
    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'payment', enabled: false, reason: '결제 설정이 없습니다' },
    ] as never)
    await mountOpenLoaded()

    expect(bodyText()).toContain('결제 설정이 없습니다')
    const radios = document.querySelectorAll<HTMLInputElement>('input[type="radio"]')
    expect(radios).toHaveLength(0)
    const payButton = Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => b.textContent?.includes('결제하기'))
    expect(payButton?.disabled).toBe(true)
    payButton?.click()
    expect(portone.openCreditCheckout).not.toHaveBeenCalled()
  })

  /**
   * 완료는 서버 응답 직후 한 틱 안에 반영돼야 한다. 타이머를 되살리면 이 단언이 깨진다.
   */
  it('marks the purchase complete immediately after the server completes it', async () => {
    const wrapper = await mountOpenLoaded()
    await selectFirstPackageAndPay(wrapper)

    capturedCallbacks?.onSuccess?.()
    await wrapper.vm.$nextTick()

    expect(bodyText()).toContain('충전 완료!')
    expect(wrapper.emitted('purchase')?.length).toBe(1)
  })

  /** 서버 완료 전에는 성공을 그리지 않는다. 허위 성공 표시를 막는 단언이다. */
  it('shows no success screen before the server completes', async () => {
    const wrapper = await mountOpenLoaded()

    await selectFirstPackageAndPay(wrapper)

    expect(bodyText()).not.toContain('충전 완료!')
    expect(wrapper.emitted('purchase')).toBeUndefined()
  })

  /**
   * 지연 타이머가 남아 있으면, 모달을 닫은 뒤에 완료 상태가 되살아나 다음 열람에서
   * 이전 결제의 성공 화면이 보인다. 대기 중인 타이머가 없어야 한다.
   */
  it('does not resurrect the success screen after the modal is closed', async () => {
    vi.useFakeTimers()
    const wrapper = await mountOpenLoaded()
    await selectFirstPackageAndPay(wrapper)

    /*
     * 서버 완료가 도착한 **직후** 사용자가 배경을 눌러 닫는다.
     *
     * 지연 타이머가 있으면 이 시점에 예약만 되고, close() 가 상태를 되돌린 뒤 타이머가
     * 나중에 paymentComplete 를 다시 true 로 만든다. onSuccess 를 부르지 않으면 타이머
     * 자체가 걸리지 않아 이 경로를 재현하지 못한다.
     */
    capturedCallbacks?.onSuccess?.()
    await wrapper.vm.$nextTick()

    /*
     * 배경을 눌러 닫는다. prop 만 바꾸면 컴포넌트의 close() 가 돌지 않아 내부 상태가
     * 그대로 남고, 실제 사용자 동작을 흉내내지 못한다. 완료 후에는 배경 클릭으로 닫을 수
     * 있어야 한다.
     */
    const backdrop = document.querySelector<HTMLElement>('.fixed.inset-0.bg-black\\/60')
    backdrop?.click()
    await wrapper.setProps({ modelValue: false })
    // 닫힌 직후 상태가 초기화되고, 예전 구현의 300ms/1500ms 지연도 남지 않아야 한다.
    vi.advanceTimersByTime(5000)
    await wrapper.vm.$nextTick()

    await wrapper.setProps({ modelValue: true })
    await wrapper.vm.$nextTick()

    expect(bodyText()).not.toContain('충전 완료!')
    vi.useRealTimers()
  })

  /** 결제 준비 실패는 그대로 보여야 한다. 실패를 성공으로 덮지 않는다. */
  it('surfaces a checkout failure instead of showing success', async () => {
    portone.openCreditCheckout.mockRejectedValue(new Error('포트원 결제가 취소되었습니다.'))
    const wrapper = await mountOpenLoaded()

    await selectFirstPackageAndPay(wrapper)
    await wrapper.vm.$nextTick()

    expect(bodyText()).toContain('포트원 결제가 취소되었습니다.')
    expect(bodyText()).not.toContain('충전 완료!')
    expect(wrapper.emitted('purchase')).toBeUndefined()
  })

  it('결제 호출 중에는 중복 결제와 닫기를 막는다', async () => {
    portone.openCreditCheckout.mockImplementation(() => new Promise(() => undefined))
    const wrapper = await mountOpenLoaded()

    const radios = document.querySelectorAll<HTMLInputElement>('input[type="radio"]')
    radios[0].dispatchEvent(new Event('change'))
    await wrapper.vm.$nextTick()

    const payButton = Array.from(document.querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => b.textContent?.includes('결제하기'))!
    payButton.click()
    payButton.click()
    await wrapper.vm.$nextTick()

    expect(portone.openCreditCheckout).toHaveBeenCalledTimes(1)
    expect(bodyText()).toContain('결제 처리 중...')
    expect(document.querySelector<HTMLButtonElement>('button[aria-label="모달 닫기"]')?.disabled).toBe(true)

    document.querySelector<HTMLElement>('.fixed.inset-0.bg-black\\/60')?.click()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
  })

  it('부모가 결제 중 모달을 닫으면 늦은 성공 콜백을 무시한다', async () => {
    portone.openCreditCheckout.mockImplementation(() => new Promise(() => undefined))
    const wrapper = await mountOpenLoaded()

    await selectFirstPackageAndPay(wrapper)
    await wrapper.setProps({ modelValue: false })
    capturedCallbacks?.onSuccess?.()

    expect(wrapper.emitted('purchase')).toBeUndefined()
    wrapper.unmount()
  })
})
