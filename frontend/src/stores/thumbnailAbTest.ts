import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ThumbnailTest, ThumbnailAbTestSummary } from '@/types/thumbnailAbTest'
import { thumbnailAbTestApi } from '@/api/thumbnailAbTest'

function generateMockTests(): ThumbnailTest[] {
  return [
    {
      id: 1,
      videoTitle: '봄맞이 브이로그 | 벚꽃 명소 투어',
      platform: 'youtube',
      status: 'COMPLETED',
      variantA: { ctr: 8.2, impressions: 45200, clicks: 3706 },
      variantB: { ctr: 11.5, impressions: 44800, clicks: 5152 },
      winner: 'B',
      startedAt: new Date(Date.now() - 7 * 86400000).toISOString(),
      endedAt: new Date(Date.now() - 1 * 86400000).toISOString(),
    },
    {
      id: 2,
      videoTitle: '직장인 퇴근 후 루틴 공개',
      platform: 'youtube',
      status: 'ACTIVE',
      variantA: { ctr: 6.8, impressions: 22100, clicks: 1503 },
      variantB: { ctr: 7.1, impressions: 21900, clicks: 1555 },
      winner: null,
      startedAt: new Date(Date.now() - 2 * 86400000).toISOString(),
    },
    {
      id: 3,
      videoTitle: '1분 요리 레시피 모음',
      platform: 'tiktok',
      status: 'COMPLETED',
      variantA: { ctr: 12.4, impressions: 98000, clicks: 12152 },
      variantB: { ctr: 9.8, impressions: 97500, clicks: 9555 },
      winner: 'A',
      startedAt: new Date(Date.now() - 14 * 86400000).toISOString(),
      endedAt: new Date(Date.now() - 5 * 86400000).toISOString(),
    },
    {
      id: 4,
      videoTitle: '서울 맛집 탐방 EP.12',
      platform: 'instagram',
      status: 'PENDING',
      variantA: { ctr: 0, impressions: 0, clicks: 0 },
      variantB: { ctr: 0, impressions: 0, clicks: 0 },
      winner: null,
      startedAt: new Date(Date.now() + 86400000).toISOString(),
    },
    {
      id: 5,
      videoTitle: '네이버 클립 첫 도전기',
      platform: 'naverclip',
      status: 'ACTIVE',
      variantA: { ctr: 5.3, impressions: 8400, clicks: 445 },
      variantB: { ctr: 6.9, impressions: 8200, clicks: 566 },
      winner: null,
      startedAt: new Date(Date.now() - 3 * 86400000).toISOString(),
    },
  ]
}

function generateMockSummary(): ThumbnailAbTestSummary {
  return {
    totalTests: 24,
    activeTests: 3,
    avgCtrImprovement: 18.5,
    bestVariantWinRate: 62.5,
    topPlatform: 'YouTube',
  }
}

export const useThumbnailAbTestStore = defineStore('thumbnailAbTest', () => {
  const tests = ref<ThumbnailTest[]>([])
  const summary = ref<ThumbnailAbTestSummary | null>(null)
  const loading = ref(false)

  const activeCount = computed(() => tests.value.filter((t) => t.status === 'ACTIVE').length)
  const completedCount = computed(() => tests.value.filter((t) => t.status === 'COMPLETED').length)

  async function fetchTests() {
    loading.value = true
    try {
      tests.value = await thumbnailAbTestApi.getTests()
    } catch {
      tests.value = generateMockTests()
    } finally {
      loading.value = false
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await thumbnailAbTestApi.getSummary()
    } catch {
      summary.value = generateMockSummary()
    }
  }

  async function deleteTest(id: number) {
    try { await thumbnailAbTestApi.deleteTest(id) } catch { /* 로컬 */ }
    tests.value = tests.value.filter((t) => t.id !== id)
  }

  return { tests, summary, loading, activeCount, completedCount, fetchTests, fetchSummary, deleteTest }
})
