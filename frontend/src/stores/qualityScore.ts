import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { QualityReport, QualityCheckRequest } from '@/types/qualityScore'
import { qualityScoreApi } from '@/api/qualityScore'

function generateMockReport(): QualityReport {
  return {
    id: 1, videoId: 1, videoTitle: '서울 맛집 TOP 5',
    overallScore: 82, overallGrade: 'A',
    metrics: [
      { category: 'TITLE', score: 90, maxScore: 100, grade: 'S', feedback: '제목이 명확하고 클릭을 유도합니다', suggestions: [] },
      { category: 'THUMBNAIL', score: 75, maxScore: 100, grade: 'B', feedback: '썸네일 텍스트가 작습니다', suggestions: ['텍스트 크기를 키우세요', '대비가 높은 색상을 사용하세요'] },
      { category: 'DESCRIPTION', score: 65, maxScore: 100, grade: 'C', feedback: '설명이 너무 짧습니다', suggestions: ['300자 이상 작성하세요', '핵심 키워드를 포함하세요', '타임스탬프를 추가하세요'] },
      { category: 'TAGS', score: 88, maxScore: 100, grade: 'A', feedback: '태그가 잘 구성되어 있습니다', suggestions: ['롱테일 키워드를 1-2개 추가하세요'] },
      { category: 'SEO', score: 78, maxScore: 100, grade: 'B', feedback: 'SEO 최적화가 양호합니다', suggestions: ['제목 앞에 핵심 키워드를 배치하세요'] },
      { category: 'ENGAGEMENT_POTENTIAL', score: 85, maxScore: 100, grade: 'A', feedback: '참여 유도 요소가 적절합니다', suggestions: ['CTA를 영상 초반에도 추가하세요'] },
    ],
    topIssues: ['설명 길이가 부족합니다', '썸네일 텍스트 가독성 개선 필요'],
    improvements: [
      { action: '설명에 타임스탬프와 관련 링크 추가', expectedImpact: '조회수 +15%', priority: 'HIGH' },
      { action: '썸네일 텍스트 크기 2배 확대', expectedImpact: 'CTR +8%', priority: 'HIGH' },
      { action: '롱테일 키워드 태그 추가', expectedImpact: '검색 노출 +20%', priority: 'MEDIUM' },
    ],
    competitorAvgScore: 71,
    checkedAt: new Date().toISOString(),
  }
}

export const useQualityScoreStore = defineStore('qualityScore', () => {
  const currentReport = ref<QualityReport | null>(null)
  const history = ref<QualityReport[]>([])
  const checking = ref(false)
  const loading = ref(false)

  const isAboveAverage = computed(() => {
    if (!currentReport.value) return false
    return currentReport.value.overallScore > currentReport.value.competitorAvgScore
  })

  async function checkQuality(request: QualityCheckRequest) {
    checking.value = true
    try {
      const response = await qualityScoreApi.checkQuality(request)
      currentReport.value = response.report
    } catch {
      currentReport.value = generateMockReport()
      currentReport.value.videoTitle = request.title
    } finally {
      checking.value = false
    }
  }

  async function fetchHistory() {
    loading.value = true
    try {
      history.value = await qualityScoreApi.getHistory()
    } catch {
      history.value = [generateMockReport()]
    } finally {
      loading.value = false
    }
  }

  return { currentReport, history, checking, loading, isAboveAverage, checkQuality, fetchHistory }
})
