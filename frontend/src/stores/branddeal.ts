import { defineStore } from 'pinia'
import { ref } from 'vue'
import { brandDealApi } from '@/api/branddeal'
import type { BrandDeal, MediaKit } from '@/types/branddeal'

export const useBrandDealStore = defineStore('branddeal', () => {
  const deals = ref<BrandDeal[]>([])
  const mediaKit = ref<MediaKit | null>(null)
  const loading = ref(false)

  async function loadDeals(status?: string) {
    loading.value = true
    try {
      deals.value = await brandDealApi.getDeals(status)
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
    try {
      mediaKit.value = await brandDealApi.getMediaKit()
    } finally {
      loading.value = false
    }
  }

  async function saveMediaKit(data: { displayName?: string; bio?: string; categories?: string[]; socialLinks?: Record<string, string>; isPublic?: boolean; slug?: string }) {
    mediaKit.value = await brandDealApi.saveMediaKit(data)
    return mediaKit.value
  }

  return { deals, mediaKit, loading, loadDeals, createDeal, deleteDeal, loadMediaKit, saveMediaKit }
})
