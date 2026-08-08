import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { KeywordPlatform, KeywordResearchResult, KeywordHistoryItem } from '@/types/keywordResearch'
import { keywordResearchApi } from '@/api/keywordResearch'
import { useNotificationStore } from '@/stores/notification'

export const useKeywordResearchStore = defineStore('keywordResearch', () => {
  const history = ref<KeywordHistoryItem[]>([])
  const loading = ref(false)
  const researching = ref(false)
  const totalCount = ref(0)
  // The backend history query uses 1-based pages.
  const page = ref(1)
  const pageSize = ref(10)
  const currentResult = ref<KeywordResearchResult | null>(null)
  const historyError = ref<string | null>(null)

  const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value))
  const hasNextPage = computed(() => (page.value + 1) * pageSize.value < totalCount.value)
  const hasPrevPage = computed(() => page.value > 0)

  const research = async (keyword: string, platforms: KeywordPlatform[]) => {
    researching.value = true
    currentResult.value = null
    try {
      currentResult.value = await keywordResearchApi.research(keyword, platforms)
      return currentResult.value
    } catch (error: any) {
      useNotificationStore().error(
        error?.response?.data?.message || error?.message || '키워드 분석에 실패했습니다. 잠시 후 다시 시도해주세요.',
      )
      return null
    } finally {
      researching.value = false
    }
  }

  const fetchHistory = async (resetPage = true) => {
    loading.value = true
    historyError.value = null
    if (resetPage) page.value = 1
    try {
      const response = await keywordResearchApi.getHistory(page.value, pageSize.value)
      history.value = response.items
      totalCount.value = response.totalCount
    } catch (error: any) {
      if (error?.response?.status !== 404) {
        historyError.value = error?.response?.data?.message || error?.message || '검색 이력을 불러오지 못했습니다.'
        useNotificationStore().error(historyError.value ?? '검색 이력을 불러오지 못했습니다.')
      }
      if (error?.response?.status === 404) {
        history.value = []
        totalCount.value = 0
      }
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
    historyError,
    research,
    fetchHistory,
    nextPage,
    prevPage,
  }
})
