import { defineStore } from 'pinia'
import { ideaApi } from '@/api/idea'
import { useNotificationStore } from '@/stores/notification'
import type { ContentIdea, IdeaStatus, IdeaPriority } from '@/types/idea'

interface IdeasState {
  ideas: ContentIdea[]
  filters: {
    status: IdeaStatus | 'all'
    priority: IdeaPriority | 'all'
    search: string
  }
  loading: boolean
}

const STATUS_MAP: Record<string, IdeaStatus> = {
  BACKLOG: 'idea',
  TODO: 'planning',
  IN_PROGRESS: 'producing',
  DONE: 'completed',
}

const STATUS_MAP_REVERSE: Record<IdeaStatus, string> = {
  idea: 'BACKLOG',
  planning: 'TODO',
  producing: 'IN_PROGRESS',
  completed: 'DONE',
}

const PRIORITY_MAP_REVERSE: Record<IdeaPriority, string> = {
  low: 'LOW',
  medium: 'MEDIUM',
  high: 'HIGH',
}

function mapApiToIdea(api: { id: number; title: string; description: string | null; status: string; category: string | null; tags: string[]; priority: string; source: string | null; referenceUrl: string | null; dueDate: string | null; createdAt: string; updatedAt: string }): ContentIdea {
  return {
    id: api.id,
    title: api.title,
    description: api.description || '',
    status: STATUS_MAP[api.status] || 'idea',
    priority: (api.priority?.toLowerCase() as IdeaPriority) || 'medium',
    platform: [],
    tags: api.tags || [],
    dueDate: api.dueDate,
    createdAt: api.createdAt,
    updatedAt: api.updatedAt,
    notes: api.source || '',
  }
}

export const useIdeasStore = defineStore('ideas', {
  state: (): IdeasState => ({
    ideas: [],
    filters: {
      status: 'all',
      priority: 'all',
      search: ''
    },
    loading: false,
  }),

  getters: {
    ideasByStatus(): Record<IdeaStatus, ContentIdea[]> {
      return {
        idea: this.ideas.filter(idea => idea.status === 'idea'),
        planning: this.ideas.filter(idea => idea.status === 'planning'),
        producing: this.ideas.filter(idea => idea.status === 'producing'),
        completed: this.ideas.filter(idea => idea.status === 'completed')
      }
    },

    filteredIdeas(): ContentIdea[] {
      let filtered = this.ideas

      if (this.filters.status !== 'all') {
        filtered = filtered.filter(idea => idea.status === this.filters.status)
      }

      if (this.filters.priority !== 'all') {
        filtered = filtered.filter(idea => idea.priority === this.filters.priority)
      }

      if (this.filters.search) {
        const searchLower = this.filters.search.toLowerCase()
        filtered = filtered.filter(idea =>
          idea.title.toLowerCase().includes(searchLower) ||
          idea.description.toLowerCase().includes(searchLower) ||
          idea.tags.some(tag => tag.toLowerCase().includes(searchLower))
        )
      }

      return filtered
    }
  },

  actions: {
    async fetchIdeas() {
      this.loading = true
      try {
        const data = await ideaApi.list()
        this.ideas = data.map(mapApiToIdea)
      } catch (e) {

        useNotificationStore().error('아이디어 처리 중 오류가 발생했습니다')
        this.loadFromLocalStorage()
      } finally {
        this.loading = false
      }
    },

    async addIdea(idea: Omit<ContentIdea, 'id' | 'createdAt' | 'updatedAt'>) {
      try {
        const data = await ideaApi.create({
          title: idea.title,
          description: idea.description,
          status: STATUS_MAP_REVERSE[idea.status] || 'BACKLOG',
          tags: idea.tags,
          priority: PRIORITY_MAP_REVERSE[idea.priority] || 'MEDIUM',
          dueDate: idea.dueDate || undefined,
        })
        this.ideas.push(mapApiToIdea(data))
      } catch (e) {

        useNotificationStore().error('아이디어 처리 중 오류가 발생했습니다')
        const newIdea: ContentIdea = {
          ...idea,
          id: Math.max(...this.ideas.map(i => i.id), 0) + 1,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString()
        }
        this.ideas.push(newIdea)
        this.saveToLocalStorage()
      }
    },

    async updateIdea(id: number, updates: Partial<ContentIdea>) {
      try {
        const data = await ideaApi.update(id, {
          title: updates.title,
          description: updates.description,
          status: updates.status ? STATUS_MAP_REVERSE[updates.status] : undefined,
          tags: updates.tags,
          priority: updates.priority ? PRIORITY_MAP_REVERSE[updates.priority] : undefined,
          dueDate: updates.dueDate || undefined,
        })
        const index = this.ideas.findIndex(idea => idea.id === id)
        if (index !== -1) {
          this.ideas[index] = mapApiToIdea(data)
        }
      } catch (e) {

        useNotificationStore().error('아이디어 처리 중 오류가 발생했습니다')
        const index = this.ideas.findIndex(idea => idea.id === id)
        if (index !== -1) {
          this.ideas[index] = {
            ...this.ideas[index],
            ...updates,
            updatedAt: new Date().toISOString()
          }
          this.saveToLocalStorage()
        }
      }
    },

    async deleteIdea(id: number) {
      try {
        await ideaApi.delete(id)
        const index = this.ideas.findIndex(idea => idea.id === id)
        if (index !== -1) {
          this.ideas.splice(index, 1)
        }
      } catch (e) {

        useNotificationStore().error('아이디어 처리 중 오류가 발생했습니다')
        const index = this.ideas.findIndex(idea => idea.id === id)
        if (index !== -1) {
          this.ideas.splice(index, 1)
          this.saveToLocalStorage()
        }
      }
    },

    async moveIdea(id: number, newStatus: IdeaStatus) {
      try {
        await ideaApi.changeStatus(id, STATUS_MAP_REVERSE[newStatus])
        const idea = this.ideas.find(i => i.id === id)
        if (idea) {
          idea.status = newStatus
          idea.updatedAt = new Date().toISOString()
        }
      } catch (e) {

        useNotificationStore().error('아이디어 처리 중 오류가 발생했습니다')
        const idea = this.ideas.find(i => i.id === id)
        if (idea) {
          idea.status = newStatus
          idea.updatedAt = new Date().toISOString()
          this.saveToLocalStorage()
        }
      }
    },

    reorderIdea(id: number, newIndex: number, status: IdeaStatus) {
      const ideaIndex = this.ideas.findIndex(i => i.id === id)
      if (ideaIndex === -1) return

      const idea = this.ideas[ideaIndex]
      this.ideas.splice(ideaIndex, 1)

      const statusIdeas = this.ideas.filter(i => i.status === status)
      const insertIndex = this.ideas.indexOf(statusIdeas[newIndex]) || this.ideas.length

      this.ideas.splice(insertIndex, 0, idea)
      this.saveToLocalStorage()
    },

    setFilter(type: 'status' | 'priority' | 'search', value: string) {
      if (type === 'status') {
        this.filters.status = value as IdeaStatus | 'all'
      } else if (type === 'priority') {
        this.filters.priority = value as IdeaPriority | 'all'
      } else {
        this.filters.search = value
      }
    },

    saveToLocalStorage() {
      localStorage.setItem('ongo-ideas', JSON.stringify(this.ideas))
    },

    loadFromLocalStorage() {
      const stored = localStorage.getItem('ongo-ideas')
      if (stored) {
        try {
          this.ideas = JSON.parse(stored)
        } catch (e) {

        }
      }
    }
  }
})
