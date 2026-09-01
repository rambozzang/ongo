import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import CampaignRewardsView from './CampaignRewardsView.vue'
import koMessages from '@/locales/ko/common.json'
import { ugcRewardApi } from '@/api/ugcReward'
import { useWorkspaceStore } from '@/stores/workspace'
import type { CampaignAnalyticsResponse } from '@/api/ugcReward'

vi.mock('@/api/ugcReward', () => ({
  ugcRewardApi: {
    getAnalytics: vi.fn(),
    listParticipants: vi.fn(),
    listAuditEvents: vi.fn(),
  },
}))

/**
 * 캠페인 성과 요약이 **측정하지 않은 값을 0 으로 보여주지 않는지** 고정한다.
 *
 * Facebook·WordPress·Vimeo 는 공유 수를, Pinterest 는 댓글 수를 API 로 주지 않는다.
 * 예전에는 그 자리의 0 이 합산돼 이 화면 — **보상 화면** — 에 "공유 0" 으로 올라갔다.
 * 실제로 공유가 없어서가 아니라 물어보지 않았기 때문인데 구분되지 않았다.
 *
 * 서버는 이제 측정한 게시물이 하나도 없으면 합계를 `null` 로 준다. 화면이 그것을
 * `?? 0` 으로 되돌리면 서버 수정이 통째로 무의미해진다.
 */
describe('UGC 캠페인 지표 측정 불가 표시', () => {
  function analytics(overrides: Partial<CampaignAnalyticsResponse> = {}): CampaignAnalyticsResponse {
    return {
      campaignId: 1,
      totalViews: 300,
      totalLikes: 30,
      totalComments: 13,
      totalShares: null,
      lastSyncedAt: '2026-08-10T00:00:00',
      posts: [],
      measuredPostCounts: { views: 2, likes: 2, comments: 2, shares: 0 },
      ...overrides,
    }
  }

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(ugcRewardApi.listParticipants).mockResolvedValue(
      { items: [], totalBudget: 0, settledTotal: 0, remaining: 0 } as never,
    )
    vi.mocked(ugcRewardApi.listAuditEvents).mockResolvedValue({ items: [] } as never)
  })

  /**
   * 화면이 **API 에서 받은 값을 그대로 그리는지** 본다. 내부 ref 를 손으로 밀어 넣으면
   * "화면이 서버 응답을 어떻게 읽는가"를 검증하지 못한다.
   */
  async function renderWith(value: CampaignAnalyticsResponse | null) {
    vi.mocked(ugcRewardApi.getAnalytics).mockResolvedValue(value as never)

    const pinia = createPinia()
    setActivePinia(pinia)
    const workspace = useWorkspaceStore()
    workspace.ensureActiveWorkspace = vi.fn().mockResolvedValue(10) as never

    const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: koMessages } })
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/campaigns/:id/rewards', component: { template: '<div />' } }],
    })
    await router.push('/campaigns/1/rewards')
    await router.isReady()

    const wrapper = mount(CampaignRewardsView, {
      global: { plugins: [pinia, i18n, router], stubs: { PageHeader: true } },
    })
    await flushPromises()
    return wrapper
  }

  it('측정하지 않은 합계는 0이 아니라 측정 불가로 보여준다', async () => {
    const wrapper = await renderWith(analytics())

    expect(wrapper.find('[data-testid="ugc-metric-shares-unavailable"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="ugc-metric-shares"]').exists()).toBe(false)
    expect(wrapper.text()).toContain(koMessages.ugc.metricUnavailable)
  })

  it('측정된 합계는 기존대로 숫자로 보여준다', async () => {
    const wrapper = await renderWith(analytics())

    expect(wrapper.find('[data-testid="ugc-metric-views"]').text()).toContain('300')
    expect(wrapper.find('[data-testid="ugc-metric-views-unavailable"]').exists()).toBe(false)
  })

  /** 측정된 0 은 사람이 넣었거나 실제로 0 인 것이다. 감추면 안 된다. */
  it('실제로 측정된 0은 숫자로 보여준다', async () => {
    const wrapper = await renderWith(
      analytics({ totalShares: 0, measuredPostCounts: { views: 2, likes: 2, comments: 2, shares: 2 } }),
    )

    expect(wrapper.find('[data-testid="ugc-metric-shares"]').text()).toContain('0')
    expect(wrapper.find('[data-testid="ugc-metric-shares-unavailable"]').exists()).toBe(false)
  })

  /** 아직 안 불러온 상태도 0 이 아니다. */
  it('분석을 아직 못 받았으면 네 지표 모두 측정 불가다', async () => {
    const wrapper = await renderWith(null)

    for (const metric of ['views', 'likes', 'comments', 'shares']) {
      expect(wrapper.find(`[data-testid="ugc-metric-${metric}-unavailable"]`).exists()).toBe(true)
      expect(wrapper.find(`[data-testid="ugc-metric-${metric}"]`).exists()).toBe(false)
    }
  })

  it('ko/en 두 로케일에 측정 불가 문구가 있다', async () => {
    const en = await import('@/locales/en/common.json')

    expect(koMessages.ugc.metricUnavailable).toBeTruthy()
    expect((en.default as { ugc: { metricUnavailable?: string } }).ugc.metricUnavailable).toBeTruthy()
  })
})
