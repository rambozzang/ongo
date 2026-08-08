import { defineStore } from 'pinia'
import type { RecyclingSuggestion } from '@/types/recycling'
import { recyclingApi } from '@/api/recycling'

/** Server-backed repost recommendations. Queue/history state is intentionally not kept client-side. */
export const useRecyclingStore = defineStore('recycling', {
  state: () => ({
    suggestions: [] as RecyclingSuggestion[],
    suggestionsLoading: false,
    error: null as string | null,
  }),

  actions: {
    async fetchSuggestions(status?: string) {
      this.suggestionsLoading = true
      this.error = null
      try {
        this.suggestions = await recyclingApi.getSuggestions(status)
      } catch (error) {
        this.error = error instanceof Error ? error.message : '재활용 제안을 불러오지 못했습니다.'
      } finally {
        this.suggestionsLoading = false
      }
    },

    async generateSuggestions() {
      this.suggestionsLoading = true
      this.error = null
      try {
        this.suggestions = await recyclingApi.generateSuggestions()
        return this.suggestions
      } catch (error) {
        this.error = error instanceof Error ? error.message : '재활용 제안을 생성하지 못했습니다.'
        return []
      } finally {
        this.suggestionsLoading = false
      }
    },

    async dismissSuggestion(id: number) {
      this.error = null
      try {
        const updated = await recyclingApi.dismissSuggestion(id)
        const index = this.suggestions.findIndex((suggestion) => suggestion.id === id)
        if (index !== -1) this.suggestions[index] = updated
        return updated
      } catch (error) {
        this.error = error instanceof Error ? error.message : '재활용 제안을 숨기지 못했습니다.'
        return null
      }
    },
  },
})
