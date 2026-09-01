import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import PaymentModal from './PaymentModal.vue'

/** usePortOne 은 별도 파일에서 계약을 검증한다. 여기서는 모달이 그 결과를 어떻게 쓰는지만 본다. */
const portone = vi.hoisted(() => ({
  openSubscriptionCheckout: vi.fn(),
  // Vue 템플릿이 실제 Ref처럼 언랩하도록 최소한의 Ref 계약을 유지한다.
  loading: { value: false, __v_isRef: true },
}))

vi.mock('@/composables/usePortOne', async () => {
  return {
    usePortOne: () => ({
      loading: portone.loading,
      openSubscriptionCheckout: portone.openSubscriptionCheckout,
    }),
  }
})

type Callbacks = { onSuccess?: () => void; onClose?: () => void }

/**
 * 플랜 정보는 **부모가 서버 값을 내려준다.** 이 컴포넌트는 상수를 뒤지지 않으므로
 * 테스트도 부모처럼 넘겨 준다 — 서버 응답과 같은 모양이면 충분하다.
 */
const STARTER_PLAN = {
  type: 'STARTER' as const,
  name: 'STARTER',
  price: 9900,
  yearlyPrice: 99000,
  maxPlatforms: 3,
  maxUploadsPerMonth: 30,
  maxScheduleDays: 7,
  analyticsPeriodDays: 30,
  storageMb: 10240,
  commentManagement: true,
  teamMembers: 0,
  freeAiCredits: 100,
  support: '이메일',
}

function renderModal(overrides: Record<string, unknown> = {}) {
  return mount(PaymentModal, {
    props: {
      modelValue: true,
      targetPlan: 'STARTER' as const,
      price: 9900,
      plan: STARTER_PLAN,
      ...overrides,
    },
    global: { stubs: { teleport: true, LoadingSpinner: true } },
  })
}

/** 유료 플랜은 정기결제 동의 없이는 결제 버튼이 잠긴다. */
async function consent(wrapper: ReturnType<typeof renderModal>) {
  const box = wrapper.find('[data-testid="billing-consent"]')
  if (!box.exists()) throw new Error('정기결제 동의 체크박스를 찾지 못했습니다')
  await box.setValue(true)
}

async function pay(wrapper: ReturnType<typeof renderModal>) {
  await consent(wrapper)
  const button = wrapper.findAll('button').find((b) => b.text().includes('결제하기'))
  if (!button) throw new Error('"결제하기" 버튼을 찾지 못했습니다')
  await button.trigger('click')
  await flushPromises()
}

/*
 * `confirm` 은 이 모달이 바깥 세계에 "결제가 끝났다"고 말하는 유일한 신호다.
 * OnboardingView 는 이 신호만 보고 유료 단계를 통과시키므로, 닫힘·실패에서 새어 나가면
 * 결제 없이 온보딩이 완료된다. 그래서 세 경로를 전부 못 박는다.
 */
