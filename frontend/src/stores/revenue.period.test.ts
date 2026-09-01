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

const summary = (total: number) => ({
  totalRevenueKrw: total,
  growthPercent: 0,
  platformBreakdown: [{ platform: 'YOUTUBE', revenueKrw: total }],
  platformRevenueAvailable: true,
})

const trends = (points: { date: string; platform: string; revenueKrw: number }[]) => ({ data: points })

/**
 * 수익 화면의 **기간 계약**을 고정한다.
 *
 * 예전에는 화면이 `'1','3','6','12'`(개월)를 들고 있는데 스토어는 항상 `'30d'` 만 불렀다.
 * "1년"을 골라도 30일치가 왔고, 화면은 그중 마지막 12행만 잘라 **12일치를 1년 총수익**
 * 으로 보여줬다. 수익은 돈을 판단하는 화면이라 이 어긋남이 그대로 오판이 된다.
 */
describe('revenue store 기간 계약', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(revenueApi.summary).mockResolvedValue(summary(1000) as never)
    vi.mocked(revenueApi.trends).mockResolvedValue(trends([]) as never)
    vi.mocked(revenueApi.platformRevenue).mockResolvedValue({ platforms: [] } as never)
    // 평균 RPM 의 조회수 표본은 이 엔드포인트에서만 온다. 기본은 표본 없음.
    vi.mocked(revenueApi.cpmRpm).mockResolvedValue({ platforms: [] } as never)
  })

  it.each(['30d', '90d', '180d', '365d'] as const)(
    '%s 를 고르면 세 엔드포인트 모두 그 기간으로 부른다',
    async (period) => {
      const store = useRevenueStore()

      await store.fetchRevenue(period)

      expect(revenueApi.summary).toHaveBeenCalledWith(period)
      expect(revenueApi.trends).toHaveBeenCalledWith(period)
      expect(revenueApi.platformRevenue).toHaveBeenCalledWith(period)
    },
  )

  it('기본 기간은 30일이며 개월 값을 쓰지 않는다', async () => {
    const store = useRevenueStore()

    await store.fetchRevenue()

    expect(revenueApi.summary).toHaveBeenCalledWith('30d')
    // 예전 계약('1','3','6','12')이 되살아나면 여기서 걸린다.
    expect(revenueApi.summary).not.toHaveBeenCalledWith('12')
  })

  it('기간을 바꾸면 다시 불러온다', async () => {
    const store = useRevenueStore()

    await store.fetchRevenue('30d')
    await store.fetchRevenue('365d')

    expect(revenueApi.trends).toHaveBeenCalledTimes(2)
    expect(revenueApi.trends).toHaveBeenLastCalledWith('365d')
    expect(store.loadedPeriod).toBe('365d')
  })

  /**
   * **늦게 온 이전 응답이 새 선택을 덮어쓰면 안 된다.**
   *
   * 기간 버튼을 빠르게 누르면 365일 요청이 30일 요청보다 늦게 끝날 수 있다. 순서만
   * 믿으면 라벨은 30일인데 값은 365일이 된다 — 이번에 고치려는 것과 같은 불일치다.
   */
  it('늦게 도착한 이전 기간 응답을 버린다', async () => {
    const store = useRevenueStore()

    let resolveSlow: (value: unknown) => void = () => {}
    const slow = new Promise((resolve) => { resolveSlow = resolve })
    vi.mocked(revenueApi.summary).mockReturnValueOnce(slow as never)
    vi.mocked(revenueApi.trends).mockReturnValueOnce(slow as never)
    vi.mocked(revenueApi.platformRevenue).mockReturnValueOnce(slow as never)

    const firstCall = store.fetchRevenue('365d')          // 느린 요청
    await store.fetchRevenue('30d')                        // 나중에 시작해 먼저 끝남

    expect(store.loadedPeriod).toBe('30d')

    resolveSlow(summary(999_999))
    await firstCall

    // 느린 365d 응답이 30d 선택을 덮지 않아야 한다.
    expect(store.loadedPeriod).toBe('30d')
  })

  it('늦게 끝난 이전 요청이 진행 중인 요청의 로딩을 끄지 않는다', async () => {
    const store = useRevenueStore()

    let resolveSlow: (value: unknown) => void = () => {}
    const slow = new Promise((resolve) => { resolveSlow = resolve })
    vi.mocked(revenueApi.summary).mockReturnValueOnce(slow as never)
    vi.mocked(revenueApi.trends).mockReturnValueOnce(slow as never)
    vi.mocked(revenueApi.platformRevenue).mockReturnValueOnce(slow as never)

    const stale = store.fetchRevenue('365d')
    const fresh = store.fetchRevenue('30d')

    resolveSlow(summary(1))
    await stale
    // 새 요청이 아직 끝나지 않았다면 로딩이 유지돼야 한다.
    await fresh
    expect(store.loading).toBe(false)
  })

  // ── 일별 vs 월 집계 ──────────────────────────────────────────────────────

  /**
   * API 는 날짜별로 준다. 그 행을 "월별 수익" 표에 그대로 넣으면 **하루치가 한 달**처럼
   * 보인다. 숨기지 않고 명시적으로 합친다.
   */
  it('일별 추세는 자르지 않고, 월 집계는 YYYY-MM 으로 합친다', async () => {
    vi.mocked(revenueApi.trends).mockResolvedValue(trends([
      { date: '2026-07-30', platform: 'YOUTUBE', revenueKrw: 100 },
      { date: '2026-07-31', platform: 'YOUTUBE', revenueKrw: 200 },
      { date: '2026-08-01', platform: 'YOUTUBE', revenueKrw: 400 },
    ]) as never)
    const store = useRevenueStore()

    await store.fetchRevenue('90d')

    // 일별은 받은 그대로 3행.
    expect(store.dailyRevenue).toHaveLength(3)
    // 월 집계는 2행이고 7월은 합산된다.
    expect(store.monthlyAggregates.map((row) => row.period)).toEqual(['2026-07', '2026-08'])
    expect(store.monthlyAggregates[0].total).toBe(300)
    expect(store.monthlyAggregates[1].total).toBe(400)
  })

  /**
   * Naver Clip 은 공개 분석 API 가 없어 수익원으로 제시하지 않는다. 그 정책 때문에
   * **총합과 플랫폼 합계가 어긋나면** 퍼센트가 100%를 넘거나 모자란다.
   */
  it('Naver Clip 제외 후에도 총합과 플랫폼 합계가 일치한다', async () => {
    vi.mocked(revenueApi.trends).mockResolvedValue(trends([
      { date: '2026-08-01', platform: 'YOUTUBE', revenueKrw: 700 },
      { date: '2026-08-01', platform: 'NAVER_CLIP', revenueKrw: 300 },
    ]) as never)
    const store = useRevenueStore()

    await store.fetchRevenue('30d')

    const platformSum = store.platformBreakdown.reduce((sum, item) => sum + item.revenue, 0)
    const monthlySum = store.monthlyAggregates.reduce((sum, row) => sum + row.total, 0)

    expect(platformSum).toBe(700)
    expect(monthlySum).toBe(700)
    expect(store.platformBreakdown.some((item) => item.platform === 'NAVER_CLIP')).toBe(false)
  })

  // ── 증감률 추세: 기준선이 0이면 비교 불가 ─────────────────────────────────
  //
  // `growthTrend` 는 스토어가 내보내지만 **현재 화면 소비자가 없다.** 그래서 계약이
  // 조용히 틀어져도 아무도 모른다 — 여기서 고정한다. 예전에는 기준선이 0일 때 `0` 을
  // 돌려줘 첫 수익 발생 구간이 "0% 변화"로 위장됐다.

  /** 추세는 마지막 6개 구간을 본다. 날짜별 행을 주면 그대로 구간이 된다. */
  async function givenTrend(values: number[]) {
    vi.mocked(revenueApi.trends).mockResolvedValue(trends(
      values.map((revenueKrw, index) => ({
        date: `2026-08-${String(index + 1).padStart(2, '0')}`,
        platform: 'YOUTUBE',
        revenueKrw,
      })),
    ) as never)
    const store = useRevenueStore()
    await store.fetchRevenue('30d')
    return store
  }

  it('첫 포인트는 비교할 앞 구간이 없어 null이다', async () => {
    const store = await givenTrend([100, 200])

    expect(store.growthTrend[0].growth).toBeNull()
  })

  /** **이 케이스가 이번 수정의 핵심이다.** 0 → 100 은 "0% 변화"가 아니다. */
  it('직전 구간이 0이면 증감률을 만들어내지 않는다', async () => {
    const store = await givenTrend([0, 100])

    expect(store.growthTrend[1].growth).toBeNull()
  })

  it('직전 구간이 있으면 실제 증감률을 계산한다', async () => {
    const store = await givenTrend([100, 150, 75])

    expect(store.growthTrend[1].growth).toBe(50)
    expect(store.growthTrend[2].growth).toBe(-50)
  })

  /** 실제로 변화가 없었던 구간은 **측정된 사실**이므로 0 을 그대로 둔다. */
  it('측정된 0% 변화는 비교 불가로 바꾸지 않는다', async () => {
    const store = await givenTrend([100, 100])

    expect(store.growthTrend[1].growth).toBe(0)
  })

  /** 소비자가 생겼을 때 숫자로 착각하지 않도록 값의 형태 자체를 고정한다. */
  it('추세 값은 숫자이거나 null이며 NaN·Infinity가 아니다', async () => {
    const store = await givenTrend([0, 100, 0, 50])

    for (const point of store.growthTrend) {
      if (point.growth === null) continue
      expect(Number.isFinite(point.growth)).toBe(true)
    }
    expect(store.growthTrend.some((point) => point.growth === null)).toBe(true)
  })

  // ── 빈 데이터 / 실패 ─────────────────────────────────────────────────────

  it('빈 응답을 0원 성공처럼 만들지 않는다', async () => {
    const store = useRevenueStore()

    await store.fetchRevenue('30d')

    expect(store.dailyRevenue).toHaveLength(0)
    expect(store.monthlyAggregates).toHaveLength(0)
    // 플랫폼 분해가 비어야 화면이 빈 상태를 그린다(0원 막대가 아니라).
    expect(store.platformBreakdown).toHaveLength(0)
  })

  // ── 원자적 스냅샷 ────────────────────────────────────────────────────────
  //
  // 세 호출을 개별로 반영하면 "라벨은 새 기간, 데이터는 이전 기간"이 만들어진다.
  // 기간 계약을 고치면서 없앤 불일치가 부분 반영으로 되살아나는 경로다.

  /** 이전 기간 데이터를 채워 둔 스토어를 만든다. */
  async function givenLoaded30d() {
    vi.mocked(revenueApi.summary).mockResolvedValue(summary(1000) as never)
    vi.mocked(revenueApi.trends).mockResolvedValue(trends([
      { date: '2026-08-01', platform: 'YOUTUBE', revenueKrw: 1000 },
    ]) as never)
    const store = useRevenueStore()
    await store.fetchRevenue('30d')
    expect(store.loadedPeriod).toBe('30d')
    expect(store.dailyRevenue).toHaveLength(1)
    return store
  }

  /**
   * **이 테스트가 이번 수정의 핵심이다.**
   *
   * 새 기간 summary 만 성공하고 trends 가 실패하면, 예전 코드는 `loadedPeriod` 를 먼저
   * 써서 라벨만 365일로 바꾸고 차트·표에는 30일 데이터를 남겼다.
   */
  it('새 기간 summary 성공 + trends 실패면 라벨도 데이터도 이전 기간을 유지한다', async () => {
    const store = await givenLoaded30d()

    vi.mocked(revenueApi.summary).mockResolvedValueOnce(summary(999_999) as never)
    vi.mocked(revenueApi.trends).mockRejectedValueOnce(new Error('trends 장애'))

    await store.fetchRevenue('365d')

    expect(store.loadedPeriod).toBe('30d')
    expect(store.dailyRevenue).toHaveLength(1)
    // 새 기간 summary 가 이전 기간 라벨 아래로 새어 들어오면 안 된다.
    expect(store.summary.totalRevenue).not.toBe(999_999)
    expect(store.loadError).toBe(true)
  })

  /** 반대 조합. trends 만 성공해도 스냅샷은 갈라지면 안 된다. */
  it('새 기간 trends 성공 + summary 실패면 라벨도 데이터도 이전 기간을 유지한다', async () => {
    const store = await givenLoaded30d()

    vi.mocked(revenueApi.summary).mockRejectedValueOnce(new Error('summary 장애'))
    vi.mocked(revenueApi.trends).mockResolvedValueOnce(trends([
      { date: '2026-01-01', platform: 'YOUTUBE', revenueKrw: 5 },
      { date: '2026-01-02', platform: 'YOUTUBE', revenueKrw: 5 },
    ]) as never)

    await store.fetchRevenue('365d')

    expect(store.loadedPeriod).toBe('30d')
    // 새 기간 trends 가 이전 라벨 아래에 섞이면 안 된다.
    expect(store.dailyRevenue).toHaveLength(1)
    expect(store.dailyRevenue[0].period).toBe('2026-08-01')
    expect(store.loadError).toBe(true)
  })

  it('platformRevenue 만 실패해도 스냅샷을 반영하지 않는다', async () => {
    const store = await givenLoaded30d()

    vi.mocked(revenueApi.platformRevenue).mockRejectedValueOnce(new Error('platform 장애'))

    await store.fetchRevenue('90d')

    expect(store.loadedPeriod).toBe('30d')
    expect(store.loadError).toBe(true)
  })

  it('셋 다 성공하면 라벨과 데이터가 함께 새 기간으로 바뀐다', async () => {
    const store = await givenLoaded30d()

    vi.mocked(revenueApi.trends).mockResolvedValueOnce(trends([
      { date: '2026-01-01', platform: 'YOUTUBE', revenueKrw: 5 },
      { date: '2026-01-02', platform: 'YOUTUBE', revenueKrw: 5 },
    ]) as never)

    await store.fetchRevenue('365d')

    expect(store.loadedPeriod).toBe('365d')
    expect(store.dailyRevenue).toHaveLength(2)
    expect(store.loadError).toBe(false)
  })

  it('조회 실패를 오류로 표시하고 이전 값을 지우지 않는다', async () => {
    vi.mocked(revenueApi.trends).mockResolvedValue(trends([
      { date: '2026-08-01', platform: 'YOUTUBE', revenueKrw: 500 },
    ]) as never)
    const store = useRevenueStore()
    await store.fetchRevenue('30d')
    expect(store.dailyRevenue).toHaveLength(1)

    vi.mocked(revenueApi.summary).mockRejectedValueOnce(new Error('수익 API 장애'))
    vi.mocked(revenueApi.trends).mockRejectedValueOnce(new Error('수익 API 장애'))
    vi.mocked(revenueApi.platformRevenue).mockRejectedValueOnce(new Error('수익 API 장애'))

    await store.fetchRevenue('30d')

    expect(store.loadError).toBe(true)
    // 장애가 0원처럼 보이면 안 된다. 마지막 성공 값을 유지한다.
    expect(store.dailyRevenue).toHaveLength(1)
  })
})
