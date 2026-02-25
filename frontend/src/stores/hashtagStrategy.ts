import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  HashtagSet,
  HashtagAnalysisRequest,
  Hashtag,
} from '@/types/hashtagStrategy'
import { hashtagStrategyApi } from '@/api/hashtagStrategy'

// ── Mock data ────────────────────────────────────────────────────────

function generateMockHashtags(strategy: 'conservative' | 'balanced' | 'aggressive'): Hashtag[] {
  const tagSets: Record<string, Hashtag[]> = {
    conservative: [
      { tag: '#일상브이로그', reachEstimate: 120000, competition: 'EASY', trend: 'STABLE', avgViews: 8500, relatedTags: ['#브이로그', '#일상'] },
      { tag: '#소소한일상', reachEstimate: 85000, competition: 'EASY', trend: 'RISING', avgViews: 6200, relatedTags: ['#일상기록', '#데일리'] },
      { tag: '#오늘의기록', reachEstimate: 65000, competition: 'EASY', trend: 'STABLE', avgViews: 4800, relatedTags: ['#일기', '#기록'] },
      { tag: '#일상공유', reachEstimate: 72000, competition: 'EASY', trend: 'STABLE', avgViews: 5100, relatedTags: ['#공유', '#일상'] },
      { tag: '#데일리로그', reachEstimate: 55000, competition: 'EASY', trend: 'RISING', avgViews: 3900, relatedTags: ['#데일리', '#로그'] },
      { tag: '#소통', reachEstimate: 95000, competition: 'MEDIUM', trend: 'STABLE', avgViews: 7100, relatedTags: ['#소통해요', '#맞팔'] },
      { tag: '#힐링영상', reachEstimate: 48000, competition: 'EASY', trend: 'RISING', avgViews: 3400, relatedTags: ['#힐링', '#편안한'] },
      { tag: '#감성브이로그', reachEstimate: 62000, competition: 'EASY', trend: 'STABLE', avgViews: 4500, relatedTags: ['#감성', '#무드'] },
    ],
    balanced: [
      { tag: '#브이로그', reachEstimate: 580000, competition: 'MEDIUM', trend: 'STABLE', avgViews: 42000, relatedTags: ['#vlog', '#일상'] },
      { tag: '#여행브이로그', reachEstimate: 320000, competition: 'MEDIUM', trend: 'RISING', avgViews: 28000, relatedTags: ['#여행', '#트립'] },
      { tag: '#맛집탐방', reachEstimate: 240000, competition: 'MEDIUM', trend: 'RISING', avgViews: 18500, relatedTags: ['#맛집', '#먹방'] },
      { tag: '#카페투어', reachEstimate: 195000, competition: 'MEDIUM', trend: 'STABLE', avgViews: 15200, relatedTags: ['#카페', '#커피'] },
      { tag: '#주말일상', reachEstimate: 145000, competition: 'EASY', trend: 'STABLE', avgViews: 11800, relatedTags: ['#주말', '#일상'] },
      { tag: '#핫플레이스', reachEstimate: 210000, competition: 'HARD', trend: 'RISING', avgViews: 16000, relatedTags: ['#핫플', '#인기'] },
      { tag: '#일상기록', reachEstimate: 130000, competition: 'EASY', trend: 'STABLE', avgViews: 9800, relatedTags: ['#일상', '#기록'] },
      { tag: '#요즘뭐해', reachEstimate: 160000, competition: 'MEDIUM', trend: 'RISING', avgViews: 12500, relatedTags: ['#요즘', '#트렌드'] },
    ],
    aggressive: [
      { tag: '#숏츠', reachEstimate: 1200000, competition: 'VERY_HARD', trend: 'RISING', avgViews: 95000, relatedTags: ['#shorts', '#릴스'] },
      { tag: '#trending', reachEstimate: 980000, competition: 'VERY_HARD', trend: 'RISING', avgViews: 72000, relatedTags: ['#트렌딩', '#인기'] },
      { tag: '#viral', reachEstimate: 850000, competition: 'VERY_HARD', trend: 'STABLE', avgViews: 65000, relatedTags: ['#바이럴', '#화제'] },
      { tag: '#먹방', reachEstimate: 720000, competition: 'HARD', trend: 'STABLE', avgViews: 55000, relatedTags: ['#mukbang', '#맛집'] },
      { tag: '#여행', reachEstimate: 680000, competition: 'HARD', trend: 'RISING', avgViews: 48000, relatedTags: ['#travel', '#트립'] },
      { tag: '#인기급상승', reachEstimate: 450000, competition: 'HARD', trend: 'RISING', avgViews: 35000, relatedTags: ['#인기', '#급상승'] },
      { tag: '#추천', reachEstimate: 560000, competition: 'VERY_HARD', trend: 'STABLE', avgViews: 42000, relatedTags: ['#추천영상', '#must'] },
      { tag: '#유튜브추천', reachEstimate: 380000, competition: 'HARD', trend: 'RISING', avgViews: 29000, relatedTags: ['#유튜브', '#추천'] },
    ],
  }
  return tagSets[strategy]
}

