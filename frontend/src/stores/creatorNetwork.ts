import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Creator, CollabRequest, NetworkSummary, CreatorCategory } from '@/types/creatorNetwork'
import { creatorNetworkApi } from '@/api/creatorNetwork'

function generateMockSummary(): NetworkSummary {
  return {
    totalConnections: 24,
    pendingRequests: 5,
    sentRequests: 3,
    avgMatchScore: 72,
    collabCompleted: 8,
  }
}

function generateMockCreators(): Creator[] {
  return [
    {
      id: 1,
      name: '뷰티크리에이터 민지',
      platform: 'youtube',
      subscriberCount: 520000,
      category: 'BEAUTY',
      matchScore: 92,
      connectionStatus: 'CONNECTED',
      bio: '뷰티 & 라이프스타일 크리에이터',
      avgViews: 85000,
      engagementRate: 6.8,
    },
    {
      id: 2,
      name: '게임왕 준호',
      platform: 'youtube',
      subscriberCount: 380000,
      category: 'GAMING',
      matchScore: 78,
      connectionStatus: 'PENDING',
      bio: '인디 게임 전문 스트리머',
      avgViews: 62000,
      engagementRate: 8.2,
    },
    {
      id: 3,
      name: '맛집탐험가 소영',
      platform: 'instagram',
      subscriberCount: 210000,
      category: 'FOOD',
      matchScore: 85,
      connectionStatus: 'CONNECTED',
      bio: '전국 맛집 리뷰 전문',
      avgViews: 45000,
      engagementRate: 5.4,
    },
    {
      id: 4,
      name: '테크리뷰 현우',
      platform: 'youtube',
      subscriberCount: 890000,
      category: 'TECH',
      matchScore: 64,
      connectionStatus: 'NONE',
      bio: 'IT 기기 리뷰 & 언박싱',
      avgViews: 120000,
      engagementRate: 4.1,
    },
    {
      id: 5,
      name: '여행하는 지은',
      platform: 'tiktok',
      subscriberCount: 150000,
      category: 'TRAVEL',
      matchScore: 71,
      connectionStatus: 'NONE',
      bio: '감성 여행 브이로그',
      avgViews: 38000,
      engagementRate: 9.3,
    },
    {
      id: 6,
      name: '피트니스 코치 태현',
      platform: 'youtube',
      subscriberCount: 95000,
      category: 'FITNESS',
      matchScore: 55,
      connectionStatus: 'CONNECTED',
      bio: '홈트레이닝 & 식단 관리',
      avgViews: 22000,
      engagementRate: 7.1,
    },
    {
      id: 7,
      name: '교육크리에이터 수빈',
      platform: 'youtube',
      subscriberCount: 420000,
      category: 'EDUCATION',
      matchScore: 88,
      connectionStatus: 'NONE',
      bio: '쉽게 배우는 코딩 & 수학',
      avgViews: 75000,
      engagementRate: 5.9,
    },
    {
      id: 8,
      name: '음악하는 도윤',
      platform: 'tiktok',
      subscriberCount: 670000,
      category: 'MUSIC',
      matchScore: 45,
      connectionStatus: 'PENDING',
      bio: '커버송 & 오리지널 음악',
      avgViews: 95000,
      engagementRate: 10.2,
    },
  ]
}

