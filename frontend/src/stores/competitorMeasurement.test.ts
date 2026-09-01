import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { competitorApi } from '@/api/competitor'
import koMessages from '@/locales/ko/common.json'
import { useCompetitorStore } from './competitor'
import CompetitorCard from '@/components/competitor/CompetitorCard.vue'
import type { Competitor } from '@/types/competitor'

vi.mock('@/api/competitor', () => ({
  competitorApi: {
    list: vi.fn(),
    benchmark: vi.fn(),
    add: vi.fn(),
    remove: vi.fn(),
    sync: vi.fn(),
    trends: vi.fn(),
  },
}))

/**
 * 경쟁 채널 카드가 **미측정을 0 으로 그리지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 1. `mapResponseToCompetitor` 가 `growthRate: 0` 을 넣었다. 목록 API 는 성장률을 주지
 *    않으므로 benchmark 가 오기 전(또는 실패한 뒤)에는 **아직 모르는 값**인데,
 *    `CompetitorCard` 는 `null` 일 때만 측정 불가를 그린다 → "0%" 와 하락 아이콘이 떴다.
 * 2. `avgViews` 는 서버 저장 모델이 `Long` non-null 이라 영상 0 건일 때 `0` 이 실렸다.
 *    분모가 없어 계산하지 못한 자리인데 "평균 조회수 0회" 로 보였다.
 * 3. `averageMetrics` 는 추적 중인 경쟁사가 없을 때 `0` 들을 돌려줘 "평균 구독자 0명" 이
 *    관측처럼 보였다.
 */
