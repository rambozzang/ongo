import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { creatorMarketplaceApi } from '@/api/creatorMarketplace'
import type { MarketplaceListing, MarketplaceOrder, MarketplaceSummary } from '@/types/creatorMarketplace'

export const useCreatorMarketplaceStore = defineStore('creatorMarketplace', () => {
  const listings = ref<MarketplaceListing[]>([])
  const orders = ref<MarketplaceOrder[]>([])
  const summary = ref<MarketplaceSummary | null>(null)
  const loading = ref(false)

  const activeListings = computed(() => listings.value.filter(l => l.isActive))
  const pendingOrders = computed(() => orders.value.filter(o => o.status === 'PENDING' || o.status === 'IN_PROGRESS'))

  async function fetchListings(serviceType?: string) {
    loading.value = true
    try {
      listings.value = await creatorMarketplaceApi.getListings(serviceType)
    } catch {
      listings.value = [
        { id: 1, creatorId: 1, creatorName: '에디터 김', serviceType: 'EDITING', title: '유튜브 영상 편집', description: '프리미어 프로 기반 전문 편집', price: 150000, currency: 'KRW', rating: 4.8, reviewCount: 52, deliveryDays: 3, isActive: true, createdAt: '2025-02-01T10:00:00Z' },
        { id: 2, creatorId: 2, creatorName: '디자이너 박', serviceType: 'THUMBNAIL', title: '썸네일 제작', description: '클릭률 높은 썸네일 디자인', price: 30000, currency: 'KRW', rating: 4.9, reviewCount: 128, deliveryDays: 1, isActive: true, createdAt: '2025-01-15T08:00:00Z' },
        { id: 3, creatorId: 3, creatorName: '작가 이', serviceType: 'SCRIPT', title: '대본 작성 서비스', description: '맞춤형 유튜브 대본 작성', price: 80000, currency: 'KRW', rating: 4.6, reviewCount: 34, deliveryDays: 2, isActive: true, createdAt: '2025-02-20T14:00:00Z' },
        { id: 4, creatorId: 4, creatorName: '성우 최', serviceType: 'VOICEOVER', title: '전문 나레이션', description: '다양한 톤의 나레이션 녹음', price: 100000, currency: 'KRW', rating: 4.7, reviewCount: 67, deliveryDays: 2, isActive: true, createdAt: '2025-03-01T09:00:00Z' },
        { id: 5, creatorId: 5, creatorName: '코치 정', serviceType: 'CONSULTING', title: '채널 성장 컨설팅', description: '1:1 맞춤 성장 전략 상담', price: 200000, currency: 'KRW', rating: 4.5, reviewCount: 18, deliveryDays: 5, isActive: true, createdAt: '2025-03-05T11:00:00Z' },
      ]
    } finally {
      loading.value = false
    }
  }

  async function fetchOrders() {
    try {
      orders.value = await creatorMarketplaceApi.getOrders()
    } catch {
      orders.value = [
        { id: 1, listingId: 1, buyerId: 10, buyerName: '크리에이터 A', sellerId: 1, sellerName: '에디터 김', status: 'IN_PROGRESS', totalPrice: 150000, orderDate: '2025-03-10T10:00:00Z', deliveryDate: null },
        { id: 2, listingId: 2, buyerId: 11, buyerName: '크리에이터 B', sellerId: 2, sellerName: '디자이너 박', status: 'COMPLETED', totalPrice: 30000, orderDate: '2025-03-08T14:00:00Z', deliveryDate: '2025-03-09T10:00:00Z' },
        { id: 3, listingId: 4, buyerId: 12, buyerName: '크리에이터 C', sellerId: 4, sellerName: '성우 최', status: 'PENDING', totalPrice: 100000, orderDate: '2025-03-12T16:00:00Z', deliveryDate: null },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await creatorMarketplaceApi.getSummary()
    } catch {
      summary.value = { totalListings: 86, activeOrders: 12, totalRevenue: 4500000, avgRating: 4.7, topServiceType: 'THUMBNAIL' }
    }
  }

  async function placeOrder(listingId: number) {
    try {
      const order = await creatorMarketplaceApi.placeOrder(listingId)
      orders.value.unshift(order)
    } catch {
      // fallback
    }
  }

  return { listings, orders, summary, loading, activeListings, pendingOrders, fetchListings, fetchOrders, fetchSummary, placeOrder }
})
