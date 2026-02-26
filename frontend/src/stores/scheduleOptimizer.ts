import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { OptimalSlot, ScheduleRecommendation, ScheduleOptimizerSummary } from '@/types/scheduleOptimizer'
import { scheduleOptimizerApi } from '@/api/scheduleOptimizer'

export const useScheduleOptimizerStore = defineStore('scheduleOptimizer', () => {
  const slots = ref<OptimalSlot[]>([])
  const recommendations = ref<ScheduleRecommendation[]>([])
  const summary = ref<ScheduleOptimizerSummary | null>(null)
  const loading = ref(false)

  const fetchSlots = async (platform: string) => {
    loading.value = true
    try {
      slots.value = await scheduleOptimizerApi.getSlots(platform)
    } catch {
      slots.value = [
        { id: 1, platform: 'YOUTUBE', dayOfWeek: '수요일', hour: 18, score: 92, audienceOnline: 45000, competitionLevel: 'LOW', reason: '타겟 오디언스 온라인 피크 시간', createdAt: '2025-01-16T00:00:00Z' },
        { id: 2, platform: 'YOUTUBE', dayOfWeek: '토요일', hour: 14, score: 88, audienceOnline: 38000, competitionLevel: 'MEDIUM', reason: '주말 높은 시청 시간', createdAt: '2025-01-16T00:00:00Z' },
        { id: 3, platform: 'TIKTOK', dayOfWeek: '금요일', hour: 20, score: 95, audienceOnline: 62000, competitionLevel: 'LOW', reason: '금요 저녁 최적 참여 시간', createdAt: '2025-01-16T00:00:00Z' },
        { id: 4, platform: 'INSTAGRAM', dayOfWeek: '화요일', hour: 12, score: 78, audienceOnline: 28000, competitionLevel: 'HIGH', reason: '점심시간 스크롤 피크', createdAt: '2025-01-16T00:00:00Z' },
      ]
    } finally {
      loading.value = false
    }
  }

  const fetchRecommendations = async () => {
    try {
      recommendations.value = await scheduleOptimizerApi.getRecommendations()
    } catch {
      recommendations.value = [
        { id: 1, videoId: 101, videoTitle: '제주도 3일 여행', currentSchedule: '2025-01-20T09:00:00Z', recommendedSchedule: '2025-01-20T18:00:00Z', platform: 'YOUTUBE', expectedImprovement: 35, confidence: 85, status: 'PENDING', createdAt: '2025-01-16T00:00:00Z' },
        { id: 2, videoId: 102, videoTitle: 'MacBook Pro M4 리뷰', currentSchedule: null, recommendedSchedule: '2025-01-22T14:00:00Z', platform: 'YOUTUBE', expectedImprovement: 22, confidence: 78, status: 'APPLIED', createdAt: '2025-01-16T00:00:00Z' },
        { id: 3, videoId: 103, videoTitle: '일상 브이로그', currentSchedule: '2025-01-21T10:00:00Z', recommendedSchedule: '2025-01-21T20:00:00Z', platform: 'TIKTOK', expectedImprovement: 45, confidence: 90, status: 'PENDING', createdAt: '2025-01-16T00:00:00Z' },
      ]
    }
  }

  const fetchSummary = async () => {
    try {
      summary.value = await scheduleOptimizerApi.getSummary()
    } catch {
      summary.value = { totalRecommendations: 42, appliedCount: 28, avgImprovement: 32, bestDay: '수요일', bestHour: 18 }
    }
  }

  return { slots, recommendations, summary, loading, fetchSlots, fetchRecommendations, fetchSummary }
})
