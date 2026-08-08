import { defineStore } from 'pinia'
import { ref } from 'vue'
import { brandDealApi } from '@/api/branddeal'
import type { BrandDeal, MediaKit } from '@/types/branddeal'

export const useBrandDealStore = defineStore('branddeal', () => {
  const deals = ref<BrandDeal[]>([])
  const mediaKit = ref<MediaKit | null>(null)
  const loading = ref(false)
  const loadError = ref<string | null>(null)
  const mediaKitError = ref<string | null>(null)

  async function loadDeals(status?: string) {
    loading.value = true
    loadError.value = null
    try {
      deals.value = await brandDealApi.getDeals(status)
    } catch (error) {
      loadError.value = error instanceof Error ? error.message : '브랜드딜을 불러오지 못했습니다.'
    } finally {
      loading.value = false
    }
  }

  async function createDeal(data: { brandName: string; contactName?: string; contactEmail?: string; dealValue?: number; status?: string; deadline?: string; notes?: string }) {
    const deal = await brandDealApi.createDeal(data)
    deals.value.unshift(deal)
    return deal
  }

  async function deleteDeal(id: number) {
    await brandDealApi.deleteDeal(id)
    deals.value = deals.value.filter(d => d.id !== id)
  }

  async function loadMediaKit() {
    loading.value = true
    mediaKitError.value = null
    try {
      mediaKit.value = await brandDealApi.getMediaKit()
    } catch (error) {
      mediaKitError.value = error instanceof Error ? error.message : '미디어킷을 불러오지 못했습니다.'
    } finally {
      loading.value = false
    }
  }

  async function saveMediaKit(data: { displayName?: string; bio?: string; categories?: string[]; socialLinks?: Record<string, string>; isPublic?: boolean; slug?: string }) {
    mediaKit.value = await brandDealApi.saveMediaKit(data)
    return mediaKit.value
  }

  return { deals, mediaKit, loading, loadError, mediaKitError, loadDeals, createDeal, deleteDeal, loadMediaKit, saveMediaKit }
})
