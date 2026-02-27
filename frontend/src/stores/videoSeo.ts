import { ref } from 'vue'
import { defineStore } from 'pinia'
import type {
  SeoAnalysis,
  SeoKeyword,
  VideoSeoSummary,
} from '@/types/videoSeo'
import { videoSeoApi } from '@/api/videoSeo'

export const useVideoSeoStore = defineStore('videoSeo', () => {
  const analyses = ref<SeoAnalysis[]>([])
  const keywords = ref<SeoKeyword[]>([])
  const summary = ref<VideoSeoSummary>({
    totalAnalyzed: 0,
    avgScore: 0,
    topKeyword: '-',
    improvementRate: 0,
    weakestArea: '-',
  })
  const isLoading = ref(false)

  async function fetchAnalyses(platform?: string) {
    isLoading.value = true
    try {
      analyses.value = await videoSeoApi.getAnalyses(platform)
    } catch {
      analyses.value = [
        { id: 1, videoTitle: '맥북 프로 M4 리뷰', platform: 'YOUTUBE', overallScore: 85, titleScore: 90, descriptionScore: 78, tagScore: 88, thumbnailScore: 82, suggestions: ['제목에 연도 추가', '설명 첫 2줄 최적화'], analyzedAt: '2025-01-28T10:00:00' },
        { id: 2, videoTitle: '서울 카페 투어', platform: 'YOUTUBE', overallScore: 72, titleScore: 65, descriptionScore: 80, tagScore: 70, thumbnailScore: 75, suggestions: ['제목에 키워드 포함', '해시태그 추가'], analyzedAt: '2025-01-27T10:00:00' },
        { id: 3, videoTitle: '홈트레이닝 루틴', platform: 'YOUTUBE', overallScore: 91, titleScore: 95, descriptionScore: 88, tagScore: 92, thumbnailScore: 90, suggestions: ['썸네일 텍스트 크기 확대'], analyzedAt: '2025-01-26T10:00:00' },
        { id: 4, videoTitle: '일본 여행 꿀팁', platform: 'YOUTUBE', overallScore: 68, titleScore: 70, descriptionScore: 60, tagScore: 72, thumbnailScore: 68, suggestions: ['설명에 타임스탬프 추가', '태그 수 늘리기', '썸네일 대비 높이기'], analyzedAt: '2025-01-25T10:00:00' },
        { id: 5, videoTitle: '2025 IT 트렌드', platform: 'TIKTOK', overallScore: 78, titleScore: 82, descriptionScore: 74, tagScore: 80, thumbnailScore: 76, suggestions: ['트렌딩 해시태그 활용'], analyzedAt: '2025-01-24T10:00:00' },
      ]
    } finally {
      isLoading.value = false
    }
  }

  async function fetchKeywords(analysisId: number) {
    try {
      keywords.value = await videoSeoApi.getKeywords(analysisId)
    } catch {
      keywords.value = [
        { id: 1, keyword: '맥북 프로 리뷰', searchVolume: 12000, competition: 'HIGH', relevance: 95, trend: 'UP' },
        { id: 2, keyword: 'M4 맥북', searchVolume: 8500, competition: 'MEDIUM', relevance: 90, trend: 'UP' },
        { id: 3, keyword: '노트북 추천 2025', searchVolume: 25000, competition: 'HIGH', relevance: 75, trend: 'STABLE' },
        { id: 4, keyword: '애플 신제품', searchVolume: 18000, competition: 'HIGH', relevance: 70, trend: 'DOWN' },
        { id: 5, keyword: '맥북 에어 vs 프로', searchVolume: 9500, competition: 'MEDIUM', relevance: 85, trend: 'UP' },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await videoSeoApi.getSummary()
    } catch {
      summary.value = { totalAnalyzed: 35, avgScore: 78.5, topKeyword: '맥북 프로 리뷰', improvementRate: 23.4, weakestArea: '설명 최적화' }
    }
  }

  return { analyses, keywords, summary, isLoading, fetchAnalyses, fetchKeywords, fetchSummary }
})
