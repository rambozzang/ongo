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
    queues: [
      {
        id: 1,
        name: '인기 영상 주간 재업로드',
        filterCriteria: {
          minViews: 50000,
          maxAge: 6,
          categories: ['브이로그', '일상'],
        },
        frequency: 'weekly',
        scheduleDays: [1, 4], // Mon, Thu
        scheduleTime: '18:00',
        platforms: ['YOUTUBE', 'TIKTOK'],
        isActive: true,
        videoCount: 12,
        nextScheduledAt: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString(),
        titleVariation: 'ai',
        createdAt: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString(),
      },
      {
        id: 2,
        name: '쇼츠 콘텐츠 재활용',
        filterCriteria: {
          minViews: 10000,
          maxAge: 3,
          categories: ['쇼츠', '꿀팁'],
        },
        frequency: 'biweekly',
        scheduleDays: [3], // Wed
        scheduleTime: '12:00',
        platforms: ['TIKTOK', 'INSTAGRAM', 'NAVER_CLIP'],
        isActive: true,
        videoCount: 8,
        nextScheduledAt: new Date(Date.now() + 5 * 24 * 60 * 60 * 1000).toISOString(),
        titleVariation: 'same',
        createdAt: new Date(Date.now() - 20 * 24 * 60 * 60 * 1000).toISOString(),
      },
      {
        id: 3,
        name: '월간 베스트 콘텐츠',
        filterCriteria: {
          minViews: 100000,
          maxAge: 12,
        },
        frequency: 'monthly',
        scheduleDays: [1], // Mon
        scheduleTime: '09:00',
        platforms: ['YOUTUBE'],
        isActive: true,
        videoCount: 5,
        nextScheduledAt: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString(),
        titleVariation: 'ai',
        createdAt: new Date(Date.now() - 60 * 24 * 60 * 60 * 1000).toISOString(),
      },
      {
        id: 4,
        name: '네이버 클립 전용 큐',
        filterCriteria: {
          minViews: 5000,
          maxAge: 2,
          platforms: ['YOUTUBE'],
        },
        frequency: 'weekly',
        scheduleDays: [2, 5], // Tue, Fri
        scheduleTime: '15:00',
        platforms: ['NAVER_CLIP'],
        isActive: false,
        videoCount: 3,
        nextScheduledAt: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString(),
        titleVariation: 'manual',
        createdAt: new Date(Date.now() - 10 * 24 * 60 * 60 * 1000).toISOString(),
      },
      {
        id: 5,
        name: '튜토리얼 시리즈 재게시',
        filterCriteria: {
          minViews: 30000,
          maxAge: 9,
          categories: ['튜토리얼', '교육'],
        },
        frequency: 'biweekly',
        scheduleDays: [6], // Sat
        scheduleTime: '10:00',
        platforms: ['YOUTUBE', 'INSTAGRAM'],
        isActive: true,
        videoCount: 7,
        nextScheduledAt: new Date(Date.now() + 8 * 24 * 60 * 60 * 1000).toISOString(),
        titleVariation: 'ai',
        createdAt: new Date(Date.now() - 45 * 24 * 60 * 60 * 1000).toISOString(),
      },
    ],
    history: [
      {
        id: 1,
        queueId: 1,
        videoId: 101,
        videoTitle: '서울 카페 투어 브이로그',
        platforms: ['YOUTUBE', 'TIKTOK'],
        scheduledAt: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString(),
        publishedAt: new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString(),
        status: 'published',
      },
      {
        id: 2,
        queueId: 2,
        videoId: 102,
        videoTitle: '30초 요리 꿀팁 모음',
        platforms: ['TIKTOK', 'INSTAGRAM'],
        scheduledAt: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(),
        publishedAt: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString(),
        status: 'published',
      },
      {
        id: 3,
        queueId: 1,
        videoId: 103,
        videoTitle: '일상 브이로그 - 한강 자전거',
        platforms: ['YOUTUBE', 'TIKTOK'],
        scheduledAt: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000).toISOString(),
        publishedAt: null,
        status: 'failed',
      },
      {
        id: 4,
        queueId: 3,
        videoId: 104,
        videoTitle: '2024 베스트 여행 영상',
        platforms: ['YOUTUBE'],
        scheduledAt: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString(),
        publishedAt: new Date(Date.now() - 5 * 24 * 60 * 60 * 1000).toISOString(),
        status: 'published',
      },
      {
        id: 5,
        queueId: 5,
        videoId: 105,
        videoTitle: '포토샵 기초 튜토리얼',
        platforms: ['YOUTUBE', 'INSTAGRAM'],
        scheduledAt: new Date(Date.now() + 1 * 24 * 60 * 60 * 1000).toISOString(),
        publishedAt: null,
        status: 'pending',
      },
      {
        id: 6,
        queueId: 2,
        videoId: 106,
        videoTitle: '운동 루틴 1분 정리',
        platforms: ['TIKTOK', 'NAVER_CLIP'],
        scheduledAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
        publishedAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString(),
        status: 'published',
      },
      {
        id: 7,
        queueId: 1,
        videoId: 107,
        videoTitle: '제주도 맛집 브이로그',
        platforms: ['YOUTUBE', 'TIKTOK'],
        scheduledAt: new Date(Date.now() - 10 * 24 * 60 * 60 * 1000).toISOString(),
        publishedAt: new Date(Date.now() - 10 * 24 * 60 * 60 * 1000).toISOString(),
        status: 'published',
      },
      {
        id: 8,
        queueId: 5,
        videoId: 108,
        videoTitle: '프리미어 프로 편집 팁',
        platforms: ['YOUTUBE'],
        scheduledAt: new Date(Date.now() - 12 * 24 * 60 * 60 * 1000).toISOString(),
        publishedAt: null,
        status: 'failed',
      },
      {
        id: 9,
        queueId: 3,
        videoId: 109,
        videoTitle: '구독자 10만 감사 영상',
        platforms: ['YOUTUBE'],
        scheduledAt: new Date(Date.now() + 3 * 24 * 60 * 60 * 1000).toISOString(),
        publishedAt: null,
        status: 'pending',
      },
      {
        id: 10,
        queueId: 2,
        videoId: 110,
        videoTitle: '아이폰 숨겨진 기능 5가지',
        platforms: ['TIKTOK', 'INSTAGRAM', 'NAVER_CLIP'],
        scheduledAt: new Date(Date.now() - 14 * 24 * 60 * 60 * 1000).toISOString(),
        publishedAt: new Date(Date.now() - 14 * 24 * 60 * 60 * 1000).toISOString(),
        status: 'published',
      },
    ],
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
      this.saveToLocalStorage()
    },

    updateQueue(id: number, updates: Partial<RecyclingQueue>) {
      const index = this.queues.findIndex((q) => q.id === id)
      if (index !== -1) {
        this.queues[index] = {
          ...this.queues[index],
          ...updates,
        }
        this.saveToLocalStorage()
      }
    },

    deleteQueue(id: number) {
      const index = this.queues.findIndex((q) => q.id === id)
      if (index !== -1) {
        this.queues.splice(index, 1)
        if (this.selectedQueueId === id) {
          this.selectedQueueId = null
        }
        this.saveToLocalStorage()
      }
    },

    toggleActive(id: number) {
      const queue = this.queues.find((q) => q.id === id)
      if (queue) {
        queue.isActive = !queue.isActive
        this.saveToLocalStorage()
      }
    },

    addVideoToQueue(queueId: number, videoId: number, videoTitle: string) {
      const queue = this.queues.find((q) => q.id === queueId)
      if (queue) {
        queue.videoCount++
        const newHistory: RecyclingHistory = {
          id: Math.max(...this.history.map((h) => h.id), 0) + 1,
          queueId,
          videoId,
          videoTitle,
          platforms: queue.platforms,
          scheduledAt: queue.nextScheduledAt,
          publishedAt: null,
          status: 'pending',
        }
        this.history.push(newHistory)
        this.saveToLocalStorage()
      }
    },

    selectQueue(id: number | null) {
      this.selectedQueueId = id
    },

    saveToLocalStorage() {
      localStorage.setItem('ongo_recycling_queues', JSON.stringify(this.queues))
      localStorage.setItem('ongo_recycling_history', JSON.stringify(this.history))
    },

    loadFromLocalStorage() {
      const queuesData = localStorage.getItem('ongo_recycling_queues')
      const historyData = localStorage.getItem('ongo_recycling_history')

      if (queuesData) {
        this.queues = JSON.parse(queuesData)
      }
      if (historyData) {
        this.history = JSON.parse(historyData)
      }
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
