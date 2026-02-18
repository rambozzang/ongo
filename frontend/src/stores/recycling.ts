import { defineStore } from 'pinia'
import type {
  RecyclingQueue,
  RecyclingHistory,
  RecyclingQueueCreateRequest,
  RecyclingSuggestion,
} from '@/types/recycling'
import { recyclingApi } from '@/api/recycling'

interface RecyclingState {
  queues: RecyclingQueue[]
  history: RecyclingHistory[]
  suggestions: RecyclingSuggestion[]
  suggestionsLoading: boolean
  selectedQueueId: number | null
}

export const useRecyclingStore = defineStore('recycling', {
  state: (): RecyclingState => ({
    queues: [],
    history: [],
    suggestions: [],
    suggestionsLoading: false,
    selectedQueueId: null,
  }),

  getters: {
    activeQueues: (state): RecyclingQueue[] => {
      return state.queues.filter((q) => q.isActive)
    },

    selectedQueue: (state): RecyclingQueue | undefined => {
      if (state.selectedQueueId === null) return undefined
      return state.queues.find((q) => q.id === state.selectedQueueId)
    },

    recentHistory: (state): RecyclingHistory[] => {
      return [...state.history].sort(
        (a, b) => new Date(b.scheduledAt).getTime() - new Date(a.scheduledAt).getTime()
      )
    },

    totalRecycledCount: (state): number => {
      return state.history.filter((h) => h.status === 'published').length
    },

    nextScheduledItem: (state): RecyclingHistory | undefined => {
      const pending = state.history
        .filter((h) => h.status === 'pending')
        .sort(
          (a, b) => new Date(a.scheduledAt).getTime() - new Date(b.scheduledAt).getTime()
        )
      return pending[0]
    },
  },

  actions: {
    createQueue(request: RecyclingQueueCreateRequest) {
      const newQueue: RecyclingQueue = {
        id: Math.max(...this.queues.map((q) => q.id), 0) + 1,
        name: request.name,
        filterCriteria: request.filterCriteria,
        frequency: request.frequency,
        scheduleDays: request.scheduleDays,
        scheduleTime: request.scheduleTime,
        platforms: request.platforms,
        isActive: true,
        videoCount: 0,
        nextScheduledAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
        titleVariation: request.titleVariation,
        createdAt: new Date().toISOString(),
      }
      this.queues.push(newQueue)
    },

    updateQueue(id: number, updates: Partial<RecyclingQueue>) {
      const index = this.queues.findIndex((q) => q.id === id)
      if (index !== -1) {
        this.queues[index] = { ...this.queues[index], ...updates }
      }
    },

    deleteQueue(id: number) {
      const index = this.queues.findIndex((q) => q.id === id)
      if (index !== -1) {
        this.queues.splice(index, 1)
        if (this.selectedQueueId === id) {
          this.selectedQueueId = null
        }
      }
    },

    toggleActive(id: number) {
      const queue = this.queues.find((q) => q.id === id)
      if (queue) {
        queue.isActive = !queue.isActive
      }
    },

    selectQueue(id: number | null) {
      this.selectedQueueId = id
    },

    // API-backed suggestion actions
    async fetchSuggestions(status?: string) {
      this.suggestionsLoading = true
      try {
        this.suggestions = await recyclingApi.getSuggestions(status)
      } catch {
        this.suggestions = []
      } finally {
        this.suggestionsLoading = false
      }
    },

    async generateSuggestions() {
      this.suggestionsLoading = true
      try {
        const newSuggestions = await recyclingApi.generateSuggestions()
        this.suggestions = [...this.suggestions, ...newSuggestions]
        return newSuggestions
      } catch {
        return []
      } finally {
        this.suggestionsLoading = false
      }
    },

    async acceptSuggestion(id: number) {
      try {
        const updated = await recyclingApi.acceptSuggestion(id)
        const index = this.suggestions.findIndex((s) => s.id === id)
        if (index !== -1) {
          this.suggestions[index] = updated
        }
        return updated
      } catch {
        return null
      }
    },

    async dismissSuggestion(id: number) {
      try {
        const updated = await recyclingApi.dismissSuggestion(id)
        const index = this.suggestions.findIndex((s) => s.id === id)
        if (index !== -1) {
          this.suggestions[index] = updated
        }
        return updated
      } catch {
        return null
      }
    },
  },
})
