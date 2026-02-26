import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { audienceSegmentApi } from '@/api/audienceSegment'
import type { AudienceSegment, SegmentInsight, CreateSegmentRequest } from '@/types/audienceSegment'

function generateMockSegments(): AudienceSegment[] {
  return [
    {
      id: 1, name: '18-24세 남성 게이머', type: 'CUSTOM',
      criteria: { ageRange: { min: 18, max: 24 }, gender: 'MALE', interests: ['게임', '테크'] },
      size: 45200, percentage: 32.1, avgWatchTime: 12.5, avgRetention: 68.3, avgCtr: 8.2, avgEngagement: 11.5,
      growthRate: 5.2, revenueContribution: 28.5,
      topContentCategories: ['게임 리뷰', '하드웨어 언박싱', '게임 플레이'],
      bestPostingTimes: [{ day: '금', hour: 20, score: 95 }, { day: '토', hour: 14, score: 88 }],
      createdAt: '2025-01-15', updatedAt: '2025-02-20',
    },
    {
      id: 2, name: '25-34세 여성 뷰티', type: 'CUSTOM',
      criteria: { ageRange: { min: 25, max: 34 }, gender: 'FEMALE', interests: ['뷰티', '패션'] },
      size: 38700, percentage: 27.5, avgWatchTime: 9.8, avgRetention: 72.1, avgCtr: 10.5, avgEngagement: 14.2,
      growthRate: 8.1, revenueContribution: 35.2,
      topContentCategories: ['메이크업 튜토리얼', '제품 리뷰', '데일리 룩'],
      bestPostingTimes: [{ day: '수', hour: 19, score: 92 }, { day: '일', hour: 11, score: 85 }],
      createdAt: '2025-01-20', updatedAt: '2025-02-18',
    },
    {
      id: 3, name: '35-44세 직장인', type: 'AGE',
      criteria: { ageRange: { min: 35, max: 44 }, interests: ['자기계발', '재테크', '건강'] },
      size: 28400, percentage: 20.2, avgWatchTime: 7.2, avgRetention: 65.8, avgCtr: 6.8, avgEngagement: 8.3,
      growthRate: 3.4, revenueContribution: 22.1,
      topContentCategories: ['재테크 팁', '건강 루틴', '생산성 도구'],
      bestPostingTimes: [{ day: '월', hour: 7, score: 90 }, { day: '목', hour: 21, score: 82 }],
      createdAt: '2025-02-01', updatedAt: '2025-02-22',
    },
  ]
}

export const useAudienceSegmentStore = defineStore('audienceSegment', () => {
  const segments = ref<AudienceSegment[]>([])
  const selectedInsight = ref<SegmentInsight | null>(null)
  const isLoading = ref(false)

  const totalAudience = computed(() => segments.value.reduce((sum, s) => sum + s.size, 0))
  const topSegment = computed(() =>
    segments.value.length > 0
      ? [...segments.value].sort((a, b) => b.revenueContribution - a.revenueContribution)[0]
      : null,
  )

  async function fetchSegments() {
    isLoading.value = true
    try {
      segments.value = await audienceSegmentApi.getAll()
    } catch {
      segments.value = generateMockSegments()
    } finally {
      isLoading.value = false
    }
  }

  async function createSegment(request: CreateSegmentRequest) {
    try {
      const created = await audienceSegmentApi.create(request)
      segments.value.push(created)
    } catch {
      const mock: AudienceSegment = {
        id: Date.now(), name: request.name, type: request.type, criteria: request.criteria,
        size: 0, percentage: 0, avgWatchTime: 0, avgRetention: 0, avgCtr: 0, avgEngagement: 0,
        growthRate: 0, revenueContribution: 0, topContentCategories: [], bestPostingTimes: [],
        createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(),
      }
      segments.value.push(mock)
    }
  }

  async function deleteSegment(id: number) {
    try { await audienceSegmentApi.delete(id) } catch { /* fallback */ }
    segments.value = segments.value.filter((s) => s.id !== id)
  }

  async function fetchInsight(segmentId: number) {
    try {
      selectedInsight.value = await audienceSegmentApi.getInsight(segmentId)
    } catch {
      selectedInsight.value = {
        segmentId,
        contentRecommendations: [
          { category: '튜토리얼', reason: '이 세그먼트의 시청 유지율이 가장 높은 카테고리', confidence: 0.92 },
          { category: '리뷰', reason: '참여율이 평균 대비 40% 높음', confidence: 0.85 },
        ],
        titleStyle: '감정 호소형 제목 + 숫자 활용이 CTR 12% 높음',
        thumbnailStyle: '밝은 색상, 얼굴 클로즈업, 텍스트 2줄 이하',
        optimalLength: { min: 8, max: 15, unit: 'minutes' },
        toneRecommendation: '친근하고 에너지 넘치는 톤',
        growthOpportunities: ['숏폼 콘텐츠 확대', 'TikTok 크로스포스팅', '커뮤니티 포스트 활성화'],
      }
    }
  }

  return { segments, selectedInsight, isLoading, totalAudience, topSegment, fetchSegments, createSegment, deleteSegment, fetchInsight }
})
