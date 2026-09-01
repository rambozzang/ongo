import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Competitor, CompetitorComparison, CompetitorVideo, CompetitorResponse, CompetitorTrendResponse, BenchmarkResponse, CompetitorInsightResult, CompetitorSyncResponse } from '@/types/competitor'
import { competitorApi } from '@/api/competitor'
import { CREDIT_INSUFFICIENT, matchesCode } from '@/composables/usePlanLimit'

/**
 * 비교할 수 없을 때 화면에 보여줄 이유.
 *
 * 서버도 `engagementRateUnavailableReason` 으로 같은 취지를 내려준다. 여기 기본값은
 * 아직 benchmark 응답을 못 받은 경우까지 덮는다 — 어느 쪽이든 숫자를 지어내지 않는다.
 */
export const ENGAGEMENT_UNAVAILABLE_REASON = '측정할 수 없어 비교할 수 없습니다'

/** 내 채널 통계를 아직 못 받았을 때. 지어낸 0 으로 비교하지 않는다. */
export const MY_STATS_UNAVAILABLE_REASON = '내 채널 데이터를 불러오지 못해 비교할 수 없습니다'

/**
 * 내 채널 통계. **모든 필드가 `null` 로 시작한다 — 아직 불러오지 않았다는 뜻이다.**
 *
 * 예전에는 0 으로 초기화했다. benchmark 응답이 오기 전이나 그 호출이 실패했을 때
 * `avgEngagement: 0` 이 그대로 비교표에 들어가 **"참여율: 나 0% vs 경쟁자 …"** 가 됐다.
 * 경쟁자 값을 null 로 고친 뒤에도 내 쪽이 0 이면 같은 종류의 거짓말이 남는다 —
 * 이번에는 내가 지고 있는 것처럼 보인다.
 *
 * `null` 은 "0" 이 아니라 "모른다" 다. 소비자는 비교·순위에 섞지 말아야 한다.
 */
interface MyStats {
  subscriberCount: number | null
  avgViews: number | null
  avgEngagement: number | null
  growthRate: number | null
}

// Map API response to local Competitor type
function mapResponseToCompetitor(resp: CompetitorResponse): Competitor {
  return {
    id: resp.id,
    name: resp.channelName,
    channelUrl: resp.channelUrl ?? '',
    platform: (resp.platform as Competitor['platform']) || 'YOUTUBE',
    /*
     * **없는 프로필 이미지를 지어내지 않는다.**
     *
     * 예전에는 외부 아바타 서비스의 URL을 id 로 조합해 넣었다. 그것은 그 서비스가
     * 주는 **무작위 인물 사진**이라, 화면에서는 그 경쟁 채널의 실제 프로필 이미지와
     * 구분되지 않았다. 사용자는 남의 얼굴을 그 채널의 것으로 읽는다.
     *
     * `null` 이면 컴포넌트가 로컬 placeholder 를 그린다.
     */
    avatarUrl: resp.profileImageUrl ?? null,
    subscriberCount: resp.subscriberCount,
    videoCount: resp.videoCount,
    avgViews: resp.avgViews,
    /*
     * 목록 API 는 참여율·성장률을 주지 않는다. **0 이 아니라 "아직 모른다"** 다.
     *
     * 예전에는 `growthRate: 0` 이었다. benchmark 가 아직 안 왔거나 실패한 초기 상태에서
     * 카드가 그것을 **"성장률 0%"** 와 하락 아이콘으로 그렸다 — `CompetitorCard` 는
     * `null` 일 때만 측정 불가를 표시하기 때문이다.
     *
     * benchmark 응답이 오면 성장률은 채워지고, 참여율은 산출 수단이 없어 계속 null 이다.
     */
    avgEngagement: null,
    growthRate: null,
    lastVideoAt: resp.lastSyncedAt ?? '',
    addedAt: resp.createdAt ?? '',
    isTracking: true,
  }
}

