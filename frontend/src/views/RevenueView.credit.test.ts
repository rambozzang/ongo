import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import RevenueView from './RevenueView.vue'
import { revenueApi } from '@/api/revenue'
import { useRevenueStore } from '@/stores/revenue'
import { useCreditStore } from '@/stores/credit'
import CreditPurchaseModal from '@/components/subscription/CreditPurchaseModal.vue'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/revenue', () => ({
  revenueApi: {
    summary: vi.fn().mockResolvedValue({
      platformRevenueAvailable: true,
      platformBreakdown: [],
      totalRevenueKrw: 0,
      growthPercent: null,
    }),
    trends: vi.fn().mockResolvedValue({ data: [] }),
    platformRevenue: vi.fn().mockResolvedValue({}),
    cpmRpm: vi.fn().mockResolvedValue({ platforms: [] }),
    brandDealRevenue: vi.fn().mockResolvedValue({ deals: [] }),
    insights: vi.fn().mockResolvedValue({ insights: [] }),
    generateInsight: vi.fn(),
    alertConfigs: vi.fn().mockResolvedValue({ configs: [] }),
    saveAlertConfig: vi.fn(),
    updateAlertConfig: vi.fn(),
    deleteAlertConfig: vi.fn(),
  },
}))

vi.mock('@/api/credit', () => ({
  creditApi: {
    getBalance: vi.fn(),
    getTransactions: vi.fn(),
    getPackages: vi.fn().mockResolvedValue([]),
    list: vi.fn(),
    purchase: vi.fn(),
  },
}))

function creditError(code: string) {
  return { response: { data: { success: false, message: '크레딧이 부족합니다', error: code } } }
}

function render() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
  const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
  const wrapper = mount(RevenueView, {
    global: {
      plugins: [pinia, router, i18n],
    },
  })
  return { wrapper, router }
}

const findGenerateButton = (wrapper: ReturnType<typeof mount>) =>
  wrapper.find('[data-testid="revenue-generate-insight-button"]')

describe('RevenueView 크레딧 차단 CTA (REVENUE_INSIGHT)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('CREDIT_INSUFFICIENT면 차단 블록 + 충전 CTA 노출, 클릭 시 실제 구매 모달 열림', async () => {
    vi.mocked(revenueApi.generateInsight).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    const { wrapper } = render()
    const store = useRevenueStore()
    await flushPromises()

    await findGenerateButton(wrapper).trigger('click')
    await flushPromises()

    expect(store.creditBlocked).toBe(true)
    const cta = wrapper.find('[data-testid="revenue-insight-credit-cta"]')
    expect(cta.exists()).toBe(true)
    expect(cta.text()).toContain('크레딧 충전하기')
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(false)

    await cta.trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(true)
  })

  it('차단 상태에서는 생성 버튼이 disabled되고 일괄 재호출 요청을 막는다', async () => {
    vi.mocked(revenueApi.generateInsight).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    const { wrapper } = render()
    const store = useRevenueStore()
    await flushPromises()

    await findGenerateButton(wrapper).trigger('click')
    await flushPromises()
    expect(store.creditBlocked).toBe(true)
    expect(findGenerateButton(wrapper).attributes('disabled')).toBeDefined()

    const callsAfterBlock = vi.mocked(revenueApi.generateInsight).mock.calls.length
    await findGenerateButton(wrapper).trigger('click')
    await flushPromises()
    expect(vi.mocked(revenueApi.generateInsight).mock.calls.length).toBe(callsAfterBlock)
  })

  it('일반 오류(비 안정 코드)면 CTA 없이 생성 오류 상태에 실제 메시지를 보존한다', async () => {
    vi.mocked(revenueApi.generateInsight).mockRejectedValue({ notAnError: true })

    const { wrapper } = render()
    const store = useRevenueStore()
    await flushPromises()

    await findGenerateButton(wrapper).trigger('click')
    await flushPromises()

    expect(store.creditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="revenue-insight-credit-cta"]').exists()).toBe(false)
    expect(store.generateInsightError).toBeTruthy()
  })

  it('REVENUE_DATA_UNAVAILABLE는 크레딧 CTA를 띄우지 않는다', async () => {
    vi.mocked(revenueApi.generateInsight).mockRejectedValue(creditError('REVENUE_DATA_UNAVAILABLE'))

    const { wrapper } = render()
    const store = useRevenueStore()
    await flushPromises()

    await findGenerateButton(wrapper).trigger('click')
    await flushPromises()

    expect(store.creditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="revenue-insight-credit-cta"]').exists()).toBe(false)
    expect(store.generateInsightError).toBeTruthy()
  })

  it('PLAN_LIMIT_EXCEEDED는 크레딧 CTA를 띄우지 않는다', async () => {
    vi.mocked(revenueApi.generateInsight).mockRejectedValue(creditError('PLAN_LIMIT_EXCEEDED'))

    const { wrapper } = render()
    const store = useRevenueStore()
    await flushPromises()

    await findGenerateButton(wrapper).trigger('click')
    await flushPromises()

    expect(store.creditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="revenue-insight-credit-cta"]').exists()).toBe(false)
    expect(store.generateInsightError).toBeTruthy()
  })

  it('403은 크레딧 CTA를 띄우지 않는다', async () => {
    vi.mocked(revenueApi.generateInsight).mockRejectedValue({
      response: { status: 403, data: { success: false, message: 'forbidden' } },
    })

    const { wrapper } = render()
    const store = useRevenueStore()
    await flushPromises()

    await findGenerateButton(wrapper).trigger('click')
    await flushPromises()

    expect(store.creditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="revenue-insight-credit-cta"]').exists()).toBe(false)
    expect(store.generateInsightError).toBeTruthy()
  })

  it('purchase 뒤 fetchBalance 호출 + 차단 해제, 자동 generateInsight API 재호출 0회', async () => {
    vi.mocked(revenueApi.generateInsight).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    const { wrapper } = render()
    const store = useRevenueStore()
    await flushPromises()

    await findGenerateButton(wrapper).trigger('click')
    await flushPromises()
    expect(store.creditBlocked).toBe(true)

    const creditStore = useCreditStore()
    const fetchBalanceSpy = vi
      .spyOn(creditStore, 'fetchBalance')
      .mockResolvedValue(undefined as never)
    const callsBefore = vi.mocked(revenueApi.generateInsight).mock.calls.length

    // 실제 CTA 클릭 → 모달이 열렸는지 확인 후 purchase 를 emit 한다.
    const cta = wrapper.find('[data-testid="revenue-insight-credit-cta"]')
    expect(cta.exists()).toBe(true)
    await cta.trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(true)

    await wrapper
      .findComponent(CreditPurchaseModal)
      .vm.$emit('purchase', { key: 'STARTER', name: 'Starter', pricePerCredit: 100, validDays: 30 })
    await flushPromises()

    expect(fetchBalanceSpy).toHaveBeenCalled()
    expect(store.creditBlocked).toBe(false)
    // 자동 재실행 없음 — purchase 전후 generateInsight 호출 횟수 동일.
    expect(vi.mocked(revenueApi.generateInsight).mock.calls.length).toBe(callsBefore)
  })
})
