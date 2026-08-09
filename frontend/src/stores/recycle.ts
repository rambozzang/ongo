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
}

export const useRecycleStore = defineStore('recycle', () => {
  const loading = ref(false)

  async function recycleVideo(videoId: number, metadata: RecycleMetadata): Promise<void> {
    loading.value = true

    try {
      // The server clones the source media and creates independent upload records.
      await videoApi.recycle(videoId, {
        title: metadata.title,
        description: metadata.description,
        tags: metadata.tags,
        category: metadata.category,
        platforms: metadata.platforms.map((platform) => ({
          platform: platform.split('#', 1)[0],
          channelId: Number(platform.split('#')[1]) || undefined,
          scheduledAt: metadata.scheduledAt,
        })),
      })

    } catch (error) {
      useNotificationStore().error('리사이클 처리 중 오류가 발생했습니다')
      throw error
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    recycleVideo,
  }
})
