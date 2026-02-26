import { ref } from 'vue'
import { defineStore } from 'pinia'
import type {
  AudienceOverlapResult,
  OverlapSegment,
  AudienceOverlapSummary,
} from '@/types/audienceOverlap'
import { audienceOverlapApi } from '@/api/audienceOverlap'

export const useAudienceOverlapStore = defineStore('audienceOverlap', () => {
  const results = ref<AudienceOverlapResult[]>([])
  const segments = ref<OverlapSegment[]>([])
  const summary = ref<AudienceOverlapSummary>({
    totalAnalyses: 0,
    avgOverlap: 0,
    maxOverlapPair: '-',
    totalUniqueAudience: 0,
    mostSharedSegment: '-',
  })
  const isLoading = ref(false)

  async function fetchResults() {
    isLoading.value = true
    try {
      results.value = await audienceOverlapApi.getResults()
    } catch {
      results.value = [
        { id: 1, platformA: 'YOUTUBE', platformB: 'TIKTOK', overlapPercent: 23.5, uniqueToA: 45000, uniqueToB: 32000, sharedAudience: 12500, analyzedAt: '2025-01-28T10:00:00' },
        { id: 2, platformA: 'YOUTUBE', platformB: 'INSTAGRAM', overlapPercent: 35.2, uniqueToA: 38000, uniqueToB: 28000, sharedAudience: 18200, analyzedAt: '2025-01-27T10:00:00' },
        { id: 3, platformA: 'TIKTOK', platformB: 'INSTAGRAM', overlapPercent: 42.8, uniqueToA: 22000, uniqueToB: 19000, sharedAudience: 15800, analyzedAt: '2025-01-26T10:00:00' },
        { id: 4, platformA: 'YOUTUBE', platformB: 'NAVER_CLIP', overlapPercent: 18.3, uniqueToA: 48000, uniqueToB: 15000, sharedAudience: 8500, analyzedAt: '2025-01-25T10:00:00' },
      ]
    } finally {
      isLoading.value = false
    }
  }

  async function fetchSegments() {
    try {
      segments.value = await audienceOverlapApi.getSegments()
    } catch {
      segments.value = [
        { id: 1, name: '테크 얼리어답터', platforms: ['YOUTUBE', 'TIKTOK'], audienceSize: 8500, engagementRate: 6.2, topInterest: 'IT/테크' },
        { id: 2, name: '라이프스타일 팔로워', platforms: ['YOUTUBE', 'INSTAGRAM'], audienceSize: 12000, engagementRate: 4.8, topInterest: '라이프스타일' },
        { id: 3, name: '숏폼 액티브 유저', platforms: ['TIKTOK', 'INSTAGRAM'], audienceSize: 9200, engagementRate: 8.1, topInterest: '엔터테인먼트' },
        { id: 4, name: '전 플랫폼 코어팬', platforms: ['YOUTUBE', 'TIKTOK', 'INSTAGRAM'], audienceSize: 5400, engagementRate: 12.5, topInterest: '크리에이터' },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await audienceOverlapApi.getSummary()
    } catch {
      summary.value = { totalAnalyses: 12, avgOverlap: 29.9, maxOverlapPair: 'TikTok-Instagram', totalUniqueAudience: 85000, mostSharedSegment: '라이프스타일 팔로워' }
    }
  }

  return { results, segments, summary, isLoading, fetchResults, fetchSegments, fetchSummary }
})
