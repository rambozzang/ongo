import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ko from '@/locales/ko/common.json'
import en from '@/locales/en/common.json'

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  fallbackLocale: 'en',
  messages: { ko, en },
})
const mountOpts = { global: { plugins: [i18n] } }

const testCtx = vi.hoisted(() => ({
  state: {
    competitors: [] as any[],
    loading: false,
    loadError: null as string | null,
    syncing: false,
    syncError: null as string | null,
    creditBlocked: false,
    insightError: null as string | null,
    insightLoading: false,
    aiInsight: null as any,
    fetchCompetitors: vi.fn(),
    syncCompetitors: vi.fn(),
    toggleTracking: vi.fn(),
    removeCompetitor: vi.fn(),
    getComparison: vi.fn(() => []),
    addCompetitor: vi.fn(),
    fetchInsight: vi.fn(),
  },
}))

vi.mock('@/stores/competitor', () => ({
  useCompetitorStore: () => testCtx.state,
}))

const creditCtx = vi.hoisted(() => ({ state: { fetchBalance: vi.fn() } }))

vi.mock('@/stores/credit', () => ({
  useCreditStore: () => creditCtx.state,
}))

vi.mock('@/components/competitor/CompetitorCard.vue', () => ({
  default: { name: 'CompetitorCard', template: '<div class="cmp-card" />', props: ['competitor', 'selected'], emits: ['select', 'toggle-tracking', 'remove'] },
}))
vi.mock('@/components/competitor/ComparisonChart.vue', () => ({
  default: { name: 'ComparisonChart', template: '<div class="cmp-chart" />', props: ['comparisons', 'myName', 'competitorName'] },
}))
vi.mock('@/components/competitor/AddCompetitorModal.vue', () => ({
  default: { name: 'AddCompetitorModal', template: '<div />', props: ['isOpen'], emits: ['close', 'add'] },
}))
vi.mock('@/components/subscription/CreditPurchaseModal.vue', () => ({
  default: {
    name: 'CreditPurchaseModal',
    template: '<div data-testid="credit-modal" @click="$emit(\'purchase\')"></div>',
    props: ['modelValue', 'requiredCredits'],
    emits: ['purchase', 'update:modelValue'],
  },
}))
vi.mock('@/components/redesign/SectionCard.vue', () => ({
  default: { name: 'SectionCard', template: '<div><slot /></div>', props: ['title', 'meta', 'bodyClass'] },
}))
vi.mock('@/components/common/EmptyState.vue', () => ({
  default: { name: 'EmptyState', template: '<div><slot name="action" /></div>', props: ['title', 'description'] },
}))
vi.mock('@/components/common/LoadingSpinner.vue', () => ({
  default: { name: 'LoadingSpinner', template: '<div />' },
}))
vi.mock('@/components/common/PageHeader.vue', () => ({
  default: { name: 'PageHeader', template: '<div><slot name="actions" /></div>', props: ['title', 'description'] },
}))

import { useCompetitorStore } from '@/stores/competitor'
import { useCreditStore } from '@/stores/credit'
import CompetitorView from '@/views/CompetitorView.vue'

const store = useCompetitorStore()
const creditStore = useCreditStore() as unknown as { fetchBalance: ReturnType<typeof vi.fn> }

beforeEach(() => {
  store.competitors = []
  store.loading = false
  store.loadError = null
  store.syncing = false
  store.syncError = null
  store.creditBlocked = false
  store.insightError = null
  store.insightLoading = false
  store.aiInsight = null
  // vi.fn 호출 이력만 초기화 (구현은 보존)
  vi.clearAllMocks()
  // getComparison 기본값 복원
  ;(store.getComparison as any).mockReturnValue([])
})

describe('CompetitorView', () => {
  it('경쟁 채널이 없으면 빈 상태를 보여주고 AI 버튼은 비활성화된다', () => {
    const wrapper = mount(CompetitorView, mountOpts)
    expect(wrapper.find('[data-testid="competitor-empty"]').exists()).toBe(true)
    const aiButton = wrapper.find('[data-testid="competitor-ai-button"]')
    expect(aiButton.exists()).toBe(true)
    expect((aiButton.element as HTMLButtonElement).disabled).toBe(true)
  })

  it('실제 데이터 + AI 인사이트 결과를 렌더링한다 (자동 재호출 없음)', async () => {
    store.competitors = [
      { id: 1, name: 'C1', channelUrl: '', platform: 'YOUTUBE', avatarUrl: null, subscriberCount: 100, videoCount: 10, avgViews: 5, avgEngagement: 2, growthRate: 1, lastVideoAt: '', addedAt: '', isTracking: true },
    ]
    store.aiInsight = {
      summary: '요약입니다',
      strengths: ['강점1'],
      weaknesses: ['약점1'],
      opportunities: ['기회1'],
      recommendations: ['추천1'],
    }
    const wrapper = mount(CompetitorView, mountOpts)
    await flushPromises()

    expect(wrapper.find('[data-testid="competitor-list"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="competitor-ai-result"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('요약입니다')
    // 마운트 시 fetchInsight 를 자동 호출하지 않는다.
    expect(store.fetchInsight).not.toHaveBeenCalled()
  })

  it('CREDIT_INSUFFICIENT: 크레딧 CTA 만 노출되고 일반 오류/AI 버튼은 숨겨진다', () => {
    store.creditBlocked = true
    const wrapper = mount(CompetitorView, mountOpts)
    expect(wrapper.find('[data-testid="competitor-ai-credit-cta"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="competitor-ai-button"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="competitor-ai-error"]').exists()).toBe(false)
  })

  it('일반 오류(403/PLAN): 크레딧 CTA 없이 일반 오류 블록만 노출된다', () => {
    store.insightError = '일부 기능을 사용할 수 없습니다 (403)'
    const wrapper = mount(CompetitorView, mountOpts)
    expect(wrapper.find('[data-testid="competitor-ai-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="competitor-ai-credit-cta"]').exists()).toBe(false)
  })

  it('크레딧 CTA 클릭 → 구매 모달이 열리고, 구매 후 자동 재호출 없이 차단만 해제된다', async () => {
    store.creditBlocked = true
    const wrapper = mount(CompetitorView, mountOpts)
    await wrapper.find('[data-testid="competitor-ai-credit-cta"] button').trigger('click')
    await flushPromises()

    const modal = wrapper.find('[data-testid="credit-modal"]')
    expect(modal.exists()).toBe(true)

    await modal.trigger('click') // @purchase
    await flushPromises()

    expect(creditStore.fetchBalance).toHaveBeenCalledTimes(1)
    expect(store.creditBlocked).toBe(false)
    // 구매 후 인사이트를 자동 재호출하지 않는다.
    expect(store.fetchInsight).not.toHaveBeenCalled()
  })

  it('동기화 성공: 서버 수치(lastSync)로 안내 문구를 구성해 노출한다', () => {
    store.lastSync = {
      requested: 2,
      synced: 2,
      unsupported: 1,
      failed: 0,
      results: [],
      competitors: [],
      totalCount: 2,
    }
    const wrapper = mount(CompetitorView, mountOpts)
    const el = wrapper.find('[data-testid="competitor-sync-success"]')
    expect(el.exists()).toBe(true)
    // 서버 수치 기반 안내 문구가 실제로 렌더링된다 (성공을 "완료"로 치지 않음).
    expect(el.text()).toContain('2건 갱신')
    expect(el.text()).toContain('1건 미지원')
  })
})
