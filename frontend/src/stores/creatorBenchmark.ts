import { ref } from 'vue'
import { defineStore } from 'pinia'
import type {
  BenchmarkResult,
  BenchmarkPeer,
  CreatorBenchmarkSummary,
} from '@/types/creatorBenchmark'
import { creatorBenchmarkApi } from '@/api/creatorBenchmark'

export const useCreatorBenchmarkStore = defineStore('creatorBenchmark', () => {
  const results = ref<BenchmarkResult[]>([])
  const peers = ref<BenchmarkPeer[]>([])
  const summary = ref<CreatorBenchmarkSummary>({
    totalMetrics: 0,
    aboveAvgCount: 0,
    topPercentile: 0,
    strongestMetric: '-',
    weakestMetric: '-',
  })
  const isLoading = ref(false)

  async function fetchResults(platform?: string) {
    isLoading.value = true
    try {
      results.value = await creatorBenchmarkApi.getResults(platform)
    } catch {
      results.value = [
        { id: 1, platform: 'YOUTUBE', category: '테크', myValue: 45000, avgValue: 32000, topValue: 120000, percentile: 72, metric: 'SUBSCRIBERS', trend: 'UP', updatedAt: '2025-01-28T10:00:00' },
        { id: 2, platform: 'YOUTUBE', category: '테크', myValue: 8500, avgValue: 5200, topValue: 45000, percentile: 68, metric: 'AVG_VIEWS', trend: 'UP', updatedAt: '2025-01-28T10:00:00' },
        { id: 3, platform: 'YOUTUBE', category: '테크', myValue: 4.2, avgValue: 3.1, topValue: 8.5, percentile: 65, metric: 'ENGAGEMENT_RATE', trend: 'STABLE', updatedAt: '2025-01-28T10:00:00' },
        { id: 4, platform: 'TIKTOK', category: '테크', myValue: 12000, avgValue: 18000, topValue: 85000, percentile: 42, metric: 'SUBSCRIBERS', trend: 'UP', updatedAt: '2025-01-28T10:00:00' },
        { id: 5, platform: 'TIKTOK', category: '테크', myValue: 25000, avgValue: 15000, topValue: 200000, percentile: 70, metric: 'AVG_VIEWS', trend: 'UP', updatedAt: '2025-01-28T10:00:00' },
        { id: 6, platform: 'INSTAGRAM', category: '테크', myValue: 8200, avgValue: 9500, topValue: 55000, percentile: 38, metric: 'SUBSCRIBERS', trend: 'DOWN', updatedAt: '2025-01-28T10:00:00' },
      ]
    } finally {
      isLoading.value = false
    }
  }

  async function fetchPeers(platform?: string) {
    try {
      peers.value = await creatorBenchmarkApi.getPeers(platform)
    } catch {
      peers.value = [
        { id: 1, name: '테크하이', platform: 'YOUTUBE', subscribers: 120000, avgViews: 45000, engagementRate: 8.5, category: '테크' },
        { id: 2, name: '가제터', platform: 'YOUTUBE', subscribers: 85000, avgViews: 32000, engagementRate: 5.2, category: '테크' },
        { id: 3, name: '디지털맨', platform: 'YOUTUBE', subscribers: 65000, avgViews: 18000, engagementRate: 4.8, category: '테크' },
        { id: 4, name: '잇템리뷰', platform: 'TIKTOK', subscribers: 85000, avgViews: 200000, engagementRate: 12.3, category: '테크' },
        { id: 5, name: '테크노트', platform: 'INSTAGRAM', subscribers: 55000, avgViews: 8000, engagementRate: 6.1, category: '테크' },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await creatorBenchmarkApi.getSummary()
    } catch {
      summary.value = { totalMetrics: 6, aboveAvgCount: 4, topPercentile: 72, strongestMetric: '구독자 수', weakestMetric: 'Instagram 구독자' }
    }
  }

  return { results, peers, summary, isLoading, fetchResults, fetchPeers, fetchSummary }
})
