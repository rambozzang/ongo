import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { ENGAGEMENT_UNAVAILABLE_REASON, MY_STATS_UNAVAILABLE_REASON, useCompetitorStore } from './competitor'
import { competitorApi } from '@/api/competitor'
import ComparisonChart from '@/components/competitor/ComparisonChart.vue'
import CompetitorCard from '@/components/competitor/CompetitorCard.vue'
import type { CompetitorComparison } from '@/types/competitor'

vi.mock('@/api/competitor', () => ({
  competitorApi: {
    list: vi.fn(),
    benchmark: vi.fn(),
  },
}))

/**
 * 경쟁자 참여율은 **측정할 수 없다.**
 *
 * 공개 API 로 남의 채널의 좋아요·댓글을 얻을 수 없어 분자가 없다. 그런데 백엔드가 그
 * 자리에 `0.0` 을 넣었고 스토어 매퍼도 `avgEngagement: 0` 으로 채웠다. 그래서 비교표가
 * **"참여율: 나 4.2% vs 경쟁자 0.0%"** 를 그렸고, 크리에이터는 추적하는 모든 경쟁사를
 * 참여율에서 압도한다고 믿게 됐다 — 존재하지 않는 경쟁 우위다.
 *
 * `null` 은 "0" 이 아니라 "모른다" 다. 평균·차이·우위 판정에 섞으면 안 된다.
 */
