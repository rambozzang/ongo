import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useRevenueStore } from './revenue'
import { revenueApi } from '@/api/revenue'

vi.mock('@/api/revenue', () => ({
  revenueApi: {
    summary: vi.fn(),
    trends: vi.fn(),
    platformRevenue: vi.fn(),
    cpmRpm: vi.fn(),
  },
}))

/**
 * 수익 요약 KPI 두 개가 **측정하지 않은 것을 숫자로 주장하지 않는지** 고정한다.
 *
 * ## 무엇이 거짓이었나
 *
 * ```
 * const averageRPM = Math.floor(totalRevenue / (data.length * 10000))
 * ```
 *
 * 분모가 `일수 × 10,000` 이다. **조회수가 어디에도 없다.** `10000` 은 "하루 1만 조회"
 * 라는, 아무 데서도 측정하지 않은 가정이고 RPM 의 정의(조회 1,000회당 수익)와 수식조차
 * 맞지 않는다. 화면은 그 값을 "₩1,234" 처럼 실측 단가로 보여줬다.
 *
 * ```
 * topPlatform: 'YOUTUBE', topPlatformRevenue: 0
 * ```
 *
 * 데이터가 하나도 없어도 "최고 수익 플랫폼: YouTube · ₩0" 이 떴다. YouTube 를 연결한
 * 적조차 없는 크리에이터에게도 그렇게 보였다.
 *
 * ## 진짜 소스
 *
 * 조회수를 주는 응답은 `/analytics/revenue/cpm-rpm` **하나뿐이다.** `summary` 와
 * `trends` 응답에는 조회수 필드 자체가 없다(`RevenueSummaryResponse`,
 * `RevenueTrendPoint` 어디에도 없음). 그래서 RPM 은 그 응답에서만 나온다.
 */
