import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  Competitor,
  CompetitorComparison,
  ContentGap,
  TrendingTopic,
  AnalysisPeriod,
} from '@/types/competitorAnalysis'
import { competitorAnalysisApi } from '@/api/competitorAnalysis'

export const useCompetitorAnalysisStore = defineStore('competitorAnalysis', () => {
  const competitors = ref<Competitor[]>([])
  const comparison = ref<CompetitorComparison | null>(null)
  const contentGaps = ref<ContentGap[]>([])
  const trendingTopics = ref<TrendingTopic[]>([])
  const selectedPeriod = ref<AnalysisPeriod>('30d')
  const activeTab = ref<'overview' | 'gaps' | 'trends'>('overview')
  const processing = ref(false)
  const error = ref<string | null>(null)

  const highOpportunityGaps = computed(() =>
    contentGaps.value.filter(g => g.opportunity === 'HIGH'),
  )

  const topTrending = computed(() =>
    [...trendingTopics.value].sort((a, b) => b.growth - a.growth).slice(0, 10),
  )

  async function fetchCompetitors() {
    processing.value = true
    error.value = null
    try {
      competitors.value = await competitorAnalysisApi.getCompetitors()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load competitors'
    } finally {
      processing.value = false
    }
  }

  async function addCompetitor(name: string, platformUrls: { platform: string; url: string }[]) {
    processing.value = true
    error.value = null
    try {
      const competitor = await competitorAnalysisApi.addCompetitor({ name, platformUrls: platformUrls as any })
      competitors.value.push(competitor)
      return competitor
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to add competitor'
      throw e
    } finally {
      processing.value = false
    }
  }

  async function removeCompetitor(competitorId: number) {
    try {
      await competitorAnalysisApi.removeCompetitor(competitorId)
      competitors.value = competitors.value.filter(c => c.id !== competitorId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to remove competitor'
    }
  }

  async function fetchComparison() {
    processing.value = true
    error.value = null
    try {
      comparison.value = await competitorAnalysisApi.getComparison(selectedPeriod.value)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load comparison'
    } finally {
      processing.value = false
    }
  }

  async function fetchContentGaps() {
    try {
      contentGaps.value = await competitorAnalysisApi.getContentGaps()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load content gaps'
    }
  }

  async function fetchTrendingTopics() {
    try {
      trendingTopics.value = await competitorAnalysisApi.getTrendingTopics()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load trending topics'
    }
  }

  function setActiveTab(tab: 'overview' | 'gaps' | 'trends') {
    activeTab.value = tab
  }

  function setPeriod(period: AnalysisPeriod) {
    selectedPeriod.value = period
  }

  return {
    competitors,
    comparison,
    contentGaps,
    trendingTopics,
    selectedPeriod,
    activeTab,
    processing,
    error,
    highOpportunityGaps,
    topTrending,
    fetchCompetitors,
    addCompetitor,
    removeCompetitor,
    fetchComparison,
    fetchContentGaps,
    fetchTrendingTopics,
    setActiveTab,
    setPeriod,
  }
})
