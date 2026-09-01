import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChannelAuditReport } from '@/types/channelAudit'
import { channelAuditApi } from '@/api/channelAudit'
import { CREDIT_INSUFFICIENT, matchesCode } from '@/composables/usePlanLimit'

export const useChannelAuditStore = defineStore('channelAudit', () => {
  const reports = ref<ChannelAuditReport[]>([])
  const loading = ref(false)
  const generating = ref(false)
  const loadError = ref<string | null>(null)
  const generationError = ref<string | null>(null)
  const creditBlocked = ref(false)
  const totalCount = ref(0)
  const page = ref(0)
  const pageSize = ref(10)
  const selectedReport = ref<ChannelAuditReport | null>(null)

  const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value))
  const hasNextPage = computed(() => (page.value + 1) * pageSize.value < totalCount.value)
  const hasPrevPage = computed(() => page.value > 0)

  const fetchReports = async (resetPage = true) => {
    loading.value = true
    loadError.value = null
    if (resetPage) page.value = 0
    try {
      const response = await channelAuditApi.getAudits(page.value, pageSize.value)
      reports.value = response.audits
      totalCount.value = response.totalCount
    } catch (error) {
      loadError.value = error instanceof Error ? error.message : '채널 진단을 불러오지 못했습니다.'
    } finally {
      loading.value = false
    }
  }

  const generateAudit = async () => {
    generating.value = true
    generationError.value = null
    // 매 호출마다 차단 상태를 초기화한다. 이전에 막혔더라도 새 시도는 깨끗하게 시작한다.
    creditBlocked.value = false
    try {
      const report = await channelAuditApi.generateAudit()
      reports.value.unshift(report)
      totalCount.value++
      selectedReport.value = report
      return report
    } catch (error) {
      // 크레딧 잔액 부족은 안정 코드로만 판단한다. 기존 에러 문구는 지워 CTA 만 보이게 한다.
      if (matchesCode(error, CREDIT_INSUFFICIENT)) {
        creditBlocked.value = true
        generationError.value = null
        return null
      }
      // 일반 오류는 기존 generationError 표시를 유지한다. PLAN_LIMIT_EXCEEDED/403 은
      // 여기서 크레딧 CTA 로 바꾸지 않고(플랜 안내가 정답) 그대로 일반 오류로 처리한다.
      generationError.value = error instanceof Error ? error.message : '채널 진단을 생성하지 못했습니다.'
      return null
    } finally {
      generating.value = false
    }
  }

  const fetchDetail = async (id: number) => {
    try {
      selectedReport.value = await channelAuditApi.getAuditDetail(id)
    } catch {
      selectedReport.value = null
    }
  }

  const nextPage = () => {
    if (hasNextPage.value) {
      page.value++
      fetchReports(false)
    }
  }

  const prevPage = () => {
    if (hasPrevPage.value) {
      page.value--
      fetchReports(false)
    }
  }

  return {
    reports,
    loading,
    generating,
    loadError,
    generationError,
    creditBlocked,
    totalCount,
    page,
    pageSize,
    totalPages,
    hasNextPage,
    hasPrevPage,
    selectedReport,
    fetchReports,
    generateAudit,
    fetchDetail,
    nextPage,
    prevPage,
  }
})
