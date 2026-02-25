import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { SubtitleTrack, VideoForSubtitle, SubtitleLanguage, SubtitleFormat } from '@/types/subtitleEditor'
import { subtitleEditorApi } from '@/api/subtitleEditor'

export const useSubtitleEditorStore = defineStore('subtitleEditor', () => {
  const videos = ref<VideoForSubtitle[]>([])
  const tracks = ref<SubtitleTrack[]>([])
  const selectedVideo = ref<VideoForSubtitle | null>(null)
  const selectedTrack = ref<SubtitleTrack | null>(null)
  const processing = ref(false)
  const error = ref<string | null>(null)

  const selectedVideoTracks = computed(() =>
    selectedVideo.value ? tracks.value.filter(t => t.videoId === selectedVideo.value!.id) : tracks.value,
  )

  const totalCues = computed(() => selectedTrack.value?.cues.length ?? 0)

  async function fetchVideos() {
    processing.value = true
    error.value = null
    try {
      videos.value = await subtitleEditorApi.getVideos()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load videos'
    } finally {
      processing.value = false
    }
  }

  async function fetchTracks() {
    try {
      tracks.value = await subtitleEditorApi.getTracks(selectedVideo.value?.id)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to load tracks'
    }
  }

  async function generateSubtitles(language: SubtitleLanguage, translateTo?: SubtitleLanguage, speakerDiarization = false) {
    if (!selectedVideo.value) return
    processing.value = true
    error.value = null
    try {
      const result = await subtitleEditorApi.generateSubtitles({
        videoId: selectedVideo.value.id,
        language,
        translateTo,
        speakerDiarization,
      })
      tracks.value.unshift(result.track)
      selectedTrack.value = result.track
      return result
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to generate subtitles'
      throw e
    } finally {
      processing.value = false
    }
  }

  async function updateCue(cueId: string, text: string, startTime?: number, endTime?: number) {
    if (!selectedTrack.value) return
    try {
      const updatedTrack = await subtitleEditorApi.updateCue({
        trackId: selectedTrack.value.id,
        cueId,
        text,
        startTime,
        endTime,
      })
      selectedTrack.value = updatedTrack
      const idx = tracks.value.findIndex(t => t.id === updatedTrack.id)
      if (idx >= 0) tracks.value[idx] = updatedTrack
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to update cue'
    }
  }

  async function deleteCue(cueId: string) {
    if (!selectedTrack.value) return
    try {
      await subtitleEditorApi.deleteCue(selectedTrack.value.id, cueId)
      selectedTrack.value = {
        ...selectedTrack.value,
        cues: selectedTrack.value.cues.filter(c => c.id !== cueId),
      }
      const idx = tracks.value.findIndex(t => t.id === selectedTrack.value!.id)
      if (idx >= 0) tracks.value[idx] = selectedTrack.value!
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to delete cue'
    }
  }

  async function exportSubtitles(format: SubtitleFormat) {
    if (!selectedTrack.value) return
    try {
      const response = await subtitleEditorApi.exportSubtitles({
        trackId: selectedTrack.value.id,
        format,
      })
      const blob = response.data
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${selectedTrack.value.videoTitle}_${selectedTrack.value.language}.${format.toLowerCase()}`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to export subtitles'
    }
  }

  async function deleteTrack(trackId: number) {
    try {
      await subtitleEditorApi.deleteTrack(trackId)
      tracks.value = tracks.value.filter(t => t.id !== trackId)
      if (selectedTrack.value?.id === trackId) {
        selectedTrack.value = null
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Failed to delete track'
    }
  }

  function selectVideo(video: VideoForSubtitle | null) {
    selectedVideo.value = video
    selectedTrack.value = null
  }

  function selectTrack(track: SubtitleTrack | null) {
    selectedTrack.value = track
  }

  function clearError() {
    error.value = null
  }

  return {
    videos,
    tracks,
    selectedVideo,
    selectedTrack,
    processing,
    error,
    selectedVideoTracks,
    totalCues,
    fetchVideos,
    fetchTracks,
    generateSubtitles,
    updateCue,
    deleteCue,
    exportSubtitles,
    deleteTrack,
    selectVideo,
    selectTrack,
    clearError,
  }
})
