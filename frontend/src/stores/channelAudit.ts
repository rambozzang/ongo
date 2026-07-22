import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChannelAuditReport } from '@/types/channelAudit'
import { channelAuditApi } from '@/api/channelAudit'

export const useChannelAuditStore = defineStore('channelAudit', () => {
  const reports = ref<ChannelAuditReport[]>([])
  const loading = ref(false)
  const generating = ref(false)
  const totalCount = ref(0)
  const page = ref(0)
  const pageSize = ref(10)
  const selectedReport = ref<ChannelAuditReport | null>(null)

  const totalPages = computed(() => Math.ceil(totalCount.value / pageSize.value))
  const hasNextPage = computed(() => (page.value + 1) * pageSize.value < totalCount.value)
  const hasPrevPage = computed(() => page.value > 0)

  const fetchReports = async (resetPage = true) => {
    loading.value = true
    if (resetPage) page.value = 0
    try {
      const response = await channelAuditApi.getAudits(page.value, pageSize.value)
      reports.value = response.audits
      totalCount.value = response.totalCount
    } catch {
      reports.value = []
      totalCount.value = 0
    } finally {
      loading.value = false
    }
  }

  const generateAudit = async () => {
    generating.value = true
    try {
      const report = await channelAuditApi.generateAudit()
      reports.value.unshift(report)
      totalCount.value++
      selectedReport.value = report
      return report
    } catch {
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
