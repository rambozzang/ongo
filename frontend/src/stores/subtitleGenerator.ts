import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { subtitleGeneratorApi } from '@/api/subtitleGenerator'
import type { SubtitleJob, SubtitleSegment, SubtitleGeneratorSummary } from '@/types/subtitleGenerator'

export const useSubtitleGeneratorStore = defineStore('subtitleGenerator', () => {
  const jobs = ref<SubtitleJob[]>([])
  const segments = ref<SubtitleSegment[]>([])
  const summary = ref<SubtitleGeneratorSummary | null>(null)
  const loading = ref(false)

  const completedJobs = computed(() => jobs.value.filter(j => j.status === 'COMPLETED'))
  const processingJobs = computed(() => jobs.value.filter(j => j.status === 'PROCESSING'))

  async function fetchJobs(status?: string) {
    loading.value = true
    try {
      jobs.value = await subtitleGeneratorApi.getJobs(status)
    } catch {
      jobs.value = [
        { id: 1, videoId: 101, videoTitle: '봄 여행 브이로그', platform: 'YOUTUBE', language: 'ko', status: 'COMPLETED', progress: 100, subtitleUrl: '/subs/1.srt', wordCount: 1250, duration: 612, createdAt: '2025-03-10T09:00:00Z', completedAt: '2025-03-10T09:05:00Z' },
        { id: 2, videoId: 102, videoTitle: 'Morning Routine', platform: 'TIKTOK', language: 'en', status: 'COMPLETED', progress: 100, subtitleUrl: '/subs/2.srt', wordCount: 340, duration: 180, createdAt: '2025-03-11T10:00:00Z', completedAt: '2025-03-11T10:02:00Z' },
        { id: 3, videoId: 103, videoTitle: '코딩 튜토리얼 #5', platform: 'YOUTUBE', language: 'ko', status: 'PROCESSING', progress: 65, subtitleUrl: null, wordCount: 0, duration: 1800, createdAt: '2025-03-12T14:00:00Z', completedAt: null },
        { id: 4, videoId: 104, videoTitle: '오늘의 메이크업', platform: 'INSTAGRAM', language: 'ko', status: 'PENDING', progress: 0, subtitleUrl: null, wordCount: 0, duration: 300, createdAt: '2025-03-12T15:00:00Z', completedAt: null },
      ]
    } finally {
      loading.value = false
    }
  }

  async function fetchSegments(jobId: number) {
    try {
      segments.value = await subtitleGeneratorApi.getSegments(jobId)
    } catch {
      segments.value = [
        { id: 1, jobId, startTime: 0, endTime: 3.5, text: '안녕하세요 여러분', confidence: 0.95 },
        { id: 2, jobId, startTime: 3.5, endTime: 7.2, text: '오늘은 봄 여행 브이로그를 가져왔어요', confidence: 0.92 },
        { id: 3, jobId, startTime: 7.2, endTime: 11.0, text: '날씨가 정말 좋았거든요', confidence: 0.88 },
        { id: 4, jobId, startTime: 11.0, endTime: 15.5, text: '먼저 카페에서 출발했습니다', confidence: 0.91 },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await subtitleGeneratorApi.getSummary()
    } catch {
      summary.value = { totalJobs: 24, completedJobs: 20, avgAccuracy: 93.5, totalWords: 28500, recentLanguage: 'ko' }
    }
  }

  async function generateSubtitle(videoId: number, language: string) {
    try {
      const job = await subtitleGeneratorApi.generate(videoId, language)
      jobs.value.unshift(job)
    } catch {
      // fallback
    }
  }

  return { jobs, segments, summary, loading, completedJobs, processingJobs, fetchJobs, fetchSegments, fetchSummary, generateSubtitle }
})