describe('경쟁 채널 미측정 표시', () => {
  const listResponse = (avgViews: number | null) => ({
    competitors: [
      {
        id: 1,
        platform: 'YOUTUBE',
        platformChannelId: 'rival',
        channelName: '경쟁 채널',
        channelUrl: null,
        subscriberCount: 10000,
        totalViews: 0,
        videoCount: avgViews === null ? 0 : 12,
        avgViews,
        profileImageUrl: null,
        lastSyncedAt: null,
        createdAt: null,
      },
    ],
    totalCount: 1,
  })

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  // ── 스토어 매핑 ─────────────────────────────────────────────────────────

  /** **이 케이스가 초기 상태에서 "성장률 0%" 를 그리던 자리다.** */
  it('benchmark 가 실패해도 성장률을 0 으로 만들지 않는다', async () => {
    vi.mocked(competitorApi.list).mockResolvedValue(listResponse(1500) as never)
    vi.mocked(competitorApi.benchmark).mockRejectedValue(new Error('benchmark down'))
    const store = useCompetitorStore()

    await store.fetchCompetitors()

    expect(store.competitors[0].growthRate).toBeNull()
    expect(store.competitors[0].growthRate).not.toBe(0)
  })

  it('benchmark 응답 전 초기 매핑도 성장률이 null 이다', async () => {
    vi.mocked(competitorApi.list).mockResolvedValue(listResponse(1500) as never)
    vi.mocked(competitorApi.benchmark).mockResolvedValue({
      myStats: {
        subscriberCount: 0,
        totalViews: null,
        videoCount: 0,
        avgViews: null,
        engagementRate: null,
        growthRate: null,
      },
      competitors: [],
    } as never)
    const store = useCompetitorStore()

    await store.fetchCompetitors()

    expect(store.competitors[0].growthRate).toBeNull()
  })

  /** 영상 0 건이면 서버가 `null` 을 준다 — 스토어가 0 으로 되살리면 안 된다. */
  it('영상이 0건인 경쟁사의 평균 조회수를 0 으로 만들지 않는다', async () => {
    vi.mocked(competitorApi.list).mockResolvedValue(listResponse(null) as never)
    vi.mocked(competitorApi.benchmark).mockRejectedValue(new Error('benchmark down'))
    const store = useCompetitorStore()

    await store.fetchCompetitors()

    expect(store.competitors[0].avgViews).toBeNull()
  })

  /** **영상이 있고 조회수가 실제 0 이면 그 평균 0 은 관측이다.** */
  it('측정된 평균 0은 0 으로 유지한다', async () => {
    vi.mocked(competitorApi.list).mockResolvedValue(listResponse(0) as never)
    vi.mocked(competitorApi.benchmark).mockRejectedValue(new Error('benchmark down'))
    const store = useCompetitorStore()

    await store.fetchCompetitors()

    expect(store.competitors[0].avgViews).toBe(0)
  })

  // ── averageMetrics ──────────────────────────────────────────────────────

  /** 추적 중인 경쟁사가 없으면 평균이 성립하지 않는다. */
  it('추적 중인 경쟁사가 없으면 평균을 만들지 않는다', () => {
    const store = useCompetitorStore()

    expect(store.averageMetrics.avgSubscribers).toBeNull()
    expect(store.averageMetrics.avgViews).toBeNull()
    expect(store.averageMetrics.avgEngagement).toBeNull()
    expect(store.averageMetrics.avgGrowthRate).toBeNull()
  })

  /** 미측정 평균은 평균 계산에서 빠진다 — 0 으로 더하면 평균이 낮아진다. */
  it('평균 조회수가 전부 미측정이면 평균도 미측정이다', async () => {
    vi.mocked(competitorApi.list).mockResolvedValue(listResponse(null) as never)
    vi.mocked(competitorApi.benchmark).mockRejectedValue(new Error('benchmark down'))
    const store = useCompetitorStore()

    await store.fetchCompetitors()

    expect(store.averageMetrics.avgViews).toBeNull()
  })

  it('측정된 평균만 모아 평균을 낸다', async () => {
    vi.mocked(competitorApi.list).mockResolvedValue(listResponse(1000) as never)
    vi.mocked(competitorApi.benchmark).mockRejectedValue(new Error('benchmark down'))
    const store = useCompetitorStore()

    await store.fetchCompetitors()

    expect(store.averageMetrics.avgViews).toBe(1000)
  })

  // ── 카드 표시 ───────────────────────────────────────────────────────────

  const competitor = (overrides: Partial<Competitor> = {}): Competitor => ({
    id: 1,
    name: '경쟁 채널',
    channelUrl: '',
    platform: 'YOUTUBE',
    avatarUrl: '',
    subscriberCount: 10000,
    videoCount: 0,
    avgViews: null,
    avgEngagement: null,
    growthRate: null,
    lastVideoAt: '',
    addedAt: '',
    isTracking: true,
    ...overrides,
  })

  const i18n = createI18n({ legacy: false, locale: 'ko', fallbackLocale: 'ko', messages: { ko: koMessages } })

  function mountCard(data: Competitor) {
    return mount(CompetitorCard, { props: { competitor: data }, global: { plugins: [i18n] } })
  }

  /** **이 케이스가 "0%" 와 하락 아이콘을 그리던 자리다.** */
  it('카드가 미측정 성장률을 0% 로 그리지 않는다', () => {
    const text = mountCard(competitor()).text()

    expect(text).not.toContain('0%')
    expect(text).not.toContain('0.0%')
  })

  it('카드가 미측정 평균 조회수를 0 으로 그리지 않는다', () => {
    const text = mountCard(competitor()).text()

    // 라벨 바로 뒤를 본다 — 같은 카드의 참여율도 항상 "측정 불가" 라 전체 문자열로는
    // 어느 칸이 미측정인지 구분되지 않는다.
    expect(text).toContain(`평균 조회수${koMessages.analyticsView.notMeasured}`)
    expect(text).not.toContain('평균 조회수0')
  })

  /** 측정된 값은 그대로 그린다 — 과도한 차단 회귀를 막는다. */
  it('카드가 측정된 평균 조회수를 그대로 그린다', () => {
    const text = mountCard(competitor({ videoCount: 12, avgViews: 1500 })).text()

    expect(text).toContain('1.5K')
  })

  /** 측정된 0 은 관측이므로 숫자 0 으로 그린다. */
  it('카드가 측정된 평균 0을 숫자로 그린다', () => {
    const text = mountCard(competitor({ videoCount: 12, avgViews: 0 })).text()

    expect(text).toContain('평균 조회수0')
    expect(text).not.toContain(`평균 조회수${koMessages.analyticsView.notMeasured}`)
  })
})

