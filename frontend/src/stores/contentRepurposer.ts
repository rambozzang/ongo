import { ref } from 'vue'
import { defineStore } from 'pinia'
import type {
  RepurposeJob,
  RepurposeTemplate,
  ContentRepurposerSummary,
} from '@/types/contentRepurposer'
import { contentRepurposerApi } from '@/api/contentRepurposer'

export const useContentRepurposerStore = defineStore('contentRepurposer', () => {
  const jobs = ref<RepurposeJob[]>([])
  const templates = ref<RepurposeTemplate[]>([])
  const summary = ref<ContentRepurposerSummary>({
    totalJobs: 0,
    completedJobs: 0,
    avgTimeSaved: 0,
    topTargetPlatform: '-',
    successRate: 0,
  })
  const isLoading = ref(false)

  async function fetchJobs(status?: string) {
    isLoading.value = true
    try {
      jobs.value = await contentRepurposerApi.getJobs(status)
    } catch {
      jobs.value = [
        { id: 1, originalTitle: '맥북 프로 M4 완벽 리뷰', originalPlatform: 'YOUTUBE', targetPlatform: 'TIKTOK', targetFormat: 'SHORT', status: 'COMPLETED', progress: 100, outputUrl: '/shorts/1.mp4', createdAt: '2025-01-28T10:00:00' },
        { id: 2, originalTitle: '서울 카페 투어 브이로그', originalPlatform: 'YOUTUBE', targetPlatform: 'INSTAGRAM', targetFormat: 'REEL', status: 'COMPLETED', progress: 100, outputUrl: '/reels/2.mp4', createdAt: '2025-01-27T10:00:00' },
        { id: 3, originalTitle: '2025 트렌드 분석', originalPlatform: 'YOUTUBE', targetPlatform: 'TIKTOK', targetFormat: 'SHORT', status: 'PROCESSING', progress: 65, outputUrl: null, createdAt: '2025-01-28T14:00:00' },
        { id: 4, originalTitle: '홈 오피스 셋업 가이드', originalPlatform: 'YOUTUBE', targetPlatform: 'NAVER_CLIP', targetFormat: 'CLIP', status: 'PENDING', progress: 0, outputUrl: null, createdAt: '2025-01-28T15:00:00' },
        { id: 5, originalTitle: '일본 여행 Vlog', originalPlatform: 'YOUTUBE', targetPlatform: 'INSTAGRAM', targetFormat: 'REEL', status: 'COMPLETED', progress: 100, outputUrl: '/reels/5.mp4', createdAt: '2025-01-26T10:00:00' },
      ]
    } finally {
      isLoading.value = false
    }
  }

  async function fetchTemplates() {
    try {
      templates.value = await contentRepurposerApi.getTemplates()
    } catch {
      templates.value = [
        { id: 1, name: '유튜브→틱톡 쇼츠', sourcePlatform: 'YOUTUBE', targetPlatform: 'TIKTOK', targetFormat: 'SHORT', description: '긴 영상에서 하이라이트 추출하여 60초 쇼츠 생성', isDefault: true },
        { id: 2, name: '유튜브→릴스', sourcePlatform: 'YOUTUBE', targetPlatform: 'INSTAGRAM', targetFormat: 'REEL', description: '인스타그램 릴스 최적화 포맷으로 변환', isDefault: true },
        { id: 3, name: '유튜브→네이버 클립', sourcePlatform: 'YOUTUBE', targetPlatform: 'NAVER_CLIP', targetFormat: 'CLIP', description: '네이버 클립용 세로 영상 자동 생성', isDefault: false },
        { id: 4, name: '틱톡→릴스 변환', sourcePlatform: 'TIKTOK', targetPlatform: 'INSTAGRAM', targetFormat: 'REEL', description: '틱톡 콘텐츠를 릴스로 워터마크 없이 변환', isDefault: false },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await contentRepurposerApi.getSummary()
    } catch {
      summary.value = { totalJobs: 48, completedJobs: 42, avgTimeSaved: 35, topTargetPlatform: 'TikTok', successRate: 87.5 }
    }
  }

  return { jobs, templates, summary, isLoading, fetchJobs, fetchTemplates, fetchSummary }
})
