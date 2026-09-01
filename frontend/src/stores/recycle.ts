import { defineStore } from 'pinia'
import { ref } from 'vue'
import { videoApi } from '@/api/video'
import { useNotificationStore } from '@/stores/notification'
import type { UploadStatus } from '@/types/video'

export interface RecycleMetadata {
  title: string
  description: string
  tags: string[]
  category: string
  platforms: string[]
  scheduledAt?: string
}

/**
 * 서버가 접수한 플랫폼 하나의 상태.
 *
 * `status` 는 백엔드 `UploadStatus` 를 그대로 받는다. 지금 `RecycleVideoUseCase` 는
 * `PublishVideoUseCase` 에 위임하고 그쪽이 모든 플랫폼을 `UPLOADING` 으로 돌려주지만
 * (실제 게시는 `VideoPublishEvent` 로 비동기 처리된다), **화면이 그 사실에 기대지 않도록**
 * 서버가 준 값을 그대로 들고 다닌다.
 */
export interface RecycleUploadOutcome {
  platform: string
  status: UploadStatus | string
  errorMessage?: string
}

export interface RecycleResult {
  videoId: number
  uploads: RecycleUploadOutcome[]
}

export const useRecycleStore = defineStore('recycle', () => {
  const loading = ref(false)

  /**
   * 재게시를 서버에 접수하고 **그 응답을 그대로 호출자에게 돌려준다.**
   *
   * 예전에는 `Promise<void>` 였다. 서버는 플랫폼별 `status` 와 `errorMessage` 를 주는데
   * 여기서 버려졌고, 화면은 판단 근거 없이 "재게시되었습니다" 를 단언했다. 어떤 플랫폼이
   * 거절됐는지는 어디에도 남지 않았다.
   */
  async function recycleVideo(videoId: number, metadata: RecycleMetadata): Promise<RecycleResult> {
    loading.value = true

    try {
      // The server clones the source media and creates independent upload records.
      return await videoApi.recycle(videoId, {
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
