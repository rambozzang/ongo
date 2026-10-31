import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { computed } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import AiView from './AiView.vue'
import { aiApi } from '@/api/ai'
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

describe('AiView 메타 생성 — 내 채널 성과 근거 표시', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(aiApi.getFeatures).mockResolvedValue([{ key: 'META_GENERATION', displayName: '제목/설명 생성', creditCost: 5 }] as never)
    document.body.innerHTML = ''
  })

  afterEach(() => {
    mountedWrapper?.unmount()
    mountedWrapper = null
  })

  const platforms = [{ platform: 'YOUTUBE', titleCandidates: ['제목'], description: '설명', hashtags: ['a'] }]

  it('근거를 썼으면 상위·하위 영상 수를 보여 준다', async () => {
    const wrapper = await renderAiView()
    await openMetaTool(wrapper)
    vi.mocked(aiApi.generateMeta).mockResolvedValue({ platforms, grounding: { used: true, topCount: 5, bottomCount: 3, reason: null } } as never)
    await submitMetaViaForm(wrapper)

    expect(document.querySelector('[data-testid="meta-grounding"]')?.textContent).toContain('5개')
  })

  it('근거를 못 썼으면 서버가 준 이유를 그대로 보여 준다', async () => {
    const wrapper = await renderAiView()
    await openMetaTool(wrapper)
    vi.mocked(aiApi.generateMeta).mockResolvedValue({
      platforms,
      grounding: { used: false, topCount: 0, bottomCount: 0, reason: '최근 90일 조회수가 집계된 영상이 2개라(6개 이상 필요) 내 채널 성과를 참고하지 않았습니다' },
    } as never)
    await submitMetaViaForm(wrapper)

    expect(document.querySelector('[data-testid="meta-grounding"]')?.textContent).toContain('6개 이상 필요')
  })
})
