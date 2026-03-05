import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  CommercePlatformConnection,
  CommerceProduct,
  AffiliateLink,
  VideoProductLink,
  CommerceKpi,
  CommerceRevenueTrend,
  PlatformPerformance,
  CommercePlatform,
} from '@/types/commerce'
import { commerceApi } from '@/api/commerce'

export const useCommerceStore = defineStore('commerce', () => {
  const loading = ref(false)
  const kpi = ref<CommerceKpi>({
    totalRevenue: 0,
    totalClicks: 0,
    conversionRate: 0,
    linkedProductCount: 0,
    revenueGrowth: 0,
    clicksGrowth: 0,
  })
  const platforms = ref<CommercePlatformConnection[]>([])
  const products = ref<CommerceProduct[]>([])
  const affiliateLinks = ref<AffiliateLink[]>([])
  const videoProductLinks = ref<VideoProductLink[]>([])
  const revenueTrends = ref<CommerceRevenueTrend[]>([])
  const platformPerformance = ref<PlatformPerformance[]>([])

  async function fetchKpi(days = 30) {
    loading.value = true
    try {
      kpi.value = await commerceApi.getKpi(days)
    } catch {
      // keep default empty kpi
    } finally {
      loading.value = false
    }
  }

  async function fetchPlatforms() {
    try {
      platforms.value = await commerceApi.getPlatformConnections()
    } catch {
      platforms.value = []
    }
  }

  async function fetchProducts(filter?: { platform?: CommercePlatform; search?: string }) {
    loading.value = true
    try {
      products.value = await commerceApi.getProducts(filter)
    } catch {
      products.value = []
    } finally {
      loading.value = false
    }
  }

  async function fetchAffiliateLinks() {
    loading.value = true
    try {
      affiliateLinks.value = await commerceApi.getAffiliateLinks()
    } catch {
      affiliateLinks.value = []
    } finally {
      loading.value = false
    }
  }

  async function fetchVideoProductLinks() {
    try {
      videoProductLinks.value = await commerceApi.getVideoProductLinks()
    } catch {
      videoProductLinks.value = []
    }
  }

  async function fetchRevenueTrends(days = 30) {
    try {
      revenueTrends.value = await commerceApi.getRevenueTrends(days)
    } catch {
      revenueTrends.value = []
    }
  }

  async function fetchPlatformPerformance(days = 30) {
    try {
      platformPerformance.value = await commerceApi.getPlatformPerformance(days)
    } catch {
      platformPerformance.value = []
    }
  }

  async function connectPlatform(platform: CommercePlatform) {
    const result = await commerceApi.connectPlatform(platform)
    const idx = platforms.value.findIndex(p => p.platform === platform)
    if (idx >= 0) {
      platforms.value[idx] = result
    } else {
      platforms.value.push(result)
    }
  }

  async function disconnectPlatform(platform: CommercePlatform) {
    const connection = platforms.value.find(p => p.platform === platform)
    if (!connection) return
    await commerceApi.disconnectPlatform(connection.id)
    const idx = platforms.value.findIndex(p => p.platform === platform)
    if (idx >= 0) {
      platforms.value[idx].status = 'DISCONNECTED'
    }
  }

  async function createAffiliateLink(productId: number) {
    const link = await commerceApi.createAffiliateLink(productId)
    affiliateLinks.value.unshift(link)
    return link
  }

  async function fetchDashboard() {
    loading.value = true
    try {
      await Promise.allSettled([
        fetchKpi(),
        fetchPlatforms(),
        fetchRevenueTrends(),
        fetchPlatformPerformance(),
      ])
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    kpi,
    platforms,
    products,
    affiliateLinks,
    videoProductLinks,
    revenueTrends,
    platformPerformance,
    fetchKpi,
    fetchPlatforms,
    fetchProducts,
    fetchAffiliateLinks,
    fetchVideoProductLinks,
    fetchRevenueTrends,
    fetchPlatformPerformance,
    connectPlatform,
    disconnectPlatform,
    createAffiliateLink,
    fetchDashboard,
  }
})
