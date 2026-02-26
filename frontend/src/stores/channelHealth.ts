import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ChannelHealthMetric, HealthTrend, ChannelHealthSummary } from '@/types/channelHealth'
import { channelHealthApi } from '@/api/channelHealth'

export const useChannelHealthStore = defineStore('channelHealth', () => {
  const metrics = ref<ChannelHealthMetric[]>([])
  const trends = ref<HealthTrend[]>([])
  const summary = ref<ChannelHealthSummary | null>(null)
  const loading = ref(false)

  const fetchMetrics = async () => {
    loading.value = true
    try {
      metrics.value = await channelHealthApi.getMetrics()
    } catch {
      metrics.value = [
        { id: 1, channelId: 1, channelName: '여행하는 크리에이터', platform: 'YOUTUBE', overallScore: 85, growthScore: 78, engagementScore: 92, consistencyScore: 88, audienceScore: 80, monetizationScore: 75, measuredAt: '2025-01-16T00:00:00Z' },
        { id: 2, channelId: 2, channelName: '테크 리뷰어', platform: 'YOUTUBE', overallScore: 72, growthScore: 65, engagementScore: 80, consistencyScore: 70, audienceScore: 75, monetizationScore: 68, measuredAt: '2025-01-16T00:00:00Z' },
        { id: 3, channelId: 3, channelName: '일상 브이로거', platform: 'TIKTOK', overallScore: 90, growthScore: 95, engagementScore: 88, consistencyScore: 82, audienceScore: 93, monetizationScore: 85, measuredAt: '2025-01-16T00:00:00Z' },
        { id: 4, channelId: 4, channelName: '쿠킹 마스터', platform: 'INSTAGRAM', overallScore: 68, growthScore: 60, engagementScore: 74, consistencyScore: 65, audienceScore: 70, monetizationScore: 62, measuredAt: '2025-01-16T00:00:00Z' },
      ]
    } finally {
      loading.value = false
    }
  }

  const fetchTrends = async (metricId: number) => {
    try {
      trends.value = await channelHealthApi.getTrends(metricId)
    } catch {
      trends.value = [
        { id: 1, metricId: 1, category: 'GROWTH', date: '2025-01-10', score: 72, change: 3.5, recommendation: '숏폼 콘텐츠 비율을 늘려보세요' },
        { id: 2, metricId: 1, category: 'ENGAGEMENT', date: '2025-01-10', score: 88, change: -1.2, recommendation: '댓글 답변 비율을 높이세요' },
        { id: 3, metricId: 1, category: 'CONSISTENCY', date: '2025-01-10', score: 85, change: 5.0, recommendation: null },
        { id: 4, metricId: 1, category: 'MONETIZATION', date: '2025-01-10', score: 70, change: 2.8, recommendation: '멤버십 콘텐츠를 추가해보세요' },
      ]
    }
  }

  const fetchSummary = async () => {
    try {
      summary.value = await channelHealthApi.getSummary()
    } catch {
      summary.value = { totalChannels: 4, avgOverallScore: 79, topChannel: '일상 브이로거', weakestArea: 'MONETIZATION', trendsUp: 3, trendsDown: 1 }
    }
  }

  return { metrics, trends, summary, loading, fetchMetrics, fetchTrends, fetchSummary }
})
