import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { InfluencerProfile, CollabRequest, MatchFilter } from '@/types/influencerMatch'
import { influencerMatchApi } from '@/api/influencerMatch'

function generateMockInfluencers(): InfluencerProfile[] {
  return [
    { id: 1, name: '맛집탐험가', avatarUrl: '', platform: 'youtube', followers: 125000, avgViews: 32000, engagementRate: 8.5, category: '음식', matchScore: 92, matchReason: '오디언스 관심사가 85% 일치합니다', audienceOverlap: 35, estimatedCost: 500000, recentContent: [{ title: '서울 숨은 맛집', views: 48000 }, { title: '제주 카페 투어', views: 35000 }] },
    { id: 2, name: '트래블러김', avatarUrl: '', platform: 'instagram', followers: 89000, avgViews: 18000, engagementRate: 12.2, category: '여행', matchScore: 87, matchReason: '비슷한 콘텐츠 스타일과 타겟 연령대', audienceOverlap: 28, estimatedCost: 350000, recentContent: [{ title: '방콕 3일 코스', views: 22000 }, { title: '일본 소도시 여행', views: 19000 }] },
    { id: 3, name: '데일리뷰티', avatarUrl: '', platform: 'tiktok', followers: 210000, avgViews: 65000, engagementRate: 15.8, category: '뷰티', matchScore: 74, matchReason: '높은 참여율과 Z세대 오디언스', audienceOverlap: 18, estimatedCost: 800000, recentContent: [{ title: '겨울 스킨케어', views: 92000 }, { title: '데일리 메이크업', views: 78000 }] },
    { id: 4, name: '테크리뷰어', avatarUrl: '', platform: 'youtube', followers: 320000, avgViews: 85000, engagementRate: 6.3, category: '테크', matchScore: 68, matchReason: '구독자 규모가 크고 신뢰도 높음', audienceOverlap: 22, estimatedCost: 1200000, recentContent: [{ title: '2024 가젯 TOP 10', views: 120000 }] },
    { id: 5, name: '운동하는직장인', avatarUrl: '', platform: 'youtube', followers: 58000, avgViews: 15000, engagementRate: 11.5, category: '피트니스', matchScore: 81, matchReason: '라이프스타일 카테고리 시너지', audienceOverlap: 30, estimatedCost: 200000, recentContent: [{ title: '직장인 아침 루틴', views: 21000 }] },
  ]
}

function generateMockCollabs(): CollabRequest[] {
  return [
    { id: 1, influencerId: 1, influencerName: '맛집탐험가', status: 'CONFIRMED', message: '맛집 콜라보 영상을 함께 찍고 싶습니다!', proposedBudget: 500000, createdAt: new Date(Date.now() - 86400000 * 5).toISOString(), updatedAt: new Date(Date.now() - 86400000 * 2).toISOString() },
    { id: 2, influencerId: 2, influencerName: '트래블러김', status: 'CONTACTED', message: '여행 콜라보 제안드립니다', proposedBudget: 350000, createdAt: new Date(Date.now() - 86400000 * 3).toISOString(), updatedAt: new Date(Date.now() - 86400000 * 3).toISOString() },
  ]
}

export const useInfluencerMatchStore = defineStore('influencerMatch', () => {
  const influencers = ref<InfluencerProfile[]>([])
  const collabs = ref<CollabRequest[]>([])
  const loading = ref(false)
  const searching = ref(false)

  const excellentMatches = computed(() => influencers.value.filter((i) => i.matchScore >= 85))
  const activeCollabs = computed(() => collabs.value.filter((c) => !['COMPLETED', 'DECLINED'].includes(c.status)))

  async function findMatches(filter?: MatchFilter) {
    searching.value = true
    try {
      const response = await influencerMatchApi.findMatches(filter)
      influencers.value = response.influencers
    } catch {
      influencers.value = generateMockInfluencers()
    } finally {
      searching.value = false
    }
  }

  async function fetchCollabs() {
    loading.value = true
    try {
      collabs.value = await influencerMatchApi.getCollabRequests()
    } catch {
      collabs.value = generateMockCollabs()
    } finally {
      loading.value = false
    }
  }

  async function sendCollab(influencerId: number, message: string, budget: number) {
    try {
      const collab = await influencerMatchApi.sendCollabRequest(influencerId, message, budget)
      collabs.value.unshift(collab)
    } catch {
      const inf = influencers.value.find((i) => i.id === influencerId)
      collabs.value.unshift({ id: Date.now(), influencerId, influencerName: inf?.name ?? '', status: 'CONTACTED', message, proposedBudget: budget, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() })
    }
  }

  return { influencers, collabs, loading, searching, excellentMatches, activeCollabs, findMatches, fetchCollabs, sendCollab }
})
