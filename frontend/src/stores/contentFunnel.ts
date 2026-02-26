import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { contentFunnelApi } from '@/api/contentFunnel'
import type { VideoFunnel, FunnelComparison, FunnelSummary } from '@/types/contentFunnel'

function generateMockSummary(): FunnelSummary {
  return {
    totalVideos: 18,
    avgConversion: 3.8,
    bestConversionVideo: '초보자를 위한 완벽 가이드',
    worstDropOffStage: '조회 -> 참여',
  }
}

function generateMockFunnels(): VideoFunnel[] {
  return [
    {
      id: 1, videoTitle: '초보자를 위한 완벽 가이드', platform: 'youtube',
      stages: [
        { name: '노출', count: 125000, rate: 100, dropOff: 0 },
        { name: '클릭', count: 18750, rate: 15.0, dropOff: 85.0 },
        { name: '조회 (30초)', count: 11250, rate: 9.0, dropOff: 40.0 },
        { name: '참여', count: 5625, rate: 4.5, dropOff: 50.0 },
        { name: '구독/전환', count: 1125, rate: 0.9, dropOff: 80.0 },
      ],
      overallConversion: 0.9, analyzedAt: '2025-02-20T10:00:00Z',
    },
    {
      id: 2, videoTitle: '2025 트렌드 총정리', platform: 'youtube',
      stages: [
        { name: '노출', count: 98000, rate: 100, dropOff: 0 },
        { name: '클릭', count: 12740, rate: 13.0, dropOff: 87.0 },
        { name: '조회 (30초)', count: 8918, rate: 9.1, dropOff: 30.0 },
        { name: '참여', count: 3567, rate: 3.6, dropOff: 60.0 },
        { name: '구독/전환', count: 891, rate: 0.91, dropOff: 75.0 },
      ],
      overallConversion: 0.91, analyzedAt: '2025-02-19T14:00:00Z',
    },
    {
      id: 3, videoTitle: '꿀팁 모음 #shorts', platform: 'tiktok',
      stages: [
        { name: '노출', count: 450000, rate: 100, dropOff: 0 },
        { name: '클릭', count: 90000, rate: 20.0, dropOff: 80.0 },
        { name: '조회 (30초)', count: 45000, rate: 10.0, dropOff: 50.0 },
        { name: '참여', count: 27000, rate: 6.0, dropOff: 40.0 },
        { name: '구독/전환', count: 4500, rate: 1.0, dropOff: 83.3 },
      ],
      overallConversion: 1.0, analyzedAt: '2025-02-18T09:00:00Z',
    },
  ]
}

function generateMockComparison(): FunnelComparison {
  const funnels = generateMockFunnels()
  return {
    videoA: funnels[0],
    videoB: funnels[1],
    stageWinners: [
      { stage: '노출', winner: 'A' },
      { stage: '클릭', winner: 'A' },
      { stage: '조회 (30초)', winner: 'B' },
      { stage: '참여', winner: 'A' },
      { stage: '구독/전환', winner: 'B' },
    ],
  }
}

export const useContentFunnelStore = defineStore('contentFunnel', () => {
  const summary = ref<FunnelSummary | null>(null)
  const funnels = ref<VideoFunnel[]>([])
  const selectedFunnel = ref<VideoFunnel | null>(null)
  const comparison = ref<FunnelComparison | null>(null)
  const loading = ref(false)

  const avgConversion = computed(() => {
    if (funnels.value.length === 0) return 0
    return funnels.value.reduce((sum, f) => sum + f.overallConversion, 0) / funnels.value.length
  })

  const bestVideo = computed(() =>
    funnels.value.length > 0
      ? [...funnels.value].sort((a, b) => b.overallConversion - a.overallConversion)[0]
      : null,
  )

  async function fetchSummary() {
    loading.value = true
    try {
      summary.value = await contentFunnelApi.getSummary()
    } catch {
      summary.value = generateMockSummary()
    } finally {
      loading.value = false
    }
  }

  async function fetchFunnels() {
    try {
      funnels.value = await contentFunnelApi.getFunnels()
    } catch {
      funnels.value = generateMockFunnels()
    }
  }

  async function selectFunnel(id: number) {
    try {
      selectedFunnel.value = await contentFunnelApi.getFunnel(id)
    } catch {
      selectedFunnel.value = funnels.value.find((f) => f.id === id) ?? null
    }
  }

  async function fetchComparison(videoAId: number, videoBId: number) {
    try {
      comparison.value = await contentFunnelApi.compare(videoAId, videoBId)
    } catch {
      comparison.value = generateMockComparison()
    }
  }

  return { summary, funnels, selectedFunnel, comparison, loading, avgConversion, bestVideo, fetchSummary, fetchFunnels, selectFunnel, fetchComparison }
})
