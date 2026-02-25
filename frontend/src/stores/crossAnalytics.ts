import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  CrossPlatformReport,
  CrossPlatformContent,
  PlatformPerformanceSummary,
} from '@/types/crossAnalytics'
import { crossAnalyticsApi } from '@/api/crossAnalytics'

// ── Mock data ────────────────────────────────────────────────────────

function generateMockReport(): CrossPlatformReport {
  const contents: CrossPlatformContent[] = [
    {
      id: 1,
      title: '주말 카페 브이로그',
      thumbnailUrl: '',
      publishedAt: new Date(Date.now() - 86400000 * 3).toISOString(),
      platforms: [
        { platform: 'youtube', videoId: 'yt1', views: 45200, likes: 3200, comments: 280, shares: 150, avgWatchTime: 245, ctr: 8.2, publishedAt: new Date(Date.now() - 86400000 * 3).toISOString() },
        { platform: 'tiktok', videoId: 'tk1', views: 128000, likes: 12400, comments: 890, shares: 3200, avgWatchTime: 42, ctr: 12.5, publishedAt: new Date(Date.now() - 86400000 * 3).toISOString() },
        { platform: 'instagram', videoId: 'ig1', views: 32000, likes: 4500, comments: 320, shares: 180, avgWatchTime: 28, ctr: 6.8, publishedAt: new Date(Date.now() - 86400000 * 3).toISOString() },
      ],
    },
    {
      id: 2,
      title: '서울 맛집 TOP 5',
      thumbnailUrl: '',
      publishedAt: new Date(Date.now() - 86400000 * 7).toISOString(),
      platforms: [
        { platform: 'youtube', videoId: 'yt2', views: 89000, likes: 6800, comments: 520, shares: 410, avgWatchTime: 380, ctr: 9.5, publishedAt: new Date(Date.now() - 86400000 * 7).toISOString() },
        { platform: 'tiktok', videoId: 'tk2', views: 256000, likes: 28000, comments: 1800, shares: 8500, avgWatchTime: 55, ctr: 15.2, publishedAt: new Date(Date.now() - 86400000 * 7).toISOString() },
        { platform: 'naverClip', videoId: 'nc2', views: 18000, likes: 1200, comments: 85, shares: 45, avgWatchTime: 65, ctr: 5.1, publishedAt: new Date(Date.now() - 86400000 * 7).toISOString() },
      ],
    },
    {
      id: 3,
      title: '일주일 식단 관리 루틴',
      thumbnailUrl: '',
      publishedAt: new Date(Date.now() - 86400000 * 14).toISOString(),
      platforms: [
        { platform: 'youtube', videoId: 'yt3', views: 62000, likes: 4800, comments: 380, shares: 290, avgWatchTime: 420, ctr: 7.8, publishedAt: new Date(Date.now() - 86400000 * 14).toISOString() },
        { platform: 'instagram', videoId: 'ig3', views: 28000, likes: 3600, comments: 210, shares: 120, avgWatchTime: 32, ctr: 7.2, publishedAt: new Date(Date.now() - 86400000 * 14).toISOString() },
      ],
    },
  ]

  const platformSummaries: PlatformPerformanceSummary[] = [
    { platform: 'youtube', totalViews: 196200, totalLikes: 14800, avgEngagement: 7.5, avgCtr: 8.5, contentCount: 3, bestPerformingContentId: 2 },
    { platform: 'tiktok', totalViews: 384000, totalLikes: 40400, avgEngagement: 10.5, avgCtr: 13.8, contentCount: 2, bestPerformingContentId: 2 },
    { platform: 'instagram', totalViews: 60000, totalLikes: 8100, avgEngagement: 13.5, avgCtr: 7.0, contentCount: 2, bestPerformingContentId: 1 },
    { platform: 'naverClip', totalViews: 18000, totalLikes: 1200, avgEngagement: 6.7, avgCtr: 5.1, contentCount: 1, bestPerformingContentId: 2 },
  ]

  return {
    id: 1,
    period: '2024-01',
    contents,
    platformSummaries,
    audienceOverlap: [
      { platforms: ['youtube', 'tiktok'], overlapPercent: 32 },
      { platforms: ['youtube', 'instagram'], overlapPercent: 45 },
      { platforms: ['tiktok', 'instagram'], overlapPercent: 28 },
      { platforms: ['youtube', 'naverClip'], overlapPercent: 18 },
    ],
    recommendations: [
      'TikTok에서 높은 도달률을 보이고 있으니 숏폼 콘텐츠를 강화하세요.',
      'YouTube와 Instagram 오디언스 중복이 45%로 높아, 차별화된 콘텐츠 전략을 고려하세요.',
      'Naver Clip의 CTR이 낮으니 썸네일 최적화를 시도해보세요.',
      '맛집 콘텐츠가 모든 플랫폼에서 우수한 성과를 보이고 있습니다.',
    ],
    generatedAt: new Date().toISOString(),
  }
}

export const useCrossAnalyticsStore = defineStore('crossAnalytics', () => {
  const report = ref<CrossPlatformReport | null>(null)
  const selectedPeriod = ref('2024-01')
  const loading = ref(false)
  const activeTab = ref<'overview' | 'contents' | 'audience'>('overview')

  const bestPlatform = computed<PlatformPerformanceSummary | null>(() => {
    if (!report.value) return null
    return [...report.value.platformSummaries].sort((a, b) => b.totalViews - a.totalViews)[0] ?? null
  })

  const totalViewsAllPlatforms = computed(() => {
    if (!report.value) return 0
    return report.value.platformSummaries.reduce((sum, p) => sum + p.totalViews, 0)
  })

  const platformViewShare = computed(() => {
    if (!report.value || totalViewsAllPlatforms.value === 0) return []
    return report.value.platformSummaries.map((p) => ({
      platform: p.platform,
      share: Math.round((p.totalViews / totalViewsAllPlatforms.value) * 100),
    }))
  })

  async function fetchReport(period?: string) {
    loading.value = true
    if (period) selectedPeriod.value = period
    try {
      report.value = await crossAnalyticsApi.getReport(selectedPeriod.value)
    } catch {
      report.value = generateMockReport()
    } finally {
      loading.value = false
    }
  }

  return {
    report,
    selectedPeriod,
    loading,
    activeTab,
    bestPlatform,
    totalViewsAllPlatforms,
    platformViewShare,
    fetchReport,
  }
})
