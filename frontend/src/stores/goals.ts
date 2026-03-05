import { defineStore } from 'pinia'
import { goalApi } from '@/api/goal'
import { useNotificationStore } from '@/stores/notification'
import type { Goal } from '@/types/goal'

interface GoalsState {
  goals: Goal[]
  showCompleted: boolean
  loading: boolean
}

function mapApiToGoal(api: { id: number; title: string; description: string | null; metricType: string; targetValue: number; currentValue: number; startDate: string; endDate: string; status: string; milestones: { id: number; title: string; targetValue: number; isReached: boolean; reachedAt: string | null; createdAt: string }[]; createdAt: string; updatedAt: string }): Goal {
  const typeMap: Record<string, string> = {
    VIEWS: 'views',
    SUBSCRIBERS: 'subscribers',
    LIKES: 'views',
    UPLOADS: 'uploads',
    REVENUE: 'revenue',
  }
  return {
    id: api.id,
    title: api.title,
    description: api.description || '',
    type: (typeMap[api.metricType] || 'custom') as Goal['type'],
    status: (api.status?.toLowerCase() || 'active') as Goal['status'],
    period: 'monthly',
    targetValue: api.targetValue,
    currentValue: api.currentValue,
    unit: '',
    startDate: api.startDate,
    endDate: api.endDate,
    milestones: api.milestones.map(m => ({
      id: m.id,
      title: m.title,
      targetValue: m.targetValue,
      isCompleted: m.isReached,
      completedAt: m.reachedAt,
    })),
    createdAt: api.createdAt,
    completedAt: api.status === 'COMPLETED' ? api.updatedAt : null,
  }
}

const METRIC_MAP: Record<string, string> = {
  subscribers: 'SUBSCRIBERS',
  views: 'VIEWS',
  uploads: 'UPLOADS',
  revenue: 'REVENUE',
  engagement: 'LIKES',
  custom: 'VIEWS',
}

export const useGoalsStore = defineStore('goals', {
  state: (): GoalsState => ({
    goals: [],
    showCompleted: false,
    loading: false,
  }),

  getters: {
    activeGoals: (state): Goal[] => {
      return state.goals.filter(goal => goal.status === 'active')
    },

    completedGoals: (state): Goal[] => {
      return state.goals.filter(goal => goal.status === 'completed')
    },

    overallProgress: (state): number => {
      const active = state.goals.filter(goal => goal.status === 'active')
      if (active.length === 0) return 0

      const totalProgress = active.reduce((sum, goal) => {
        return sum + (goal.currentValue / goal.targetValue) * 100
      }, 0)

      return Math.round(totalProgress / active.length)
    },

    upcomingDeadlines(): Goal[] {
      const active = this.activeGoals
      const now = new Date()

      return active
        .filter(goal => {
          const endDate = new Date(goal.endDate)
          const daysLeft = Math.ceil((endDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
          return daysLeft <= 7 && daysLeft >= 0
        })
        .sort((a, b) => new Date(a.endDate).getTime() - new Date(b.endDate).getTime())
    },
  },

  actions: {
    async fetchGoals() {
      this.loading = true
      try {
        const data = await goalApi.list()
        this.goals = data.map(mapApiToGoal)
      } catch {
        useNotificationStore().error('목표 목록을 불러오는 중 오류가 발생했습니다')
      } finally {
        this.loading = false
      }
    },

    async createGoal(goal: Omit<Goal, 'id' | 'createdAt' | 'completedAt'>) {
      const data = await goalApi.create({
        title: goal.title,
        description: goal.description,
        metricType: METRIC_MAP[goal.type] || 'VIEWS',
        targetValue: goal.targetValue,
        startDate: goal.startDate,
        endDate: goal.endDate,
      })
      this.goals.unshift(mapApiToGoal(data))
    },

    async updateGoal(id: number, updates: Partial<Goal>) {
      const data = await goalApi.update(id, {
        title: updates.title,
        description: updates.description,
        metricType: updates.type ? METRIC_MAP[updates.type] : undefined,
        targetValue: updates.targetValue,
        startDate: updates.startDate,
        endDate: updates.endDate,
        status: updates.status?.toUpperCase(),
      })
      const index = this.goals.findIndex(g => g.id === id)
      if (index !== -1) {
        this.goals[index] = mapApiToGoal(data)
      }
    },

    async deleteGoal(id: number) {
      await goalApi.delete(id)
      const index = this.goals.findIndex(g => g.id === id)
      if (index !== -1) {
        this.goals.splice(index, 1)
      }
    },

    async updateProgress(id: number, currentValue: number) {
      const data = await goalApi.updateProgress(id, currentValue)
      const index = this.goals.findIndex(g => g.id === id)
      if (index !== -1) {
        this.goals[index] = mapApiToGoal(data)
      }
    },

    async completeMilestone(goalId: number, milestoneId: number) {
      const goal = this.goals.find(g => g.id === goalId)
      if (goal) {
        const milestone = goal.milestones.find(m => m.id === milestoneId)
        if (milestone && !milestone.isCompleted) {
          await this.updateProgress(goalId, milestone.targetValue)
        }
      }
    },

    async pauseGoal(id: number) {
      await this.updateGoal(id, { status: 'paused' })
    },

    async resumeGoal(id: number) {
      await this.updateGoal(id, { status: 'active' })
    },
  },
})
