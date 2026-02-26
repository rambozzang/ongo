import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ContentSeries, SeriesAnalytics, CreateSeriesRequest } from '@/types/contentSeries'
import { contentSeriesApi } from '@/api/contentSeries'

function generateMockSeries(): ContentSeries[] {
  return [
    {
      id: 1, title: '주간 먹방 탐험', description: '매주 새로운 맛집을 탐험하는 시리즈', status: 'ACTIVE',
      platform: 'youtube', frequency: 'WEEKLY', totalEpisodes: 24, publishedEpisodes: 18, avgViews: 35000,
      avgRetention: 62, totalViews: 630000, tags: ['먹방', '맛집', '브이로그'],
      episodes: Array.from({ length: 18 }, (_, i) => ({
        id: i + 1, seriesId: 1, episodeNumber: i + 1, title: `EP.${i + 1} ${['강남', '홍대', '이태원', '성수', '을지로'][i % 5]} 맛집`,
        status: 'PUBLISHED' as const, platform: 'youtube', views: 25000 + Math.floor(Math.random() * 20000),
        likes: 800 + Math.floor(Math.random() * 500), comments: 50 + Math.floor(Math.random() * 80),
        publishedDate: new Date(Date.now() - (17 - i) * 7 * 86400000).toISOString(),
      })),
      createdAt: new Date(Date.now() - 126 * 86400000).toISOString(), updatedAt: new Date().toISOString(),
    },
    {
      id: 2, title: '1분 뷰티 꿀팁', description: '짧고 유용한 뷰티 팁 시리즈', status: 'ACTIVE',
      platform: 'tiktok', frequency: 'DAILY', totalEpisodes: 60, publishedEpisodes: 45, avgViews: 52000,
      avgRetention: 78, totalViews: 2340000, tags: ['뷰티', '꿀팁', '숏폼'],
      episodes: Array.from({ length: 45 }, (_, i) => ({
        id: 100 + i, seriesId: 2, episodeNumber: i + 1, title: `뷰티팁 #${i + 1}`,
        status: 'PUBLISHED' as const, platform: 'tiktok', views: 35000 + Math.floor(Math.random() * 35000),
        likes: 2000 + Math.floor(Math.random() * 3000), comments: 100 + Math.floor(Math.random() * 200),
        publishedDate: new Date(Date.now() - (44 - i) * 86400000).toISOString(),
      })),
      createdAt: new Date(Date.now() - 60 * 86400000).toISOString(), updatedAt: new Date().toISOString(),
    },
    {
      id: 3, title: '코딩 챌린지 30일', description: '30일 동안 매일 하나의 코딩 챌린지', status: 'COMPLETED',
      platform: 'youtube', frequency: 'DAILY', totalEpisodes: 30, publishedEpisodes: 30, avgViews: 18000,
      avgRetention: 55, totalViews: 540000, tags: ['코딩', '교육', '챌린지'],
      episodes: [], createdAt: new Date(Date.now() - 90 * 86400000).toISOString(), updatedAt: new Date(Date.now() - 60 * 86400000).toISOString(),
    },
  ]
}

export const useContentSeriesStore = defineStore('contentSeries', () => {
  const seriesList = ref<ContentSeries[]>([])
  const currentSeries = ref<ContentSeries | null>(null)
  const analytics = ref<SeriesAnalytics | null>(null)
  const loading = ref(false)

  const activeSeries = computed(() => seriesList.value.filter((s) => s.status === 'ACTIVE'))
  const completedSeries = computed(() => seriesList.value.filter((s) => ['COMPLETED', 'ARCHIVED'].includes(s.status)))

  async function fetchAll() {
    loading.value = true
    try {
      seriesList.value = await contentSeriesApi.getAll()
    } catch {
      seriesList.value = generateMockSeries()
    } finally {
      loading.value = false
    }
  }

  async function createSeries(request: CreateSeriesRequest) {
    try {
      const series = await contentSeriesApi.create(request)
      seriesList.value.unshift(series)
    } catch {
      const mock: ContentSeries = {
        id: Date.now(), ...request, description: request.description, status: 'ACTIVE',
        coverImageUrl: undefined, totalEpisodes: request.plannedEpisodes ?? 0, publishedEpisodes: 0,
        avgViews: 0, avgRetention: 0, totalViews: 0, episodes: [], tags: request.tags ?? [],
        createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
      }
      seriesList.value.unshift(mock)
    }
  }

  async function deleteSeries(id: number) {
    try { await contentSeriesApi.delete(id) } catch { /* 로컬 */ }
    seriesList.value = seriesList.value.filter((s) => s.id !== id)
  }

  async function fetchAnalytics(seriesId: number) {
    try {
      analytics.value = await contentSeriesApi.getAnalytics(seriesId)
    } catch {
      const series = seriesList.value.find((s) => s.id === seriesId)
      analytics.value = {
        seriesId,
        viewsTrend: (series?.episodes ?? []).map((ep) => ({ episode: ep.episodeNumber, views: ep.views ?? 0, retention: 50 + Math.random() * 30 })),
        subscriberGrowth: 1250,
        avgEngagement: 5.8,
        bestPerformingEpisode: series?.episodes[0] ?? null,
        dropOffRate: 12,
        audienceReturnRate: 68,
      }
    }
  }

  return { seriesList, currentSeries, analytics, loading, activeSeries, completedSeries, fetchAll, createSeries, deleteSeries, fetchAnalytics }
})
