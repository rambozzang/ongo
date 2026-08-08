import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { favoritesApi } from '@/api/favorites'

export interface FavoriteItem {
  videoId: number
  addedAt: string
}

const MAX_FAVORITES = 50

export const useFavoritesStore = defineStore('favorites', () => {
  const favoriteItems = ref<FavoriteItem[]>([])
  const loaded = ref(false)
  const loading = ref(false)

  // Computed: just the IDs for easy lookup
  const favorites = computed(() => favoriteItems.value.map((item) => item.videoId))

  // Computed: count
  const favoriteCount = computed(() => favoriteItems.value.length)

  // Check if video is favorited
  function isFavorite(videoId: number): boolean {
    return favoriteItems.value.some((item) => item.videoId === videoId)
  }

  // Toggle favorite status
  async function ensureLoaded(): Promise<void> {
    if (loaded.value || loading.value) return
    loading.value = true
    try {
      const ids = await favoritesApi.list()
      favoriteItems.value = ids.slice(0, MAX_FAVORITES).map((videoId) => ({ videoId, addedAt: '' }))
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function toggleFavorite(videoId: number): Promise<boolean> {
    await ensureLoaded()
    if (!isFavorite(videoId) && favoriteItems.value.length >= MAX_FAVORITES) {
      throw new Error(`최대 ${MAX_FAVORITES}개까지만 즐겨찾기할 수 있습니다`)
    }
    const result = await favoritesApi.toggle(videoId)
    const index = favoriteItems.value.findIndex((item) => item.videoId === videoId)
    if (result.favorite && index === -1) {
      favoriteItems.value.unshift({ videoId, addedAt: new Date().toISOString() })
    } else if (!result.favorite && index !== -1) {
      favoriteItems.value.splice(index, 1)
    }
    return result.favorite
  }

  // Remove a favorite
  async function removeFavorite(videoId: number): Promise<void> {
    await favoritesApi.remove(videoId)
    const index = favoriteItems.value.findIndex((item) => item.videoId === videoId)
    if (index !== -1) {
      favoriteItems.value.splice(index, 1)
    }
  }

  // Clear all favorites
  async function clearFavorites(): Promise<void> {
    await favoritesApi.removeAll()
    favoriteItems.value = []
  }

  return {
    favoriteItems,
    favorites,
    favoriteCount,
    loaded,
    loading,
    ensureLoaded,
    isFavorite,
    toggleFavorite,
    removeFavorite,
    clearFavorites,
  }
})