/**
 * 내 채널 **구독자 수의 미측정**이 0 으로 되살아나지 않는지 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * 서버가 `channels.sumOf { it.subscriberCount }` 로 모든 채널을 더했다. Threads·LinkedIn
 * 어댑터는 팔로워 수를 **묻지도 않고** `0` 을 박아 넣으므로, 그 두 플랫폼만 연동한
 * 크리에이터는 합계가 항상 `0` 이었다. `MyChannelStats.subscriberCount` 가 non-null 이라
 * 그 `0` 이 비교표에 **"구독자 0명"** 으로, `myRanking` 에서는 **항상 꼴찌**로 그려졌다.
 *
 * 서버가 이제 그 자리를 `null` 로 준다. 스토어는 이미 nullable 계약이었으므로 여기서는
 * **그 null 이 0 으로 바뀌지 않는지**를 본다.
 */
describe('내 채널 구독자 미측정', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  const list = {
    competitors: [
      {
        id: 1,
        platform: 'YOUTUBE',
        platformChannelId: 'rival',
        channelName: '경쟁 채널',
        channelUrl: null,
        subscriberCount: 10000,
        totalViews: 0,
        videoCount: 12,
        avgViews: 1500,
        profileImageUrl: null,
        lastSyncedAt: null,
        createdAt: null,
      },
    ],
    totalCount: 1,
  }

  async function storeWithSubscribers(subscriberCount: number | null) {
    vi.mocked(competitorApi.list).mockResolvedValue(list as never)
    vi.mocked(competitorApi.benchmark).mockResolvedValue({
      myStats: {
        subscriberCount,
        totalViews: null,
        videoCount: 0,
        avgViews: null,
        engagementRate: null,
        growthRate: null,
      },
      competitors: [],
    } as never)
    const store = useCompetitorStore()
    await store.fetchCompetitors()
    return store
  }

  /** **이 케이스가 "구독자 0명" 을 측정 결과로 그리던 자리다.** */
  it('미측정 구독자 수를 0 으로 만들지 않는다', async () => {
    const store = await storeWithSubscribers(null)

    expect(store.myStats.subscriberCount).toBeNull()
    expect(store.myStats.subscriberCount).not.toBe(0)
  })

  /** **측정된 0 은 관측이다.** 갓 만든 채널의 구독자 0 명. */
  it('측정된 0 구독자는 0 으로 유지한다', async () => {
    const store = await storeWithSubscribers(0)

    expect(store.myStats.subscriberCount).toBe(0)
  })

  it('측정된 구독자 수는 그대로 받는다', async () => {
    const store = await storeWithSubscribers(8000)

    expect(store.myStats.subscriberCount).toBe(8000)
  })

  // ── 순위 ────────────────────────────────────────────────────────────────

  /** 순위를 매길 기준이 없다 — 0 으로 매기면 항상 꼴찌가 된다. */
  it('구독자 수가 미측정이면 순위를 만들지 않는다', async () => {
    const store = await storeWithSubscribers(null)

    expect(store.myRanking).toBeNull()
  })

  /** 측정된 0 이면 순위는 매길 수 있다 — 경쟁사보다 아래인 것이 관측 결과다. */
  it('측정된 0 구독자로는 순위를 매긴다', async () => {
    const store = await storeWithSubscribers(0)

    expect(store.myRanking).toBe(2)
  })

  // ── 비교표 ──────────────────────────────────────────────────────────────

  /** 숫자를 지어내지 않고 기존 "비교 불가" UX 를 쓴다. */
  it('구독자 수가 미측정이면 비교 불가로 표시한다', async () => {
    const store = await storeWithSubscribers(null)

    const subscribers = store.getComparison(1).find(m => m.metric === '구독자')!

    expect(subscribers.comparable).toBe(false)
    expect(subscribers.myValue).toBeNull()
    expect(subscribers.difference).toBeNull()
    expect(subscribers.differencePercent).toBeNull()
  })

  /** **측정된 값의 비교는 그대로 동작한다.** 과도한 차단 회귀를 막는다. */
  it('측정된 구독자 수는 비교를 그대로 계산한다', async () => {
    const store = await storeWithSubscribers(8000)

    const subscribers = store.getComparison(1).find(m => m.metric === '구독자')!

    expect(subscribers.comparable).toBe(true)
    expect(subscribers.myValue).toBe(8000)
    expect(subscribers.difference).toBe(-2000)
  })
})