export const useCompetitorStore = defineStore('competitor', () => {
  const competitors = ref<Competitor[]>([])
  // 전부 null 로 시작한다. 불러오기 전의 0 은 측정값과 구분되지 않는다.
  const myStats = ref<MyStats>({
    subscriberCount: null,
    avgViews: null,
    avgEngagement: null,
    growthRate: null,
  })
  const competitorVideos = ref<CompetitorVideo[]>([])
  const trends = ref<CompetitorTrendResponse[]>([])
  const benchmark = ref<BenchmarkResponse | null>(null)
  const aiInsight = ref<CompetitorInsightResult | null>(null)
  const insightLoading = ref(false)
  // 실제 동기화 진행/실패 상태. 가짜 성공을 만들지 않는다 — 실패하면 그대로 보여준다.
  const syncing = ref(false)
  const syncError = ref<string | null>(null)
  // AI 인사이트: 일반 오류와 크레딧 부족을 구분한다.
  const insightError = ref<string | null>(null)
  const creditBlocked = ref(false)
  const loading = ref(false)
  const loadError = ref<string | null>(null)
  const lastSync = ref<CompetitorSyncResponse | null>(null)

  const trackedCompetitors = computed(() =>
    competitors.value.filter(c => c.isTracking)
  )

  /*
   * 구독자 수 상위 5. **재지 못한 경쟁사는 순위에 넣지 않는다.**
   *
   * 예전에는 `subscriberCount` 가 non-null 이라 0 으로 정렬돼 항상 맨 아래에 붙었고,
   * 화면에는 "구독자 0명" 인 채널로 보였다. 모르는 것은 줄 세울 수 없다.
   */
  const topCompetitors = computed(() =>
    competitors.value
      .filter((c): c is Competitor & { subscriberCount: number } => c.subscriberCount !== null)
      .sort((a, b) => b.subscriberCount - a.subscriberCount)
      .slice(0, 5)
  )

  const averageMetrics = computed(() => {
    const tracked = trackedCompetitors.value
    /*
     * **추적 중인 경쟁사가 없으면 평균이 성립하지 않는다.**
     *
     * 예전에는 `avgSubscribers: 0, avgViews: 0, avgGrowthRate: 0` 을 돌려줬다. 아무도
     * 추적하지 않는 상태가 화면에서 "평균 구독자 0명" 이라는 관측처럼 보였다.
     */
    if (tracked.length === 0) {
      return { avgSubscribers: null, avgViews: null, avgEngagement: null, avgGrowthRate: null }
    }
    /*
     * 측정 불가를 평균에 섞지 않는다.
     *
     * 예전에는 `sum + c.avgEngagement` 로 null 자리의 0 을 그대로 더해, 참여율을 하나도
     * 모르는 상태가 "평균 0%" 라는 측정 결과로 나왔다. 측정된 값만 모아 그 개수로
     * 나누고, 하나도 없으면 `null` 이다.
     */
    const measuredEngagement = tracked
      .map(c => c.avgEngagement)
      .filter((rate): rate is number => rate !== null)
    const measuredGrowth = tracked
      .map(c => c.growthRate)
      .filter((rate): rate is number => rate !== null)
    /*
     * **측정된 평균 조회수만 평균에 넣는다.** 영상이 0 건인 경쟁사는 서버가 `null` 을
     * 주는데, 그것을 0 으로 더하면 평균이 실제보다 낮아져 내가 앞서 보인다.
     */
    const measuredViews = tracked
      .map(c => c.avgViews)
      .filter((views): views is number => views !== null)
    const measuredSubscribers = tracked
      .map(c => c.subscriberCount)
      .filter((count): count is number => count !== null)

    return {
      /*
       * **측정된 구독자 수만 평균에 넣는다.** `avgViews`·`avgEngagement` 와 같은 이유다 —
       * 재지 못한 자리를 0 으로 더하면 평균이 실제보다 낮아져 내가 앞서 보인다.
       */
      avgSubscribers: measuredSubscribers.length > 0
        ? Math.round(measuredSubscribers.reduce((sum, count) => sum + count, 0) / measuredSubscribers.length)
        : null,
      avgViews: measuredViews.length > 0
        ? Math.round(measuredViews.reduce((sum, views) => sum + views, 0) / measuredViews.length)
        : null,
      avgEngagement: measuredEngagement.length > 0
        ? Number((measuredEngagement.reduce((sum, rate) => sum + rate, 0) / measuredEngagement.length).toFixed(1))
        : null,
      /*
       * **측정된 성장률만 평균에 넣는다.** `avgEngagement` 와 같은 이유다 —
       * `null` 을 0 으로 더하면 수집 이력이 없는 경쟁사가 평균을 끌어내려,
       * 내가 실제보다 앞서 보인다.
       */
      avgGrowthRate: measuredGrowth.length > 0
        ? Number((measuredGrowth.reduce((sum, rate) => sum + rate, 0) / measuredGrowth.length).toFixed(1))
        : null,
    }
  })

  /**
   * 구독자 수 기준 내 순위. **내 통계를 아직 모르면 `null`** — 순위를 만들 수 없다.
   *
   * 예전에는 불러오기 전 `subscriberCount = 0` 으로 순위를 매겨 항상 꼴찌가 나왔다.
   */
  const myRanking = computed<number | null>(() => {
    const mine = myStats.value.subscriberCount
    if (mine === null) return null
    // 구독자 수를 모르는 경쟁사는 줄을 세울 수 없다 — 순위 계산에서 뺀다.
    const measured = competitors.value
      .map(c => c.subscriberCount)
      .filter((count): count is number => count !== null)
    const allChannels = [mine, ...measured].sort((a, b) => b - a)
    return allChannels.indexOf(mine) + 1
  })

  async function fetchCompetitors(): Promise<boolean> {
    loading.value = true
    loadError.value = null
    try {
      const [listResult, benchmarkResult] = await Promise.all([
        competitorApi.list(),
        competitorApi.benchmark().catch(() => null),
      ])
      competitors.value = listResult.competitors.map(mapResponseToCompetitor)
      if (benchmarkResult) {
        benchmark.value = benchmarkResult
        myStats.value = {
          // 서버가 `null` 을 주면 구독자 수를 조회하는 채널이 하나도 없다는 뜻이다.
          // `?? 0` 으로 채우면 순위가 나를 항상 꼴찌로 매기고 비교표가 "구독자 0명" 을
          // 측정 결과로 그린다 — 아래 `avgEngagement` 와 같은 계약이다.
          subscriberCount: benchmarkResult.myStats.subscriberCount,
          avgViews: benchmarkResult.myStats.avgViews,
          avgEngagement: benchmarkResult.myStats.engagementRate,
          growthRate: benchmarkResult.myStats.growthRate,
        }
        for (const comp of competitors.value) {
          const bm = benchmarkResult.competitors.find(b => b.id === comp.id)
          if (bm) {
            comp.growthRate = bm.growthRate
            // 서버가 산출할 수 없다고 말하면(null) 그대로 둔다. 0 으로 채우지 않는다.
            comp.avgEngagement = bm.engagementRate
          }
        }
      }
      return true
    } catch (e) {
      /*
       * 목록 조회가 실패했다. 경쟁자를 비우되 **myStats 에 숫자를 만들어 넣지 않는다.**
       * null 로 남으면 비교표가 "비교 불가"를 보여주고, 0 을 넣으면 측정 결과가 된다.
       */
      competitors.value = []
      benchmark.value = null
      loadError.value = e instanceof Error ? e.message : '경쟁 채널 목록을 불러오지 못했습니다.'
      return false
    } finally {
      loading.value = false
    }
  }

  async function addCompetitor(competitor: Omit<Competitor, 'id' | 'addedAt'>) {
    const result = await competitorApi.add({
      platform: competitor.platform,
      platformChannelId: competitor.channelUrl || `ch_${Date.now()}`,
      channelName: competitor.name,
      channelUrl: competitor.channelUrl,
      // 측정되지 않은 구독자 수는 보내지 않는다. `?? 0` 을 하면 "구독자 0명" 으로 저장된다.
      subscriberCount: competitor.subscriberCount ?? undefined,
      // 측정되지 않은 영상 수는 보내지 않는다. `?? 0` 을 하면 "영상 0개" 로 저장된다.
      videoCount: competitor.videoCount ?? undefined,
      // 측정되지 않은 평균은 보내지 않는다. `?? 0` 을 하면 서버에 "평균 0회" 로 저장된다.
      avgViews: competitor.avgViews ?? undefined,
      // 프로필 이미지가 없으면 필드를 보내지 않는다. 빈 문자열을 저장하면 화면이
      // `src=''` 로 렌더링해 브라우저가 현재 페이지를 다시 요청한다.
      profileImageUrl: competitor.avatarUrl ?? undefined,
    })
    competitors.value.push(mapResponseToCompetitor(result))
  }

  async function removeCompetitor(id: number) {
    await competitorApi.remove(id)
    competitors.value = competitors.value.filter(c => c.id !== id)
  }

  function toggleTracking(id: number) {
    const competitor = competitors.value.find(c => c.id === id)
    if (competitor) {
      competitor.isTracking = !competitor.isTracking
    }
  }

  function refreshData() {
    fetchCompetitors()
  }

  /**
   * 내 채널과 경쟁자를 지표별로 비교한다.
   *
   * **측정 불가는 숫자로 만들지 않는다.** 참여율은 공개 API 로 경쟁 채널의 좋아요·댓글을
   * 얻을 수 없어 서버가 `null` 을 준다. 예전에는 그 자리에 0 이 들어가 모든 비교에서
   * 내가 이기는 것처럼 보였다 — 있지도 않은 우위를 근거로 전략을 세우게 된다.
   */
  function getComparison(competitorId: number): CompetitorComparison[] {
    const competitor = competitors.value.find(c => c.id === competitorId)
    if (!competitor) return []

    const metrics: { metric: string; myValue: number | null; competitorValue: number | null }[] = [
      { metric: '구독자', myValue: myStats.value.subscriberCount, competitorValue: competitor.subscriberCount },
      { metric: '평균 조회수', myValue: myStats.value.avgViews, competitorValue: competitor.avgViews },
      { metric: '참여율', myValue: myStats.value.avgEngagement, competitorValue: competitor.avgEngagement },
      { metric: '성장률', myValue: myStats.value.growthRate, competitorValue: competitor.growthRate },
    ]

    return metrics.map(m => {
      const comparable = m.myValue !== null && m.competitorValue !== null
      if (!comparable) {
        return {
          ...m,
          difference: null,
          differencePercent: null,
          comparable: false,
          // 어느 쪽이 없는지 구분한다. "내 데이터 로딩 실패"와 "애초에 못 구하는 값"은
          // 사용자가 할 수 있는 일이 다르다 — 앞은 새로고침, 뒤는 기다려도 안 된다.
          unavailableReason: m.myValue === null
            ? MY_STATS_UNAVAILABLE_REASON
            : ENGAGEMENT_UNAVAILABLE_REASON,
        }
      }
      const mine = m.myValue as number
      const theirs = m.competitorValue as number
      return {
        ...m,
        difference: mine - theirs,
        // 기준이 0 이면 비율을 만들 수 없다. 0 을 넣으면 "차이 없음"이 되어 버린다.
        differencePercent: theirs > 0
          ? Number((((mine - theirs) / theirs) * 100).toFixed(1))
          : null,
        comparable: true,
      }
    })
  }


  function getCompetitorVideos(competitorId?: number): CompetitorVideo[] {
    if (competitorId) {
      return competitorVideos.value.filter(v => v.competitorId === competitorId)
    }
    return competitorVideos.value
  }

  async function fetchTrends(competitorIds: number[] = [], days = 30) {
    try {
      trends.value = await competitorApi.trends(competitorIds, days)
    } catch {
      trends.value = []
    }
  }

  async function syncCompetitors(): Promise<CompetitorSyncResponse | null> {
    syncing.value = true
    syncError.value = null
    try {
      // api.sync() 는 CompetitorSyncResponse(실제 건수)를 그대로 돌려준다.
      const result = await competitorApi.sync()
      // 동기화 성공 시 최신 목록·벤치마크를 다시 받는다. 실패는 syncError 로 노출.
      const refreshed = await fetchCompetitors()
      if (!refreshed) {
        // 동기화 API 자체는 성공했어도 화면이 최신 결과를 읽지 못하면 성공으로
        // 알리지 않는다. 그렇지 않으면 사용자는 최신 데이터가 보인다고 믿고
        // 유료 인사이트를 실행할 수 있다.
        syncError.value = loadError.value || '동기화 후 최신 경쟁사 데이터를 불러오지 못했습니다.'
        return null
      }
      // 실제 응답 수치를 보존한다(성공을 무조건 "완료"로 치지 않는다).
      lastSync.value = result
      return result
    } catch (e) {
      syncError.value = e instanceof Error ? e.message : '경쟁사 데이터 동기화에 실패했습니다.'
      return null
    } finally {
      syncing.value = false
    }
  }

  async function fetchInsight() {
    insightLoading.value = true
    // 매 호출마다 크레딧 차단·일반 오류 상태를 초기화한다.
    creditBlocked.value = false
    insightError.value = null
    aiInsight.value = null
    try {
      aiInsight.value = await competitorApi.insight()
    } catch (e) {
      // 크레딧 잔액 부족은 안정 코드로만 판단한다. 일반 오류와 섞지 않는다.
      if (matchesCode(e, CREDIT_INSUFFICIENT)) {
        creditBlocked.value = true
        insightError.value = null
        return
      }
      insightError.value = e instanceof Error ? e.message : 'AI 인사이트 생성에 실패했습니다.'
      console.error('AI 인사이트 생성 실패:', e)
    } finally {
      insightLoading.value = false
    }
  }

  return {
    competitors,
    myStats,
    competitorVideos,
    loading,
    loadError,
    trackedCompetitors,
    topCompetitors,
    averageMetrics,
    myRanking,
    addCompetitor,
    removeCompetitor,
    toggleTracking,
    refreshData,
    getComparison,
    getCompetitorVideos,
    fetchCompetitors,
    trends,
    benchmark,
    aiInsight,
    insightLoading,
    syncing,
    syncError,
    lastSync,
    insightError,
    creditBlocked,
    fetchTrends,
    fetchInsight,
    syncCompetitors,
  }
})
