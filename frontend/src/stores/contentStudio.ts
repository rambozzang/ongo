import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type {
  Clip,
  Caption,
  Thumbnail,
  VideoSummary,
  CreateClipRequest,
  GenerateCaptionRequest,
  GenerateThumbnailRequest,
  UpdateCaptionRequest,
  ContentStudioHistory,
} from '@/types/contentStudio'
import { contentStudioApi } from '@/api/contentStudio'

export const useContentStudioStore = defineStore('contentStudio', () => {
  // State
  const clips = ref<Clip[]>([])
  const captions = ref<Caption[]>([])
  const thumbnails = ref<Thumbnail[]>([])
  const videos = ref<VideoSummary[]>([])
  const selectedVideo = ref<VideoSummary | null>(null)
  const history = ref<ContentStudioHistory[]>([])
  const activeTab = ref<'clips' | 'captions' | 'thumbnails'>('clips')

  const processing = ref(false)
  const loadingVideos = ref(false)
  const loadingHistory = ref(false)
  const error = ref<string | null>(null)

  // Getters
  const selectedVideoClips = computed(() =>
    selectedVideo.value
      ? clips.value.filter(c => c.videoId === selectedVideo.value!.id)
      : clips.value,
  )

  const selectedVideoCaptions = computed(() =>
    selectedVideo.value
      ? captions.value.filter(c => c.videoId === selectedVideo.value!.id)
      : captions.value,
  )

  const selectedVideoThumbnails = computed(() =>
    selectedVideo.value
      ? thumbnails.value.filter(t => t.videoId === selectedVideo.value!.id)
      : thumbnails.value,
  )

  // Actions
  async function fetchVideos() {
    loadingVideos.value = true
    error.value = null
    try {
      videos.value = await contentStudioApi.getVideos()
    } catch (e) {
      error.value = e instanceof Error ? e.message : '영상 목록 로드 실패'
    } finally {
      loadingVideos.value = false
    }
  }

  async function fetchClips() {
    processing.value = true
    error.value = null
    try {
      clips.value = await contentStudioApi.getClips(selectedVideo.value?.id)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '클립 목록 로드 실패'
    } finally {
      processing.value = false
    }
  }

  async function createClip(request: CreateClipRequest) {
    processing.value = true
    error.value = null
    try {
      const result = await contentStudioApi.createClip(request)
      clips.value.unshift(result.clip)
      return result
    } catch (e) {
      error.value = e instanceof Error ? e.message : '클립 생성 실패'
      throw e
    } finally {
      processing.value = false
    }
  }

  async function deleteClip(clipId: number) {
    error.value = null
    try {
      await contentStudioApi.deleteClip(clipId)
      clips.value = clips.value.filter(c => c.id !== clipId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '클립 삭제 실패'
      throw e
    }
  }

  async function fetchCaptions() {
    processing.value = true
    error.value = null
    try {
      captions.value = await contentStudioApi.getCaptions(selectedVideo.value?.id)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '자막 목록 로드 실패'
    } finally {
      processing.value = false
    }
  }

  async function generateCaption(request: GenerateCaptionRequest) {
    processing.value = true
    error.value = null
    try {
      const result = await contentStudioApi.generateCaption(request)
      captions.value.unshift(result.caption)
      return result
    } catch (e) {
      error.value = e instanceof Error ? e.message : '자막 생성 실패'
      throw e
    } finally {
      processing.value = false
    }
  }

  async function updateCaption(captionId: number, request: UpdateCaptionRequest) {
    error.value = null
    try {
      const updated = await contentStudioApi.updateCaption(captionId, request)
      const idx = captions.value.findIndex(c => c.id === captionId)
      if (idx >= 0) captions.value[idx] = updated
      return updated
    } catch (e) {
      error.value = e instanceof Error ? e.message : '자막 수정 실패'
      throw e
    }
  }

  async function deleteCaption(captionId: number) {
    error.value = null
    try {
      await contentStudioApi.deleteCaption(captionId)
      captions.value = captions.value.filter(c => c.id !== captionId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '자막 삭제 실패'
      throw e
    }
  }

  async function fetchThumbnails() {
    processing.value = true
    error.value = null
    try {
      thumbnails.value = await contentStudioApi.getThumbnails(selectedVideo.value?.id)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '썸네일 목록 로드 실패'
    } finally {
      processing.value = false
    }
  }

  async function generateThumbnail(request: GenerateThumbnailRequest) {
    processing.value = true
    error.value = null
    try {
      const result = await contentStudioApi.generateThumbnail(request)
      thumbnails.value.unshift(...result.thumbnails)
      return result
    } catch (e) {
      error.value = e instanceof Error ? e.message : '썸네일 생성 실패'
      throw e
    } finally {
      processing.value = false
    }
  }

  async function deleteThumbnail(thumbnailId: number) {
    error.value = null
    try {
      await contentStudioApi.deleteThumbnail(thumbnailId)
      thumbnails.value = thumbnails.value.filter(t => t.id !== thumbnailId)
    } catch (e) {
      error.value = e instanceof Error ? e.message : '썸네일 삭제 실패'
      throw e
    }
  }

  async function fetchHistory() {
    loadingHistory.value = true
    error.value = null
    try {
      history.value = await contentStudioApi.getHistory()
    } catch (e) {
      error.value = e instanceof Error ? e.message : '히스토리 로드 실패'
    } finally {
      loadingHistory.value = false
    }
  }

  function selectVideo(video: VideoSummary | null) {
    selectedVideo.value = video
  }

  function setActiveTab(tab: 'clips' | 'captions' | 'thumbnails') {
    activeTab.value = tab
  }

  function clearError() {
    error.value = null
  }

  return {
    // State
    clips,
    captions,
    thumbnails,
    videos,
    selectedVideo,
    history,
    activeTab,
    processing,
    loadingVideos,
    loadingHistory,
    error,
    // Getters
    selectedVideoClips,
    selectedVideoCaptions,
    selectedVideoThumbnails,
    // Actions
    fetchVideos,
    fetchClips,
    createClip,
    deleteClip,
    fetchCaptions,
    generateCaption,
    updateCaption,
    deleteCaption,
    fetchThumbnails,
    generateThumbnail,
    deleteThumbnail,
    fetchHistory,
    selectVideo,
    setActiveTab,
    clearError,
  }
})
