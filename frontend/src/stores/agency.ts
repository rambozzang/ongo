import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  AgencyCreator,
  AgencyKpi,
  CreatorComparison,
  AgencyScheduleItem,
  AgencyActivity,
  ClientPortalLink,
  ClientPortalData,
} from '@/types/agency'

// Mock data
const mockKpi: AgencyKpi = {
  totalCreators: 12,
  totalSubscribers: 4_850_000,
  totalViews: 128_500_000,
  totalRevenue: 45_200_000,
  subscribersChange: 125_000,
  viewsChangePercent: 12.5,
  revenueChangePercent: 8.3,
  creatorsChange: 2,
}

const mockCreators: AgencyCreator[] = [
  {
    id: 1,
    name: '김크리에이터',
    profileImageUrl: null,
    channels: [
      { id: 1, platform: 'YOUTUBE', channelName: '김크리에이터 TV', subscriberCount: 1_250_000, videoCount: 342 },
      { id: 2, platform: 'TIKTOK', channelName: '@kimcreator', subscriberCount: 850_000, videoCount: 520 },
    ],
    totalSubscribers: 2_100_000,
    totalViews: 45_000_000,
    totalRevenue: 15_800_000,
    recentGrowthPercent: 8.5,
    status: 'active',
    joinedAt: '2024-03-15',
  },
  {
    id: 2,
    name: '이콘텐츠',
    profileImageUrl: null,
    channels: [
      { id: 3, platform: 'YOUTUBE', channelName: '이콘텐츠', subscriberCount: 680_000, videoCount: 198 },
      { id: 4, platform: 'INSTAGRAM', channelName: '@leecontent', subscriberCount: 420_000, videoCount: 310 },
    ],
    totalSubscribers: 1_100_000,
    totalViews: 32_000_000,
    totalRevenue: 12_400_000,
    recentGrowthPercent: 15.2,
    status: 'active',
    joinedAt: '2024-05-20',
  },
  {
    id: 3,
    name: '박스튜디오',
    profileImageUrl: null,
    channels: [
      { id: 5, platform: 'YOUTUBE', channelName: '박스튜디오', subscriberCount: 950_000, videoCount: 275 },
      { id: 6, platform: 'NAVER_CLIP', channelName: '박스튜디오 클립', subscriberCount: 120_000, videoCount: 85 },
    ],
    totalSubscribers: 1_070_000,
    totalViews: 28_500_000,
    totalRevenue: 9_800_000,
    recentGrowthPercent: -2.1,
    status: 'active',
    joinedAt: '2024-01-10',
  },
  {
    id: 4,
    name: '최뷰티',
    profileImageUrl: null,
    channels: [
      { id: 7, platform: 'INSTAGRAM', channelName: '@choibeauty', subscriberCount: 380_000, videoCount: 450 },
      { id: 8, platform: 'TIKTOK', channelName: '@choibeauty', subscriberCount: 200_000, videoCount: 680 },
    ],
    totalSubscribers: 580_000,
    totalViews: 23_000_000,
    totalRevenue: 7_200_000,
    recentGrowthPercent: 22.8,
    status: 'active',
    joinedAt: '2024-07-01',
  },
]

const mockComparisons: CreatorComparison[] = [
  {
    creatorId: 1,
    creatorName: '김크리에이터',
    color: '#6366F1',
    data: Array.from({ length: 7 }, (_, i) => ({
      date: `2025-01-${String(i + 20).padStart(2, '0')}`,
      views: 150000 + Math.floor(Math.random() * 50000),
      engagement: 4.2 + Math.random() * 2,
    })),
  },
  {
    creatorId: 2,
    creatorName: '이콘텐츠',
    color: '#EC4899',
    data: Array.from({ length: 7 }, (_, i) => ({
      date: `2025-01-${String(i + 20).padStart(2, '0')}`,
      views: 120000 + Math.floor(Math.random() * 40000),
      engagement: 3.8 + Math.random() * 2,
    })),
  },
  {
    creatorId: 3,
    creatorName: '박스튜디오',
    color: '#10B981',
    data: Array.from({ length: 7 }, (_, i) => ({
      date: `2025-01-${String(i + 20).padStart(2, '0')}`,
      views: 100000 + Math.floor(Math.random() * 30000),
      engagement: 3.5 + Math.random() * 1.5,
    })),
  },
]

const mockSchedules: AgencyScheduleItem[] = [
  { id: 1, creatorId: 1, creatorName: '김크리에이터', videoTitle: '신년 스페셜 브이로그', platforms: ['YOUTUBE', 'TIKTOK'], scheduledAt: '2025-02-01T14:00:00', status: 'scheduled' },
  { id: 2, creatorId: 2, creatorName: '이콘텐츠', videoTitle: '겨울 패션 하울', platforms: ['INSTAGRAM', 'YOUTUBE'], scheduledAt: '2025-02-01T18:00:00', status: 'scheduled' },
  { id: 3, creatorId: 3, creatorName: '박스튜디오', videoTitle: '설날 요리 레시피', platforms: ['YOUTUBE', 'NAVER_CLIP'], scheduledAt: '2025-02-02T10:00:00', status: 'scheduled' },
  { id: 4, creatorId: 4, creatorName: '최뷰티', videoTitle: '봄 메이크업 튜토리얼', platforms: ['INSTAGRAM', 'TIKTOK'], scheduledAt: '2025-02-02T15:00:00', status: 'scheduled' },
  { id: 5, creatorId: 1, creatorName: '김크리에이터', videoTitle: '주간 Q&A 라이브', platforms: ['YOUTUBE'], scheduledAt: '2025-02-03T20:00:00', status: 'scheduled' },
]

