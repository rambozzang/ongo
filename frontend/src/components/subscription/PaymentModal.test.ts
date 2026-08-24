import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import PaymentModal from './PaymentModal.vue'

/** usePortOne 은 별도 파일에서 계약을 검증한다. 여기서는 모달이 그 결과를 어떻게 쓰는지만 본다. */
const portone = vi.hoisted(() => ({ openSubscriptionCheckout: vi.fn() }))

vi.mock('@/composables/usePortOne', async () => {
  const { ref } = await import('vue')
  return {
    usePortOne: () => ({
      loading: ref(false),
      openSubscriptionCheckout: portone.openSubscriptionCheckout,
    }),
  }
})

type Callbacks = { onSuccess?: () => void; onClose?: () => void }

function renderModal() {
  return mount(PaymentModal, {
    props: { modelValue: true, targetPlan: 'STARTER' as const, price: 9900 },
    global: { stubs: { teleport: true, LoadingSpinner: true } },
  })
}

async function pay(wrapper: ReturnType<typeof renderModal>) {
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
})
