import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Video, VideoListFilter, VideoCreateRequest, VideoFeedItem } from '@/types/video'
import type { Platform } from '@/types/channel'
import type { PageResponse } from '@/types/api'
import { videoApi } from '@/api/video'

export const useVideoStore = defineStore('video', () => {
  const videos = ref<PageResponse<Video> | null>(null)
  const currentVideo = ref<Video | null>(null)
  const isLoadingList = ref(false)
  const isLoadingDetail = ref(false)
  const listLoadError = ref<string | null>(null)
  const detailLoadError = ref<string | null>(null)
  const filter = ref<VideoListFilter>({})
  const sortField = ref<string>('createdAt')
  const sortDirection = ref<'ASC' | 'DESC'>('DESC')

  // Feed state
  const feedItems = ref<VideoFeedItem[]>([])
  const feedPlatforms = ref<Platform[]>([])
  const feedErrors = ref<string[] | null>(null)
  const feedLoadError = ref(false)
  const isFeedLoading = ref(false)
  const feedFilter = ref<{ platform?: string; sort: string }>({ sort: 'recent' })

  // Backwards-compatible loading ref
  const loading = isLoadingList

  async function fetchVideos(page = 0, size = 20) {
    isLoadingList.value = true
    listLoadError.value = null
    try {
      videos.value = await videoApi.list({
        ...filter.value,
        page,
        size,
        sort: sortField.value,
        direction: sortDirection.value,
      })
    } catch (error) {
      // A transport failure is not an empty library. Keep the last confirmed
      // page so a retry cannot make existing videos appear deleted.
      listLoadError.value = error instanceof Error ? error.message : '영상을 불러오지 못했습니다.'
    } finally {
      isLoadingList.value = false
    }
  }

  async function fetchVideo(id: number) {
    isLoadingDetail.value = true
    detailLoadError.value = null
    currentVideo.value = null
    try {
      currentVideo.value = await videoApi.get(id)
    } catch (error) {
      detailLoadError.value = error instanceof Error ? error.message : '영상 정보를 불러오지 못했습니다.'
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

  async function fetchFeed(page = 0, size = 20) {
    isFeedLoading.value = true
    feedLoadError.value = false
    try {
      const result = await videoApi.feed({
        platform: feedFilter.value.platform,
        page,
        size,
        sort: feedFilter.value.sort,
      })
      feedItems.value = result.items
      feedPlatforms.value = result.platforms
      feedErrors.value = result.errors
    } catch {
      // Do not turn a server outage into a convincing empty feed. Keep the
      // last successful data and let the screen expose a retryable error.
      feedLoadError.value = true
    } finally {
      isFeedLoading.value = false
    }
  }

  function invalidateCache() {
    videos.value = null
  }

  return {
    videos,
    currentVideo,
    loading,
    isLoadingList,
    isLoadingDetail,
    listLoadError,
    detailLoadError,
    filter,
    sortField,
    sortDirection,
    fetchVideos,
    fetchVideo,
    createVideo,
    deleteVideo,
    setFilter,
    setSort,
    invalidateCache,
    feedItems,
    feedPlatforms,
    feedErrors,
    feedLoadError,
    isFeedLoading,
    feedFilter,
    fetchFeed,
  }
})
