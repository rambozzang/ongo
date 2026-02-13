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
      } catch (e) {
        console.error('Failed to fetch goals:', e)
        useNotificationStore().error('목표 처리 중 오류가 발생했습니다')
        this.loadFromLocalStorage()
      } finally {
        this.loading = false
      }
    },

    async createGoal(goal: Omit<Goal, 'id' | 'createdAt' | 'completedAt'>) {
      try {
        const data = await goalApi.create({
          title: goal.title,
          description: goal.description,
          metricType: METRIC_MAP[goal.type] || 'VIEWS',
          targetValue: goal.targetValue,
          startDate: goal.startDate,
          endDate: goal.endDate,
        })
        this.goals.unshift(mapApiToGoal(data))
      } catch (e) {
        console.error('Failed to create goal:', e)
        useNotificationStore().error('목표 처리 중 오류가 발생했습니다')
        const newGoal: Goal = {
          ...goal,
          id: Math.max(...this.goals.map(g => g.id), 0) + 1,
          createdAt: new Date().toISOString(),
          completedAt: null,
        }
        this.goals.unshift(newGoal)
        this.saveToLocalStorage()
      }
    },

    async updateGoal(id: number, updates: Partial<Goal>) {
      try {
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
      } catch (e) {
        console.error('Failed to update goal:', e)
        useNotificationStore().error('목표 처리 중 오류가 발생했습니다')
        const index = this.goals.findIndex(g => g.id === id)
        if (index !== -1) {
          this.goals[index] = { ...this.goals[index], ...updates }
          if (updates.currentValue !== undefined) {
            const goal = this.goals[index]
            if (goal.currentValue >= goal.targetValue && goal.status === 'active') {
              goal.status = 'completed'
              goal.completedAt = new Date().toISOString()
            }
          }
          this.saveToLocalStorage()
        }
      }
    },

    async deleteGoal(id: number) {
      try {
        await goalApi.delete(id)
        const index = this.goals.findIndex(g => g.id === id)
        if (index !== -1) {
          this.goals.splice(index, 1)
        }
      } catch (e) {
        console.error('Failed to delete goal:', e)
        useNotificationStore().error('목표 처리 중 오류가 발생했습니다')
        const index = this.goals.findIndex(g => g.id === id)
        if (index !== -1) {
          this.goals.splice(index, 1)
          this.saveToLocalStorage()
        }
      }
    },

    async updateProgress(id: number, currentValue: number) {
      try {
        const data = await goalApi.updateProgress(id, currentValue)
        const index = this.goals.findIndex(g => g.id === id)
        if (index !== -1) {
          this.goals[index] = mapApiToGoal(data)
        }
      } catch (e) {
        console.error('Failed to update progress:', e)
        useNotificationStore().error('목표 처리 중 오류가 발생했습니다')
        this.updateGoal(id, { currentValue })
        const goal = this.goals.find(g => g.id === id)
        if (goal) {
          goal.milestones.forEach(milestone => {
            if (!milestone.isCompleted && currentValue >= milestone.targetValue) {
              milestone.isCompleted = true
              milestone.completedAt = new Date().toISOString()
            }
          })
          this.saveToLocalStorage()
        }
      }
    },

    completeMilestone(goalId: number, milestoneId: number) {
      const goal = this.goals.find(g => g.id === goalId)
      if (goal) {
        const milestone = goal.milestones.find(m => m.id === milestoneId)
        if (milestone && !milestone.isCompleted) {
          milestone.isCompleted = true
          milestone.completedAt = new Date().toISOString()
          this.saveToLocalStorage()
        }
      }
    },

    async pauseGoal(id: number) {
      await this.updateGoal(id, { status: 'paused' })
    },

    async resumeGoal(id: number) {
      await this.updateGoal(id, { status: 'active' })
    },

    saveToLocalStorage() {
      localStorage.setItem('ongo-goals', JSON.stringify(this.goals))
    },

    loadFromLocalStorage() {
      const stored = localStorage.getItem('ongo-goals')
      if (stored) {
        try {
          this.goals = JSON.parse(stored)
        } catch (e) {
          console.error('Failed to load goals from localStorage:', e)
        }
      }
    },
  },
})
