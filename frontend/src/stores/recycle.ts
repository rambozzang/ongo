import { defineStore } from 'pinia'
import { ref } from 'vue'
import { videoApi } from '@/api/video'
import { useNotificationStore } from '@/stores/notification'

export interface RecycleMetadata {
  title: string
  description: string
  tags: string[]
  category: string
  platforms: string[]
  scheduledAt?: string
  useAI: boolean
}

export interface RecycleRecord {
  originalVideoId: number
  originalTitle: string
  recycledAt: string
  platforms: string[]
}

export const useRecycleStore = defineStore('recycle', () => {
  const recentRecycles = ref<RecycleRecord[]>([])
  const loading = ref(false)

  // Load from localStorage on init
  const storedRecycles = localStorage.getItem('recentRecycles')
  if (storedRecycles) {
    try {
      recentRecycles.value = JSON.parse(storedRecycles)
    } catch {
      recentRecycles.value = []
    }
  }

  async function recycleVideo(videoId: number, metadata: RecycleMetadata): Promise<void> {
    loading.value = true

    try {
      // Create a new video based on recycled content
      await videoApi.create({
        title: metadata.title,
        description: metadata.description,
        tags: metadata.tags,
        category: metadata.category,
      })

      // Track recycle in recent list
      const record: RecycleRecord = {
        originalVideoId: videoId,
        originalTitle: metadata.title,
        recycledAt: new Date().toISOString(),
        platforms: metadata.platforms,
      }

      recentRecycles.value.unshift(record)

      // Keep only last 10 recycles
      if (recentRecycles.value.length > 10) {
        recentRecycles.value = recentRecycles.value.slice(0, 10)
      }

      // Persist to localStorage
      localStorage.setItem('recentRecycles', JSON.stringify(recentRecycles.value))
    } catch (error) {
      useNotificationStore().error('리사이클 처리 중 오류가 발생했습니다')
      throw error
    } finally {
      loading.value = false
    }
  }

  function clearRecycleHistory() {
    recentRecycles.value = []
    localStorage.removeItem('recentRecycles')
  }

  return {
    recentRecycles,
    loading,
    recycleVideo,
    clearRecycleHistory,
  }
})
