import { defineStore } from 'pinia'
import { ref } from 'vue'
import { creatorMilestoneApi } from '@/api/creatorMilestone'
import type {
  CreatorMilestone,
  CreatorMilestoneSummary,
  CreateMilestoneRequest,
} from '@/types/creatorMilestone'

export const useCreatorMilestoneStore = defineStore('creatorMilestone', () => {
  const milestones = ref<CreatorMilestone[]>([])
  const summary = ref<CreatorMilestoneSummary>({
    totalMilestones: 0,
    achievedCount: 0,
    inProgressCount: 0,
    nextMilestone: '',
    achievementRate: 0,
  })
  const isLoading = ref(false)

  const mockMilestones: CreatorMilestone[] = [
    {
      id: 1, type: 'SUBSCRIBERS', title: '구독자 10,000명 달성', description: '유튜브 채널 구독자 10,000명 돌파',
      targetValue: 10000, currentValue: 8750, progress: 87.5, platform: 'YOUTUBE',
      status: 'IN_PROGRESS', targetDate: '2025-06-01', createdAt: '2024-01-01T00:00:00Z',
    },
    {
      id: 2, type: 'VIEWS', title: '총 조회수 100만 돌파', description: '유튜브 채널 총 조회수 1,000,000회 달성',
      targetValue: 1000000, currentValue: 1000000, progress: 100, platform: 'YOUTUBE',
      status: 'ACHIEVED', achievedAt: '2025-02-15T10:00:00Z', createdAt: '2024-06-01T00:00:00Z',
    },
    {
      id: 3, type: 'VIDEOS', title: '영상 100개 업로드', description: '유튜브에 총 100개 영상 업로드',
      targetValue: 100, currentValue: 72, progress: 72, platform: 'YOUTUBE',
      status: 'IN_PROGRESS', targetDate: '2025-12-31', createdAt: '2024-03-01T00:00:00Z',
    },
    {
      id: 4, type: 'SUBSCRIBERS', title: '틱톡 팔로워 50,000명', description: '틱톡 계정 팔로워 50,000명 달성',
      targetValue: 50000, currentValue: 32100, progress: 64.2, platform: 'TIKTOK',
      status: 'IN_PROGRESS', targetDate: '2025-09-01', createdAt: '2024-07-01T00:00:00Z',
    },
    {
      id: 5, type: 'REVENUE', title: '월 수익 500만원', description: '전체 플랫폼 합산 월 수익 500만원 달성',
      targetValue: 5000000, currentValue: 3200000, progress: 64, platform: 'YOUTUBE',
      status: 'IN_PROGRESS', targetDate: '2025-12-31', createdAt: '2024-09-01T00:00:00Z',
    },
    {
      id: 6, type: 'WATCH_HOURS', title: '시청 시간 4,000시간', description: '유튜브 파트너 프로그램 요건 달성',
      targetValue: 4000, currentValue: 4000, progress: 100, platform: 'YOUTUBE',
      status: 'ACHIEVED', achievedAt: '2024-11-20T14:00:00Z', createdAt: '2024-01-01T00:00:00Z',
    },
  ]

  const mockSummary: CreatorMilestoneSummary = {
    totalMilestones: 6,
    achievedCount: 2,
    inProgressCount: 4,
    nextMilestone: '구독자 10,000명 달성',
    achievementRate: 33.3,
  }

  async function fetchMilestones(status?: string) {
    isLoading.value = true
    try {
      milestones.value = await creatorMilestoneApi.getMilestones(status)
    } catch {
      milestones.value = status
        ? mockMilestones.filter((m) => m.status === status)
        : mockMilestones
    } finally {
      isLoading.value = false
    }
  }

  async function createMilestone(request: CreateMilestoneRequest) {
    try {
      const milestone = await creatorMilestoneApi.createMilestone(request)
      milestones.value.push(milestone)
    } catch {
      milestones.value.push({
        id: Date.now(), type: request.type as CreatorMilestone['type'], title: request.title,
        description: request.description, targetValue: request.targetValue, currentValue: 0,
        progress: 0, platform: request.platform, status: 'PENDING',
        targetDate: request.targetDate, createdAt: new Date().toISOString(),
      })
    }
  }

  async function deleteMilestone(id: number) {
    try {
      await creatorMilestoneApi.deleteMilestone(id)
    } catch {
      // mock fallback
    }
    milestones.value = milestones.value.filter((m) => m.id !== id)
  }

  async function fetchSummary() {
    try {
      summary.value = await creatorMilestoneApi.getSummary()
    } catch {
      summary.value = mockSummary
    }
  }

  return {
    milestones, summary, isLoading,
    fetchMilestones, createMilestone, deleteMilestone, fetchSummary,
  }
})
