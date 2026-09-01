import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import AiView from './AiView.vue'
import { aiApi } from '@/api/ai'
import { useAiStore } from '@/stores/ai'
import koMessages from '@/locales/ko/common.json'

/**
 * 실제 유료 API 호출 이후 서버가 CREDIT_INSUFFICIENT 를 돌려주는 경우(사전 잔액이
 * stale/race 상태) 충전 CTA 로 전환되는지 검증한다. 사전 부족(useCredit.checkAndUse)
 * 모달 흐름은 별도로 보존되는지도 확인한다.
 */

// checkAndUse 결과를 테스트별로 바꿀 수 있게 hoist 한다.
const hoist = vi.hoisted(() => ({ checkAndUseResult: true }))

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

vi.mock('@/api/credit', () => ({
  creditApi: { getPackages: vi.fn(), getBalance: vi.fn(), getTransactions: vi.fn() },
}))

vi.mock('@/api/revenue', () => ({ revenueApi: { summary: vi.fn() } }))

vi.mock('@/composables/useCredit', () => ({
  useCredit: () => ({
    balance: computed(() => 1000),
    isLow: computed(() => false),
    usedToday: computed(() => 0),
    checkAndUse: vi.fn().mockImplementation(() => Promise.resolve(hoist.checkAndUseResult)),
    fetchBalance: vi.fn(),
    fetchTransactions: vi.fn(),
    hasEnoughCredits: () => hoist.checkAndUseResult,
  }),
}))

function creditError(code: string) {
  return { response: { data: { success: false, message: '크레딧이 부족합니다', error: code } } }
}

let mountedWrapper: ReturnType<typeof mount> | null = null

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
  mountedWrapper = wrapper
  await flushPromises()
  return wrapper
}

const openMetaTool = async (wrapper: ReturnType<typeof mount>) => {
  await wrapper.find('[data-testid="ai-tool-meta"]').trigger('click')
  await flushPromises()
}

const submitMetaViaForm = async (wrapper: ReturnType<typeof mount>) => {
  const vm = wrapper.vm as unknown as {
    metaForm: { script: string; platforms: string[]; category: string }
    submitMeta: () => Promise<void>
  }
  vm.metaForm.script = '스크립트'
  vm.metaForm.platforms = ['YOUTUBE']
  vm.metaForm.category = '게임'
  await vm.submitMeta()
  await flushPromises()
}

describe('AiView 실제 API 크레딧 부족 전환 (AI 도구)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    hoist.checkAndUseResult = true
    vi.mocked(aiApi.getFeatures).mockResolvedValue([
      { key: 'META_GENERATION', displayName: '제목/설명 생성', creditCost: 5 },
      { key: 'HASHTAG_RECOMMENDATION', displayName: '해시태그 추천', creditCost: 3 },
      { key: 'PERFORMANCE_REPORT', displayName: '성과 리포트', creditCost: 8 },
      { key: 'STRATEGY_COACH', displayName: 'AI 전략 코치', creditCost: 10 },
      { key: 'REVENUE_REPORT', displayName: '수익 분석 리포트', creditCost: 8 },
    ] as never)
    document.body.innerHTML = ''
  })

  afterEach(() => {
    mountedWrapper?.unmount()
    mountedWrapper = null
    document.body.innerHTML = ''
  })

  it('CREDIT_INSUFFICIENT면 CTA 노출 + 클릭 시 실제 패키지 모달 open, 자동 재호출 0회', async () => {
    vi.mocked(aiApi.generateMeta).mockRejectedValue(creditError('CREDIT_INSUFFICIENT'))

    const wrapper = await renderAiView()
    const aiStore = useAiStore()
    await openMetaTool(wrapper)
    await submitMetaViaForm(wrapper)

    expect(aiStore.creditBlocked).toBe(true)
    const cta = document.body.querySelector('[data-testid="ai-credit-cta"]')
    expect(cta).not.toBeNull()
    expect(cta?.textContent).toContain('크레딧 충전하기')

    const callsBefore = vi.mocked(aiApi.generateMeta).mock.calls.length
    cta?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    // CTA 클릭 → 기존 실제 크레딧 패키지 모달이 열린다.
    expect((wrapper.vm as unknown as { showCreditModal: boolean }).showCreditModal).toBe(true)
    // 자동 API 재호출 없음 — CTA 전후 generateMeta 호출 횟수 동일.
    expect(vi.mocked(aiApi.generateMeta).mock.calls.length).toBe(callsBefore)
  })

  it('일반 오류(비 안정 코드)면 크레딧 CTA 없이 기존 error 를 보여준다', async () => {
    vi.mocked(aiApi.generateMeta).mockRejectedValue(new Error('AI 서버 오류'))

    const wrapper = await renderAiView()
    const aiStore = useAiStore()
    await openMetaTool(wrapper)
    await submitMetaViaForm(wrapper)

    expect(aiStore.creditBlocked).toBe(false)
    expect(document.body.querySelector('[data-testid="ai-credit-cta"]')).toBeNull()
    expect(aiStore.error).toBeTruthy()
  })

  it('PLAN_LIMIT_EXCEEDED는 크레딧 CTA를 띄우지 않는다', async () => {
    vi.mocked(aiApi.generateMeta).mockRejectedValue(creditError('PLAN_LIMIT_EXCEEDED'))

    const wrapper = await renderAiView()
    const aiStore = useAiStore()
    await openMetaTool(wrapper)
    await submitMetaViaForm(wrapper)

    expect(aiStore.creditBlocked).toBe(false)
    expect(document.body.querySelector('[data-testid="ai-credit-cta"]')).toBeNull()
  })

  it('403은 크레딧 CTA를 띄우지 않는다', async () => {
    vi.mocked(aiApi.generateMeta).mockRejectedValue({
      response: { status: 403, data: { success: false, message: 'forbidden' } },
    })

    const wrapper = await renderAiView()
    const aiStore = useAiStore()
    await openMetaTool(wrapper)
    await submitMetaViaForm(wrapper)

    expect(aiStore.creditBlocked).toBe(false)
    expect(document.body.querySelector('[data-testid="ai-credit-cta"]')).toBeNull()
  })

  it('기존 사전 잔액 부족(checkAndUse=false) 모달 흐름이 보존된다', async () => {
    hoist.checkAndUseResult = false

    const wrapper = await renderAiView()
    await openMetaTool(wrapper)
    await submitMetaViaForm(wrapper)

    // 사전 부족이므로 실제 API 는 호출되지 않고 곧바로 모달이 열린다.
    expect(vi.mocked(aiApi.generateMeta).mock.calls.length).toBe(0)
    expect((wrapper.vm as unknown as { showCreditModal: boolean }).showCreditModal).toBe(true)
  })
})
