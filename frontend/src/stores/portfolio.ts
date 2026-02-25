import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  Portfolio,
  PortfolioProfile,
  ShowcaseContent,
  BrandCollaboration,
  PortfolioTheme,
} from '@/types/portfolio'

const STORAGE_KEY = 'ongo_portfolio'

const mockPortfolio: Portfolio = {
  id: 1,
  profile: {
    displayName: '크리에이터 닉네임',
    bio: '안녕하세요! 테크와 라이프스타일을 다루는 크리에이터입니다. 새로운 기술과 트렌드를 쉽고 재미있게 전달합니다.',
    profileImageUrl: '',
    categories: ['테크', '라이프스타일', '리뷰'],
    contactEmail: 'creator@example.com',
    website: 'https://example.com',
  },
  channelStats: [
    {
      id: 1,
      platform: 'youtube',
      channelName: 'Creator Channel',
      subscribers: 125000,
      totalViews: 18500000,
      avgViews: 45000,
      engagementRate: 6.2,
      profileImageUrl: '',
    },
    {
      id: 2,
      platform: 'tiktok',
      channelName: '@creator_tt',
      subscribers: 89000,
      totalViews: 42000000,
      avgViews: 120000,
      engagementRate: 8.5,
      profileImageUrl: '',
    },
    {
      id: 3,
      platform: 'instagram',
      channelName: '@creator_ig',
      subscribers: 67000,
      totalViews: 5200000,
      avgViews: 18000,
      engagementRate: 4.8,
      profileImageUrl: '',
    },
    {
      id: 4,
      platform: 'naver',
      channelName: '크리에이터 네이버',
      subscribers: 23000,
      totalViews: 3100000,
      avgViews: 8500,
      engagementRate: 3.2,
      profileImageUrl: '',
    },
  ],
  showcaseContents: [
    {
      id: 1,
      title: '2025 최고의 가성비 노트북 TOP 5',
      thumbnailUrl: '',
      platform: 'youtube',
      viewCount: 285000,
      likeCount: 12400,
      publishedAt: '2025-03-15',
      videoUrl: '',
      order: 0,
    },
    {
      id: 2,
      title: '하루 루틴 브이로그 | 크리에이터의 일상',
      thumbnailUrl: '',
      platform: 'youtube',
      viewCount: 142000,
      likeCount: 8900,
      publishedAt: '2025-03-10',
      videoUrl: '',
      order: 1,
    },
    {
      id: 3,
      title: '이 앱 진짜 미쳤다 🔥',
      thumbnailUrl: '',
      platform: 'tiktok',
      viewCount: 520000,
      likeCount: 34000,
      publishedAt: '2025-03-08',
      videoUrl: '',
      order: 2,
    },
    {
      id: 4,
      title: '봄맞이 데스크 셋업 변경!',
      thumbnailUrl: '',
      platform: 'instagram',
      viewCount: 67000,
      likeCount: 4500,
      publishedAt: '2025-02-28',
      videoUrl: '',
      order: 3,
    },
  ],
  brandCollaborations: [
    {
      id: 1,
      brandName: '삼성전자',
      brandLogoUrl: '',
      campaignTitle: 'Galaxy S25 언박싱 캠페인',
      platform: 'youtube',
      deliverables: '유튜브 영상 1개 + 숏츠 2개',
      completedAt: '2025-02-20',
      resultSummary: '조회수 320K, 참여율 7.1%',
    },
    {
      id: 2,
      brandName: '쿠팡',
      brandLogoUrl: '',
      campaignTitle: '쿠팡 로켓배송 브이로그',
      platform: 'youtube',
      deliverables: '유튜브 영상 1개',
      completedAt: '2025-01-15',
      resultSummary: '조회수 180K, 참여율 5.3%',
    },
    {
      id: 3,
      brandName: 'Adobe',
      brandLogoUrl: '',
      campaignTitle: 'Premiere Pro 워크플로우',
      platform: 'tiktok',
      deliverables: '틱톡 영상 3개',
      completedAt: '2024-12-10',
      resultSummary: '총 조회수 890K, 참여율 9.2%',
    },
  ],
  theme: 'minimal',
  slug: 'creator-nick',
  isPublic: true,
  publicUrl: 'https://ongo.kr/p/creator-nick',
  createdAt: '2024-10-01T00:00:00Z',
  updatedAt: '2025-03-20T00:00:00Z',
}