describe('PaymentModal 결제 완료 신호', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    portone.loading.value = false
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('결제창을 닫으면 confirm 을 보내지 않는다', async () => {
    portone.openSubscriptionCheckout.mockImplementation(
      async (_plan: string, cb: Callbacks) => cb.onClose?.(),
    )

    const wrapper = renderModal()
    await pay(wrapper)
    vi.advanceTimersByTime(5000)
    await flushPromises()

    expect(wrapper.emitted('confirm')).toBeUndefined()
    expect(wrapper.text()).not.toContain('플랜 변경 완료')
  })

  it('결제가 실패하면 confirm 없이 사유를 보여준다', async () => {
    portone.openSubscriptionCheckout.mockRejectedValue(new Error('카드 한도를 초과했습니다'))

    const wrapper = renderModal()
    await pay(wrapper)
    vi.advanceTimersByTime(5000)
    await flushPromises()

    expect(wrapper.emitted('confirm')).toBeUndefined()
    expect(wrapper.text()).toContain('카드 한도를 초과했습니다')
    // 재시도할 수 있어야 한다.
    expect(wrapper.findAll('button').some((b) => b.text().includes('결제하기'))).toBe(true)
  })

  /*
   * 서버 거절 사유는 ResData.message 에 한국어로 온다. 그런데 client.ts 인터셉터는 401 만
   * 가로채고 나머지는 원본 AxiosError 를 그대로 넘기므로, 400 은 영문 전송 문구로만 도착했다.
   * 중복 구독 결제 가드에 막힌 사용자가 원인을 알 수 없던 자리다.
   */
  it('서버가 400 으로 거절하면 응답 본문의 한국어 사유를 보여준다', async () => {
    const rejection = Object.assign(new Error('Request failed with status code 400'), {
      response: {
        status: 400,
        data: {
          success: false,
          message: '이미 Starter 구독을 이용 중입니다. Starter 구독 결제를 새로 만들 수 없습니다.',
          error: 'SUBSCRIPTION_ALREADY_ACTIVE',
        },
      },
    })
    portone.openSubscriptionCheckout.mockRejectedValue(rejection)

    const wrapper = renderModal()
    await pay(wrapper)

    expect(wrapper.text()).toContain('이미 Starter 구독을 이용 중입니다')
    expect(wrapper.text()).not.toContain('Request failed with status code')
    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  it('응답 본문에 사유가 없으면 영문 전송 문구 대신 안내 문구를 보여준다', async () => {
    const rejection = Object.assign(new Error('Request failed with status code 500'), {
      response: { status: 500, data: { success: false } },
    })
    portone.openSubscriptionCheckout.mockRejectedValue(rejection)

    const wrapper = renderModal()
    await pay(wrapper)

    expect(wrapper.text()).not.toContain('Request failed with status code')
    expect(wrapper.text()).toContain('결제 준비에 실패했습니다')
  })

  it('사유를 알 수 없는 실패에도 안내 문구를 보여주고 confirm 을 보내지 않는다', async () => {
    portone.openSubscriptionCheckout.mockRejectedValue({})

    const wrapper = renderModal()
    await pay(wrapper)
    await flushPromises()

    expect(wrapper.emitted('confirm')).toBeUndefined()
    expect(wrapper.text()).toContain('결제 준비에 실패했습니다')
  })

  it('서버 검증에 성공해도 웹훅 동기화를 기다리는 동안은 confirm 을 보내지 않는다', async () => {
    portone.openSubscriptionCheckout.mockImplementation(
      async (_plan: string, cb: Callbacks) => cb.onSuccess?.(),
    )

    const wrapper = renderModal()
    await pay(wrapper)

    // onSuccess 는 이미 불렸지만 대기 중이다.
    vi.advanceTimersByTime(1400)
    await flushPromises()
    expect(wrapper.emitted('confirm')).toBeUndefined()
    expect(wrapper.text()).toContain('결제 처리 중')
  })

  it('결제 처리 중 배경을 눌러도 모달을 닫거나 성공 신호를 잃지 않는다', async () => {
    portone.openSubscriptionCheckout.mockImplementation(
      async (_plan: string, cb: Callbacks) => cb.onSuccess?.(),
    )

    const wrapper = renderModal()
    await pay(wrapper)
    await wrapper.find('.fixed.inset-0[aria-hidden="true"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    vi.advanceTimersByTime(1500)
    await flushPromises()
    expect(wrapper.emitted('confirm')).toHaveLength(1)
  })

  it('PortOne 호출 중에는 닫기 버튼과 Escape로 결제 흐름을 끊지 않는다', async () => {
    portone.loading.value = true
    const wrapper = renderModal()

    await wrapper.find('button[aria-label="모달 닫기"]').trigger('click')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.find('.fixed.inset-0[aria-hidden="true"]').trigger('click')

    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.find('button[aria-label="모달 닫기"]').attributes('disabled')).toBeDefined()
  })

  it('부모가 처리 중 모달을 제거하면 지연 완료 타이머가 성공 신호를 보내지 않는다', async () => {
    portone.openSubscriptionCheckout.mockImplementation(
      async (_plan: string, cb: Callbacks) => cb.onSuccess?.(),
    )

    const wrapper = renderModal()
    await pay(wrapper)
    await wrapper.setProps({ modelValue: false })
    vi.advanceTimersByTime(5000)
    await flushPromises()

    expect(wrapper.emitted('confirm')).toBeUndefined()
  })

  it('동기화 대기가 끝나면 confirm 을 정확히 한 번 보낸다', async () => {
    portone.openSubscriptionCheckout.mockImplementation(
      async (_plan: string, cb: Callbacks) => cb.onSuccess?.(),
    )

    const wrapper = renderModal()
    await pay(wrapper)
    vi.advanceTimersByTime(1500)
    await flushPromises()

    expect(wrapper.emitted('confirm')).toHaveLength(1)
    expect(wrapper.text()).toContain('플랜 변경 완료')
  })

  /*
   * 결제 성공 처리는 부모가 v-model 을 직접 내린다(온보딩·구독 화면). 그 경로에서는 close() 가
   * 호출되지 않아 완료 화면이 인스턴스에 그대로 남았고, 다음에 열면 결제 버튼이 없어
   * 정상적인 상위 플랜 업그레이드가 막혔다.
   */
  it('부모가 닫았다 다시 열면 직전 결제 화면이 남지 않는다', async () => {
    portone.openSubscriptionCheckout.mockImplementation(
      async (_plan: string, cb: Callbacks) => cb.onSuccess?.(),
    )

    const wrapper = renderModal()
    await pay(wrapper)
    vi.advanceTimersByTime(1500)
    await flushPromises()
    expect(wrapper.text()).toContain('플랜 변경 완료')

    // close() 를 거치지 않고 부모가 그대로 내렸다가 다시 올리는 경로.
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true, targetPlan: 'PRO', price: 19900 })

    expect(wrapper.text()).not.toContain('플랜 변경 완료')
    expect(wrapper.findAll('button').some((b) => b.text().includes('결제하기'))).toBe(true)
  })

  it('다시 열면 직전 실패 사유도 남지 않는다', async () => {
    portone.openSubscriptionCheckout.mockRejectedValue(new Error('카드 한도를 초과했습니다'))

    const wrapper = renderModal()
    await pay(wrapper)
    expect(wrapper.text()).toContain('카드 한도를 초과했습니다')

    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true })

    expect(wrapper.text()).not.toContain('카드 한도를 초과했습니다')
  })

  it('대화상자에 제목과 설명이 연결되어 있다', () => {
    const wrapper = renderModal()
    const dialog = wrapper.find('[role="dialog"]')
    const panel = wrapper.find('[role="dialog"] > div[aria-describedby]')

    expect(dialog.attributes('aria-labelledby')).toBeTruthy()
    expect(panel.attributes('aria-describedby')).toBeTruthy()
    expect(wrapper.find(`#${panel.attributes('aria-describedby')}`).exists()).toBe(true)
  })

  it('결제 주기를 지정하지 않으면 월간으로 결제한다', async () => {
    // 모달이 보여주는 가격은 월 요금이다. 기본값이 어긋나면 표시가와 청구액이 달라진다.
    portone.openSubscriptionCheckout.mockImplementation(
      async (_plan: string, cb: Callbacks) => cb.onSuccess?.(),
    )

    const wrapper = renderModal()
    await pay(wrapper)

    const [plan, , billingCycle] = portone.openSubscriptionCheckout.mock.calls[0]
    expect(plan).toBe('STARTER')
    expect(billingCycle).toBe('MONTHLY')
  })

  it('연간 결제는 연간 금액과 연간 단위를 표시한다', () => {
    const wrapper = renderModal({ billingCycle: 'YEARLY', price: 99000 })

    expect(wrapper.text()).toContain('₩99,000')
    expect(wrapper.text()).toContain('/년')
    expect(wrapper.text()).not.toContain('월 결제 금액')
  })
})

