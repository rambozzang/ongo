import { beforeEach, describe, expect, it, vi } from 'vitest'
import { computed } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import AiView from './AiView.vue'
import { aiApi } from '@/api/ai'
import { revenueApi } from '@/api/revenue'
import koMessages from '@/locales/ko/common.json'

/**
 * 수익 분석 리포트는 서버가 광고 수익을 수집하지 않는 동안 항상
 * `REVENUE_DATA_UNAVAILABLE` 로 거절된다. 크레딧은 빠지지 않지만, 성공할 수 있는 것처럼
 * 도구 목록에 띄워두면 사용자는 눌러보고 원인 모를 오류만 본다.
 */
vi.mock('@/api/revenue', () => ({
  revenueApi: { summary: vi.fn() },
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

async function renderAiView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/ai', component: { template: '<div />' } }],
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

const revenueCard = (wrapper: Awaited<ReturnType<typeof renderAiView>>) =>
  wrapper.get('[data-testid="ai-tool-revenue-report"]')

const otherCard = (wrapper: Awaited<ReturnType<typeof renderAiView>>) =>
  wrapper.get('[data-testid="ai-tool-report"]')

const modalOpen = (wrapper: Awaited<ReturnType<typeof renderAiView>>) =>
  document.body.querySelector('[role="dialog"]') !== null || wrapper.find('[role="dialog"]').exists()

describe('AiView 수익 분석 리포트 도구', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(aiApi.getFeatures).mockResolvedValue([
      { key: 'META_GENERATION', displayName: '제목/설명 생성', creditCost: 5 },
      { key: 'HASHTAG_RECOMMENDATION', displayName: '해시태그 추천', creditCost: 3 },
      { key: 'PERFORMANCE_REPORT', displayName: '성과 리포트', creditCost: 8 },
      { key: 'STRATEGY_COACH', displayName: 'AI 전략 코치', creditCost: 10 },
      { key: 'REVENUE_REPORT', displayName: '수익 분석 리포트', creditCost: 8 },
    ] as never)
    document.body.innerHTML = ''
  })

  it('플랫폼 광고 수익 미수집이면 미지원으로 표시하고 열지 않는다', async () => {
    vi.mocked(revenueApi.summary).mockResolvedValue({
      totalRevenue: 0,
      totalRevenueKrw: 0,
      growthPercent: 0,
      platformBreakdown: [],
      platformRevenueAvailable: false,
      platformRevenueUnavailableReason: '현재 플랫폼 분석 연동에서는 광고 수익을 자동 수집하지 않습니다.',
    } as never)

    const wrapper = await renderAiView()
    const card = revenueCard(wrapper)

    expect(card.attributes('aria-disabled')).toBe('true')
    // 서버가 내려준 사유를 그대로 보여준다. 화면이 지어내지 않는다.
    expect(card.text()).toContain('현재 플랫폼 분석 연동에서는 광고 수익을 자동 수집하지 않습니다.')
    expect(card.text()).toContain('지원 예정')
    expect(card.get('button').attributes('disabled')).toBeDefined()

    // UI 만 막지 않는다. 카드 전체 클릭도 모달을 열면 안 된다.
    await card.trigger('click')
    await flushPromises()
    expect(modalOpen(wrapper)).toBe(false)
  })

  it('수익 수집이 가능해지면 다시 열린다', async () => {
    vi.mocked(revenueApi.summary).mockResolvedValue({
      totalRevenue: 5_000_000,
      totalRevenueKrw: 5,
      growthPercent: 0,
      platformBreakdown: [],
      platformRevenueAvailable: true,
      platformRevenueUnavailableReason: null,
    } as never)

    const wrapper = await renderAiView()
    const card = revenueCard(wrapper)

    expect(card.attributes('aria-disabled')).toBeUndefined()
    expect(card.get('button').attributes('disabled')).toBeUndefined()
    expect(card.text()).not.toContain('지원 예정')

    await card.trigger('click')
    await flushPromises()
    expect(modalOpen(wrapper)).toBe(true)
  })

  /** 가용성을 모르는 채로 열어봤자 서버가 거절한다. 모르면 막는다. */
  it('가용성 조회가 실패하면 미지원으로 본다', async () => {
    vi.mocked(revenueApi.summary).mockRejectedValue(new Error('network'))

    const wrapper = await renderAiView()
    const card = revenueCard(wrapper)

    expect(card.attributes('aria-disabled')).toBe('true')
    // 서버 사유가 없으면 화면의 기본 문구로 대체한다.
    expect(card.text()).toContain('광고 수익을 자동 수집하지 않아')
  })

  /** 수익과 무관한 유료 AI 도구까지 같이 막으면 안 된다. */
  it('수익과 무관한 도구는 영향을 받지 않는다', async () => {
    vi.mocked(revenueApi.summary).mockRejectedValue(new Error('network'))

    const wrapper = await renderAiView()
    const card = otherCard(wrapper)

    expect(card.attributes('aria-disabled')).toBeUndefined()
    expect(card.get('button').attributes('disabled')).toBeUndefined()

    await card.trigger('click')
    await flushPromises()
    expect(modalOpen(wrapper)).toBe(true)
  })

  it('도구 카드가 스페이스 키로도 열려야 한다', async () => {
    vi.mocked(revenueApi.summary).mockRejectedValue(new Error('network'))

    const wrapper = await renderAiView()
    const card = otherCard(wrapper)

    await card.trigger('keydown.space')
    await flushPromises()

    expect(modalOpen(wrapper)).toBe(true)
  })
})
