import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createPinia, setActivePinia } from 'pinia'
import ContentGapCard from '@/components/dashboard/ContentGapCard.vue'
import { aiApi } from '@/api/ai'
import { useCreditStore } from '@/stores/credit'
import CreditPurchaseModal from '@/components/subscription/CreditPurchaseModal.vue'
import koMessages from '@/locales/ko/common.json'

vi.mock('@/api/ai', () => ({
  aiApi: { contentGapAnalysis: vi.fn(), getFeatures: vi.fn() },
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

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })

function creditError(code: string) {
  return { response: { data: { success: false, message: '크레딧이 부족합니다', error: code } } }
}

interface ContentGapVm {
  creditBlocked: boolean
  error: string
  result: unknown
}

describe('ContentGapCard 크레딧 차단 CTA (CONTENT_GAP_ANALYSIS)', () => {
  let pinia: ReturnType<typeof createPinia>
  let wrapper: ReturnType<typeof mount> | null = null

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    vi.clearAllMocks()
    vi.mocked(aiApi.getFeatures).mockResolvedValue([
      { key: 'CONTENT_GAP_ANALYSIS', displayName: '콘텐츠 갭 분석', creditCost: 10 },
    ])
  })

  afterEach(() => {
    wrapper?.unmount()
    document.body.innerHTML = ''
  })

  function mountCard() {
    return mount(ContentGapCard, {
      global: { plugins: [pinia, i18n] },
    })
  }

  function startButton() {
    return wrapper!.findAll('button').find((b) => b.text().includes('분석 시작'))!
  }

  async function readyToAnalyze() {
    await flushPromises()
    expect(startButton().attributes('disabled')).toBeUndefined()
  }

  it('CREDIT_INSUFFICIENT면 충전 CTA가 노출되고 클릭하면 실제 구매 모달이 열린다', async () => {
    vi.mocked(aiApi.contentGapAnalysis).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    wrapper = mountCard()
    const vm = wrapper.vm as unknown as ContentGapVm
    await readyToAnalyze()
    await startButton().trigger('click')
    await flushPromises()

    // 안정 코드로만 막힘 상태가 켜지고, AsyncState error 중복은 피한다(에러 비움).
    expect(vm.creditBlocked).toBe(true)
    expect(vm.error).toBe('')
    const cta = wrapper.find('[data-testid="contentgap-credit-cta"]')
    expect(cta.exists()).toBe(true)
    expect(cta.text()).toContain('크레딧 충전하기')
    // 아직 구매 모달은 닫혀 있다.
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(false)

    await cta.trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(true)
  })

  it('일반 오류는 CTA 없이 기존 error+AsyncState retry를 유지한다', async () => {
    vi.mocked(aiApi.contentGapAnalysis).mockRejectedValue(new Error('network fail'))

    wrapper = mountCard()
    const vm = wrapper.vm as unknown as ContentGapVm
    await readyToAnalyze()
    await startButton().trigger('click')
    await flushPromises()

    expect(vm.creditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="contentgap-credit-cta"]').exists()).toBe(false)
    // AsyncState 에러 슬롯에 기존 메시지가 노출된다.
    expect(wrapper.text()).toContain('network fail')
  })

  it('정상 분석은 결과를 표시하고 CTA가 없다', async () => {
    vi.mocked(aiApi.contentGapAnalysis).mockResolvedValue({
      opportunities: [
        {
          topic: '주제A',
          estimatedDemand: 'HIGH',
          competitionLevel: 'LOW',
          suggestedAngle: '앵글',
          relevanceScore: 1,
        },
      ],
      oversaturated: [],
    } as never)

    wrapper = mountCard()
    const vm = wrapper.vm as unknown as ContentGapVm
    await readyToAnalyze()
    await startButton().trigger('click')
    await flushPromises()

    expect(vm.creditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="contentgap-credit-cta"]').exists()).toBe(false)
    expect(vm.result).not.toBeNull()
    expect(wrapper.text()).toContain('주제A')
  })

  it('purchase 이벤트 뒤 fetchBalance 호출 + 상태 정리(자동 재호출 없음)', async () => {
    vi.mocked(aiApi.contentGapAnalysis).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    wrapper = mountCard()
    const vm = wrapper.vm as unknown as ContentGapVm
    const creditStore = useCreditStore()
    const fetchBalanceSpy = vi.spyOn(creditStore, 'fetchBalance').mockResolvedValue(undefined as never)

    await readyToAnalyze()
    await startButton().trigger('click')
    await flushPromises()
    expect(vm.creditBlocked).toBe(true)
    const callsBefore = vi.mocked(aiApi.contentGapAnalysis).mock.calls.length

    // 실제 사용자 경로: CTA 클릭으로 충전 모달이 열린다.
    const cta = wrapper.find('[data-testid="contentgap-credit-cta"]')
    await cta.trigger('click')
    await flushPromises()
    expect(wrapper.findComponent(CreditPurchaseModal).props('modelValue')).toBe(true)

    await wrapper.findComponent(CreditPurchaseModal).vm.$emit('purchase', {
      key: 'STARTER',
      name: 'Starter',
      pricePerCredit: 100,
      validDays: 30,
    })
    await flushPromises()

    expect(fetchBalanceSpy).toHaveBeenCalled()
    expect(vm.creditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="contentgap-credit-cta"]').exists()).toBe(false)
    // 자동 재실행 없음 — purchase 전후 analyze 호출 횟수 동일.
    expect(vi.mocked(aiApi.contentGapAnalysis).mock.calls.length).toBe(callsBefore)
  })

  it('PLAN_LIMIT_EXCEEDED는 크레딧 CTA를 띄우지 않는다', async () => {
    vi.mocked(aiApi.contentGapAnalysis).mockRejectedValue(creditError('PLAN_LIMIT_EXCEEDED'))

    wrapper = mountCard()
    const vm = wrapper.vm as unknown as ContentGapVm
    await readyToAnalyze()
    await startButton().trigger('click')
    await flushPromises()

    expect(vm.creditBlocked).toBe(false)
    expect(wrapper.find('[data-testid="contentgap-credit-cta"]').exists()).toBe(false)
  })

  it('비용 조회에 실패하면 분석 요청을 열지 않고 다시 확인을 안내한다', async () => {
    vi.mocked(aiApi.getFeatures).mockRejectedValue(new Error('pricing unavailable'))

    wrapper = mountCard()
    await flushPromises()

    const button = wrapper.findAll('button').find((b) => b.text().includes('비용 다시 확인'))!
    expect(button.attributes('disabled')).toBeUndefined()
    expect(vi.mocked(aiApi.contentGapAnalysis)).not.toHaveBeenCalled()

    vi.mocked(aiApi.getFeatures).mockResolvedValue([
      { key: 'CONTENT_GAP_ANALYSIS', displayName: '콘텐츠 갭 분석', creditCost: 10 },
    ])
    await button.trigger('click')
    await flushPromises()
    expect(button.attributes('disabled')).toBeUndefined()
    expect(wrapper.text()).toContain('10 크레딧')
  })
})
