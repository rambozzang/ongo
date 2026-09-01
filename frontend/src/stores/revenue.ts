import { defineStore } from 'pinia'
import { ref, shallowRef, computed } from 'vue'
import type { Platform } from '@/types/channel'
import type {
  RevenueSummary,
  PlatformRevenueData,
  RevenueTrendPoint,
  CpmRpmResponse,
  BrandDealRevenueResponse,
  RevenueInsight,
  RevenueAlertConfig,
} from '@/types/revenue'
import { revenueApi } from '@/api/revenue'
import { CREDIT_INSUFFICIENT, matchesCode } from '@/composables/usePlanLimit'

/**
 * 수익 조회 기간. **값이 곧 API 가 받는 일수**다(`periodToDays` 가 `\d+d` 를 해석한다).
 *
 * 예전에는 화면이 `'1','3','6','12'`(개월)를 들고 있었고 스토어는 항상 `'30d'` 를
 * 불렀다. 라벨과 실제 데이터가 어긋난 채로 굳었으므로, 이제 **화면이 고르는 값과 API 가
 * 받는 값을 같게** 둔다.
 */
export type RevenuePeriod = '30d' | '90d' | '180d' | '365d'

export const DEFAULT_REVENUE_PERIOD: RevenuePeriod = '30d'

export interface RevenueData {
  period: string
  /** API가 실제로 반환한 플랫폼별 수익. 하드코딩된 플랫폼 목록을 사용하지 않는다. */
  platforms: Record<string, number>
  total: number
}

/**
 * 평균 RPM 을 낼 수 없는 이유.
 *
 * 화면이 문구를 고르는 데 쓴다. 스토어가 i18n 키를 직접 들고 있으면 로케일 구조가
 * 바뀔 때마다 스토어를 고쳐야 하므로 판정만 여기서 하고 문구는 뷰가 붙인다.
 */
export type AvgRpmUnavailableReason =
  /** 조회수 표본을 불러오지 못했다(네트워크·서버 오류). */
  | 'loadFailed'
  /** 불러왔지만 조회수가 측정된 행이 없다. 분모가 없어 RPM 이 성립하지 않는다. */
  | 'noViewSample'

export interface RevenueSummaryLocal {
  totalRevenue: number
  /**
   * 직전 동일 길이 기간 대비 성장률(%). **비교할 이전 기간 수익이 없으면 `null`** 이다.
   *
   * 화면은 이 값을 퍼센트 숫자나 증감 색상으로 바꾸지 말고 "비교 불가"로 표시해야 한다.
   */
  monthlyGrowth: number | null
  /**
   * 조회 1,000회당 수익. **조회수 표본이 없으면 `null`** 이다.
   *
   * 예전에는 `totalRevenue / (data.length * 10000)` 이었다. 분모의 `10000` 은
   * "하루 1만 조회" 라는 **아무 데서도 측정하지 않은 가정**이고, RPM 의 정의(조회
   * 1,000회당)와 수식조차 맞지 않았다. 수익 화면은 그 숫자를 "₩1,234" 처럼 실측
   * 단가로 보여줬다.
   *
   * 조회수를 주는 응답은 `/analytics/revenue/cpm-rpm` 하나뿐이다 — `summary` 와
   * `trends` 응답에는 조회수 필드 자체가 없다. 그래서 이 값은 그 응답에서만 나온다.
   */
  averageRPM: number | null
  /** [averageRPM] 이 `null` 인 이유. 값이 있으면 `null`. */
  averageRpmUnavailableReason: AvgRpmUnavailableReason | null
  /**
   * 수익이 가장 큰 플랫폼. **집계된 플랫폼이 없으면 `null`** 이다.
   *
   * 예전에는 기본값이 `'YOUTUBE'` 였다. 데이터가 하나도 없어도 "최고 수익 플랫폼:
   * YouTube · ₩0" 이 떴고, YouTube 를 연결한 적조차 없는 크리에이터에게도 그렇게 보였다.
   *
   * `PLATFORM_CONFIG` 에 없는 플랫폼도 **원문 그대로 남긴다.** YouTube 로 바꿔치면
   * 실제로 관측된 플랫폼이 사라진다. 라벨은 화면이 폴백한다.
   */
  topPlatform: string | null
  /** [topPlatform] 의 수익. 플랫폼이 `null` 이면 함께 `null`. */
  topPlatformRevenue: number | null
}

