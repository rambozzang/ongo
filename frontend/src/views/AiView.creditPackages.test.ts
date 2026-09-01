import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import AiView from './AiView.vue'
import { aiApi } from '@/api/ai'
import { creditApi } from '@/api/credit'
import { capabilitiesApi } from '@/api/capabilities'
import koMessages from '@/locales/ko/common.json'

const portone = vi.hoisted(() => ({
  openCreditCheckout: vi.fn(),
  loading: { value: false, __v_isRef: true },
}))

/**
 * 크레딧 부족 모달의 가격이 **서버 값인지** 고정한다.
 *
 * 이 모달은 결제 직전에 뜨는 화면이다. 예전에는 `CREDIT_PACKAGES` 상수를 그렸는데, 결제
 * 금액은 서버가 `CreditPackage` enum 에서 계산하므로 서버에서 가격을 바꾼 날 **사용자가 본
 * 금액과 청구액이 갈린다.** 두 숫자가 다르다는 사실은 결제창이 뜨기 전까지 드러나지 않는다.
 */

vi.mock('@/api/credit', () => ({
  creditApi: { getPackages: vi.fn(), getBalance: vi.fn(), getTransactions: vi.fn() },
}))
vi.mock('@/api/ai', () => ({
  aiApi: {
    getFeatures: vi.fn(),
    generateMeta: vi.fn(),
    generateHashtags: vi.fn(),
    generateReport: vi.fn(),
    strategyCoach: vi.fn(),
    revenueReport: vi.fn(),
  },
}))
vi.mock('@/api/capabilities', () => ({
  capabilitiesApi: { list: vi.fn(), clearCache: vi.fn() },
}))
vi.mock('@/composables/usePortOne', () => ({
  usePortOne: () => ({
    loading: portone.loading,
    openCreditCheckout: (...args: unknown[]) => portone.openCreditCheckout(...args),
  }),
}))
vi.mock('@/api/revenue', () => ({ revenueApi: { summary: vi.fn() } }))

vi.mock('@/composables/useCredit', () => ({
  useCredit: () => ({
    balance: computed(() => 1000),
    isLow: computed(() => false),
    usedToday: computed(() => 0),
    checkAndUse: vi.fn(),
    fetchBalance: vi.fn(),
    fetchTransactions: vi.fn(),
    hasEnoughCredits: () => true,
  }),
}))

const SERVER_PACKAGES = [
  { name: 'STARTER', displayName: '스타터 팩', credits: 500, price: 4900, validDays: 30, pricePerCredit: 9.8 },
  { name: 'BUSINESS', displayName: '비즈니스 팩', credits: 10000, price: 49900, validDays: 180, pricePerCredit: 5 },
]

async function renderAiView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/ai', component: { template: '<div />' } },
      { path: '/subscription', component: { template: '<div />' } },
    ],
  })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  await router.push('/ai')
  await router.isReady()
  const wrapper = mount(AiView, {
    global: {
      plugins: [pinia, router, i18n],
      stubs: {
        PageHeader: { template: '<header><slot name="actions" /></header>' },
        PageGuide: true,
        SectionCard: { template: '<section><slot /></section>' },
        AiLoadingOverlay: true,
        AiTypingEffect: true,
      },
    },
  })
  await flushPromises()
  return wrapper
}

/** 모달은 크레딧이 모자랄 때만 열린다. 여는 조건이 아니라 **연 뒤의 표시**를 본다. */
async function openCreditModal(wrapper: Awaited<ReturnType<typeof renderAiView>>) {
  ;(wrapper.vm as unknown as { showCreditModal: boolean }).showCreditModal = true
  await flushPromises()
}

const bodyText = () => document.body.textContent ?? ''

describe('AiView 크레딧 부족 모달', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    portone.loading.value = false
    portone.openCreditCheckout.mockResolvedValue(undefined)
    vi.mocked(aiApi.getFeatures).mockResolvedValue([
      { key: 'META_GENERATION', displayName: '제목/설명 생성', creditCost: 5 },
      { key: 'HASHTAG_RECOMMENDATION', displayName: '해시태그 추천', creditCost: 3 },
      { key: 'PERFORMANCE_REPORT', displayName: '성과 리포트', creditCost: 8 },
      { key: 'STRATEGY_COACH', displayName: 'AI 전략 코치', creditCost: 10 },
      { key: 'REVENUE_REPORT', displayName: '수익 분석 리포트', creditCost: 8 },
    ] as never)
    vi.mocked(capabilitiesApi.list).mockResolvedValue([
      { key: 'payment', enabled: true, reason: null },
    ] as never)
    document.body.innerHTML = ''
  })

  /** **핵심.** 서버가 준 가격만 그린다. */
  it('패키지 가격을 서버 응답으로 그린다', async () => {
    vi.mocked(creditApi.getPackages).mockResolvedValue(SERVER_PACKAGES as never)
    const wrapper = await renderAiView()

    await openCreditModal(wrapper)

    expect(creditApi.getPackages).toHaveBeenCalled()
    expect(bodyText()).toContain('스타터 팩')
    expect(bodyText()).toContain('4,900')
  })

  /** 못 받았으면 그리지 않는다 — 오래된 숫자가 빈 화면보다 나쁘다. */
  it('조회에 실패하면 가격을 그리지 않고 사유를 알린다', async () => {
    vi.mocked(creditApi.getPackages).mockRejectedValue(new Error('서버가 응답하지 않습니다'))
    const wrapper = await renderAiView()

    await openCreditModal(wrapper)

    expect(bodyText()).not.toContain('4,900')
    expect(document.body.querySelector('[data-testid="credit-packages-error"]')).not.toBeNull()
    expect(bodyText()).toContain('서버가 응답하지 않습니다')
  })

  /** 서버가 준 패키지 카드를 선택할 수 있어야 한다. 실제 checkout 식별자는 별도 테스트로 고정한다. */
  it('서버가 준 패키지 카드를 선택한다', async () => {
    vi.mocked(creditApi.getPackages).mockResolvedValue(SERVER_PACKAGES as never)
    const wrapper = await renderAiView()
    await openCreditModal(wrapper)

    const cards = Array.from(document.body.querySelectorAll<HTMLElement>('[role="dialog"] .cursor-pointer'))
    expect(cards.length).toBeGreaterThan(0)
    cards[0].click()
    await flushPromises()

    expect(document.querySelector<HTMLInputElement>('[role="dialog"] input[type="radio"]:checked')?.value)
      .toBe('스타터 팩')
  })

  it('AI 페이지의 충전 CTA가 구독 페이지 이동이 아니라 실제 크레딧 checkout을 연다', async () => {
    vi.mocked(creditApi.getPackages).mockResolvedValue(SERVER_PACKAGES as never)
    const wrapper = await renderAiView()
    await openCreditModal(wrapper)

    const card = document.querySelector<HTMLElement>('[role="dialog"] .cursor-pointer')
    card?.click()
    await wrapper.vm.$nextTick()
    const payButton = Array.from(document.querySelectorAll<HTMLButtonElement>('[role="dialog"] button'))
      .find((button) => button.textContent?.includes('결제하기'))
    payButton?.click()
    await wrapper.vm.$nextTick()

    expect(portone.openCreditCheckout).toHaveBeenCalledWith('STARTER', expect.anything())
  })
})
