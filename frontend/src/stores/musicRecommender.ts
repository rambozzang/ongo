import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { musicRecommenderApi } from '@/api/musicRecommender'
import type { MusicRecommendation, MusicRecommenderSummary } from '@/types/musicRecommender'

export const useMusicRecommenderStore = defineStore('musicRecommender', () => {
  const recommendations = ref<MusicRecommendation[]>([])
  const summary = ref<MusicRecommenderSummary | null>(null)
  const loading = ref(false)

  const appliedRecommendations = computed(() => recommendations.value.filter(r => r.status === 'APPLIED'))

  async function fetchRecommendations() {
    loading.value = true
    try {
      recommendations.value = await musicRecommenderApi.getRecommendations()
    } catch {
      recommendations.value = [
        { id: 1, videoId: 101, videoTitle: '봄 여행 브이로그', tracks: [
          { id: 1, title: 'Spring Morning', artist: 'Nature Beats', genre: 'Acoustic', mood: '밝은', bpm: 110, duration: 180, previewUrl: null, licenseType: 'FREE', matchScore: 95, createdAt: '2025-03-10' },
          { id: 2, title: 'Sunny Day Walk', artist: 'Chill Vibes', genre: 'Lo-fi', mood: '편안한', bpm: 90, duration: 210, previewUrl: null, licenseType: 'STANDARD', matchScore: 88, createdAt: '2025-03-10' },
        ], selectedTrackId: 1, status: 'APPLIED', createdAt: '2025-03-10T09:00:00Z' },
        { id: 2, videoId: 102, videoTitle: '코딩 튜토리얼', tracks: [
          { id: 3, title: 'Focus Time', artist: 'Deep Work', genre: 'Electronic', mood: '집중', bpm: 120, duration: 240, previewUrl: null, licenseType: 'FREE', matchScore: 92, createdAt: '2025-03-11' },
          { id: 4, title: 'Digital Flow', artist: 'Tech Sound', genre: 'Ambient', mood: '차분한', bpm: 85, duration: 300, previewUrl: null, licenseType: 'PREMIUM', matchScore: 87, createdAt: '2025-03-11' },
        ], selectedTrackId: null, status: 'RECOMMENDED', createdAt: '2025-03-11T10:00:00Z' },
        { id: 3, videoId: 103, videoTitle: '운동 루틴', tracks: [
          { id: 5, title: 'Power Up', artist: 'Gym Beats', genre: 'EDM', mood: '에너지', bpm: 140, duration: 200, previewUrl: null, licenseType: 'STANDARD', matchScore: 96, createdAt: '2025-03-12' },
        ], selectedTrackId: null, status: 'PENDING', createdAt: '2025-03-12T14:00:00Z' },
      ]
    } finally {
      loading.value = false
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await musicRecommenderApi.getSummary()
    } catch {
      summary.value = { totalRecommendations: 45, appliedTracks: 32, topGenre: 'Lo-fi', topMood: '편안한', avgMatchScore: 89.5 }
    }
  }

  return { recommendations, summary, loading, appliedRecommendations, fetchRecommendations, fetchSummary }
})