const mockActivities: AgencyActivity[] = [
  { id: 1, creatorName: '김크리에이터', action: 'uploaded', target: '신년 스페셜 브이로그', createdAt: '2025-01-26T14:30:00', type: 'upload' },
  { id: 2, creatorName: '이콘텐츠', action: 'scheduled', target: '겨울 패션 하울', createdAt: '2025-01-26T13:00:00', type: 'schedule' },
  { id: 3, creatorName: '박스튜디오', action: 'reached', target: '구독자 95만명 달성', createdAt: '2025-01-26T10:15:00', type: 'analytics' },
  { id: 4, creatorName: '최뷰티', action: 'connected', target: 'TikTok 채널 연동', createdAt: '2025-01-25T22:00:00', type: 'channel' },
  { id: 5, creatorName: '김크리에이터', action: 'published', target: '게임 리뷰 #45', createdAt: '2025-01-25T18:30:00', type: 'upload' },
  { id: 6, creatorName: '이콘텐츠', action: 'analyzed', target: '주간 성과 리포트', createdAt: '2025-01-25T09:00:00', type: 'analytics' },
]

const mockClientLinks: ClientPortalLink[] = [
  { id: 1, creatorId: 1, creatorName: '김크리에이터', token: 'abc123def456', expiresAt: '2025-03-01T00:00:00', isActive: true, createdAt: '2025-01-20T10:00:00' },
  { id: 2, creatorId: 2, creatorName: '이콘텐츠', token: 'xyz789ghi012', expiresAt: '2025-02-15T00:00:00', isActive: true, createdAt: '2025-01-15T14:00:00' },
]

const mockPortalData: ClientPortalData = {
  creator: {
    name: '김크리에이터',
    profileImageUrl: null,
    bio: '테크/게임 크리에이터 | 210만 구독자 | 브랜드 협업 문의 환영',
  },
  channels: [
    { id: 1, platform: 'YOUTUBE', channelName: '김크리에이터 TV', subscriberCount: 1_250_000, videoCount: 342 },
    { id: 2, platform: 'TIKTOK', channelName: '@kimcreator', subscriberCount: 850_000, videoCount: 520 },
  ],
  kpi: {
    totalViews: 45_000_000,
    totalSubscribers: 2_100_000,
    engagementRate: 5.8,
    estimatedROI: 320,
    viewsChangePercent: 12.5,
    subscribersChange: 45_000,
    engagementChangePercent: 1.2,
    roiChangePercent: 15.0,
  },
  campaigns: [
    {
      id: 1,
      name: '2025 신년 캠페인',
      status: 'active',
      startDate: '2025-01-15',
      endDate: '2025-02-15',
      totalViews: 2_500_000,
      totalEngagement: 185_000,
      deliverables: 5,
      completedDeliverables: 3,
    },
    {
      id: 2,
      name: 'CES 2025 리뷰 시리즈',
      status: 'completed',
      startDate: '2025-01-06',
      endDate: '2025-01-20',
      totalViews: 4_200_000,
      totalEngagement: 312_000,
      deliverables: 4,
      completedDeliverables: 4,
    },
    {
      id: 3,
      name: '봄 신제품 런칭',
      status: 'upcoming',
      startDate: '2025-02-20',
      endDate: '2025-03-20',
      totalViews: 0,
      totalEngagement: 0,
      deliverables: 6,
      completedDeliverables: 0,
    },
  ],
  upcomingContent: [
    { id: 1, title: '신년 스페셜 브이로그', platform: 'YOUTUBE', scheduledAt: '2025-02-01T14:00:00', status: 'scheduled' },
    { id: 2, title: '게임 리뷰 #46', platform: 'YOUTUBE', scheduledAt: '2025-02-03T18:00:00', status: 'scheduled' },
    { id: 3, title: '일상 쇼츠', platform: 'TIKTOK', scheduledAt: '2025-02-04T12:00:00', status: 'scheduled' },
  ],
  recentPerformance: Array.from({ length: 14 }, (_, i) => ({
    date: `2025-01-${String(i + 13).padStart(2, '0')}`,
    views: 200000 + Math.floor(Math.random() * 100000),
    engagement: 4.5 + Math.random() * 3,
  })),
}

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
      // Mock data loading
      await new Promise((r) => setTimeout(r, 500))
      kpi.value = mockKpi
      creators.value = mockCreators
      comparisons.value = mockComparisons
      schedules.value = mockSchedules
      activities.value = mockActivities
      clientLinks.value = mockClientLinks
    } finally {
      loading.value = false
    }
  }

  async function fetchComparisons(creatorIds: number[], _period: string) {
    await new Promise((r) => setTimeout(r, 300))
    comparisons.value = mockComparisons.filter((c) => creatorIds.includes(c.creatorId))
  }

  async function createClientLink(creatorId: number) {
    await new Promise((r) => setTimeout(r, 300))
    const creator = creators.value.find((c) => c.id === creatorId)
    const newLink: ClientPortalLink = {
      id: clientLinks.value.length + 1,
      creatorId,
      creatorName: creator?.name ?? '알 수 없음',
      token: Math.random().toString(36).substring(2, 14),
      expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
      isActive: true,
      createdAt: new Date().toISOString(),
    }
    clientLinks.value.push(newLink)
    return newLink
  }

  async function revokeClientLink(linkId: number) {
    await new Promise((r) => setTimeout(r, 200))
    clientLinks.value = clientLinks.value.filter((l) => l.id !== linkId)
  }

  async function fetchPortalData(_token: string) {
    portalLoading.value = true
    try {
      await new Promise((r) => setTimeout(r, 500))
      portalData.value = mockPortalData
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
