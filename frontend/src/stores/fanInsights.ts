import { ref } from 'vue'
import { defineStore } from 'pinia'
import type {
  FanDemographic,
  FanBehavior,
  FanSegment,
  FanInsightsSummary,
} from '@/types/fanInsights'
import { fanInsightsApi } from '@/api/fanInsights'

export const useFanInsightsStore = defineStore('fanInsights', () => {
  const demographics = ref<FanDemographic[]>([])
  const behaviors = ref<FanBehavior[]>([])
  const segments = ref<FanSegment[]>([])
  const summary = ref<FanInsightsSummary>({
    totalFans: 0,
    topAgeGroup: '-',
    topCountry: '-',
    avgWatchDuration: 0,
    peakActiveHour: 0,
  })
  const isLoading = ref(false)

  async function fetchDemographics(platform?: string) {
    isLoading.value = true
    try {
      demographics.value = await fanInsightsApi.getDemographics(platform)
    } catch {
      demographics.value = [
        { id: 1, platform: 'YOUTUBE', ageGroup: '18-24', gender: 'MALE', percentage: 35.2, country: '대한민국', city: '서울' },
        { id: 2, platform: 'YOUTUBE', ageGroup: '25-34', gender: 'MALE', percentage: 28.1, country: '대한민국', city: '서울' },
        { id: 3, platform: 'YOUTUBE', ageGroup: '18-24', gender: 'FEMALE', percentage: 18.5, country: '대한민국', city: '부산' },
        { id: 4, platform: 'YOUTUBE', ageGroup: '25-34', gender: 'FEMALE', percentage: 12.3, country: '대한민국', city: '인천' },
        { id: 5, platform: 'TIKTOK', ageGroup: '13-17', gender: 'FEMALE', percentage: 22.0, country: '대한민국', city: '서울' },
        { id: 6, platform: 'TIKTOK', ageGroup: '18-24', gender: 'MALE', percentage: 30.5, country: '대한민국', city: '대전' },
      ]
    } finally {
      isLoading.value = false
    }
  }

  async function fetchBehaviors(platform?: string) {
    try {
      behaviors.value = await fanInsightsApi.getBehaviors(platform)
    } catch {
      behaviors.value = [
        { id: 1, platform: 'YOUTUBE', activeHour: 20, activeDay: '금요일', watchDuration: 8.5, returnRate: 42.3, commentRate: 3.2, shareRate: 1.8 },
        { id: 2, platform: 'YOUTUBE', activeHour: 18, activeDay: '토요일', watchDuration: 12.1, returnRate: 38.5, commentRate: 2.8, shareRate: 2.1 },
        { id: 3, platform: 'TIKTOK', activeHour: 22, activeDay: '수요일', watchDuration: 3.2, returnRate: 55.0, commentRate: 5.5, shareRate: 8.2 },
        { id: 4, platform: 'INSTAGRAM', activeHour: 19, activeDay: '일요일', watchDuration: 2.5, returnRate: 30.2, commentRate: 4.1, shareRate: 3.5 },
      ]
    }
  }

  async function fetchSegments() {
    try {
      segments.value = await fanInsightsApi.getSegments()
    } catch {
      segments.value = [
        { id: 1, name: '열성 팬', description: '매주 3회 이상 시청하는 충성 팬', memberCount: 2500, avgEngagement: 12.5, topInterests: ['테크', '리뷰', '언박싱'], platform: 'YOUTUBE' },
        { id: 2, name: '캐주얼 시청자', description: '월 1~2회 시청하는 가벼운 시청자', memberCount: 15000, avgEngagement: 2.1, topInterests: ['트렌드', '쇼츠'], platform: 'YOUTUBE' },
        { id: 3, name: 'TikTok 팬', description: 'TikTok에서 주로 활동하는 팬', memberCount: 8000, avgEngagement: 8.3, topInterests: ['챌린지', '댄스', '트렌드'], platform: 'TIKTOK' },
        { id: 4, name: '신규 구독자', description: '최근 30일 내 구독한 신규 팬', memberCount: 3200, avgEngagement: 5.5, topInterests: ['리뷰', '브이로그'], platform: 'YOUTUBE' },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await fanInsightsApi.getSummary()
    } catch {
      summary.value = { totalFans: 45200, topAgeGroup: '18-24', topCountry: '대한민국', avgWatchDuration: 6.8, peakActiveHour: 20 }
    }
  }

  return { demographics, behaviors, segments, summary, isLoading, fetchDemographics, fetchBehaviors, fetchSegments, fetchSummary }
})
