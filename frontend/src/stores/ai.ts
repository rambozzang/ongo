import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  GenerateMetaRequest,
  GenerateMetaResponse,
  GenerateHashtagsRequest,
  GenerateHashtagsResponse,
  GenerateIdeasResponse,
  GenerateReportResponse,
} from '@/types/ai'
import { aiApi } from '@/api/ai'

export const useAiStore = defineStore('ai', () => {
  const metaResult = ref<GenerateMetaResponse | null>(null)
  const hashtagResult = ref<GenerateHashtagsResponse | null>(null)
  const ideasResult = ref<GenerateIdeasResponse | null>(null)
  const reportResult = ref<GenerateReportResponse | null>(null)
  const isGeneratingMeta = ref(false)
  const isGeneratingHashtags = ref(false)
  const isGeneratingIdeas = ref(false)
  const isGeneratingReport = ref(false)
  const error = ref<string | null>(null)

  // Backwards-compatible aggregate loading flag
  const loading = computed(() =>
    isGeneratingMeta.value ||
    isGeneratingHashtags.value ||
    isGeneratingIdeas.value ||
    isGeneratingReport.value,
  )

  async function generateMeta(request: GenerateMetaRequest) {
    if (isGeneratingMeta.value) return metaResult.value
    isGeneratingMeta.value = true
    error.value = null
    try {
      metaResult.value = await aiApi.generateMeta(request)
      return metaResult.value
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'AI 요청 실패'
      throw e
    } finally {
      isGeneratingMeta.value = false
    }
  }

  async function generateHashtags(request: GenerateHashtagsRequest) {
    if (isGeneratingHashtags.value) return hashtagResult.value
    isGeneratingHashtags.value = true
    error.value = null
    try {
      hashtagResult.value = await aiApi.generateHashtags(request)
      return hashtagResult.value
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'AI 요청 실패'
      throw e
    } finally {
      isGeneratingHashtags.value = false
    }
  }

  async function generateIdeas(category: string) {
    if (isGeneratingIdeas.value) return ideasResult.value
    isGeneratingIdeas.value = true
    error.value = null
    try {
      ideasResult.value = await aiApi.generateIdeas({ category })
      return ideasResult.value
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'AI 요청 실패'
      throw e
    } finally {
      isGeneratingIdeas.value = false
    }
  }

  async function generateReport(period: '7d' | '30d') {
    if (isGeneratingReport.value) return reportResult.value
    isGeneratingReport.value = true
    error.value = null
    const days = period === '30d' ? 30 : 7
    try {
      reportResult.value = await aiApi.generateReport({ days })
      return reportResult.value
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'AI 요청 실패'
      throw e
    } finally {
      isGeneratingReport.value = false
    }
  }

  function clearResults() {
    metaResult.value = null
    hashtagResult.value = null
    ideasResult.value = null
    reportResult.value = null
    error.value = null
  }

  return {
    metaResult,
    hashtagResult,
    ideasResult,
    reportResult,
    loading,
    isGeneratingMeta,
    isGeneratingHashtags,
    isGeneratingIdeas,
    isGeneratingReport,
    error,
    generateMeta,
    generateHashtags,
    generateIdeas,
    generateReport,
    clearResults,
  }
})