/*
 * 결제 진행 시 카드 등록 창이 한 번 더 뜬다. 예고 없이 창이 두 번 열리면 사용자는 결제가
 * 두 번 되는 줄 알고, 자동 청구에 동의한 적도 없게 된다.
 */
describe('PaymentModal 정기결제 동의', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    portone.loading.value = false
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('유료 플랜은 동의 전에 결제 버튼이 잠겨 있다', () => {
    const wrapper = renderModal()

    const button = wrapper.findAll('button').find((b) => b.text().includes('결제하기'))!
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('동의하면 결제 버튼이 풀린다', async () => {
    const wrapper = renderModal()
    await consent(wrapper)

    const button = wrapper.findAll('button').find((b) => b.text().includes('결제하기'))!
    expect(button.attributes('disabled')).toBeUndefined()
  })

  it('동의 없이는 결제를 시작하지 않는다', async () => {
    const wrapper = renderModal()

    const button = wrapper.findAll('button').find((b) => b.text().includes('결제하기'))!
    await button.trigger('click')
    await flushPromises()

    expect(portone.openSubscriptionCheckout).not.toHaveBeenCalled()
  })

  /** 자동 청구가 일어난다는 사실과 해지 방법이 문구에 있어야 한다. */
  it('동의 문구가 자동 청구와 해지 방법을 알린다', () => {
    const text = renderModal().text()

    expect(text).toContain('자동으로 결제')
    expect(text).toContain('카드 등록 창')
    expect(text).toContain('해지')
  })

  /** 무료 플랜은 결제 자체가 없다. 동의를 요구하면 기존 흐름이 막힌다. */
  it('무료 플랜에는 동의 체크박스를 띄우지 않는다', () => {
    const wrapper = mount(PaymentModal, {
      props: {
        modelValue: true,
        targetPlan: 'FREE' as const,
        price: 0,
        plan: { ...STARTER_PLAN, type: 'FREE' as const, name: 'FREE', price: 0, yearlyPrice: 0 },
      },
      global: { stubs: { teleport: true, LoadingSpinner: true } },
    })

    expect(wrapper.find('[data-testid="billing-consent"]').exists()).toBe(false)
  })
})
