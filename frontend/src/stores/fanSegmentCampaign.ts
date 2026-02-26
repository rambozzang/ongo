import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { fanSegmentCampaignApi } from '@/api/fanSegmentCampaign'
import type { SegmentCampaign, FanSegment, CampaignSummary } from '@/types/fanSegmentCampaign'

function generateMockSummary(): CampaignSummary {
  return {
    totalCampaigns: 24,
    activeCampaigns: 5,
    avgOpenRate: 42.8,
    totalReach: 156400,
  }
}

function generateMockCampaigns(): SegmentCampaign[] {
  return [
    {
      id: 1, name: '신규 구독자 환영 캠페인', segmentId: 1, segmentName: '신규 구독자',
      type: 'EMAIL', status: 'COMPLETED', targetCount: 3200, sentCount: 3180,
      openRate: 58.2, clickRate: 12.4, completedAt: '2025-02-15T10:00:00Z', createdAt: '2025-02-10T09:00:00Z',
    },
    {
      id: 2, name: '열성팬 전용 프리뷰', segmentId: 2, segmentName: '열성팬 (상위 10%)',
      type: 'PUSH', status: 'SENDING', targetCount: 1500, sentCount: 820,
      openRate: 72.1, clickRate: 28.5, createdAt: '2025-02-18T14:00:00Z',
    },
    {
      id: 3, name: '비활성 팬 재참여', segmentId: 3, segmentName: '30일 미접속',
      type: 'EMAIL', status: 'SCHEDULED', targetCount: 8500, sentCount: 0,
      openRate: 0, clickRate: 0, scheduledAt: '2025-03-01T09:00:00Z', createdAt: '2025-02-20T11:00:00Z',
    },
    {
      id: 4, name: '멤버십 업그레이드 안내', segmentId: 4, segmentName: '무료 멤버',
      type: 'IN_APP', status: 'COMPLETED', targetCount: 12000, sentCount: 11850,
      openRate: 35.6, clickRate: 8.2, completedAt: '2025-02-12T18:00:00Z', createdAt: '2025-02-08T16:00:00Z',
    },
    {
      id: 5, name: '라이브 방송 알림', segmentId: 5, segmentName: '라이브 시청자',
      type: 'PUSH', status: 'DRAFT', targetCount: 5600, sentCount: 0,
      openRate: 0, clickRate: 0, createdAt: '2025-02-22T08:00:00Z',
    },
    {
      id: 6, name: '설문조사 참여 요청', segmentId: 2, segmentName: '열성팬 (상위 10%)',
      type: 'SMS', status: 'COMPLETED', targetCount: 1500, sentCount: 1480,
      openRate: 45.3, clickRate: 18.7, completedAt: '2025-02-14T12:00:00Z', createdAt: '2025-02-13T10:00:00Z',
    },
  ]
}

function generateMockSegments(): FanSegment[] {
  return [
    { id: 1, name: '신규 구독자', criteria: '구독 30일 이내', fanCount: 3200, avgEngagement: 8.5, createdAt: '2025-01-10' },
    { id: 2, name: '열성팬 (상위 10%)', criteria: '참여도 상위 10%', fanCount: 1500, avgEngagement: 24.3, createdAt: '2025-01-12' },
    { id: 3, name: '30일 미접속', criteria: '최근 접속 30일 초과', fanCount: 8500, avgEngagement: 1.2, createdAt: '2025-01-15' },
    { id: 4, name: '무료 멤버', criteria: '멤버십 미가입', fanCount: 12000, avgEngagement: 5.8, createdAt: '2025-01-20' },
    { id: 5, name: '라이브 시청자', criteria: '라이브 방송 1회 이상 시청', fanCount: 5600, avgEngagement: 15.7, createdAt: '2025-02-01' },
  ]
}

export const useFanSegmentCampaignStore = defineStore('fanSegmentCampaign', () => {
  const summary = ref<CampaignSummary | null>(null)
  const campaigns = ref<SegmentCampaign[]>([])
  const segments = ref<FanSegment[]>([])
  const loading = ref(false)

  const activeCampaigns = computed(() =>
    campaigns.value.filter((c) => c.status === 'SENDING' || c.status === 'SCHEDULED'),
  )

  async function fetchSummary() {
    loading.value = true
    try {
      summary.value = await fanSegmentCampaignApi.getSummary()
    } catch {
      summary.value = generateMockSummary()
    } finally {
      loading.value = false
    }
  }

  async function fetchCampaigns() {
    try {
      campaigns.value = await fanSegmentCampaignApi.getCampaigns()
    } catch {
      campaigns.value = generateMockCampaigns()
    }
  }

  async function fetchSegments() {
    try {
      segments.value = await fanSegmentCampaignApi.getSegments()
    } catch {
      segments.value = generateMockSegments()
    }
  }

  async function deleteCampaign(id: number) {
    try { await fanSegmentCampaignApi.deleteCampaign(id) } catch { /* 로컬 */ }
    campaigns.value = campaigns.value.filter((c) => c.id !== id)
  }

  return { summary, campaigns, segments, loading, activeCampaigns, fetchSummary, fetchCampaigns, fetchSegments, deleteCampaign }
})