describe('수익 요약 KPI 측정 계약', () => {
  const summaryResponse = (breakdown: { platform: string; revenueKrw: number }[], total = 1000) => ({
    totalRevenueKrw: total,
    growthPercent: null,
    platformBreakdown: breakdown.map((b) => ({ ...b, revenueMicro: b.revenueKrw * 1e6, percentage: 0 })),
    platformRevenueAvailable: true,
  })

  const cpmRpmResponse = (
    platforms: { platform: string; rpm: number | null; views: number; revenueMicro: number }[],
  ) => ({ platforms: platforms.map((p) => ({ ...p, cpm: null, impressions: 0, unavailableMetrics: {} })) })

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(revenueApi.summary).mockResolvedValue(summaryResponse([{ platform: 'YOUTUBE', revenueKrw: 1000 }]) as never)
    vi.mocked(revenueApi.trends).mockResolvedValue({ data: [] } as never)
    vi.mocked(revenueApi.platformRevenue).mockResolvedValue({ platforms: [] } as never)
    vi.mocked(revenueApi.cpmRpm).mockResolvedValue({ platforms: [] } as never)
  })

  // ── 평균 RPM: 조회수 표본에서만 ──────────────────────────────────────────

  /** **이 케이스가 임의 분모로 만든 "₩1,234" 를 내던 자리다.** */
  it('조회수 표본이 없으면 RPM을 만들지 않는다', async () => {
    const store = useRevenueStore()

    await store.fetchRevenue('30d')

    expect(store.summary.averageRPM).toBeNull()
    expect(store.summary.averageRpmUnavailableReason).toBe('noViewSample')
    // 총수익은 있지만 그것만으로 RPM 을 만들면 안 된다.
    expect(store.summary.totalRevenue).toBe(1000)
  })

  it('조회수 표본이 있으면 조회수 기준으로 계산한다', async () => {
    vi.mocked(revenueApi.cpmRpm).mockResolvedValue(cpmRpmResponse([
      // 수익 1,000원 / 조회 2,000회 * 1,000 = 500원
      { platform: 'YOUTUBE', rpm: 500, views: 2000, revenueMicro: 1_000_000_000 },
    ]) as never)
    const store = useRevenueStore()

    await store.fetchRevenue('30d')

    expect(store.summary.averageRPM).toBe(500)
    expect(store.summary.averageRpmUnavailableReason).toBeNull()
  })

  /**
   * **조회수 가중이어야 한다.** 플랫폼별 RPM 의 산술평균을 내면 조회 100회 플랫폼과
   * 100만회 플랫폼이 같은 무게가 되어 전체 평균이 아닌 숫자가 나온다.
   */
  it('여러 플랫폼은 조회수로 가중해 합친다', async () => {
    vi.mocked(revenueApi.cpmRpm).mockResolvedValue(cpmRpmResponse([
      { platform: 'YOUTUBE', rpm: 1000, views: 1000, revenueMicro: 1_000_000_000 }, // 1,000원 / 1,000회
      { platform: 'TIKTOK', rpm: 100, views: 9000, revenueMicro: 900_000_000 }, // 900원 / 9,000회
    ]) as never)
    const store = useRevenueStore()

    await store.fetchRevenue('30d')

    // 가중: (1000 + 900) / (1000 + 9000) * 1000 = 190원. 산술평균이면 550원이다.
    expect(store.summary.averageRPM).toBe(190)
  })

  /** 분모가 없는 행의 수익만 분자에 더하면 평균이 실제보다 높아진다. */
  it('조회수가 0인 행은 분자에서도 제외한다', async () => {
    vi.mocked(revenueApi.cpmRpm).mockResolvedValue(cpmRpmResponse([
      { platform: 'YOUTUBE', rpm: 500, views: 2000, revenueMicro: 1_000_000_000 },
      { platform: 'FACEBOOK', rpm: null, views: 0, revenueMicro: 9_000_000_000 },
    ]) as never)
    const store = useRevenueStore()

    await store.fetchRevenue('30d')

    // 제외하지 않으면 (1000 + 9000) / 2000 * 1000 = 5,000원이 된다.
    expect(store.summary.averageRPM).toBe(500)
  })

  /** **조회는 있는데 수익이 0 이면 그 0 은 실측이다.** 감추면 실제 관찰을 잃는다. */
  it('조회가 있고 수익이 0이면 RPM 0원을 보존한다', async () => {
    vi.mocked(revenueApi.cpmRpm).mockResolvedValue(cpmRpmResponse([
      { platform: 'YOUTUBE', rpm: 0, views: 5000, revenueMicro: 0 },
    ]) as never)
    const store = useRevenueStore()

    await store.fetchRevenue('30d')

    expect(store.summary.averageRPM).toBe(0)
    expect(store.summary.averageRpmUnavailableReason).toBeNull()
  })

  /**
   * 불러오지 못한 것과 불러왔지만 표본이 없는 것은 다른 상태다. 그리고 RPM 하나가
   * 실패했다고 수익 합계·추세까지 되돌리면 잃는 것이 더 크다.
   */
  it('조회수 조회가 실패하면 사유를 구분하고 나머지 스냅샷은 살린다', async () => {
    vi.mocked(revenueApi.cpmRpm).mockRejectedValue(new Error('조회 실패'))
    const store = useRevenueStore()

    await store.fetchRevenue('30d')

    expect(store.summary.averageRPM).toBeNull()
    expect(store.summary.averageRpmUnavailableReason).toBe('loadFailed')
    expect(store.summary.totalRevenue).toBe(1000)
    expect(store.loadError).toBe(false)
    expect(store.loadedPeriod).toBe('30d')
  })

  it('RPM 표본도 선택한 기간으로 부른다', async () => {
    const store = useRevenueStore()

    await store.fetchRevenue('365d')

    expect(revenueApi.cpmRpm).toHaveBeenCalledWith('365d')
  })

  // ── 최고 수익 플랫폼: 지어내지 않는다 ────────────────────────────────────

  /** **이 케이스가 "YouTube · ₩0" 을 만들던 자리다.** */
  it('집계된 플랫폼이 없으면 플랫폼 이름을 만들지 않는다', async () => {
    vi.mocked(revenueApi.summary).mockResolvedValue(summaryResponse([], 0) as never)
    const store = useRevenueStore()

    await store.fetchRevenue('30d')

    expect(store.summary.topPlatform).toBeNull()
    expect(store.summary.topPlatformRevenue).toBeNull()
  })

  it('집계가 있으면 가장 큰 플랫폼을 고른다', async () => {
    vi.mocked(revenueApi.summary).mockResolvedValue(summaryResponse([
      { platform: 'YOUTUBE', revenueKrw: 300 },
      { platform: 'TIKTOK', revenueKrw: 700 },
    ]) as never)
    const store = useRevenueStore()

    await store.fetchRevenue('30d')

    expect(store.summary.topPlatform).toBe('TIKTOK')
    expect(store.summary.topPlatformRevenue).toBe(700)
  })

  /**
   * 알 수 없는 플랫폼을 YouTube 로 바꿔치면 **실제로 관측된 플랫폼이 화면에서 사라진다.**
   * 라벨이 없는 것과 값이 틀린 것은 다르다.
   */
  it('알 수 없는 플랫폼도 YouTube로 바꾸지 않는다', async () => {
    vi.mocked(revenueApi.summary).mockResolvedValue(summaryResponse([
      { platform: 'SOME_NEW_PLATFORM', revenueKrw: 900 },
    ]) as never)
    const store = useRevenueStore()

    await store.fetchRevenue('30d')

    expect(store.summary.topPlatform).toBe('SOME_NEW_PLATFORM')
    expect(store.summary.topPlatformRevenue).toBe(900)
  })

  // ── 초기 상태 ────────────────────────────────────────────────────────────

  /**
   * 아직 아무것도 부르지 않은 상태에서도 화면은 KPI 카드를 그린다. 그때 기본값이
   * `YOUTUBE`/`0` 이면 로딩 한 프레임 동안 거짓이 보인다.
   */
  it('초기 상태에서도 플랫폼과 RPM을 지어내지 않는다', () => {
    const store = useRevenueStore()

    expect(store.summary.topPlatform).toBeNull()
    expect(store.summary.topPlatformRevenue).toBeNull()
    expect(store.summary.averageRPM).toBeNull()
    expect(store.summary.monthlyGrowth).toBeNull()
  })

  /** 행이 하나도 없으면 비교할 이전 구간도 없다. `0` 은 "변화 없음"이라는 주장이다. */
  it('데이터가 없으면 성장률을 0으로 채우지 않는다', async () => {
    vi.mocked(revenueApi.summary).mockResolvedValue(summaryResponse([], 0) as never)
    const store = useRevenueStore()

    await store.fetchRevenue('30d')

    expect(store.summary.monthlyGrowth).toBeNull()
  })
})
