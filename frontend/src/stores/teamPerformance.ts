import { defineStore } from 'pinia'
import { ref } from 'vue'
import { teamPerformanceApi } from '@/api/teamPerformance'
import type {
  TeamMemberPerformance,
  TeamActivity,
  TeamPerformanceSummary,
} from '@/types/teamPerformance'

export const useTeamPerformanceStore = defineStore('teamPerformance', () => {
  const members = ref<TeamMemberPerformance[]>([])
  const activities = ref<TeamActivity[]>([])
  const summary = ref<TeamPerformanceSummary>({
    totalMembers: 0,
    avgCompletionRate: 0,
    totalTasksCompleted: 0,
    totalContentProduced: 0,
    topPerformer: '',
  })
  const isLoading = ref(false)

  const mockMembers: TeamMemberPerformance[] = [
    {
      id: 1, name: '김민수', email: 'minsu@example.com', role: '영상 편집자',
      tasksCompleted: 45, tasksAssigned: 50, completionRate: 90, avgCompletionTime: 2.3,
      contentProduced: 38, onTimeRate: 92, rating: 4.8, streak: 12, lastActiveAt: '2025-03-10T15:30:00Z',
    },
    {
      id: 2, name: '이서연', email: 'seoyeon@example.com', role: '콘텐츠 기획자',
      tasksCompleted: 62, tasksAssigned: 65, completionRate: 95.4, avgCompletionTime: 1.8,
      contentProduced: 55, onTimeRate: 97, rating: 4.9, streak: 21, lastActiveAt: '2025-03-10T16:00:00Z',
    },
    {
      id: 3, name: '박준혁', email: 'junhyuk@example.com', role: '썸네일 디자이너',
      tasksCompleted: 78, tasksAssigned: 82, completionRate: 95.1, avgCompletionTime: 1.2,
      contentProduced: 78, onTimeRate: 94, rating: 4.7, streak: 8, lastActiveAt: '2025-03-10T14:00:00Z',
    },
    {
      id: 4, name: '최수진', email: 'sujin@example.com', role: 'SNS 매니저',
      tasksCompleted: 35, tasksAssigned: 42, completionRate: 83.3, avgCompletionTime: 3.1,
      contentProduced: 28, onTimeRate: 78, rating: 4.3, streak: 3, lastActiveAt: '2025-03-10T11:00:00Z',
    },
    {
      id: 5, name: '정우진', email: 'woojin@example.com', role: '스크립트 작가',
      tasksCompleted: 25, tasksAssigned: 28, completionRate: 89.3, avgCompletionTime: 4.5,
      contentProduced: 25, onTimeRate: 86, rating: 4.5, streak: 6, lastActiveAt: '2025-03-10T13:00:00Z',
    },
  ]

  const mockActivities: TeamActivity[] = [
    { id: 1, memberId: 2, memberName: '이서연', action: '콘텐츠 기획 완료', target: '주간 브이로그 시리즈 #15', timestamp: '2025-03-10T16:00:00Z' },
    { id: 2, memberId: 1, memberName: '김민수', action: '영상 편집 완료', target: '쿠킹 챌린지 에피소드 8', timestamp: '2025-03-10T15:30:00Z' },
    { id: 3, memberId: 3, memberName: '박준혁', action: '썸네일 제작', target: '여행 브이로그 Day 3', timestamp: '2025-03-10T14:00:00Z' },
    { id: 4, memberId: 5, memberName: '정우진', action: '스크립트 초안 제출', target: '제품 리뷰 #22', timestamp: '2025-03-10T13:00:00Z' },
    { id: 5, memberId: 4, memberName: '최수진', action: 'SNS 포스팅', target: 'Instagram 릴스 업로드', timestamp: '2025-03-10T11:00:00Z' },
    { id: 6, memberId: 2, memberName: '이서연', action: '기획안 리뷰', target: '브랜드 콜라보 기획', timestamp: '2025-03-09T17:00:00Z' },
    { id: 7, memberId: 1, memberName: '김민수', action: '영상 컬러 그레이딩', target: '패션 하울 영상', timestamp: '2025-03-09T16:00:00Z' },
    { id: 8, memberId: 3, memberName: '박준혁', action: '배너 디자인', target: '채널 아트 리뉴얼', timestamp: '2025-03-09T14:00:00Z' },
  ]

  const mockSummary: TeamPerformanceSummary = {
    totalMembers: 5,
    avgCompletionRate: 90.6,
    totalTasksCompleted: 245,
    totalContentProduced: 224,
    topPerformer: '이서연',
  }

  async function fetchMembers() {
    isLoading.value = true
    try {
      members.value = await teamPerformanceApi.getMembers()
    } catch {
      members.value = mockMembers
    } finally {
      isLoading.value = false
    }
  }

  async function fetchActivities(limit = 20) {
    try {
      activities.value = await teamPerformanceApi.getActivities(limit)
    } catch {
      activities.value = mockActivities.slice(0, limit)
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await teamPerformanceApi.getSummary()
    } catch {
      summary.value = mockSummary
    }
  }

  return {
    members,
    activities,
    summary,
    isLoading,
    fetchMembers,
    fetchActivities,
    fetchSummary,
  }
})
