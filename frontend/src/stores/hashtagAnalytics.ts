import { defineStore } from 'pinia'
import { ref } from 'vue'
import { hashtagAnalyticsApi } from '@/api/hashtagAnalytics'
import type {
  HashtagPerformance,
  HashtagRecommendation,
  HashtagGroup,
  HashtagAnalyticsSummary,
  AnalyzeHashtagsRequest,
} from '@/types/hashtagAnalytics'

export const useHashtagAnalyticsStore = defineStore('hashtagAnalytics', () => {
  const performances = ref<HashtagPerformance[]>([])
  const recommendations = ref<HashtagRecommendation[]>([])
  const groups = ref<HashtagGroup[]>([])
  const summary = ref<HashtagAnalyticsSummary>({
    totalHashtags: 0,
    trendingCount: 0,
    avgEngagementRate: 0,
    topPerformer: '',
    groupCount: 0,
  })
  const isLoading = ref(false)

  // Mock data
  const mockPerformances: HashtagPerformance[] = [
    {
      id: 1,
      hashtag: '#브이로그',
      platform: 'YOUTUBE',
      usageCount: 45,
      totalViews: 2500000,
      avgEngagement: 8.7,
      growthRate: 12.3,
      trendDirection: 'UP',
      category: '라이프스타일',
      lastUsedAt: '2025-03-10T10:00:00Z',
      createdAt: '2024-01-01T00:00:00Z',
    },
    {
      id: 2,
      hashtag: '#먹방',
      platform: 'YOUTUBE',
      usageCount: 32,
      totalViews: 1800000,
      avgEngagement: 7.2,
      growthRate: -3.1,
      trendDirection: 'DOWN',
      category: '음식',
      lastUsedAt: '2025-03-08T14:00:00Z',
      createdAt: '2024-02-15T00:00:00Z',
    },
    {
      id: 3,
      hashtag: '#OOTD',
      platform: 'INSTAGRAM',
      usageCount: 28,
      totalViews: 950000,
      avgEngagement: 11.5,
      growthRate: 25.8,
      trendDirection: 'UP',
      category: '패션',
      lastUsedAt: '2025-03-09T08:00:00Z',
      createdAt: '2024-03-01T00:00:00Z',
    },
    {
      id: 4,
      hashtag: '#코딩튜토리얼',
      platform: 'YOUTUBE',
      usageCount: 18,
      totalViews: 620000,
      avgEngagement: 6.8,
      growthRate: 0.5,
      trendDirection: 'STABLE',
      category: '교육',
      lastUsedAt: '2025-03-07T16:00:00Z',
      createdAt: '2024-04-01T00:00:00Z',
    },
    {
      id: 5,
      hashtag: '#여행브이로그',
      platform: 'TIKTOK',
      usageCount: 22,
      totalViews: 3200000,
      avgEngagement: 14.2,
      growthRate: 45.1,
      trendDirection: 'UP',
      category: '여행',
      lastUsedAt: '2025-03-10T12:00:00Z',
      createdAt: '2024-05-01T00:00:00Z',
    },
  ]

  const mockGroups: HashtagGroup[] = [
    {
      id: 1,
      name: '브이로그 세트',
      hashtags: ['#브이로그', '#일상', '#데일리', '#vlog', '#daily'],
      platform: 'YOUTUBE',
      usageCount: 23,
      createdAt: '2024-06-01T00:00:00Z',
    },
    {
      id: 2,
      name: '먹방 세트',
      hashtags: ['#먹방', '#맛집', '#foodie', '#mukbang'],
      platform: 'YOUTUBE',
      usageCount: 15,
      createdAt: '2024-07-01T00:00:00Z',
    },
  ]

  const mockSummary: HashtagAnalyticsSummary = {
    totalHashtags: 87,
    trendingCount: 12,
    avgEngagementRate: 8.4,
    topPerformer: '#여행브이로그',
    groupCount: 5,
  }

  async function fetchPerformances(platform?: string) {
    isLoading.value = true
    try {
      performances.value = await hashtagAnalyticsApi.getPerformances(platform)
    } catch {
      performances.value = platform
        ? mockPerformances.filter((p) => p.platform === platform)
        : mockPerformances
    } finally {
      isLoading.value = false
    }
  }

  async function analyzeHashtags(request: AnalyzeHashtagsRequest) {
    isLoading.value = true
    try {
      recommendations.value = await hashtagAnalyticsApi.analyzeHashtags(request)
    } catch {
      recommendations.value = [
        { hashtag: `#${request.topic}`, relevanceScore: 95, expectedReach: 500000, competition: 'MEDIUM', reason: '주제와 직접 관련된 인기 해시태그' },
        { hashtag: `#${request.topic}팁`, relevanceScore: 82, expectedReach: 120000, competition: 'LOW', reason: '교육 콘텐츠에 효과적인 롱테일 해시태그' },
        { hashtag: '#추천', relevanceScore: 70, expectedReach: 800000, competition: 'HIGH', reason: '범용 인기 해시태그로 노출 극대화' },
      ]
    } finally {
      isLoading.value = false
    }
  }

  async function fetchGroups() {
    try {
      groups.value = await hashtagAnalyticsApi.getGroups()
    } catch {
      groups.value = mockGroups
    }
  }

  async function createGroup(name: string, hashtags: string[], platform: string) {
    try {
      const group = await hashtagAnalyticsApi.createGroup(name, hashtags, platform)
      groups.value.push(group)
    } catch {
      groups.value.push({
        id: Date.now(),
        name,
        hashtags,
        platform,
        usageCount: 0,
        createdAt: new Date().toISOString(),
      })
    }
  }

  async function deleteGroup(id: number) {
    try {
      await hashtagAnalyticsApi.deleteGroup(id)
    } catch {
      // mock fallback
    }
    groups.value = groups.value.filter((g) => g.id !== id)
  }

  async function fetchSummary() {
    try {
      summary.value = await hashtagAnalyticsApi.getSummary()
    } catch {
      summary.value = mockSummary
    }
  }

  return {
    performances,
    recommendations,
    groups,
    summary,
    isLoading,
    fetchPerformances,
    analyzeHashtags,
    fetchGroups,
    createGroup,
    deleteGroup,
    fetchSummary,
  }
})
