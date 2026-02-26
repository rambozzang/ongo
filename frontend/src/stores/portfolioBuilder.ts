import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Portfolio, PortfolioBuilderSummary } from '@/types/portfolioBuilder'
import { portfolioBuilderApi } from '@/api/portfolioBuilder'

export const usePortfolioBuilderStore = defineStore('portfolioBuilder', () => {
  const portfolios = ref<Portfolio[]>([])
  const summary = ref<PortfolioBuilderSummary | null>(null)
  const loading = ref(false)

  const fetchPortfolios = async () => {
    loading.value = true
    try {
      portfolios.value = await portfolioBuilderApi.getPortfolios()
    } catch {
      portfolios.value = [
        { id: 1, title: '여행 크리에이터 포트폴리오', description: '국내외 여행 콘텐츠 전문 크리에이터', template: 'CREATIVE', theme: 'ocean', isPublished: true, publicUrl: 'https://ongo.io/p/travel-creator', viewCount: 1250, sections: [
          { id: 1, portfolioId: 1, sectionType: 'INTRO', title: '소개', content: '안녕하세요! 여행 브이로거입니다.', order: 1, isVisible: true },
          { id: 2, portfolioId: 1, sectionType: 'STATS', title: '채널 통계', content: '구독자 10만, 총 조회수 5천만', order: 2, isVisible: true },
          { id: 3, portfolioId: 1, sectionType: 'VIDEOS', title: '대표 영상', content: '인기 영상 TOP 5', order: 3, isVisible: true },
        ], createdAt: '2025-01-10T10:00:00Z', updatedAt: '2025-01-15T14:00:00Z' },
        { id: 2, title: '테크 리뷰어 프로필', description: 'IT 기기 리뷰 전문', template: 'PROFESSIONAL', theme: 'dark', isPublished: true, publicUrl: 'https://ongo.io/p/tech-reviewer', viewCount: 830, sections: [
          { id: 4, portfolioId: 2, sectionType: 'INTRO', title: 'About Me', content: '테크 리뷰 5년차 크리에이터', order: 1, isVisible: true },
          { id: 5, portfolioId: 2, sectionType: 'BRANDS', title: '협업 브랜드', content: '삼성, LG, Apple 등', order: 2, isVisible: true },
        ], createdAt: '2025-01-12T09:00:00Z', updatedAt: '2025-01-14T11:00:00Z' },
        { id: 3, title: '쿠킹 채널', description: '한식 요리 레시피', template: 'MINIMAL', theme: 'light', isPublished: false, publicUrl: null, viewCount: 0, sections: [], createdAt: '2025-01-15T16:00:00Z', updatedAt: '2025-01-15T16:00:00Z' },
      ]
    } finally {
      loading.value = false
    }
  }

  const fetchSummary = async () => {
    try {
      summary.value = await portfolioBuilderApi.getSummary()
    } catch {
      summary.value = { totalPortfolios: 3, publishedCount: 2, totalViews: 2080, avgSections: 3, topTemplate: 'CREATIVE' }
    }
  }

  return { portfolios, summary, loading, fetchPortfolios, fetchSummary }
})
