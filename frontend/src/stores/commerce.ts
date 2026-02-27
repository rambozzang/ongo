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
      // 실패 시 mock 데이터 사용
      kpi.value = generateMockKpi()
    } finally {
      loading.value = false
    }
  }

  async function fetchPlatforms() {
    try {
      platforms.value = await commerceApi.getPlatformConnections()
    } catch {
      platforms.value = generateMockPlatforms()
    }
  }

  async function fetchProducts(filter?: { platform?: CommercePlatform; search?: string }) {
    loading.value = true
    try {
      products.value = await commerceApi.getProducts(filter)
    } catch {
      products.value = generateMockProducts()
    } finally {
      loading.value = false
    }
  }

  async function fetchAffiliateLinks() {
    loading.value = true
    try {
      affiliateLinks.value = await commerceApi.getAffiliateLinks()
    } catch {
      affiliateLinks.value = generateMockAffiliateLinks()
    } finally {
      loading.value = false
    }
  }

  async function fetchVideoProductLinks() {
    try {
      videoProductLinks.value = await commerceApi.getVideoProductLinks()
    } catch {
      videoProductLinks.value = generateMockVideoProductLinks()
    }
  }

  async function fetchRevenueTrends(days = 30) {
    try {
      revenueTrends.value = await commerceApi.getRevenueTrends(days)
    } catch {
      revenueTrends.value = generateMockRevenueTrends()
    }
  }

  async function fetchPlatformPerformance(days = 30) {
    try {
      platformPerformance.value = await commerceApi.getPlatformPerformance(days)
    } catch {
      platformPerformance.value = generateMockPlatformPerformance()
    }
  }

  async function connectPlatform(platform: CommercePlatform) {
    try {
      const result = await commerceApi.connectPlatform(platform)
      const idx = platforms.value.findIndex(p => p.platform === platform)
      if (idx >= 0) {
        platforms.value[idx] = result
      } else {
        platforms.value.push(result)
      }
    } catch {
      // mock 연결
      const idx = platforms.value.findIndex(p => p.platform === platform)
      if (idx >= 0) {
        platforms.value[idx].status = 'CONNECTED'
        platforms.value[idx].connectedAt = new Date().toISOString()
      }
    }
  }

  async function disconnectPlatform(platform: CommercePlatform) {
    const connection = platforms.value.find(p => p.platform === platform)
    if (!connection) return
    try {
      await commerceApi.disconnectPlatform(connection.id)
    } catch {
      // mock
    }
    const idx = platforms.value.findIndex(p => p.platform === platform)
    if (idx >= 0) {
      platforms.value[idx].status = 'DISCONNECTED'
    }
  }

  async function createAffiliateLink(productId: number) {
    try {
      const link = await commerceApi.createAffiliateLink(productId)
      affiliateLinks.value.unshift(link)
      return link
    } catch {
      // mock
      const product = products.value.find(p => p.id === productId)
      const mockLink: AffiliateLink = {
        id: Date.now(),
        productId,
        productName: product?.name ?? '상품',
        originalUrl: product?.affiliateUrl ?? 'https://example.com',
        affiliateUrl: `https://link.ongo.kr/aff/${Date.now()}`,
        shortUrl: `https://ongo.kr/s/${Date.now().toString(36)}`,
        platform: product?.platform ?? 'COUPANG',
        clicks: 0,
        conversions: 0,
        revenue: 0,
        createdAt: new Date().toISOString(),
      }
      affiliateLinks.value.unshift(mockLink)
      return mockLink
    }
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

  // ----- Mock 데이터 생성 -----
  function generateMockKpi(): CommerceKpi {
    return {
      totalRevenue: 2_847_300,
      totalClicks: 15_420,
      conversionRate: 3.2,
      linkedProductCount: 48,
      revenueGrowth: 12.5,
      clicksGrowth: 8.3,
    }
  }

  function generateMockPlatforms(): CommercePlatformConnection[] {
    return [
      {
        id: 1,
        platform: 'COUPANG',
        status: 'CONNECTED',
        storeName: '크리에이터 스토어',
        connectedAt: '2025-12-01T00:00:00Z',
      },
      {
        id: 2,
        platform: 'NAVER_SMARTSTORE',
        status: 'CONNECTED',
        storeName: '네이버 스마트스토어',
        connectedAt: '2025-11-15T00:00:00Z',
      },
      {
        id: 3,
        platform: 'TIKTOK_SHOP',
        status: 'DISCONNECTED',
      },
    ]
  }

  function generateMockProducts(): CommerceProduct[] {
    return [
      {
        id: 1,
        name: '무선 블루투스 이어폰 Pro',
        imageUrl: '',
        price: 45000,
        platform: 'COUPANG',
        category: '전자기기',
        linkedVideoCount: 3,
        clicks: 1240,
        conversions: 42,
        revenue: 378000,
        affiliateUrl: 'https://link.coupang.com/aff/123',
        createdAt: '2025-12-10T00:00:00Z',
      },
      {
        id: 2,
        name: '촉촉 수분크림 200ml',
        imageUrl: '',
        price: 28000,
        platform: 'NAVER_SMARTSTORE',
        category: '뷰티',
        linkedVideoCount: 5,
        clicks: 2300,
        conversions: 89,
        revenue: 534000,
        affiliateUrl: 'https://smartstore.naver.com/aff/456',
        createdAt: '2025-12-08T00:00:00Z',
      },
      {
        id: 3,
        name: '데일리 크로스백 미니',
        imageUrl: '',
        price: 35000,
        platform: 'COUPANG',
        category: '패션',
        linkedVideoCount: 2,
        clicks: 890,
        conversions: 28,
        revenue: 196000,
        affiliateUrl: 'https://link.coupang.com/aff/789',
        createdAt: '2025-12-05T00:00:00Z',
      },
      {
        id: 4,
        name: '홈트레이닝 요가매트',
        imageUrl: '',
        price: 22000,
        platform: 'COUPANG',
        category: '스포츠',
        linkedVideoCount: 1,
        clicks: 560,
        conversions: 15,
        revenue: 66000,
        affiliateUrl: 'https://link.coupang.com/aff/101',
        createdAt: '2025-12-01T00:00:00Z',
      },
      {
        id: 5,
        name: 'LED 링라이트 스탠드',
        imageUrl: '',
        price: 55000,
        platform: 'NAVER_SMARTSTORE',
        category: '촬영장비',
        linkedVideoCount: 4,
        clicks: 1100,
        conversions: 35,
        revenue: 385000,
        affiliateUrl: 'https://smartstore.naver.com/aff/202',
        createdAt: '2025-11-28T00:00:00Z',
      },
      {
        id: 6,
        name: '비타민C 세럼 30ml',
        imageUrl: '',
        price: 32000,
        platform: 'TIKTOK_SHOP',
        category: '뷰티',
        linkedVideoCount: 6,
        clicks: 3200,
        conversions: 120,
        revenue: 768000,
        affiliateUrl: 'https://tiktok.com/shop/aff/303',
        createdAt: '2025-11-25T00:00:00Z',
      },
    ]
  }

  function generateMockAffiliateLinks(): AffiliateLink[] {
    return [
      {
        id: 1,
        productId: 1,
        productName: '무선 블루투스 이어폰 Pro',
        originalUrl: 'https://www.coupang.com/product/123',
        affiliateUrl: 'https://link.coupang.com/aff/123',
        shortUrl: 'https://ongo.kr/s/abc123',
        platform: 'COUPANG',
        clicks: 1240,
        conversions: 42,
        revenue: 378000,
        createdAt: '2025-12-10T00:00:00Z',
      },
      {
        id: 2,
        productId: 2,
        productName: '촉촉 수분크림 200ml',
        originalUrl: 'https://smartstore.naver.com/product/456',
        affiliateUrl: 'https://smartstore.naver.com/aff/456',
        shortUrl: 'https://ongo.kr/s/def456',
        platform: 'NAVER_SMARTSTORE',
        clicks: 2300,
        conversions: 89,
        revenue: 534000,
        createdAt: '2025-12-08T00:00:00Z',
      },
      {
        id: 3,
        productId: 6,
        productName: '비타민C 세럼 30ml',
        originalUrl: 'https://tiktok.com/shop/product/303',
        affiliateUrl: 'https://tiktok.com/shop/aff/303',
        shortUrl: 'https://ongo.kr/s/ghi789',
        platform: 'TIKTOK_SHOP',
        clicks: 3200,
        conversions: 120,
        revenue: 768000,
        createdAt: '2025-11-25T00:00:00Z',
      },
    ]
  }

  function generateMockVideoProductLinks(): VideoProductLink[] {
    return [
      {
        id: 1,
        videoId: 101,
        videoTitle: '이어폰 솔직 리뷰 | 가성비 끝판왕',
        videoThumbnail: '',
        productId: 1,
        productName: '무선 블루투스 이어폰 Pro',
        productImageUrl: '',
        platform: 'COUPANG',
        clicks: 450,
        conversions: 15,
        revenue: 135000,
        linkedAt: '2025-12-12T00:00:00Z',
      },
      {
        id: 2,
        videoId: 102,
        videoTitle: '겨울 스킨케어 루틴 공개',
        videoThumbnail: '',
        productId: 2,
        productName: '촉촉 수분크림 200ml',
        productImageUrl: '',
        platform: 'NAVER_SMARTSTORE',
        clicks: 890,
        conversions: 38,
        revenue: 228000,
        linkedAt: '2025-12-10T00:00:00Z',
      },
      {
        id: 3,
        videoId: 103,
        videoTitle: '뷰티 틱톡 바이럴 세럼 리뷰',
        videoThumbnail: '',
        productId: 6,
        productName: '비타민C 세럼 30ml',
        productImageUrl: '',
        platform: 'TIKTOK_SHOP',
        clicks: 1200,
        conversions: 52,
        revenue: 332800,
        linkedAt: '2025-12-08T00:00:00Z',
      },
    ]
  }

  function generateMockRevenueTrends(): CommerceRevenueTrend[] {
    const trends: CommerceRevenueTrend[] = []
    const today = new Date()
    for (let i = 29; i >= 0; i--) {
      const date = new Date(today)
      date.setDate(date.getDate() - i)
      const dateStr = date.toISOString().split('T')[0]
      trends.push({
        date: dateStr,
        revenue: Math.floor(50000 + Math.random() * 150000),
        clicks: Math.floor(300 + Math.random() * 700),
        conversions: Math.floor(5 + Math.random() * 25),
      })
    }
    return trends
  }

  function generateMockPlatformPerformance(): PlatformPerformance[] {
    return [
      {
        platform: 'COUPANG',
        revenue: 1_250_000,
        clicks: 6_800,
        conversions: 210,
        conversionRate: 3.1,
        productCount: 22,
      },
      {
        platform: 'NAVER_SMARTSTORE',
        revenue: 980_000,
        clicks: 5_200,
        conversions: 175,
        conversionRate: 3.4,
        productCount: 18,
      },
      {
        platform: 'TIKTOK_SHOP',
        revenue: 617_300,
        clicks: 3_420,
        conversions: 98,
        conversionRate: 2.9,
        productCount: 8,
      },
    ]
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