describe('경쟁자 참여율 측정 불가 계약', () => {
  const listResponse = {
    competitors: [
      { id: 1, platform: 'YOUTUBE', channelName: 'A', subscriberCount: 10000, videoCount: 50, avgViews: 10000 },
      { id: 2, platform: 'YOUTUBE', channelName: 'B', subscriberCount: 20000, videoCount: 80, avgViews: 20000 },
    ],
  }

  const benchmarkResponse = {
    myStats: {
      subscriberCount: 5000,
      totalViews: 100000,
      videoCount: 20,
      avgViews: 5000,
      engagementRate: 4.2,
      growthRate: 3.1,
    },
    competitors: [
      {
        id: 1, channelName: 'A', platform: 'YOUTUBE', subscriberCount: 10000, totalViews: 500000,
        videoCount: 50, avgViews: 10000,
        engagementRate: null,
        engagementRateUnavailableReason: '공개 API로 경쟁 채널의 좋아요·댓글 수를 얻을 수 없어 참여율을 계산할 수 없습니다',
        growthRate: 1.5, profileImageUrl: null,
      },
      {
        id: 2, channelName: 'B', platform: 'YOUTUBE', subscriberCount: 20000, totalViews: 900000,
        videoCount: 80, avgViews: 20000,
        engagementRate: null,
        engagementRateUnavailableReason: '공개 API로 경쟁 채널의 좋아요·댓글 수를 얻을 수 없어 참여율을 계산할 수 없습니다',
        growthRate: -0.5, profileImageUrl: null,
      },
    ],
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(competitorApi.list).mockResolvedValue(listResponse as never)
    vi.mocked(competitorApi.benchmark).mockResolvedValue(benchmarkResponse as never)
  })

  // ── 스토어: 0으로 채우지 않는다 ────────────────────────────────────────────

  it('서버가 측정 불가라고 하면 0으로 채우지 않는다', async () => {
    const store = useCompetitorStore()

    await store.fetchCompetitors()

    expect(store.competitors.map(c => c.avgEngagement)).toEqual([null, null])
  })

  /** 벤치마크 응답이 아직 없을 때도 0 이 아니다 — "아직 모른다" 와 "0%" 는 다르다. */
  it('벤치마크 응답이 없어도 참여율을 0으로 만들지 않는다', async () => {
    vi.mocked(competitorApi.benchmark).mockRejectedValue(new Error('벤치마크 실패'))
    const store = useCompetitorStore()

    await store.fetchCompetitors()

    expect(store.competitors.every(c => c.avgEngagement === null)).toBe(true)
  })

  it('내 채널 참여율은 실제 측정값을 그대로 쓴다', async () => {
    const store = useCompetitorStore()

    await store.fetchCompetitors()

    expect(store.myStats.avgEngagement).toBe(4.2)
  })

  // ── 내 통계 미로드: 0으로 시작하지 않는다 ─────────────────────────────────
  //
  // 경쟁자 값을 null 로 고쳐도 **내 쪽이 0 이면 같은 거짓말이 남는다.** 이번에는
  // "참여율: 나 0% vs 경쟁자 …" 로 내가 지고 있는 것처럼 보인다.

  it('불러오기 전 내 통계는 0이 아니라 null이다', () => {
    const store = useCompetitorStore()

    expect(store.myStats.avgEngagement).toBeNull()
    expect(store.myStats.growthRate).toBeNull()
    expect(store.myStats.subscriberCount).toBeNull()
    expect(store.myStats.avgViews).toBeNull()
  })

  /** benchmark 가 실패하면 내 통계는 **없는 채로** 남아야 한다. 0 을 지어내지 않는다. */
  it('benchmark 실패 시 내 통계에 숫자를 만들어 넣지 않는다', async () => {
    vi.mocked(competitorApi.benchmark).mockRejectedValue(new Error('벤치마크 실패'))
    const store = useCompetitorStore()

    await store.fetchCompetitors()

    expect(store.myStats.avgEngagement).toBeNull()
    expect(store.myStats.growthRate).toBeNull()
    expect(store.myStats.subscriberCount).toBeNull()
  })

  it('목록 조회가 실패해도 내 통계를 0으로 만들지 않는다', async () => {
    vi.mocked(competitorApi.list).mockRejectedValue(new Error('목록 실패'))
    const store = useCompetitorStore()

    await store.fetchCompetitors()

    expect(store.competitors).toEqual([])
    expect(store.myStats.subscriberCount).toBeNull()
    expect(store.myStats.avgEngagement).toBeNull()
  })

  /**
   * 내 데이터를 못 받았으면 **모든 지표가** 비교 불가다. 구독자·조회수까지 0 으로
   * 비교하면 전 지표에서 내가 압도적으로 지는 화면이 나온다.
   */
  it('내 통계를 못 받으면 모든 지표가 비교 불가다', async () => {
    vi.mocked(competitorApi.benchmark).mockRejectedValue(new Error('벤치마크 실패'))
    const store = useCompetitorStore()
    await store.fetchCompetitors()

    const rows = store.getComparison(1)

    expect(rows).toHaveLength(4)
    expect(rows.every(r => r.comparable === false)).toBe(true)
    expect(rows.every(r => r.difference === null && r.differencePercent === null)).toBe(true)
  })

  /** 내 데이터 로딩 실패와 "애초에 못 구하는 값"은 사용자가 할 수 있는 일이 다르다. */
  it('비교 불가 사유가 내 데이터 미로드와 측정 불가를 구분한다', async () => {
    const store = useCompetitorStore()
    await store.fetchCompetitors()

    // 정상 로드 뒤에는 참여율만 측정 불가다.
    const loaded = store.getComparison(1).find(c => c.metric === '참여율')!
    expect(loaded.unavailableReason).toBe(ENGAGEMENT_UNAVAILABLE_REASON)

    store.myStats.subscriberCount = null
    const unloaded = store.getComparison(1).find(c => c.metric === '구독자')!
    expect(unloaded.unavailableReason).toBe(MY_STATS_UNAVAILABLE_REASON)
  })

  /** 순위도 마찬가지다. 0 으로 매기면 불러오기 전에는 항상 꼴찌가 나온다. */
  it('내 구독자 수를 모르면 순위를 만들지 않는다', async () => {
    vi.mocked(competitorApi.benchmark).mockRejectedValue(new Error('벤치마크 실패'))
    const store = useCompetitorStore()
    await store.fetchCompetitors()

    expect(store.myRanking).toBeNull()
  })

  it('내 구독자 수를 알면 순위를 계산한다', async () => {
    const store = useCompetitorStore()

    await store.fetchCompetitors()

    // 20000(B) > 10000(A) > 5000(나) → 3위
    expect(store.myRanking).toBe(3)
  })

  // ── 평균: null을 숫자로 섞지 않는다 ────────────────────────────────────────

  /**
   * **이것이 원래의 조작 지점이다.** 예전에는 `sum + c.avgEngagement` 로 null 자리의 0 을
   * 더해, 참여율을 하나도 모르는 상태가 "평균 0%" 라는 측정 결과로 나왔다.
   */
  it('측정값이 하나도 없으면 평균 참여율은 null이다', async () => {
    const store = useCompetitorStore()

    await store.fetchCompetitors()

    expect(store.averageMetrics.avgEngagement).toBeNull()
    // 다른 지표는 측정값이므로 계속 계산된다.
    expect(store.averageMetrics.avgSubscribers).toBe(15000)
    expect(store.averageMetrics.avgGrowthRate).toBe(0.5)
  })

  /** 언젠가 산출 수단이 생기면 측정된 값만 모아 평균을 낸다. */
  it('일부만 측정되면 측정된 값끼리만 평균을 낸다', async () => {
    const store = useCompetitorStore()
    await store.fetchCompetitors()

    store.competitors[0].avgEngagement = 6
    store.competitors[1].avgEngagement = null

    expect(store.averageMetrics.avgEngagement).toBe(6)
  })

  // ── 비교: 우열을 주장하지 않는다 ───────────────────────────────────────────

  it('참여율 비교는 comparable=false 이고 차이를 계산하지 않는다', async () => {
    const store = useCompetitorStore()
    await store.fetchCompetitors()

    const engagement = store.getComparison(1).find(c => c.metric === '참여율')!

    expect(engagement.comparable).toBe(false)
    expect(engagement.competitorValue).toBeNull()
    expect(engagement.difference).toBeNull()
    expect(engagement.differencePercent).toBeNull()
    expect(engagement.unavailableReason).toBeTruthy()
  })

  /** 측정 가능한 지표는 예전 그대로 계산돼야 한다. */
  it('측정 가능한 지표는 기존대로 차이를 계산한다', async () => {
    const store = useCompetitorStore()
    await store.fetchCompetitors()

    const subscribers = store.getComparison(1).find(c => c.metric === '구독자')!

    expect(subscribers.comparable).toBe(true)
    expect(subscribers.difference).toBe(5000 - 10000)
    expect(subscribers.differencePercent).toBe(-50)
  })

  /** 기준값이 0 이면 비율의 기준이 없다. 0% 로 채우면 "차이 없음"이 된다. */
  it('경쟁자 값이 0이면 증감률을 만들어내지 않는다', async () => {
    const store = useCompetitorStore()
    await store.fetchCompetitors()
    store.competitors[0].growthRate = 0

    const growth = store.getComparison(1).find(c => c.metric === '성장률')!

    expect(growth.comparable).toBe(true)
    expect(growth.differencePercent).toBeNull()
  })

  // ── 화면: 숫자 대신 안내 ───────────────────────────────────────────────────

  function comparison(overrides: Partial<CompetitorComparison>): CompetitorComparison {
    return {
      metric: '참여율',
      myValue: 4.2,
      competitorValue: null,
      difference: null,
      differencePercent: null,
      comparable: false,
      unavailableReason: '측정할 수 없어 비교할 수 없습니다',
      ...overrides,
    }
  }

  it('비교 차트는 측정 불가 지표에 막대와 증감 배지를 그리지 않는다', () => {
    const wrapper = mount(ComparisonChart, { props: { comparisons: [comparison({})] } })

    expect(wrapper.find('[data-testid="comparison-unavailable"]').exists()).toBe(true)
    // 0 폭 막대는 "가장 낮음" 으로 읽힌다.
    expect(wrapper.find('[data-testid="comparison-diff"]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('0.0%')
  })

  it('비교 차트는 측정 가능한 지표에는 기존대로 막대와 배지를 그린다', () => {
    const wrapper = mount(ComparisonChart, {
      props: {
        comparisons: [comparison({
          metric: '구독자', myValue: 5000, competitorValue: 10000,
          difference: -5000, differencePercent: -50, comparable: true, unavailableReason: undefined,
        })],
      },
    })

    expect(wrapper.find('[data-testid="comparison-diff"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="comparison-unavailable"]').exists()).toBe(false)
  })

  it('경쟁자 카드는 참여율을 0%가 아니라 측정 불가로 보여준다', () => {
    const wrapper = mount(CompetitorCard, {
      props: {
        competitor: {
          id: 1, name: 'A', channelUrl: '', platform: 'YOUTUBE', avatarUrl: '',
          subscriberCount: 10000, videoCount: 50, avgViews: 10000,
          avgEngagement: null, growthRate: 1.5,
          lastVideoAt: '', addedAt: '', isTracking: true,
        },
      },
    })

    expect(wrapper.find('[data-testid="competitor-engagement-unavailable"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="competitor-engagement"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('측정 불가')
    expect(wrapper.text()).not.toContain('0%')
  })

  it('참여율이 측정되면 카드가 숫자를 보여준다', () => {
    const wrapper = mount(CompetitorCard, {
      props: {
        competitor: {
          id: 1, name: 'A', channelUrl: '', platform: 'YOUTUBE', avatarUrl: '',
          subscriberCount: 10000, videoCount: 50, avgViews: 10000,
          avgEngagement: 3.4, growthRate: 1.5,
          lastVideoAt: '', addedAt: '', isTracking: true,
        },
      },
    })

    expect(wrapper.find('[data-testid="competitor-engagement"]').text()).toContain('3.4')
    expect(wrapper.find('[data-testid="competitor-engagement-unavailable"]').exists()).toBe(false)
  })
})
