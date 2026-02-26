import { ref } from 'vue'
import { defineStore } from 'pinia'
import type {
  CalendarSuggestion,
  CalendarSlot,
  ContentCalendarAiSummary,
} from '@/types/contentCalendarAi'
import { contentCalendarAiApi } from '@/api/contentCalendarAi'

export const useContentCalendarAiStore = defineStore('contentCalendarAi', () => {
  const suggestions = ref<CalendarSuggestion[]>([])
  const slots = ref<CalendarSlot[]>([])
  const summary = ref<ContentCalendarAiSummary>({
    totalSuggestions: 0,
    acceptedCount: 0,
    avgConfidence: 0,
    bestTimeSlot: '-',
    topPlatform: '-',
  })
  const isLoading = ref(false)

  async function fetchSuggestions(status?: string) {
    isLoading.value = true
    try {
      suggestions.value = await contentCalendarAiApi.getSuggestions(status)
    } catch {
      suggestions.value = [
        { id: 1, title: '주간 브이로그 #47', description: '일상 브이로그 시리즈 - 카페 투어', suggestedDate: '2025-02-03', suggestedTime: '18:00', platform: 'YOUTUBE', contentType: 'VLOG', topic: '일상', expectedEngagement: 4.2, confidence: 87, status: 'PENDING', createdAt: '2025-01-28T10:00:00' },
        { id: 2, title: '쇼츠: 5초 레시피', description: '간단 레시피 쇼츠', suggestedDate: '2025-02-04', suggestedTime: '12:00', platform: 'YOUTUBE', contentType: 'SHORTS', topic: '요리', expectedEngagement: 6.8, confidence: 92, status: 'ACCEPTED', createdAt: '2025-01-28T10:00:00' },
        { id: 3, title: '트렌드 챌린지 참여', description: '이번 주 인기 챌린지 참여 영상', suggestedDate: '2025-02-05', suggestedTime: '20:00', platform: 'TIKTOK', contentType: 'CHALLENGE', topic: '엔터테인먼트', expectedEngagement: 8.5, confidence: 78, status: 'PENDING', createdAt: '2025-01-28T10:00:00' },
        { id: 4, title: '제품 리뷰: 신형 이어폰', description: 'IT 제품 리뷰 시리즈', suggestedDate: '2025-02-06', suggestedTime: '19:00', platform: 'YOUTUBE', contentType: 'REVIEW', topic: 'IT/테크', expectedEngagement: 3.9, confidence: 81, status: 'PENDING', createdAt: '2025-01-28T10:00:00' },
        { id: 5, title: '릴스: OOTD 모음', description: '겨울 코디 모음 릴스', suggestedDate: '2025-02-07', suggestedTime: '11:00', platform: 'INSTAGRAM', contentType: 'REELS', topic: '패션', expectedEngagement: 5.3, confidence: 85, status: 'ACCEPTED', createdAt: '2025-01-28T10:00:00' },
      ]
    } finally {
      isLoading.value = false
    }
  }

  async function fetchSlots(startDate: string, endDate: string) {
    try {
      slots.value = await contentCalendarAiApi.getSlots(startDate, endDate)
    } catch {
      slots.value = [
        { date: '2025-02-03', time: '18:00', platform: 'YOUTUBE', score: 92, reason: '해당 시간대 시청자 활동률 최고' },
        { date: '2025-02-04', time: '12:00', platform: 'YOUTUBE', score: 88, reason: '점심시간 쇼츠 시청률 높음' },
        { date: '2025-02-05', time: '20:00', platform: 'TIKTOK', score: 95, reason: '밤 8시 TikTok 활성 사용자 최다' },
        { date: '2025-02-06', time: '19:00', platform: 'YOUTUBE', score: 85, reason: '평일 저녁 리뷰 콘텐츠 선호' },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await contentCalendarAiApi.getSummary()
    } catch {
      summary.value = { totalSuggestions: 12, acceptedCount: 8, avgConfidence: 84.5, bestTimeSlot: '금요일 18:00', topPlatform: 'YOUTUBE' }
    }
  }

  return { suggestions, slots, summary, isLoading, fetchSuggestions, fetchSlots, fetchSummary }
})
