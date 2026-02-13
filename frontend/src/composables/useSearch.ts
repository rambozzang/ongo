import { ref, computed, watch, type Component } from 'vue'
import { useRouter } from 'vue-router'
import {
  VideoCameraIcon,
  RectangleGroupIcon,
  Cog6ToothIcon,
  PlusCircleIcon,
  ChartBarIcon,
  SparklesIcon,
  CreditCardIcon,
} from '@heroicons/vue/24/outline'
import { videoApi } from '@/api/video'
import { channelApi } from '@/api/channel'
import { PLATFORM_CONFIG } from '@/types/channel'

export interface SearchResult {
  id: string
  title: string
  subtitle?: string
  category: '영상' | '채널' | '일정' | '설정'
  icon: Component
  route: string
}

export interface QuickAction {
  id: string
  title: string
  icon: Component
  iconColor: string
  route: string
}

// Static settings entries (always available for search)
const settingsEntries = [
  { id: 's1', title: '계정 설정', subtitle: '설정 · 프로필 및 보안', route: '/settings' },
  { id: 's2', title: '채널 연동', subtitle: '설정 · 플랫폼 관리', route: '/channels' },
  { id: 's3', title: '알림 설정', subtitle: '설정 · 알림 및 이메일', route: '/settings' },
  { id: 's4', title: '구독 관리', subtitle: '설정 · 플랜 및 결제', route: '/subscription' },
]

const quickActions: QuickAction[] = [
  { id: '1', title: '새 영상 업로드', icon: PlusCircleIcon, iconColor: 'text-primary-500', route: '/videos/new' },
  { id: '2', title: '분석 보기', icon: ChartBarIcon, iconColor: 'text-blue-500', route: '/analytics' },
  { id: '3', title: 'AI 도구', icon: SparklesIcon, iconColor: 'text-purple-500', route: '/ai' },
  { id: '4', title: '설정', icon: Cog6ToothIcon, iconColor: 'text-gray-500', route: '/settings' },
  { id: '5', title: '구독 관리', icon: CreditCardIcon, iconColor: 'text-green-500', route: '/subscription' },
]

const RECENT_SEARCHES_KEY = 'ongo_recent_searches'
const MAX_RECENT_SEARCHES = 5

let debounceTimer: ReturnType<typeof setTimeout> | null = null

export function useSearch() {
  const router = useRouter()
  const query = ref('')
  const results = ref<SearchResult[]>([])
  const isLoading = ref(false)
  const recentSearches = ref<string[]>([])

  // Load recent searches from localStorage
  const loadRecentSearches = () => {
    try {
      const stored = localStorage.getItem(RECENT_SEARCHES_KEY)
      if (stored) {
        recentSearches.value = JSON.parse(stored)
      }
    } catch (e) {
      console.error('Failed to load recent searches:', e)
      recentSearches.value = []
    }
  }

  // Save recent searches to localStorage
  const saveRecentSearches = () => {
    try {
      localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(recentSearches.value))
    } catch (e) {
      console.error('Failed to save recent searches:', e)
    }
  }

  // Add a search to recent searches
  const addRecentSearch = (searchQuery: string) => {
    if (!searchQuery.trim()) return

    // Remove if already exists
    const filtered = recentSearches.value.filter((q) => q !== searchQuery)

    // Add to beginning
    recentSearches.value = [searchQuery, ...filtered].slice(0, MAX_RECENT_SEARCHES)

    saveRecentSearches()
  }

  // Clear all recent searches
  const clearRecentSearches = () => {
    recentSearches.value = []
    localStorage.removeItem(RECENT_SEARCHES_KEY)
  }

  // Perform search (debounced) — fetches from real APIs
  const performSearch = (searchQuery: string) => {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
    }

    if (!searchQuery.trim()) {
      results.value = []
      isLoading.value = false
      return
    }

    isLoading.value = true

    debounceTimer = setTimeout(async () => {
      const lowerQuery = searchQuery.toLowerCase()
      const searchResults: SearchResult[] = []

      try {
        // Fetch videos and channels from the API in parallel
        const [videosPage, channelsList] = await Promise.all([
          videoApi.list({ keyword: searchQuery, page: 0, size: 10 }).catch(() => null),
          channelApi.list().catch(() => []),
        ])

        // Map video results
        if (videosPage) {
          videosPage.content.forEach((video) => {
            searchResults.push({
              id: String(video.id),
              title: video.title,
              subtitle: `영상 · ${video.createdAt.slice(0, 10)}`,
              category: '영상',
              icon: VideoCameraIcon,
              route: `/videos/${video.id}`,
            })
          })
        }

        // Filter channels client-side (channel API doesn't support keyword search)
        channelsList.forEach((channel) => {
          if (channel.channelName.toLowerCase().includes(lowerQuery)) {
            const platformLabel = PLATFORM_CONFIG[channel.platform]?.label ?? channel.platform
            searchResults.push({
              id: String(channel.id),
              title: channel.channelName,
              subtitle: `채널 · ${platformLabel}`,
              category: '채널',
              icon: RectangleGroupIcon,
              route: '/channels',
            })
          }
        })
      } catch {
        // Silently fall through — still show settings results
      }

      // Search static settings entries
      settingsEntries.forEach((setting) => {
        if (setting.title.toLowerCase().includes(lowerQuery) || setting.subtitle.toLowerCase().includes(lowerQuery)) {
          searchResults.push({
            id: setting.id,
            title: setting.title,
            subtitle: setting.subtitle,
            category: '설정',
            icon: Cog6ToothIcon,
            route: setting.route,
          })
        }
      })

      results.value = searchResults
      isLoading.value = false
    }, 300)
  }

  // Watch query changes
  watch(query, (newQuery) => {
    performSearch(newQuery)
  })

  // Navigate to a result
  const navigateToResult = (result: SearchResult) => {
    addRecentSearch(query.value)
    router.push(result.route)
  }

  // Navigate to quick action
  const navigateToQuickAction = (action: QuickAction) => {
    router.push(action.route)
  }

  // Get quick actions
  const getQuickActions = () => quickActions

  // Group results by category
  const groupedResults = computed(() => {
    const groups: Record<string, SearchResult[]> = {}

    results.value.forEach((result) => {
      if (!groups[result.category]) {
        groups[result.category] = []
      }
      groups[result.category].push(result)
    })

    return groups
  })

  // Initialize
  loadRecentSearches()

  return {
    query,
    results,
    groupedResults,
    isLoading,
    recentSearches,
    quickActions: getQuickActions(),
    addRecentSearch,
    clearRecentSearches,
    navigateToResult,
    navigateToQuickAction,
  }
}
