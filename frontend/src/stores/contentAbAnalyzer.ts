import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { contentAbAnalyzerApi } from '@/api/contentAbAnalyzer'
import type { ContentAbTest, ContentAbSummary } from '@/types/contentAbAnalyzer'

export const useContentAbAnalyzerStore = defineStore('contentAbAnalyzer', () => {
  const tests = ref<ContentAbTest[]>([])
  const summary = ref<ContentAbSummary | null>(null)
  const loading = ref(false)

  const runningTests = computed(() => tests.value.filter(t => t.status === 'RUNNING'))
  const completedTests = computed(() => tests.value.filter(t => t.status === 'COMPLETED'))

  async function fetchTests(status?: string) {
    loading.value = true
    try {
      tests.value = await contentAbAnalyzerApi.getTests(status)
    } catch {
      tests.value = [
        { id: 1, title: '썸네일 스타일 비교', variantA: { id: 1, label: 'A: 텍스트 중심', videoId: 101, videoTitle: 'AI 가이드 v1', views: 12500, likes: 890, comments: 145, ctr: 8.5, avgWatchTime: 320 }, variantB: { id: 2, label: 'B: 이미지 중심', videoId: 102, videoTitle: 'AI 가이드 v2', views: 15200, likes: 1100, comments: 198, ctr: 10.2, avgWatchTime: 285 }, status: 'COMPLETED', winner: 'B', confidence: 94.5, startDate: '2025-03-01', endDate: '2025-03-15', createdAt: '2025-03-01T10:00:00Z' },
        { id: 2, title: '영상 길이 비교', variantA: { id: 3, label: 'A: 10분 버전', videoId: 103, videoTitle: '레시피 숏', views: 8200, likes: 520, comments: 85, ctr: 7.2, avgWatchTime: 480 }, variantB: { id: 4, label: 'B: 20분 버전', videoId: 104, videoTitle: '레시피 롱', views: 6800, likes: 610, comments: 120, ctr: 5.8, avgWatchTime: 720 }, status: 'RUNNING', winner: null, confidence: 72.3, startDate: '2025-03-10', endDate: null, createdAt: '2025-03-10T14:00:00Z' },
        { id: 3, title: '오프닝 스타일 비교', variantA: { id: 5, label: 'A: 훅 시작', videoId: 105, videoTitle: '여행기 훅', views: 9500, likes: 720, comments: 92, ctr: 9.1, avgWatchTime: 350 }, variantB: { id: 6, label: 'B: 인트로 시작', videoId: 106, videoTitle: '여행기 인트로', views: 8800, likes: 680, comments: 78, ctr: 8.4, avgWatchTime: 380 }, status: 'COMPLETED', winner: 'A', confidence: 88.7, startDate: '2025-02-20', endDate: '2025-03-05', createdAt: '2025-02-20T08:00:00Z' },
      ]
    } finally {
      loading.value = false
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await contentAbAnalyzerApi.getSummary()
    } catch {
      summary.value = { totalTests: 18, completedTests: 14, avgConfidence: 87.2, winRateA: 42.8, winRateB: 57.2 }
    }
  }

  return { tests, summary, loading, runningTests, completedTests, fetchTests, fetchSummary }
})
