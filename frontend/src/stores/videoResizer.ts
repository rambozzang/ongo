import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ResizeJob, VideoForResize, PlatformTarget } from '@/types/videoResizer'
import { videoResizerApi } from '@/api/videoResizer'

export const useVideoResizerStore = defineStore('videoResizer', () => {
  const videos = ref<VideoForResize[]>([])
  const jobs = ref<ResizeJob[]>([])
  const selectedVideo = ref<VideoForResize | null>(null)
  const processing = ref(false)
  const error = ref<string | null>(null)

  const activeJobs = computed(() => jobs.value.filter(j => j.status === 'PROCESSING' || j.status === 'PENDING'))
  const completedJobs = computed(() => jobs.value.filter(j => j.status === 'COMPLETED'))
  const selectedVideoJobs = computed(() =>
    selectedVideo.value ? jobs.value.filter(j => j.videoId === selectedVideo.value!.id) : jobs.value,
  )

  async function fetchVideos() {
    processing.value = true
    error.value = null
    try {
      videos.value = await videoResizerApi.getVideos()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load videos'
    } finally {
      processing.value = false
    }
  }

  async function fetchJobs() {
    try {
      jobs.value = await videoResizerApi.getJobs(selectedVideo.value?.id)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load jobs'
    }
  }

  async function createResize(targets: { platform: PlatformTarget; aspectRatio: string; smartCrop: boolean }[]) {
    if (!selectedVideo.value) return
    processing.value = true
    error.value = null
    try {
      const result = await videoResizerApi.createResize({
        videoId: selectedVideo.value.id,
        targets: targets as any,
      })
      jobs.value.unshift(...result.jobs)
      return result
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to create resize'
      throw e
    } finally {
      processing.value = false
    }
  }

  async function cancelJob(jobId: number) {
    try {
      await videoResizerApi.cancelJob(jobId)
      const job = jobs.value.find(j => j.id === jobId)
      if (job) job.status = 'FAILED'
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to cancel job'
    }
  }

  function selectVideo(video: VideoForResize | null) {
    selectedVideo.value = video
  }

  return {
    videos,
    jobs,
    selectedVideo,
    processing,
    error,
    activeJobs,
    completedJobs,
    selectedVideoJobs,
    fetchVideos,
    fetchJobs,
    createResize,
    cancelJob,
    selectVideo,
  }
})
