import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Video, VideoListFilter, VideoCreateRequest } from '@/types/video'
import type { PageResponse } from '@/types/api'
import { videoApi } from '@/api/video'

export const useVideoStore = defineStore('video', () => {
  const videos = ref<PageResponse<Video> | null>(null)
  const currentVideo = ref<Video | null>(null)
  const isLoadingList = ref(false)
  const isLoadingDetail = ref(false)
  const filter = ref<VideoListFilter>({})
  const sortField = ref<string>('createdAt')
  const sortDirection = ref<'ASC' | 'DESC'>('DESC')

  // Backwards-compatible loading ref
  const loading = isLoadingList

  async function fetchVideos(page = 0, size = 20) {
    isLoadingList.value = true
    try {
      videos.value = await videoApi.list({
        ...filter.value,
        page,
        size,
        sort: sortField.value,
        direction: sortDirection.value,
      })
    } catch {
      // Guest mode or API error - continue with empty videos
      videos.value = { content: [], totalElements: 0, totalPages: 0, page: 0, size, hasNext: false, hasPrevious: false }
    } finally {
      isLoadingList.value = false
    }
  }

  async function fetchVideo(id: number) {
    isLoadingDetail.value = true
    try {
      currentVideo.value = await videoApi.get(id)
    } catch {
      // Guest mode or API error
      currentVideo.value = null
    } finally {
      isLoadingDetail.value = false
    }
  }

  async function createVideo(request: VideoCreateRequest) {
    return await videoApi.create(request)
  }

  async function deleteVideo(id: number) {
    await videoApi.delete(id)
    if (videos.value) {
      videos.value.content = videos.value.content.filter((v) => v.id !== id)
    }
  }

  function setFilter(f: VideoListFilter) {
    filter.value = f
    fetchVideos()
  }

  function setSort(field: string, direction: 'ASC' | 'DESC' = 'DESC') {
    sortField.value = field
    sortDirection.value = direction
    fetchVideos()
  }

  return {
    videos,
    currentVideo,
    loading,
    isLoadingList,
    isLoadingDetail,
    filter,
    sortField,
    sortDirection,
    fetchVideos,
    fetchVideo,
    createVideo,
    deleteVideo,
    setFilter,
    setSort,
  }
})
