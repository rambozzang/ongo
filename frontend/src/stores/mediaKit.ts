import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { MediaKit, MediaKitTemplate } from '@/types/mediaKit'
import { mediaKitApi } from '@/api/mediaKit'

function generateMockKit(): MediaKit {
  return {
    id: 1,
    title: '나의 미디어킷',
    templateStyle: 'MODERN',
    bio: '크리에이티브한 콘텐츠로 10만 구독자와 함께하는 크리에이터입니다.',
    platforms: [
      { platform: 'youtube', followers: 105000, avgViews: 28000, engagementRate: 6.2, growthRate: 3.5 },
      { platform: 'instagram', followers: 48000, avgViews: 12000, engagementRate: 4.8, growthRate: 2.1 },
      { platform: 'tiktok', followers: 72000, avgViews: 45000, engagementRate: 8.5, growthRate: 7.2 },
    ],
    demographics: [
      { ageGroup: '18-24', percentage: 35, gender: 'FEMALE' },
      { ageGroup: '25-34', percentage: 30, gender: 'FEMALE' },
      { ageGroup: '18-24', percentage: 20, gender: 'MALE' },
      { ageGroup: '25-34', percentage: 15, gender: 'MALE' },
    ],
    topContent: [
      { title: '일주일 브이로그 모음', views: 125000 },
      { title: '인생 맛집 TOP 10', views: 98000 },
      { title: '봄 데일리 메이크업', views: 87000 },
    ],
    campaignResults: [
      { id: 1, brandName: '뷰티브랜드A', campaignName: '신제품 런칭', platform: 'youtube', views: 65000, engagement: 4200, roi: 3.2, date: '2025-01-15' },
      { id: 2, brandName: '패션몰B', campaignName: '봄 컬렉션', platform: 'instagram', views: 32000, engagement: 2800, roi: 2.8, date: '2025-02-20' },
    ],
    rateCards: [
      { type: 'SPONSORED_VIDEO', price: 2000000, currency: 'KRW', description: '브랜디드 영상 (10-15분)' },
      { type: 'SHORT_FORM', price: 800000, currency: 'KRW', description: '숏폼 콘텐츠 (60초 이내)' },
      { type: 'PRODUCT_PLACEMENT', price: 1200000, currency: 'KRW', description: '제품 PPL (영상 내 자연스러운 노출)' },
      { type: 'STORY_POST', price: 500000, currency: 'KRW', description: '인스타그램 스토리 (3컷)' },
    ],
    contactEmail: 'creator@example.com',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    status: 'DRAFT',
  }
}

const mockTemplates: MediaKitTemplate[] = [
  { id: 1, name: '모던', style: 'MODERN' },
  { id: 2, name: '클래식', style: 'CLASSIC' },
  { id: 3, name: '미니멀', style: 'MINIMAL' },
  { id: 4, name: '크리에이티브', style: 'CREATIVE' },
]

export const useMediaKitStore = defineStore('mediaKit', () => {
  const kits = ref<MediaKit[]>([])
  const currentKit = ref<MediaKit | null>(null)
  const templates = ref<MediaKitTemplate[]>([])
  const loading = ref(false)
  const generating = ref(false)

  const publishedKits = computed(() => kits.value.filter((k) => k.status === 'PUBLISHED'))
  const draftKits = computed(() => kits.value.filter((k) => k.status === 'DRAFT'))

  async function fetchTemplates() {
    try {
      templates.value = await mediaKitApi.getTemplates()
    } catch {
      templates.value = mockTemplates
    }
  }

  async function fetchKits() {
    loading.value = true
    try {
      kits.value = await mediaKitApi.getMyKits()
    } catch {
      kits.value = [generateMockKit()]
    } finally {
      loading.value = false
    }
  }

  async function generateKit(style: MediaKit['templateStyle']) {
    generating.value = true
    try {
      const kit = await mediaKitApi.generate({ templateStyle: style, includeRateCards: true, includeCampaigns: true })
      kits.value.unshift(kit)
      currentKit.value = kit
    } catch {
      const mock = generateMockKit()
      mock.templateStyle = style
      mock.id = Date.now()
      kits.value.unshift(mock)
      currentKit.value = mock
    } finally {
      generating.value = false
    }
  }

  async function publishKit(id: number) {
    try {
      const kit = await mediaKitApi.publishKit(id)
      const idx = kits.value.findIndex((k) => k.id === id)
      if (idx >= 0) kits.value[idx] = kit
    } catch {
      const kit = kits.value.find((k) => k.id === id)
      if (kit) {
        kit.status = 'PUBLISHED'
        kit.publishedUrl = `https://ongo.kr/media-kit/${id}`
      }
    }
  }

  async function deleteKit(id: number) {
    try { await mediaKitApi.deleteKit(id) } catch { /* 로컬 */ }
    kits.value = kits.value.filter((k) => k.id !== id)
    if (currentKit.value?.id === id) currentKit.value = null
  }

  return { kits, currentKit, templates, loading, generating, publishedKits, draftKits, fetchTemplates, fetchKits, generateKit, publishKit, deleteKit }
})