function generateMockSets(): HashtagSet[] {
  const conservativeTags = generateMockHashtags('conservative')
  const balancedTags = generateMockHashtags('balanced')
  const aggressiveTags = generateMockHashtags('aggressive')

  return [
    {
      id: 1,
      name: 'conservative',
      hashtags: conservativeTags,
      totalReachEstimate: conservativeTags.reduce((sum, h) => sum + h.reachEstimate, 0),
      overallDifficulty: 'EASY',
      score: 72,
      platform: 'YOUTUBE',
      createdAt: new Date().toISOString(),
    },
    {
      id: 2,
      name: 'balanced',
      hashtags: balancedTags,
      totalReachEstimate: balancedTags.reduce((sum, h) => sum + h.reachEstimate, 0),
      overallDifficulty: 'MEDIUM',
      score: 85,
      platform: 'YOUTUBE',
      createdAt: new Date().toISOString(),
    },
    {
      id: 3,
      name: 'aggressive',
      hashtags: aggressiveTags,
      totalReachEstimate: aggressiveTags.reduce((sum, h) => sum + h.reachEstimate, 0),
      overallDifficulty: 'HARD',
      score: 64,
      platform: 'YOUTUBE',
      createdAt: new Date().toISOString(),
    },
  ]
}

function generateMockSavedSets(): HashtagSet[] {
  return [
    {
      id: 101,
      name: 'balanced',
      hashtags: generateMockHashtags('balanced').slice(0, 5),
      totalReachEstimate: 1480000,
      overallDifficulty: 'MEDIUM',
      score: 82,
      platform: 'YOUTUBE',
      createdAt: new Date(Date.now() - 86400000 * 2).toISOString(),
    },
    {
      id: 102,
      name: 'conservative',
      hashtags: generateMockHashtags('conservative').slice(0, 5),
      totalReachEstimate: 437000,
      overallDifficulty: 'EASY',
      score: 75,
      platform: 'TIKTOK',
      createdAt: new Date(Date.now() - 86400000 * 5).toISOString(),
    },
  ]
}

export const useHashtagStrategyStore = defineStore('hashtagStrategy', () => {
  const analysisResult = ref<HashtagSet[]>([])
  const savedSets = ref<HashtagSet[]>([])
  const loading = ref(false)
  const analyzing = ref(false)

  async function analyze(request: HashtagAnalysisRequest) {
    analyzing.value = true
    try {
      const response = await hashtagStrategyApi.analyze(request)
      analysisResult.value = response.sets
    } catch {
      // Mock 데이터 사용
      const mockSets = generateMockSets()
      analysisResult.value = mockSets.map((s) => ({ ...s, platform: request.platform }))
    } finally {
      analyzing.value = false
    }
  }

  async function fetchSavedSets() {
    loading.value = true
    try {
      savedSets.value = await hashtagStrategyApi.getSavedSets()
    } catch {
      savedSets.value = generateMockSavedSets()
    } finally {
      loading.value = false
    }
  }

  async function saveSet(set: HashtagSet) {
    try {
      const saved = await hashtagStrategyApi.saveSet(set)
      savedSets.value.unshift(saved)
    } catch {
      const mockSaved = { ...set, id: Date.now() }
      savedSets.value.unshift(mockSaved)
    }
  }

  async function deleteSet(id: number) {
    try {
      await hashtagStrategyApi.deleteSet(id)
    } catch {
      // 로컬에서 제거
    }
    savedSets.value = savedSets.value.filter((s) => s.id !== id)
  }

  return {
    analysisResult,
    savedSets,
    loading,
    analyzing,
    analyze,
    fetchSavedSets,
    saveSet,
    deleteSet,
  }
})
