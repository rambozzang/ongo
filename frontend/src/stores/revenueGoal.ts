import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { RevenueGoal, RevenueGoalMilestone, RevenueGoalSummary } from '@/types/revenueGoal'
import { revenueGoalApi } from '@/api/revenueGoal'

function generateMockGoals(): RevenueGoal[] {
  return [
    {
      id: 1,
      name: '월 수익 500만원 달성',
      targetAmount: 5000000,
      currentAmount: 3250000,
      currency: 'KRW',
      period: 'MONTHLY',
      platform: 'youtube',
      status: 'ACTIVE',
      progress: 65,
      startDate: '2025-01-01',
      endDate: '2025-12-31',
    },
    {
      id: 2,
      name: 'TikTok 수익 100만원',
      targetAmount: 1000000,
      currentAmount: 1000000,
      currency: 'KRW',
      period: 'QUARTERLY',
      platform: 'tiktok',
      status: 'COMPLETED',
      progress: 100,
      startDate: '2025-01-01',
      endDate: '2025-03-31',
    },
    {
      id: 3,
      name: '연간 총 수익 5천만원',
      targetAmount: 50000000,
      currentAmount: 18500000,
      currency: 'KRW',
      period: 'YEARLY',
      platform: 'all',
      status: 'ACTIVE',
      progress: 37,
      startDate: '2025-01-01',
      endDate: '2025-12-31',
    },
    {
      id: 4,
      name: 'Instagram 수익 50만원',
      targetAmount: 500000,
      currentAmount: 120000,
      currency: 'KRW',
      period: 'MONTHLY',
      platform: 'instagram',
      status: 'ACTIVE',
      progress: 24,
      startDate: '2025-02-01',
      endDate: '2025-02-28',
    },
  ]
}

function generateMockMilestones(): RevenueGoalMilestone[] {
  return [
    { id: 1, goalId: 1, label: '100만원 돌파', targetAmount: 1000000, reached: true, reachedAt: '2025-01-15T10:00:00Z' },
    { id: 2, goalId: 1, label: '200만원 돌파', targetAmount: 2000000, reached: true, reachedAt: '2025-02-03T14:30:00Z' },
    { id: 3, goalId: 1, label: '300만원 돌파', targetAmount: 3000000, reached: true, reachedAt: '2025-02-20T09:15:00Z' },
    { id: 4, goalId: 1, label: '400만원 돌파', targetAmount: 4000000, reached: false },
    { id: 5, goalId: 1, label: '500만원 최종 목표', targetAmount: 5000000, reached: false },
    { id: 6, goalId: 3, label: '1천만원 돌파', targetAmount: 10000000, reached: true, reachedAt: '2025-01-28T16:00:00Z' },
    { id: 7, goalId: 3, label: '2천만원 돌파', targetAmount: 20000000, reached: false },
    { id: 8, goalId: 3, label: '3천만원 돌파', targetAmount: 30000000, reached: false },
    { id: 9, goalId: 3, label: '5천만원 최종 목표', targetAmount: 50000000, reached: false },
  ]
}

function generateMockSummary(): RevenueGoalSummary {
  return {
    totalGoals: 8,
    activeGoals: 5,
    avgProgress: 52.3,
    totalTarget: 62500000,
    totalCurrent: 22870000,
  }
}

export const useRevenueGoalStore = defineStore('revenueGoal', () => {
  const goals = ref<RevenueGoal[]>([])
  const milestones = ref<RevenueGoalMilestone[]>([])
  const summary = ref<RevenueGoalSummary | null>(null)
  const loading = ref(false)

  const activeGoalCount = computed(() => goals.value.filter((g) => g.status === 'ACTIVE').length)

  async function fetchGoals() {
    loading.value = true
    try {
      goals.value = await revenueGoalApi.getGoals()
    } catch {
      goals.value = generateMockGoals()
    } finally {
      loading.value = false
    }
  }

  async function fetchMilestones(goalId?: number) {
    try {
      if (goalId) {
        const data = await revenueGoalApi.getMilestones(goalId)
        milestones.value = [...milestones.value.filter((m) => m.goalId !== goalId), ...data]
      }
    } catch {
      milestones.value = generateMockMilestones()
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await revenueGoalApi.getSummary()
    } catch {
      summary.value = generateMockSummary()
    }
  }

  async function deleteGoal(id: number) {
    try { await revenueGoalApi.deleteGoal(id) } catch { /* 로컬 */ }
    goals.value = goals.value.filter((g) => g.id !== id)
    milestones.value = milestones.value.filter((m) => m.goalId !== id)
  }

  function getMilestonesForGoal(goalId: number): RevenueGoalMilestone[] {
    return milestones.value.filter((m) => m.goalId === goalId)
  }

  return { goals, milestones, summary, loading, activeGoalCount, fetchGoals, fetchMilestones, fetchSummary, deleteGoal, getMilestonesForGoal }
})
