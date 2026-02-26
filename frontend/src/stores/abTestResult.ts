import { defineStore } from 'pinia'
import { ref } from 'vue'
import { abTestResultApi } from '@/api/abTestResult'
import type {
  ABTestResult,
  ABTestResultSummary,
} from '@/types/abTestResult'

export const useABTestResultStore = defineStore('abTestResult', () => {
  const results = ref<ABTestResult[]>([])
  const summary = ref<ABTestResultSummary>({
    totalTests: 0,
    runningTests: 0,
    completedTests: 0,
    avgConfidence: 0,
    avgImprovement: 0,
  })
  const isLoading = ref(false)

  const mockResults: ABTestResult[] = [
    {
      id: 1,
      testId: 101,
      testName: '썸네일 색상 테스트 - 봄 컬렉션',
      status: 'COMPLETED',
      startDate: '2025-03-01T00:00:00Z',
      endDate: '2025-03-15T00:00:00Z',
      variants: [
        { id: 'A', name: '파란 배경', description: '파란색 그라데이션 배경', views: 45200, clicks: 3616, ctr: 8.0, avgWatchTime: 245, engagement: 7.2, conversions: 180, isControl: true, isWinner: false },
        { id: 'B', name: '분홍 배경', description: '분홍색 그라데이션 배경', views: 44800, clicks: 5376, ctr: 12.0, avgWatchTime: 312, engagement: 9.8, conversions: 268, isControl: false, isWinner: true },
      ],
      winner: 'B',
      confidence: 97.5,
      metric: 'CTR',
      platform: 'YOUTUBE',
      createdAt: '2025-03-01T00:00:00Z',
    },
    {
      id: 2,
      testId: 102,
      testName: '제목 스타일 테스트 - 요리 영상',
      status: 'RUNNING',
      startDate: '2025-03-10T00:00:00Z',
      variants: [
        { id: 'A', name: '질문형 제목', description: '"이걸 몰랐다고?" 스타일', views: 12300, clicks: 1353, ctr: 11.0, avgWatchTime: 198, engagement: 6.5, conversions: 67, isControl: true, isWinner: false },
        { id: 'B', name: '숫자형 제목', description: '"5가지 비법" 스타일', views: 12100, clicks: 1573, ctr: 13.0, avgWatchTime: 215, engagement: 7.1, conversions: 78, isControl: false, isWinner: false },
        { id: 'C', name: '감성형 제목', description: '"마음이 따뜻해지는" 스타일', views: 11900, clicks: 1190, ctr: 10.0, avgWatchTime: 267, engagement: 8.3, conversions: 59, isControl: false, isWinner: false },
      ],
      confidence: 72.3,
      metric: 'CTR',
      platform: 'YOUTUBE',
      createdAt: '2025-03-10T00:00:00Z',
    },
    {
      id: 3,
      testId: 103,
      testName: '업로드 시간 테스트 - 테크 리뷰',
      status: 'COMPLETED',
      startDate: '2025-02-15T00:00:00Z',
      endDate: '2025-03-01T00:00:00Z',
      variants: [
        { id: 'A', name: '오전 9시', description: '출근 시간대', views: 28700, clicks: 2583, ctr: 9.0, avgWatchTime: 178, engagement: 5.9, conversions: 143, isControl: true, isWinner: false },
        { id: 'B', name: '오후 6시', description: '퇴근 시간대', views: 29100, clicks: 3492, ctr: 12.0, avgWatchTime: 234, engagement: 8.1, conversions: 201, isControl: false, isWinner: true },
      ],
      winner: 'B',
      confidence: 95.8,
      metric: 'Engagement',
      platform: 'YOUTUBE',
      createdAt: '2025-02-15T00:00:00Z',
    },
  ]

  const mockSummary: ABTestResultSummary = {
    totalTests: 15,
    runningTests: 3,
    completedTests: 12,
    avgConfidence: 89.2,
    avgImprovement: 18.5,
  }

  async function fetchResults(status?: string) {
    isLoading.value = true
    try {
      results.value = await abTestResultApi.getResults(status)
    } catch {
      results.value = status
        ? mockResults.filter((r) => r.status === status)
        : mockResults
    } finally {
      isLoading.value = false
    }
  }

  async function pauseTest(id: number) {
    try {
      const updated = await abTestResultApi.pauseTest(id)
      const idx = results.value.findIndex((r) => r.id === id)
      if (idx !== -1) results.value[idx] = updated
    } catch {
      const idx = results.value.findIndex((r) => r.id === id)
      if (idx !== -1) results.value[idx].status = 'PAUSED'
    }
  }

  async function resumeTest(id: number) {
    try {
      const updated = await abTestResultApi.resumeTest(id)
      const idx = results.value.findIndex((r) => r.id === id)
      if (idx !== -1) results.value[idx] = updated
    } catch {
      const idx = results.value.findIndex((r) => r.id === id)
      if (idx !== -1) results.value[idx].status = 'RUNNING'
    }
  }

  async function stopTest(id: number) {
    try {
      const updated = await abTestResultApi.stopTest(id)
      const idx = results.value.findIndex((r) => r.id === id)
      if (idx !== -1) results.value[idx] = updated
    } catch {
      const idx = results.value.findIndex((r) => r.id === id)
      if (idx !== -1) results.value[idx].status = 'CANCELLED'
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await abTestResultApi.getSummary()
    } catch {
      summary.value = mockSummary
    }
  }

  return {
    results,
    summary,
    isLoading,
    fetchResults,
    pauseTest,
    resumeTest,
    stopTest,
    fetchSummary,
  }
})