export const usePortfolioStore = defineStore('portfolio', () => {
  const portfolio = ref<Portfolio | null>(null)
  const loading = ref(false)
  const isDirty = ref(false)

  const totalSubscribers = computed(() =>
    portfolio.value?.channelStats.reduce((sum, ch) => sum + ch.subscribers, 0) ?? 0,
  )

  const totalViews = computed(() =>
    portfolio.value?.channelStats.reduce((sum, ch) => sum + ch.totalViews, 0) ?? 0,
  )

  const avgEngagement = computed(() => {
    const stats = portfolio.value?.channelStats
    if (!stats || stats.length === 0) return 0
    return Number((stats.reduce((sum, ch) => sum + ch.engagementRate, 0) / stats.length).toFixed(1))
  })

  function loadFromStorage() {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      try {
        portfolio.value = JSON.parse(saved)
        return true
      } catch {
        // ignore
      }
    }
    return false
  }

  function saveToStorage() {
    if (portfolio.value) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(portfolio.value))
    }
  }

  async function fetchPortfolio() {
    loading.value = true
    try {
      // Mock 데이터 사용
      if (!loadFromStorage()) {
        portfolio.value = { ...mockPortfolio }
        saveToStorage()
      }
    } finally {
      loading.value = false
    }
  }

  function updateProfile(updates: Partial<PortfolioProfile>) {
    if (!portfolio.value) return
    portfolio.value.profile = { ...portfolio.value.profile, ...updates }
    isDirty.value = true
  }

  function updateTheme(theme: PortfolioTheme) {
    if (!portfolio.value) return
    portfolio.value.theme = theme
    isDirty.value = true
  }

  function updateSlug(slug: string) {
    if (!portfolio.value) return
    portfolio.value.slug = slug
    portfolio.value.publicUrl = `https://ongo.kr/p/${slug}`
    isDirty.value = true
  }

  function togglePublic() {
    if (!portfolio.value) return
    portfolio.value.isPublic = !portfolio.value.isPublic
    isDirty.value = true
  }

  function reorderShowcase(fromIndex: number, toIndex: number) {
    if (!portfolio.value) return
    const items = [...portfolio.value.showcaseContents]
    const [moved] = items.splice(fromIndex, 1)
    items.splice(toIndex, 0, moved)
    items.forEach((item, i) => (item.order = i))
    portfolio.value.showcaseContents = items
    isDirty.value = true
  }

  function addShowcaseContent(content: Omit<ShowcaseContent, 'id' | 'order'>) {
    if (!portfolio.value) return
    const newId = Math.max(0, ...portfolio.value.showcaseContents.map(c => c.id)) + 1
    const order = portfolio.value.showcaseContents.length
    portfolio.value.showcaseContents.push({ ...content, id: newId, order })
    isDirty.value = true
  }

  function removeShowcaseContent(contentId: number) {
    if (!portfolio.value) return
    portfolio.value.showcaseContents = portfolio.value.showcaseContents
      .filter(c => c.id !== contentId)
      .map((c, i) => ({ ...c, order: i }))
    isDirty.value = true
  }

  function addBrandCollaboration(collab: Omit<BrandCollaboration, 'id'>) {
    if (!portfolio.value) return
    const newId = Math.max(0, ...portfolio.value.brandCollaborations.map(c => c.id)) + 1
    portfolio.value.brandCollaborations.push({ ...collab, id: newId })
    isDirty.value = true
  }

  function removeBrandCollaboration(collabId: number) {
    if (!portfolio.value) return
    portfolio.value.brandCollaborations = portfolio.value.brandCollaborations.filter(c => c.id !== collabId)
    isDirty.value = true
  }

  function savePortfolio() {
    if (!portfolio.value) return
    portfolio.value.updatedAt = new Date().toISOString()
    saveToStorage()
    isDirty.value = false
  }

  return {
    portfolio,
    loading,
    isDirty,
    totalSubscribers,
    totalViews,
    avgEngagement,
    fetchPortfolio,
    updateProfile,
    updateTheme,
    updateSlug,
    togglePublic,
    reorderShowcase,
    addShowcaseContent,
    removeShowcaseContent,
    addBrandCollaboration,
    removeBrandCollaboration,
    savePortfolio,
  }
})
