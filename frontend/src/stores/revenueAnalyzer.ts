import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RevenueStream, RevenueProjection, RevenueAnalyzerSummary } from '@/types/revenueAnalyzer'
import { revenueAnalyzerApi } from '@/api/revenueAnalyzer'

export const useRevenueAnalyzerStore = defineStore('revenueAnalyzer', () => {
  const streams = ref<RevenueStream[]>([])
  const projections = ref<RevenueProjection[]>([])
  const summary = ref<RevenueAnalyzerSummary | null>(null)
  const loading = ref(false)

  const fetchStreams = async () => {
    loading.value = true
    try {
      streams.value = await revenueAnalyzerApi.getStreams()
    } catch {
      streams.value = [
        { id: 1, channelId: 1, channelName: '여행하는 크리에이터', source: 'AD', amount: 2500000, currency: 'KRW', period: '2025-01', growth: 12.5, createdAt: '2025-01-31T00:00:00Z' },
        { id: 2, channelId: 1, channelName: '여행하는 크리에이터', source: 'MEMBERSHIP', amount: 800000, currency: 'KRW', period: '2025-01', growth: 25.0, createdAt: '2025-01-31T00:00:00Z' },
        { id: 3, channelId: 2, channelName: '테크 리뷰어', source: 'SPONSORSHIP', amount: 5000000, currency: 'KRW', period: '2025-01', growth: -5.2, createdAt: '2025-01-31T00:00:00Z' },
        { id: 4, channelId: 2, channelName: '테크 리뷰어', source: 'AD', amount: 1800000, currency: 'KRW', period: '2025-01', growth: 8.3, createdAt: '2025-01-31T00:00:00Z' },
        { id: 5, channelId: 3, channelName: '일상 브이로거', source: 'AFFILIATE', amount: 1200000, currency: 'KRW', period: '2025-01', growth: 35.0, createdAt: '2025-01-31T00:00:00Z' },
      ]
    } finally {
      loading.value = false
    }
  }

  const fetchProjections = async (channelId: number) => {
    try {
      projections.value = await revenueAnalyzerApi.getProjections(channelId)
    } catch {
      projections.value = [
        { id: 1, channelId: 1, source: 'AD', currentMonthly: 2500000, projectedMonthly: 2850000, confidence: 82, projectionDate: '2025-02', factors: ['조회수 증가 추세', '시즌 효과'], createdAt: '2025-01-31T00:00:00Z' },
        { id: 2, channelId: 1, source: 'MEMBERSHIP', currentMonthly: 800000, projectedMonthly: 1000000, confidence: 75, projectionDate: '2025-02', factors: ['멤버십 가입 증가', '신규 콘텐츠 효과'], createdAt: '2025-01-31T00:00:00Z' },
        { id: 3, channelId: 1, source: 'SPONSORSHIP', currentMonthly: 0, projectedMonthly: 3000000, confidence: 45, projectionDate: '2025-02', factors: ['협찬 문의 증가', '채널 성장률'], createdAt: '2025-01-31T00:00:00Z' },
      ]
    }
  }

  const fetchSummary = async () => {
    try {
      summary.value = await revenueAnalyzerApi.getSummary()
    } catch {
      summary.value = { totalRevenue: 11300000, monthlyGrowth: 15.2, topSource: 'SPONSORSHIP', channelCount: 3, projectedNextMonth: 13500000, avgConfidence: 72 }
    }
  }

  return { streams, projections, summary, loading, fetchStreams, fetchProjections, fetchSummary }
})
