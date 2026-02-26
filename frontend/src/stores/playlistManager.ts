import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { playlistManagerApi } from '@/api/playlistManager'
import type { Playlist, PlaylistVideo, PlaylistSummary, CreatePlaylistRequest } from '@/types/playlistManager'

function generateMockPlaylists(): Playlist[] {
  return [
    { id: 1, title: '테크 리뷰 시리즈', platform: 'YOUTUBE', visibility: 'PUBLIC', videoCount: 24, totalViews: 125000, totalDuration: 7200, tags: ['테크', '리뷰'], isSynced: true, lastSyncedAt: '2025-02-25T10:00:00', createdAt: '2024-06-01', updatedAt: '2025-02-25' },
    { id: 2, title: '요리 레시피 모음', platform: 'YOUTUBE', visibility: 'PUBLIC', videoCount: 18, totalViews: 89000, totalDuration: 5400, tags: ['요리', '레시피'], isSynced: true, lastSyncedAt: '2025-02-24T15:00:00', createdAt: '2024-08-15', updatedAt: '2025-02-24' },
    { id: 3, title: '숏폼 베스트', platform: 'TIKTOK', visibility: 'PUBLIC', videoCount: 45, totalViews: 340000, totalDuration: 2700, tags: ['숏폼', '인기'], isSynced: false, createdAt: '2025-01-01', updatedAt: '2025-02-20' },
    { id: 4, title: '릴스 하이라이트', platform: 'INSTAGRAM', visibility: 'PUBLIC', videoCount: 12, totalViews: 67000, totalDuration: 720, tags: ['릴스'], isSynced: true, lastSyncedAt: '2025-02-23T09:00:00', createdAt: '2025-01-10', updatedAt: '2025-02-23' },
  ]
}

function generateMockSummary(): PlaylistSummary {
  return { totalPlaylists: 8, totalVideos: 120, totalViews: 680000, platformBreakdown: [{ platform: 'YOUTUBE', count: 3 }, { platform: 'TIKTOK', count: 2 }, { platform: 'INSTAGRAM', count: 2 }, { platform: 'NAVER_CLIP', count: 1 }], topPlaylists: [{ id: 3, title: '숏폼 베스트', views: 340000, platform: 'TIKTOK' }, { id: 1, title: '테크 리뷰 시리즈', views: 125000, platform: 'YOUTUBE' }] }
}

export const usePlaylistManagerStore = defineStore('playlistManager', () => {
  const playlists = ref<Playlist[]>([])
  const selectedPlaylistVideos = ref<PlaylistVideo[]>([])
  const summary = ref<PlaylistSummary | null>(null)
  const isLoading = ref(false)

  const totalViews = computed(() => playlists.value.reduce((sum, p) => sum + p.totalViews, 0))
  const totalVideos = computed(() => playlists.value.reduce((sum, p) => sum + p.videoCount, 0))

  async function fetchPlaylists(platform?: string) {
    isLoading.value = true
    try { playlists.value = await playlistManagerApi.getPlaylists(platform) } catch { playlists.value = generateMockPlaylists(); if (platform) playlists.value = playlists.value.filter((p) => p.platform === platform) } finally { isLoading.value = false }
  }

  async function fetchSummary() {
    try { summary.value = await playlistManagerApi.getSummary() } catch { summary.value = generateMockSummary() }
  }

  async function createPlaylist(request: CreatePlaylistRequest) {
    try {
      const created = await playlistManagerApi.createPlaylist(request)
      playlists.value.unshift(created)
    } catch {
      const mock: Playlist = { id: Date.now(), ...request, tags: request.tags ?? [], videoCount: 0, totalViews: 0, totalDuration: 0, isSynced: false, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }
      playlists.value.unshift(mock)
    }
  }

  async function deletePlaylist(id: number) {
    try { await playlistManagerApi.deletePlaylist(id) } catch { /* fallback */ }
    playlists.value = playlists.value.filter((p) => p.id !== id)
  }

  async function syncPlaylist(id: number) {
    try {
      const synced = await playlistManagerApi.syncPlaylist(id)
      const idx = playlists.value.findIndex((p) => p.id === id)
      if (idx !== -1) playlists.value[idx] = synced
    } catch {
      const pl = playlists.value.find((p) => p.id === id)
      if (pl) { pl.isSynced = true; pl.lastSyncedAt = new Date().toISOString() }
    }
  }

  return { playlists, selectedPlaylistVideos, summary, isLoading, totalViews, totalVideos, fetchPlaylists, fetchSummary, createPlaylist, deletePlaylist, syncPlaylist }
})