function generateMockCollabRequests(): CollabRequest[] {
  return [
    {
      id: 1,
      senderId: 2,
      senderName: '게임왕 준호',
      receiverId: 0,
      receiverName: '나',
      message: '게임 리뷰 콜라보 영상 함께 촬영하실래요?',
      status: 'PENDING',
      collabType: 'COLLAB_VIDEO',
      createdAt: new Date(Date.now() - 86400000).toISOString(),
    },
    {
      id: 2,
      senderId: 0,
      senderName: '나',
      receiverId: 3,
      receiverName: '맛집탐험가 소영',
      message: '맛집 투어 콜라보 제안드립니다!',
      status: 'ACCEPTED',
      collabType: 'CROSS_PROMOTION',
      createdAt: new Date(Date.now() - 3 * 86400000).toISOString(),
    },
    {
      id: 3,
      senderId: 8,
      senderName: '음악하는 도윤',
      receiverId: 0,
      receiverName: '나',
      message: '라이브 합동 방송 어떠세요?',
      status: 'PENDING',
      collabType: 'LIVE_TOGETHER',
      createdAt: new Date(Date.now() - 2 * 86400000).toISOString(),
    },
    {
      id: 4,
      senderId: 0,
      senderName: '나',
      receiverId: 7,
      receiverName: '교육크리에이터 수빈',
      message: '교육 콘텐츠 크로스 프로모션 제안합니다.',
      status: 'DECLINED',
      collabType: 'CROSS_PROMOTION',
      createdAt: new Date(Date.now() - 7 * 86400000).toISOString(),
    },
  ]
}

export const useCreatorNetworkStore = defineStore('creatorNetwork', () => {
  const creators = ref<Creator[]>([])
  const collabRequests = ref<CollabRequest[]>([])
  const summary = ref<NetworkSummary | null>(null)
  const loading = ref(false)

  const connectedCreators = computed(() => creators.value.filter((c) => c.connectionStatus === 'CONNECTED'))
  const pendingCreators = computed(() => creators.value.filter((c) => c.connectionStatus === 'PENDING'))

  async function fetchSummary() {
    try {
      summary.value = await creatorNetworkApi.getSummary()
    } catch {
      summary.value = generateMockSummary()
    }
  }

  async function fetchCreators(filter?: { category?: CreatorCategory; platform?: string }) {
    loading.value = true
    try {
      creators.value = await creatorNetworkApi.getCreators(filter)
    } catch {
      creators.value = generateMockCreators()
    } finally {
      loading.value = false
    }
  }

  async function fetchCollabRequests() {
    try {
      collabRequests.value = await creatorNetworkApi.getCollabRequests()
    } catch {
      collabRequests.value = generateMockCollabRequests()
    }
  }

  async function connectCreator(creatorId: number) {
    try {
      await creatorNetworkApi.connect(creatorId)
    } catch { /* local */ }
    const creator = creators.value.find((c) => c.id === creatorId)
    if (creator) creator.connectionStatus = 'PENDING'
  }

  async function disconnectCreator(creatorId: number) {
    try {
      await creatorNetworkApi.disconnect(creatorId)
    } catch { /* local */ }
    const creator = creators.value.find((c) => c.id === creatorId)
    if (creator) creator.connectionStatus = 'NONE'
  }

  async function sendCollabRequest(receiverId: number, message: string, collabType: string) {
    try {
      const req = await creatorNetworkApi.sendCollabRequest({ receiverId, message, collabType })
      collabRequests.value.unshift(req)
    } catch {
      const receiver = creators.value.find((c) => c.id === receiverId)
      collabRequests.value.unshift({
        id: Date.now(),
        senderId: 0,
        senderName: '나',
        receiverId,
        receiverName: receiver?.name ?? '크리에이터',
        message,
        status: 'PENDING',
        collabType: collabType as CollabRequest['collabType'],
        createdAt: new Date().toISOString(),
      })
    }
  }

  async function respondCollabRequest(id: number, accept: boolean) {
    try {
      const updated = await creatorNetworkApi.respondCollabRequest(id, accept)
      const idx = collabRequests.value.findIndex((r) => r.id === id)
      if (idx >= 0) collabRequests.value[idx] = updated
    } catch {
      const req = collabRequests.value.find((r) => r.id === id)
      if (req) req.status = accept ? 'ACCEPTED' : 'DECLINED'
    }
  }

  return {
    creators,
    collabRequests,
    summary,
    loading,
    connectedCreators,
    pendingCreators,
    fetchSummary,
    fetchCreators,
    fetchCollabRequests,
    connectCreator,
    disconnectCreator,
    sendCollabRequest,
    respondCollabRequest,
  }
})
