import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { agencyApi } from '@/api/agency'
import type {
  AgencyCreator,
  AgencyKpi,
  CreatorComparison,
  AgencyScheduleItem,
  AgencyActivity,
  ClientPortalLink,
  ClientPortalData,
} from '@/types/agency'

export const useAgencyStore = defineStore('agency', () => {
  const kpi = ref<AgencyKpi | null>(null)
  const creators = ref<AgencyCreator[]>([])
  const comparisons = ref<CreatorComparison[]>([])
  const schedules = ref<AgencyScheduleItem[]>([])
  const activities = ref<AgencyActivity[]>([])
  const clientLinks = ref<ClientPortalLink[]>([])
  const portalData = ref<ClientPortalData | null>(null)
  const loading = ref(false)
  const portalLoading = ref(false)

  const activeCreators = computed(() => creators.value.filter((c) => c.status === 'active'))
  const topCreator = computed(() =>
    [...creators.value].sort((a, b) => b.totalViews - a.totalViews)[0] ?? null,
  )

  async function fetchDashboard() {
    loading.value = true
    try {
      const [kpiData, creatorsData, schedulesData, activitiesData, linksData] = await Promise.allSettled([
        agencyApi.getKpi(),
        agencyApi.listCreators(),
        agencyApi.listSchedules(),
        agencyApi.listActivities(),
        agencyApi.listClientLinks(),
      ])
      if (kpiData.status === 'fulfilled') kpi.value = kpiData.value
      if (creatorsData.status === 'fulfilled') creators.value = creatorsData.value
      if (schedulesData.status === 'fulfilled') schedules.value = schedulesData.value
      if (activitiesData.status === 'fulfilled') activities.value = activitiesData.value
      if (linksData.status === 'fulfilled') clientLinks.value = linksData.value
    } finally {
      loading.value = false
    }
  }

  async function fetchComparisons(creatorIds: number[], period: string) {
    try {
      comparisons.value = await agencyApi.getCreatorComparison(creatorIds, period)
    } catch {
      comparisons.value = []
    }
  }

  async function createClientLink(creatorId: number) {
    const newLink = await agencyApi.createClientLink(creatorId)
    clientLinks.value.push(newLink)
    return newLink
  }

  async function revokeClientLink(linkId: number) {
    await agencyApi.revokeClientLink(linkId)
    clientLinks.value = clientLinks.value.filter((l) => l.id !== linkId)
  }

  async function fetchPortalData(token: string) {
    portalLoading.value = true
    try {
      portalData.value = await agencyApi.getClientPortalData(token)
    } finally {
      portalLoading.value = false
    }
  }

  return {
    kpi,
    creators,
    comparisons,
    schedules,
    activities,
    clientLinks,
    portalData,
    loading,
    portalLoading,
    activeCreators,
    topCreator,
    fetchDashboard,
    fetchComparisons,
    createClientLink,
    revokeClientLink,
    fetchPortalData,
  }
})
