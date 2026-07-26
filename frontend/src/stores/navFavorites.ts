import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const STORAGE_KEY = 'ongo-nav-favorites'

/** 사이드바 즐겨찾기 최대 개수 */
export const MAX_NAV_FAVORITES = 8

export type NavFavoriteResult = 'added' | 'removed' | 'limit'

/**
 * 네비게이션 즐겨찾기(핀) — 라우트 경로만 localStorage에 영속화한다.
 * 라벨/아이콘은 저장하지 않고 useNavigation 의 정의에서 매번 조회하므로,
 * 라우트가 제거되면 해당 즐겨찾기는 자연스럽게 렌더링에서 제외된다.
 */
export const useNavFavoritesStore = defineStore('navFavorites', () => {
  const favoritePaths = ref<string[]>([])

  // Load from localStorage or use defaults
  function loadFavorites() {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (!stored) {
      favoritePaths.value = []
      return
    }
    try {
      const parsed: unknown = JSON.parse(stored)
      favoritePaths.value = Array.isArray(parsed)
        ? parsed.filter((path): path is string => typeof path === 'string').slice(0, MAX_NAV_FAVORITES)
        : []
    } catch {
      favoritePaths.value = []
    }
  }

  // Save to localStorage
  function saveFavorites() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(favoritePaths.value))
  }

  const favoriteCount = computed(() => favoritePaths.value.length)

  function isFavorite(path: string): boolean {
    return favoritePaths.value.includes(path)
  }

  // Toggle favorite status
  function toggleFavorite(path: string): NavFavoriteResult {
    const index = favoritePaths.value.indexOf(path)
    if (index !== -1) {
      favoritePaths.value.splice(index, 1)
      saveFavorites()
      return 'removed'
    }
    if (favoritePaths.value.length >= MAX_NAV_FAVORITES) {
      return 'limit'
    }
    favoritePaths.value.push(path)
    saveFavorites()
    return 'added'
  }

  function removeFavorite(path: string): void {
    const index = favoritePaths.value.indexOf(path)
    if (index !== -1) {
      favoritePaths.value.splice(index, 1)
      saveFavorites()
    }
  }

  function clearFavorites(): void {
    favoritePaths.value = []
    localStorage.removeItem(STORAGE_KEY)
  }

  // Initialize on store creation
  loadFavorites()

  return {
    favoritePaths,
    favoriteCount,
    isFavorite,
    toggleFavorite,
    removeFavorite,
    clearFavorites,
  }
})
