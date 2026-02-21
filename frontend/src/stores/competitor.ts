import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Competitor, CompetitorComparison, CompetitorVideo, CompetitorResponse, CompetitorTrendResponse, BenchmarkResponse, CompetitorInsightResult } from '@/types/competitor'
import { competitorApi } from '@/api/competitor'

interface MyStats {
  subscriberCount: number
  avgViews: number
  avgEngagement: number
  growthRate: number
}

// Map API response to local Competitor type
function mapResponseToCompetitor(resp: CompetitorResponse): Competitor {
  return {
    id: resp.id,
    name: resp.channelName,
    channelUrl: resp.channelUrl ?? '',
    platform: (resp.platform as Competitor['platform']) || 'YOUTUBE',
    avatarUrl: resp.profileImageUrl ?? `https://i.pravatar.cc/150?img=${resp.id}`,
    subscriberCount: resp.subscriberCount,
    videoCount: resp.videoCount,
    avgViews: resp.avgViews,
    avgEngagement: 0,
    growthRate: 0,
    lastVideoAt: resp.lastSyncedAt ?? '',
    addedAt: resp.createdAt ?? '',
    isTracking: true,
  }
}

export const useCompetitorStore = defineStore('competitor', () => {
  const competitors = ref<Competitor[]>([])
  const myStats = ref<MyStats>({
    subscriberCount: 0,
    avgViews: 0,
    avgEngagement: 0,
    growthRate: 0,
  })
  const competitorVideos = ref<CompetitorVideo[]>([])
  const trends = ref<CompetitorTrendResponse[]>([])
  const benchmark = ref<BenchmarkResponse | null>(null)
  const aiInsight = ref<CompetitorInsightResult | null>(null)
  const insightLoading = ref(false)

  const trackedCompetitors = computed(() =>
    competitors.value.filter(c => c.isTracking)
  )

  const topCompetitors = computed(() =>
    [...competitors.value]
      .sort((a, b) => b.subscriberCount - a.subscriberCount)
      .slice(0, 5)
  )

  const averageMetrics = computed(() => {
    const tracked = trackedCompetitors.value
    if (tracked.length === 0) {
      return { avgSubscribers: 0, avgViews: 0, avgEngagement: 0, avgGrowthRate: 0 }
    }
    return {
      avgSubscribers: Math.round(tracked.reduce((sum, c) => sum + c.subscriberCount, 0) / tracked.length),
      avgViews: Math.round(tracked.reduce((sum, c) => sum + c.avgViews, 0) / tracked.length),
      avgEngagement: Number((tracked.reduce((sum, c) => sum + c.avgEngagement, 0) / tracked.length).toFixed(1)),
      avgGrowthRate: Number((tracked.reduce((sum, c) => sum + c.growthRate, 0) / tracked.length).toFixed(1)),
    }
  })

  const myRanking = computed(() => {
    const allChannels = [
      { subscriberCount: myStats.value.subscriberCount },
      ...competitors.value,
    ].sort((a, b) => b.subscriberCount - a.subscriberCount)
    return allChannels.findIndex(c => c.subscriberCount === myStats.value.subscriberCount) + 1
  })

  async function fetchCompetitors() {
    try {
      const [listResult, benchmarkResult] = await Promise.all([
        competitorApi.list(),
        competitorApi.benchmark().catch(() => null),
      ])
      competitors.value = listResult.competitors.map(mapResponseToCompetitor)
      if (benchmarkResult) {
        benchmark.value = benchmarkResult
        myStats.value = {
          subscriberCount: benchmarkResult.myStats.subscriberCount,
          avgViews: benchmarkResult.myStats.avgViews,
          avgEngagement: benchmarkResult.myStats.engagementRate,
          growthRate: benchmarkResult.myStats.growthRate,
        }
        for (const comp of competitors.value) {
          const bm = benchmarkResult.competitors.find(b => b.id === comp.id)
          if (bm) {
            comp.growthRate = bm.growthRate
          }
        }
      }
    } catch {
      competitors.value = []
    }
  }

  async function addCompetitor(competitor: Omit<Competitor, 'id' | 'addedAt'>) {
    const result = await competitorApi.add({
      platform: competitor.platform,
      platformChannelId: competitor.channelUrl || `ch_${Date.now()}`,
      channelName: competitor.name,
      channelUrl: competitor.channelUrl,
      subscriberCount: competitor.subscriberCount,
      videoCount: competitor.videoCount,
      avgViews: competitor.avgViews,
      profileImageUrl: competitor.avatarUrl,
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

  function getComparison(competitorId: number): CompetitorComparison[] {
    const competitor = competitors.value.find(c => c.id === competitorId)
    if (!competitor) return []

    const metrics = [
      { metric: '구독자', myValue: myStats.value.subscriberCount, competitorValue: competitor.subscriberCount },
      { metric: '평균 조회수', myValue: myStats.value.avgViews, competitorValue: competitor.avgViews },
      { metric: '참여율', myValue: myStats.value.avgEngagement, competitorValue: competitor.avgEngagement },
      { metric: '성장률', myValue: myStats.value.growthRate, competitorValue: competitor.growthRate },
    ]

    return metrics.map(m => ({
      ...m,
      difference: m.myValue - m.competitorValue,
      differencePercent: m.competitorValue > 0
        ? Number((((m.myValue - m.competitorValue) / m.competitorValue) * 100).toFixed(1))
        : 0,
    }))
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

  async function fetchInsight() {
    insightLoading.value = true
    try {
      aiInsight.value = await competitorApi.insight()
    } catch (e) {
      console.error('AI 인사이트 생성 실패:', e)
    } finally {
      insightLoading.value = false
    }
  }

  return {
    competitors,
    myStats,
    competitorVideos,
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
    fetchTrends,
    fetchInsight,
  }
})
