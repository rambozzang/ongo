import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  GenerateMetaRequest,
  GenerateMetaResponse,
  GenerateHashtagsRequest,
  GenerateHashtagsResponse,
  GenerateReportResponse,
  StrategyCoachRequest,
  StrategyCoachResponse,
  RevenueReportResponse,
} from '@/types/ai'
import { aiApi } from '@/api/ai'
import { CREDIT_INSUFFICIENT, matchesCode } from '@/composables/usePlanLimit'

export const useAiStore = defineStore('ai', () => {
  const metaResult = ref<GenerateMetaResponse | null>(null)
  const hashtagResult = ref<GenerateHashtagsResponse | null>(null)
  const reportResult = ref<GenerateReportResponse | null>(null)
  const strategyCoachResult = ref<StrategyCoachResponse | null>(null)
  const revenueReportResult = ref<RevenueReportResponse | null>(null)
  const isGeneratingMeta = ref(false)
  const isGeneratingHashtags = ref(false)
  const isGeneratingReport = ref(false)
  const isGeneratingStrategyCoach = ref(false)
  const isGeneratingRevenueReport = ref(false)
  const error = ref<string | null>(null)
  const creditBlocked = ref(false)

  // Backwards-compatible aggregate loading flag
  const loading = computed(() =>
    isGeneratingMeta.value ||
    isGeneratingHashtags.value ||
    isGeneratingReport.value ||
    isGeneratingStrategyCoach.value ||
    isGeneratingRevenueReport.value,
  )

  async function generateMeta(request: GenerateMetaRequest) {
    if (isGeneratingMeta.value) return metaResult.value
    isGeneratingMeta.value = true
    error.value = null
    creditBlocked.value = false
    try {
      metaResult.value = await aiApi.generateMeta(request)
      return metaResult.value
    } catch (e) {
      // 크레딧 잔액 부족은 안정 코드로만 판단한다. 일반 오류와 CTA 상태를 섞지 않는다.
      if (matchesCode(e, CREDIT_INSUFFICIENT)) {
        creditBlocked.value = true
        error.value = null
        return null
      }
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
    creditBlocked.value = false
    try {
      hashtagResult.value = await aiApi.generateHashtags(request)
      return hashtagResult.value
    } catch (e) {
      // 크레딧 잔액 부족은 안정 코드로만 판단한다. 일반 오류와 CTA 상태를 섞지 않는다.
      if (matchesCode(e, CREDIT_INSUFFICIENT)) {
        creditBlocked.value = true
        error.value = null
        return null
      }
      error.value = e instanceof Error ? e.message : 'AI 요청 실패'
      throw e
    } finally {
      isGeneratingHashtags.value = false
    }
  }

  async function generateReport(period: '7d' | '30d') {
    if (isGeneratingReport.value) return reportResult.value
    isGeneratingReport.value = true
    error.value = null
    creditBlocked.value = false
    const days = period === '30d' ? 30 : 7
    try {
      reportResult.value = await aiApi.generateReport({ days })
      return reportResult.value
    } catch (e) {
      // 크레딧 잔액 부족은 안정 코드로만 판단한다. 일반 오류와 CTA 상태를 섞지 않는다.
      if (matchesCode(e, CREDIT_INSUFFICIENT)) {
        creditBlocked.value = true
        error.value = null
        return null
      }
      error.value = e instanceof Error ? e.message : 'AI 요청 실패'
      throw e
    } finally {
      isGeneratingReport.value = false
    }
  }

  async function generateStrategyCoach(request: StrategyCoachRequest) {
    if (isGeneratingStrategyCoach.value) return strategyCoachResult.value
    isGeneratingStrategyCoach.value = true
    error.value = null
    creditBlocked.value = false
    try {
      strategyCoachResult.value = await aiApi.strategyCoach(request)
      return strategyCoachResult.value
    } catch (e) {
      // 크레딧 잔액 부족은 안정 코드로만 판단한다. 일반 오류와 CTA 상태를 섞지 않는다.
      if (matchesCode(e, CREDIT_INSUFFICIENT)) {
        creditBlocked.value = true
        error.value = null
        return null
      }
      error.value = e instanceof Error ? e.message : 'AI 요청 실패'
      throw e
    } finally {
      isGeneratingStrategyCoach.value = false
    }
  }

  async function generateRevenueReport(days: number) {
    if (isGeneratingRevenueReport.value) return revenueReportResult.value
    isGeneratingRevenueReport.value = true
    error.value = null
    creditBlocked.value = false
    try {
      revenueReportResult.value = await aiApi.revenueReport({ days })
      return revenueReportResult.value
    } catch (e) {
      // 크레딧 잔액 부족은 안정 코드로만 판단한다. 일반 오류와 CTA 상태를 섞지 않는다.
      if (matchesCode(e, CREDIT_INSUFFICIENT)) {
        creditBlocked.value = true
        error.value = null
        return null
      }
      error.value = e instanceof Error ? e.message : 'AI 요청 실패'
      throw e
    } finally {
      isGeneratingRevenueReport.value = false
    }
  }

  function clearResults() {
    metaResult.value = null
    hashtagResult.value = null
    reportResult.value = null
    strategyCoachResult.value = null
    revenueReportResult.value = null
    error.value = null
    creditBlocked.value = false
  }

  return {
    metaResult,
    hashtagResult,
    reportResult,
    strategyCoachResult,
    revenueReportResult,
    loading,
    isGeneratingMeta,
    isGeneratingHashtags,
    isGeneratingReport,
    isGeneratingStrategyCoach,
    isGeneratingRevenueReport,
    error,
    creditBlocked,
    generateMeta,
    generateHashtags,
    generateReport,
    generateStrategyCoach,
    generateRevenueReport,
    clearResults,
  }
})
