import { defineStore } from 'pinia'
import { ref } from 'vue'
import { calendarInsightsApi } from '@/api/calendarInsights'
import type {
  CalendarInsight,
  OptimalTimeSlot,
  UploadPattern,
  CalendarInsightsSummary,
} from '@/types/calendarInsights'

export const useCalendarInsightsStore = defineStore('calendarInsights', () => {
  const insights = ref<CalendarInsight[]>([])
  const optimalTimeSlots = ref<OptimalTimeSlot[]>([])
  const uploadPatterns = ref<UploadPattern[]>([])
  const summary = ref<CalendarInsightsSummary>({
    totalUploads: 0,
    avgUploadsPerWeek: 0,
    bestDay: '',
    bestHour: 0,
    consistencyScore: 0,
  })
  const isLoading = ref(false)

  const mockInsights: CalendarInsight[] = [
    { id: 1, date: '2025-03-10', dayOfWeek: 'MON', hour: 18, uploadCount: 3, avgViews: 45000, avgEngagement: 8.2, score: 85 },
    { id: 2, date: '2025-03-11', dayOfWeek: 'TUE', hour: 20, uploadCount: 2, avgViews: 62000, avgEngagement: 9.1, score: 92 },
    { id: 3, date: '2025-03-12', dayOfWeek: 'WED', hour: 12, uploadCount: 1, avgViews: 38000, avgEngagement: 7.5, score: 78 },
    { id: 4, date: '2025-03-13', dayOfWeek: 'THU', hour: 19, uploadCount: 4, avgViews: 71000, avgEngagement: 10.3, score: 95 },
    { id: 5, date: '2025-03-14', dayOfWeek: 'FRI', hour: 17, uploadCount: 2, avgViews: 55000, avgEngagement: 8.8, score: 88 },
    { id: 6, date: '2025-03-15', dayOfWeek: 'SAT', hour: 10, uploadCount: 5, avgViews: 82000, avgEngagement: 11.2, score: 97 },
    { id: 7, date: '2025-03-16', dayOfWeek: 'SUN', hour: 14, uploadCount: 1, avgViews: 29000, avgEngagement: 6.4, score: 65 },
  ]

  const mockOptimalSlots: OptimalTimeSlot[] = [
    { dayOfWeek: 'SAT', hour: 10, score: 97, expectedViews: 82000, expectedEngagement: 11.2, reason: '주말 오전, 시청자 활동 최고조' },
    { dayOfWeek: 'THU', hour: 19, score: 95, expectedViews: 71000, expectedEngagement: 10.3, reason: '목요일 저녁, 퇴근 후 콘텐츠 소비 피크' },
    { dayOfWeek: 'TUE', hour: 20, score: 92, expectedViews: 62000, expectedEngagement: 9.1, reason: '화요일 밤, 안정적인 시청률' },
    { dayOfWeek: 'FRI', hour: 17, score: 88, expectedViews: 55000, expectedEngagement: 8.8, reason: '금요일 오후, 주말 앞둔 높은 관심도' },
  ]

  const mockPatterns: UploadPattern[] = [
    { platform: 'YOUTUBE', totalUploads: 48, avgUploadsPerWeek: 3.2, mostActiveDay: 'THU', mostActiveHour: 19, consistency: 85 },
    { platform: 'TIKTOK', totalUploads: 72, avgUploadsPerWeek: 5.1, mostActiveDay: 'SAT', mostActiveHour: 10, consistency: 78 },
    { platform: 'INSTAGRAM', totalUploads: 35, avgUploadsPerWeek: 2.3, mostActiveDay: 'TUE', mostActiveHour: 20, consistency: 62 },
    { platform: 'NAVER_CLIP', totalUploads: 20, avgUploadsPerWeek: 1.4, mostActiveDay: 'FRI', mostActiveHour: 17, consistency: 55 },
  ]

  const mockSummary: CalendarInsightsSummary = {
    totalUploads: 175,
    avgUploadsPerWeek: 12.0,
    bestDay: 'SAT',
    bestHour: 10,
    consistencyScore: 72,
  }

  async function fetchInsights(year: number, month: number) {
    isLoading.value = true
    try {
      insights.value = await calendarInsightsApi.getInsights(year, month)
    } catch {
      insights.value = mockInsights
    } finally {
      isLoading.value = false
    }
  }

  async function fetchOptimalTimeSlots(platform?: string) {
    try {
      optimalTimeSlots.value = await calendarInsightsApi.getOptimalTimeSlots(platform)
    } catch {
      optimalTimeSlots.value = platform
        ? mockOptimalSlots
        : mockOptimalSlots
    }
  }

  async function fetchUploadPatterns() {
    try {
      uploadPatterns.value = await calendarInsightsApi.getUploadPatterns()
    } catch {
      uploadPatterns.value = mockPatterns
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await calendarInsightsApi.getSummary()
    } catch {
      summary.value = mockSummary
    }
  }

  return {
    insights,
    optimalTimeSlots,
    uploadPatterns,
    summary,
    isLoading,
    fetchInsights,
    fetchOptimalTimeSlots,
    fetchUploadPatterns,
    fetchSummary,
  }
})
