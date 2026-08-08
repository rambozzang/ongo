import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface FavoriteItem {
  videoId: number
  addedAt: string
}

const MAX_FAVORITES = 50

export const useFavoritesStore = defineStore('favorites', () => {
  const favoriteItems = ref<FavoriteItem[]>([])

  // Computed: just the IDs for easy lookup
  const favorites = computed(() => favoriteItems.value.map((item) => item.videoId))

  // Computed: count
  const favoriteCount = computed(() => favoriteItems.value.length)

  // Check if video is favorited
  function isFavorite(videoId: number): boolean {
    return favoriteItems.value.some((item) => item.videoId === videoId)
  }

  // Toggle favorite status
  function toggleFavorite(videoId: number): boolean {
    const index = favoriteItems.value.findIndex((item) => item.videoId === videoId)

    if (index !== -1) {
      // Remove from favorites
      favoriteItems.value.splice(index, 1)
      return false
    } else {
      // Add to favorites (check limit)
      if (favoriteItems.value.length >= MAX_FAVORITES) {
        throw new Error(`최대 ${MAX_FAVORITES}개까지만 즐겨찾기할 수 있습니다`)
      }

      favoriteItems.value.unshift({
        videoId,
        addedAt: new Date().toISOString(),
      })
      return true
    }
  }

  // Remove a favorite
  function removeFavorite(videoId: number): void {
    const index = favoriteItems.value.findIndex((item) => item.videoId === videoId)
    if (index !== -1) {
      favoriteItems.value.splice(index, 1)
    }
  }

  // Clear all favorites
  function clearFavorites(): void {
    favoriteItems.value = []
  }

  return {
    favoriteItems,
    favorites,
    favoriteCount,
    isFavorite,
    toggleFavorite,
    removeFavorite,
    clearFavorites,
  }
})
