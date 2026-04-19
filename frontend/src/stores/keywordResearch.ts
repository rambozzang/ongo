import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { KeywordPlatform, KeywordResearchResult, KeywordHistoryItem } from '@/types/keywordResearch'
import { keywordResearchApi } from '@/api/keywordResearch'

export const useKeywordResearchStore = defineStore('keywordResearch', () => {
  const history = ref<KeywordHistoryItem[]>([])
  const loading = ref(false)
  const researching = ref(false)
  const totalCount = ref(0)
  const page = ref(0)
  const pageSize = ref(10)
  const currentResult = ref<KeywordResearchResult | null>(null)

  const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value))
  const hasNextPage = computed(() => (page.value + 1) * pageSize.value < totalCount.value)
  const hasPrevPage = computed(() => page.value > 0)

  const research = async (keyword: string, platforms: KeywordPlatform[]) => {
    researching.value = true
    currentResult.value = null
    try {
      currentResult.value = await keywordResearchApi.research(keyword, platforms)
      return currentResult.value
    } catch {
      return null
    } finally {
      researching.value = false
    }
  }

  const fetchHistory = async (resetPage = true) => {
    loading.value = true
    if (resetPage) page.value = 0
    try {
      const response = await keywordResearchApi.getHistory(page.value, pageSize.value)
      history.value = response.items
      totalCount.value = response.totalCount
    } catch {
      history.value = []
      totalCount.value = 0
    } finally {
      loading.value = false
    }
  }

  const nextPage = () => {
    if (hasNextPage.value) {
      page.value++
      fetchHistory(false)
    }
  }

  const prevPage = () => {
    if (hasPrevPage.value) {
      page.value--
      fetchHistory(false)
    }
  }

  return {
    history,
    loading,
    researching,
    totalCount,
    page,
    pageSize,
    totalPages,
    hasNextPage,
    hasPrevPage,
    currentResult,
    research,
    fetchHistory,
    nextPage,
    prevPage,
  }
})