/**
 * 두 구간 사이의 증감률(%). **기준선이 0 이면 `null`** — 비교할 대상이 없다는 뜻이다.
 *
 * 예전에는 0 을 돌려줬다. `Infinity`/`NaN` 을 막는다는 의도는 옳았지만, `0` 은
 * **"변화 없음"이라는 측정한 적 없는 사실**을 주장한다. 첫 수익이 발생한 구간이
 * "0% 변화"로 위장돼 크리에이터가 성과가 없었다고 읽는다.
 *
 * 백엔드 `RevenueSummaryResponse.growthPercent`(도메인 `MetricChange`)와 **같은 정책**이다.
 * 이쪽은 프론트에서 일별/월별 행을 이어 붙여 만드는 값이라 별도 함수지만, 판정은 같다.
 * 갈라지면 같은 화면의 KPI 와 추세가 서로 다른 답을 말한다.
 */
function percentageChange(current: number, previous: number): number | null {
  if (previous === 0) return null
  return ((current - previous) / previous) * 100
}

export const useRevenueStore = defineStore('revenue', () => {
  /**
   * **일별** 수익 행. 이름은 `monthly` 지만 `trends` API 가 날짜별로 주므로 실제로는
   * 하루 한 행이다. 이름을 바꾸면 이 스토어를 쓰는 다른 화면이 깨지므로 그대로 두고,
   * 월 집계가 필요한 표는 아래 [monthlyAggregates] 를 쓴다.
   */
  const monthlyRevenue = shallowRef<RevenueData[]>([])

  /** 마지막으로 성공적으로 불러온 기간. 화면이 라벨을 실제 데이터에 맞추는 데 쓴다. */
  const loadedPeriod = ref<RevenuePeriod>(DEFAULT_REVENUE_PERIOD)

  /**
   * 일별 수익. `trends` API 가 주는 그대로이며 **자르지 않는다.**
   *
   * 예전 화면은 여기서 마지막 N개를 잘라 "N개월"이라고 불렀다. API 는 이미 요청한
   * 기간만큼만 돌려주므로 자를 이유가 없고, 자르면 라벨과 실제가 어긋난다.
   */
  const dailyRevenue = computed<RevenueData[]>(() => monthlyRevenue.value)

  /**
   * 월 집계. **"월별 수익" 표는 이것을 쓴다.**
   *
   * API 는 일별로 준다. 그 행을 그대로 월별 표에 넣으면 하루치가 한 달처럼 보인다.
   * 숨기지 않고 여기서 명시적으로 `YYYY-MM` 으로 합친다.
   */
  const monthlyAggregates = computed<RevenueData[]>(() => {
    const byMonth = new Map<string, RevenueData>()
    for (const day of monthlyRevenue.value) {
      const month = day.period.slice(0, 7) // YYYY-MM
      if (!byMonth.has(month)) {
        byMonth.set(month, { period: month, platforms: {}, total: 0 })
      }
      const entry = byMonth.get(month)!
      for (const [platform, amount] of Object.entries(day.platforms)) {
        entry.platforms[platform] = (entry.platforms[platform] ?? 0) + amount
      }
      entry.total += day.total
    }
    return Array.from(byMonth.values()).sort((a, b) => a.period.localeCompare(b.period))
  })

  /** 진행 중인 조회의 일련번호. 늦게 온 응답을 버리는 데 쓴다. */
  let activeRevenueRequestId = 0
  const summary = ref<RevenueSummaryLocal>({
    totalRevenue: 0,
    // 아직 아무것도 불러오지 않았거나 데이터가 없으면 비교 자체가 불가능하다.
    monthlyGrowth: null,
    averageRPM: null,
    averageRpmUnavailableReason: 'noViewSample',
    topPlatform: null,
    topPlatformRevenue: null,
  })
  const loading = ref(false)
  const loadError = ref(false)
  const apiSummary = ref<RevenueSummary | null>(null)
  const apiTrends = shallowRef<RevenueTrendPoint[]>([])
  const apiPlatformRevenue = ref<PlatformRevenueData | null>(null)
  const cpmRpmData = ref<CpmRpmResponse | null>(null)
  const brandDealData = ref<BrandDealRevenueResponse | null>(null)
  const cpmRpmLoading = ref(false)
  const brandDealLoading = ref(false)
  const cpmRpmError = ref<string | null>(null)
  const brandDealError = ref<string | null>(null)

  // AI 인사이트
  const revenueInsights = shallowRef<RevenueInsight[]>([])
  const insightsLoading = ref(false)
  const insightsError = ref<string | null>(null)
  const generateInsightLoading = ref(false)
  // 생성 전용 오류 상태(조회 오류 insightsError 와 분리). 재시도 의미를 섞지 않는다.
  const generateInsightError = ref<string | null>(null)
  // 크레딧 잔액 부족 차단 상태(CREDIT_INSUFFICIENT 안정 코드만).
  const creditBlocked = ref(false)

  // 알림 설정
  const alertConfigs = shallowRef<RevenueAlertConfig[]>([])
  const alertConfigLoading = ref(false)
  const alertConfigError = ref<string | null>(null)

  /**
   * 조회수로 가중한 평균 RPM. **조회가 측정된 행이 없으면 `null`** 이다.
   *
   * 플랫폼별 RPM 의 산술평균이 아니다. 조회수 100회 플랫폼과 100만회 플랫폼을 같은
   * 무게로 더하면 전체 평균이 아니라 아무것도 아닌 숫자가 나온다. 분자(수익)와
   * 분모(조회)를 **각각 합산한 뒤 한 번 나눈다.**
   *
   * 분모가 0 인 행은 서버가 `rpm = null` 로 준다. 그 행의 수익을 분자에만 더하면
   * 평균이 실제보다 높아지므로 행 자체를 제외한다 — 분자와 분모는 같은 표본에서 나와야 한다.
   */
  function weightedAverageRpm(source: CpmRpmResponse | null): number | null {
    if (!source) return null
    const measured = source.platforms.filter(
      (item) => item.views > 0 && typeof item.rpm === 'number' && Number.isFinite(item.rpm),
    )
    if (measured.length === 0) return null

    const totalViews = measured.reduce((sum, item) => sum + item.views, 0)
    if (totalViews <= 0) return null
    const totalRevenueKrw = measured.reduce((sum, item) => sum + item.revenueMicro / 1_000_000, 0)

    const rpm = (totalRevenueKrw / totalViews) * 1000
    return Number.isFinite(rpm) ? Math.round(rpm * 100) / 100 : null
  }

  function calculateSummary(data: RevenueData[], rpmSource: CpmRpmResponse | null): RevenueSummaryLocal {
    const averageRPM = weightedAverageRpm(rpmSource)
    // 불러오지 못한 것과 불러왔지만 표본이 없는 것은 다른 상태다. 화면이 구분해 말해야 한다.
    const averageRpmUnavailableReason: AvgRpmUnavailableReason | null =
      averageRPM !== null ? null : rpmSource === null ? 'loadFailed' : 'noViewSample'

    if (data.length === 0) {
      return {
        totalRevenue: 0,
        // 행이 하나도 없으면 비교할 이전 구간도 없다. 0 은 "변화 없음"이라는 주장이 된다.
        monthlyGrowth: null,
        averageRPM,
        averageRpmUnavailableReason,
        topPlatform: null,
        topPlatformRevenue: null,
      }
    }

    const totalRevenue = data.reduce((sum, item) => sum + item.total, 0)
    /*
     * 비교할 이전 구간이 없거나 그 값이 0 이면 성장률은 **정의되지 않는다.**
     * 0 으로 채우면 "변화 없음"이라는, 측정한 적 없는 사실을 주장하게 된다.
     * (이 값은 보통 아래에서 서버 `growthPercent` 로 덮이지만, 그 전 상태도 정직해야 한다.)
     */
    let monthlyGrowth: number | null = null
    if (data.length >= 2) {
      // 추세와 **같은 함수**를 쓴다. 판정이 갈라지면 KPI 와 추세가 다른 답을 말한다.
      monthlyGrowth = percentageChange(
        data[data.length - 1].total,
        data[data.length - 2].total,
      )
    }

    const platformTotals = data.reduce<Record<string, number>>((totals, item) => {
      for (const [platform, revenue] of Object.entries(item.platforms)) {
        totals[platform] = (totals[platform] ?? 0) + revenue
      }
      return totals
    }, {})

    /*
     * 씨앗 없이 **실제 집계에서만** 최댓값을 고른다. 예전에는 `{ platform: 'YOUTUBE',
     * revenue: 0 }` 을 씨앗으로 줘서, 집계가 비어 있으면 그 씨앗이 그대로 답이 됐다.
     * 연결한 적도 없는 YouTube 가 "최고 수익 플랫폼" 으로 떴다.
     */
    const topPlatformEntry = Object.entries(platformTotals).reduce<{ platform: string; revenue: number } | null>(
      (max, [platform, revenue]) => (max === null || revenue > max.revenue ? { platform, revenue } : max),
      null,
    )

    return {
      totalRevenue,
      // null 은 비교 불가다. 반올림하려다 0 으로 만들면 "변화 없음"이 되어 버린다.
      monthlyGrowth: monthlyGrowth === null ? null : Math.round(monthlyGrowth * 100) / 100,
      averageRPM,
      averageRpmUnavailableReason,
      topPlatform: topPlatformEntry?.platform ?? null,
      topPlatformRevenue: topPlatformEntry?.revenue ?? null,
    }
  }

  const totalAnnualRevenue = computed(() =>
    monthlyRevenue.value.reduce((sum, item) => sum + item.total, 0),
  )

  const platformBreakdown = computed(() => {
    const total = totalAnnualRevenue.value
    if (total === 0) return []

    const platformTotals = monthlyRevenue.value.reduce<Record<string, number>>((totals, item) => {
      for (const [platform, revenue] of Object.entries(item.platforms)) {
        // Naver Clip has no public upload/analytics API. Do not present legacy
        // rows as a currently supported revenue source.
        if (platform !== 'NAVER_CLIP') {
          totals[platform] = (totals[platform] ?? 0) + revenue
        }
      }
      return totals
    }, {})

    return Object.entries(platformTotals).map(([platform, revenue]) => ({
      platform: platform as Platform,
      revenue,
      percentage: Math.round((revenue / total) * 100 * 100) / 100,
    }))
  })

  /**
   * 마지막 6개 행의 **행 대 행** 증감률. `growth` 가 **`null` 이면 비교 불가**다.
   *
   * - 첫 포인트: 앞선 행이 없다 → `null`
   * - 직전 행 수익이 0: 비율의 기준이 없다 → `null`
   * - 그 밖: 실제 계산값(0%, 양수, 음수 모두 측정된 사실이므로 그대로 둔다)
   *
   * 소비자는 `null` 을 숫자·`NaN`·`0%` 로 그리면 안 된다. 첫 수익이 발생한 구간을
   * "0% 변화"로 위장하는 것이 이 값이 가진 원래 결함이었다.
   *
   * **[monthlyRevenue] 는 이름과 달리 일별 행**이므로 이 값도 일 대 일 비교다.
   * "월 성장률"로 라벨을 붙이면 안 된다. 서버가 주는 기간 대 기간 성장률은
   * `summary.monthlyGrowth`(백엔드 `growthPercent`)이며 별개의 값이다 —
   * 판정 규칙만 같고 대상 구간이 다르다.
   */
  const growthTrend = computed<{ period: string; growth: number | null }[]>(() => {
    if (monthlyRevenue.value.length < 2) return []

    const recent = monthlyRevenue.value.slice(-6)
    return recent.map((item, index) => {
      // 첫 포인트는 비교할 앞 구간이 없다. 0 으로 채우면 "변화 없음"이 되어 버린다.
      if (index === 0) return { period: item.period, growth: null }
      const previous = recent[index - 1].total
      const growth = percentageChange(item.total, previous)
      return {
        period: item.period,
        growth: growth === null ? null : Math.round(growth * 100) / 100,
      }
    })
  })

  /**
   * 선택한 기간의 수익을 불러온다.
   *
   * @param period `30d` / `90d` / `180d` / `365d` — **API 가 실제로 받는 일수**다.
   *   예전에는 화면이 `'1','3','6','12'`(개월)를 들고 있으면서 여기서는 항상 `'30d'` 만
   *   호출했다. 그래서 "1년"을 골라도 30일치가 오고, 화면은 그중 마지막 12행만 잘라
   *   **12일치를 1년 총수익으로** 보여줬다.
   */
  async function fetchRevenue(period: RevenuePeriod = DEFAULT_REVENUE_PERIOD) {
    /*
     * 늦게 도착한 이전 요청이 새 선택을 덮어쓰지 않게 한다.
     *
     * 기간 버튼을 빠르게 누르면 365일 요청이 30일 요청보다 늦게 끝날 수 있다. 순서만
     * 믿으면 화면 라벨은 30일인데 값은 365일이 된다 — 이번에 고치려는 것과 똑같은
     * 종류의 불일치다.
     */
    const requestId = ++activeRevenueRequestId
    const isStale = () => requestId !== activeRevenueRequestId

    loading.value = true
    loadError.value = false
    try {
      /*
       * cpm-rpm 을 **네 번째로** 함께 부른다. 개요 탭의 평균 RPM 카드에 쓸 조회수가
       * 이 응답에만 있기 때문이다(`summary`·`trends` 에는 조회수 필드가 없다).
       *
       * 다만 아래 원자적 스냅샷 판정에는 넣지 않는다. RPM 은 보조 지표이고, 그것 하나가
       * 실패했다고 수익 합계·추세까지 이전 기간으로 되돌리면 잃는 것이 더 크다.
       * 대신 실패는 RPM 카드에서 "불러오지 못함" 으로 드러난다.
       */
      const results = await Promise.allSettled([
        revenueApi.summary(period),
        revenueApi.trends(period),
        revenueApi.platformRevenue(period),
        revenueApi.cpmRpm(period),
      ])

      // 뒤늦게 온 응답이면 **아무것도 쓰지 않는다.** 로딩 플래그도 새 요청이 관리한다.
      if (isStale()) return

      /*
       * **셋 다 성공했을 때만 반영한다(원자적 스냅샷).**
       *
       * 예전에는 `loadedPeriod` 를 먼저 쓰고 각 응답을 개별로 반영했다. 새 기간의
       * summary 만 성공하고 trends 가 실패하면, 라벨은 새 기간인데 차트·표는 **이전
       * 기간 데이터**가 남는다. 기간 계약을 고치면서 없앤 바로 그 불일치가 부분 반영으로
       * 되살아난다.
       *
       * 부분 반영을 하려면 데이터마다 기간을 따로 추적하고 화면이 그것을 구분해 표시해야
       * 하는데, 그건 이 화면의 모든 카드가 서로 다른 기간을 말할 수 있다는 뜻이다.
       * 수익 화면에서는 **이전 기간 전체를 그대로 두고 오류를 알리는 쪽**이 정직하다.
       */
      const [summaryResult, trendsResult, platformResult, cpmRpmResult] = results
      if (
        summaryResult.status !== 'fulfilled' ||
        trendsResult.status !== 'fulfilled' ||
        platformResult.status !== 'fulfilled'
      ) {
        // 이전 스냅샷을 **그대로 둔다.** 장애가 0원이나 다른 기간처럼 보이면 안 된다.
        loadError.value = true
        return
      }

      {
        apiSummary.value = summaryResult.value
        apiTrends.value = trendsResult.value.data
        apiPlatformRevenue.value = platformResult.value

        // Build monthlyRevenue from trends API data grouped by date
        const trendsByDate = new Map<string, RevenueData>()
        for (const point of trendsResult.value.data) {
          const period = point.date
          if (!trendsByDate.has(period)) {
            trendsByDate.set(period, {
              period,
              platforms: {},
              total: 0,
            })
          }
          const entry = trendsByDate.get(period)!
          const amount = point.revenueKrw ?? 0
          const platform = point.platform?.toUpperCase() ?? 'UNKNOWN'
          // Legacy Naver Clip rows must not be presented as a supported,
          // provider-backed revenue source because no public analytics API is
          // available for it.
          if (platform === 'NAVER_CLIP') continue
          entry.platforms[platform] = (entry.platforms[platform] ?? 0) + amount
          entry.total += amount
        }
        monthlyRevenue.value = Array.from(trendsByDate.values())

        // cpm-rpm 은 실패해도 스냅샷을 막지 않는다. 실패면 `null` 이 들어가고
        // calculateSummary 가 그것을 "불러오지 못함" 으로 판정한다.
        const rpmSource = cpmRpmResult.status === 'fulfilled' ? (cpmRpmResult.value ?? null) : null
        if (rpmSource) cpmRpmData.value = rpmSource

        summary.value = calculateSummary(monthlyRevenue.value, rpmSource)
        // 요청한 기간의 권위 있는 합계는 summary 엔드포인트다. 위에서 셋 다 성공한
        // 경우에만 여기 오므로 trends 실패로 0 이 덮이는 일은 없다.
        const apiData = summaryResult.value
        /*
         * 씨앗 없이 실제 breakdown 에서만 고른다. 예전 씨앗 `{ 'YOUTUBE', 0 }` 은
         * breakdown 이 비어 있을 때 그대로 답이 됐고, 화면은 "최고 수익 플랫폼:
         * YouTube · ₩0" 을 실측처럼 보여줬다.
         *
         * `PLATFORM_CONFIG` 에 없는 플랫폼도 원문 그대로 남긴다. YouTube 로 바꿔치면
         * 실제로 관측된 플랫폼이 화면에서 사라진다.
         */
        const topPlatform = apiData.platformBreakdown.reduce<{ platform: string; revenue: number } | null>(
          (top, item) => (top === null || item.revenueKrw > top.revenue
            ? { platform: item.platform, revenue: item.revenueKrw }
            : top),
          null,
        )
        summary.value = {
          ...summary.value,
          totalRevenue: apiData.totalRevenueKrw,
          monthlyGrowth: apiData.growthPercent,
          topPlatform: topPlatform?.platform ?? null,
          topPlatformRevenue: topPlatform?.revenue ?? null,
        }

        /*
         * **라벨은 데이터가 모두 반영된 뒤에 바꾼다.**
         *
         * 먼저 쓰면 그 아래 어느 한 줄이라도 실패했을 때 라벨만 새 기간이 된다.
         * 이 줄이 스냅샷의 마지막이라는 사실 자체가 "여기까지 왔으면 전부 새 기간"
         * 이라는 보장이다.
         */
        loadedPeriod.value = period
      }
    } catch {
      // Keep the last successful values; an outage must not look like zero revenue.
      if (!isStale()) loadError.value = true
    } finally {
      // 늦게 끝난 이전 요청이 진행 중인 새 요청의 로딩을 끄면 화면이 다 불러온 것처럼 보인다.
      if (!isStale()) loading.value = false
    }
  }

  async function fetchCpmRpm(period = '30d') {
    cpmRpmLoading.value = true
    cpmRpmError.value = null
    try {
      cpmRpmData.value = await revenueApi.cpmRpm(period)
    } catch (error) {
      cpmRpmError.value =
        error instanceof Error ? error.message : 'CPM·RPM 데이터를 불러오지 못했습니다.'
    } finally {
      cpmRpmLoading.value = false
    }
  }

  async function fetchBrandDealRevenue(period = '90d') {
    brandDealLoading.value = true
    brandDealError.value = null
    try {
      brandDealData.value = await revenueApi.brandDealRevenue(period)
    } catch (error) {
      brandDealError.value =
        error instanceof Error ? error.message : '브랜드딜 수익을 불러오지 못했습니다.'
    } finally {
      brandDealLoading.value = false
    }
  }

  async function fetchInsights() {
    insightsLoading.value = true
    insightsError.value = null
    try {
      // 백엔드가 { insights, totalElements, page, size } 래퍼 반환
      const response = await revenueApi.insights()
      revenueInsights.value = response.insights
    } catch (error) {
      insightsError.value =
        error instanceof Error ? error.message : '수익 인사이트를 불러오지 못했습니다.'
    } finally {
      insightsLoading.value = false
    }
  }

  async function generateInsight() {
    generateInsightLoading.value = true
    // 매 호출마다 생성 오류·차단 상태를 초기화한다. 조회 오류(insightsError)는 건드리지 않는다.
    generateInsightError.value = null
    creditBlocked.value = false
    try {
      const insight = await revenueApi.generateInsight()
      revenueInsights.value = [insight, ...revenueInsights.value]
      return insight
    } catch (error) {
      // 크레딧 잔액 부족은 안정 코드로만 판단한다. 생성 오류 문구는 비워 CTA 만 보이게 한다.
      if (matchesCode(error, CREDIT_INSUFFICIENT)) {
        creditBlocked.value = true
        return null
      }
      // REVENUE_DATA_UNAVAILABLE·PLAN_LIMIT_EXCEEDED·403 등은 크레딧 부족으로 오인하지 않는다.
      // 일반 생성 오류는 실제 메시지를 보존한다.
      generateInsightError.value =
        error instanceof Error ? error.message : '수익 인사이트 생성에 실패했습니다.'
      return null
    } finally {
      generateInsightLoading.value = false
    }
  }

  async function fetchAlertConfigs() {
    alertConfigLoading.value = true
    alertConfigError.value = null
    try {
      // 백엔드가 { configs: [...] } 래퍼 반환
      const response = await revenueApi.alertConfigs()
      alertConfigs.value = response.configs
    } catch (error) {
      alertConfigError.value =
        error instanceof Error ? error.message : '수익 알림 설정을 불러오지 못했습니다.'
    } finally {
      alertConfigLoading.value = false
    }
  }

  async function saveAlertConfig(config: Omit<RevenueAlertConfig, 'id'>) {
    const saved = await revenueApi.saveAlertConfig(config)
    alertConfigs.value = [...alertConfigs.value, saved]
    return saved
  }

  async function updateAlertConfig(id: number, config: Partial<RevenueAlertConfig>) {
    const updated = await revenueApi.updateAlertConfig(id, config)
    alertConfigs.value = alertConfigs.value.map((c) => (c.id === id ? updated : c))
    return updated
  }

  async function deleteAlertConfig(id: number) {
    await revenueApi.deleteAlertConfig(id)
    alertConfigs.value = alertConfigs.value.filter((c) => c.id !== id)
  }

  return {
    monthlyRevenue,
    dailyRevenue,
    monthlyAggregates,
    loadedPeriod,
    summary,
    loading,
    loadError,
    totalAnnualRevenue,
    platformBreakdown,
    growthTrend,
    apiSummary,
    apiTrends,
    apiPlatformRevenue,
    fetchRevenue,
    cpmRpmData,
    brandDealData,
    cpmRpmLoading,
    brandDealLoading,
    cpmRpmError,
    brandDealError,
    fetchCpmRpm,
    fetchBrandDealRevenue,
    revenueInsights,
    insightsLoading,
    insightsError,
    generateInsightLoading,
    generateInsightError,
    creditBlocked,
    alertConfigs,
    alertConfigLoading,
    alertConfigError,
    fetchInsights,
    generateInsight,
    fetchAlertConfigs,
    saveAlertConfig,
    updateAlertConfig,
    deleteAlertConfig,
  }
})
