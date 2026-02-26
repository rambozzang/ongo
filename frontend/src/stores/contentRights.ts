import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { contentRightsApi } from '@/api/contentRights'
import type { ContentRight, RightsAlert, RightsSummary, CreateRightRequest } from '@/types/contentRights'

function generateMockRights(): ContentRight[] {
  return [
    { id: 1, videoId: 'v001', videoTitle: '봄 메이크업 튜토리얼', assetName: 'Spring Vibes - BGM', assetType: 'MUSIC', licenseType: 'ROYALTY_FREE', licenseStatus: 'ACTIVE', source: 'Artlist', cost: 16900, currency: 'KRW', createdAt: '2025-01-10', updatedAt: '2025-01-10', expiresAt: '2026-01-10' },
    { id: 2, videoId: 'v002', videoTitle: '게이밍 PC 리뷰', assetName: 'Epic Battle Theme', assetType: 'MUSIC', licenseType: 'SUBSCRIPTION', licenseStatus: 'EXPIRING_SOON', source: 'Epidemic Sound', cost: 0, currency: 'KRW', createdAt: '2025-02-01', updatedAt: '2025-02-01', expiresAt: '2025-03-15' },
    { id: 3, videoId: 'v003', videoTitle: '여행 브이로그', assetName: 'Mountain Sunset Photo', assetType: 'IMAGE', licenseType: 'CREATIVE_COMMONS', licenseStatus: 'ACTIVE', source: 'Unsplash', cost: 0, currency: 'KRW', createdAt: '2025-01-20', updatedAt: '2025-01-20' },
    { id: 4, videoId: 'v001', videoTitle: '봄 메이크업 튜토리얼', assetName: 'Pretendard Font', assetType: 'FONT', licenseType: 'FREE', licenseStatus: 'ACTIVE', source: 'Google Fonts', cost: 0, currency: 'KRW', createdAt: '2025-01-10', updatedAt: '2025-01-10' },
    { id: 5, videoId: 'v004', videoTitle: '요리 레시피', assetName: 'Kitchen Sounds Pack', assetType: 'SOUND_EFFECT', licenseType: 'PAID', licenseStatus: 'EXPIRED', source: 'Envato Elements', cost: 5000, currency: 'KRW', createdAt: '2024-06-01', updatedAt: '2024-06-01', expiresAt: '2025-01-01' },
  ]
}

function generateMockAlerts(): RightsAlert[] {
  return [
    { id: 1, contentRightId: 2, assetName: 'Epic Battle Theme', assetType: 'MUSIC', message: 'Epidemic Sound 구독이 14일 후 만료됩니다', severity: 'WARNING', daysUntilExpiry: 14, isRead: false, createdAt: '2025-02-26' },
    { id: 2, contentRightId: 5, assetName: 'Kitchen Sounds Pack', assetType: 'SOUND_EFFECT', message: '라이선스가 만료되었습니다. 대체 에셋을 찾아보세요.', severity: 'CRITICAL', daysUntilExpiry: -56, isRead: false, createdAt: '2025-02-26' },
  ]
}

export const useContentRightsStore = defineStore('contentRights', () => {
  const rights = ref<ContentRight[]>([])
  const alerts = ref<RightsAlert[]>([])
  const summary = ref<RightsSummary | null>(null)
  const isLoading = ref(false)

  const activeRights = computed(() => rights.value.filter((r) => r.licenseStatus === 'ACTIVE'))
  const expiringRights = computed(() => rights.value.filter((r) => r.licenseStatus === 'EXPIRING_SOON'))
  const expiredRights = computed(() => rights.value.filter((r) => r.licenseStatus === 'EXPIRED'))
  const unreadAlerts = computed(() => alerts.value.filter((a) => !a.isRead))

  async function fetchAll() {
    isLoading.value = true
    try {
      rights.value = await contentRightsApi.getAll()
    } catch {
      rights.value = generateMockRights()
    }
    try {
      alerts.value = await contentRightsApi.getAlerts()
    } catch {
      alerts.value = generateMockAlerts()
    }
    isLoading.value = false
  }

  async function createRight(request: CreateRightRequest) {
    try {
      const created = await contentRightsApi.create(request)
      rights.value.push(created)
    } catch {
      const mock: ContentRight = { id: Date.now(), ...request, licenseStatus: 'ACTIVE', createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), cost: request.cost ?? 0, currency: request.currency ?? 'KRW' }
      rights.value.push(mock)
    }
  }

  async function deleteRight(id: number) {
    try { await contentRightsApi.delete(id) } catch { /* fallback */ }
    rights.value = rights.value.filter((r) => r.id !== id)
  }

  async function markAlertRead(alertId: number) {
    try { await contentRightsApi.markAlertRead(alertId) } catch { /* fallback */ }
    const alert = alerts.value.find((a) => a.id === alertId)
    if (alert) alert.isRead = true
  }

  return { rights, alerts, summary, isLoading, activeRights, expiringRights, expiredRights, unreadAlerts, fetchAll, createRight, deleteRight, markAlertRead }
})
