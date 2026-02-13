<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="isOpen"
        class="fixed inset-0 z-[100] flex items-start justify-center bg-black/50 p-4 pt-[20vh] backdrop-blur-sm"
        role="dialog"
        aria-modal="true"
        aria-label="명령 팔레트"
        @click="closeModal"
        @keydown.esc="closeModal"
      >
        <Transition
          enter-active-class="transition duration-200 ease-out"
          enter-from-class="opacity-0 translate-y-4"
          enter-to-class="opacity-100 translate-y-0"
          leave-active-class="transition duration-150 ease-in"
          leave-from-class="opacity-100 translate-y-0"
          leave-to-class="opacity-0 translate-y-4"
        >
          <div
            v-if="isOpen"
            class="w-full max-w-[560px] overflow-hidden rounded-xl border border-gray-200/50 bg-white/80 shadow-2xl backdrop-blur-xl dark:border-gray-700/50 dark:bg-gray-900/80"
            @click.stop
          >
            <!-- Search Input -->
            <div class="relative border-b border-gray-200/50 dark:border-gray-700/50">
              <MagnifyingGlassIcon
                class="absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400"
              />
              <input
                ref="searchInput"
                v-model="searchQuery"
                type="text"
                placeholder="검색..."
                role="combobox"
                aria-autocomplete="list"
                aria-label="명령어 검색"
                :aria-expanded="filteredItems.length > 0"
                class="w-full bg-transparent py-4 pl-12 pr-24 text-sm text-gray-900 placeholder-gray-400 outline-none dark:text-gray-100"
                @keydown.down.prevent="selectNext"
                @keydown.up.prevent="selectPrevious"
                @keydown.enter.prevent="executeSelected"
              />
              <div
                class="absolute right-4 top-1/2 -translate-y-1/2 text-xs text-gray-400"
              >
                <kbd
                  class="rounded border border-gray-300 bg-gray-100 px-2 py-1 font-mono dark:border-gray-600 dark:bg-gray-800"
                >
                  {{ isMac ? '⌘K' : 'Ctrl+K' }}
                </kbd>
              </div>
            </div>

            <!-- Results List -->
            <div class="max-h-[400px] overflow-y-auto py-2">
              <div v-if="filteredItems.length === 0" class="px-4 py-8 text-center">
                <p class="text-sm text-gray-500 dark:text-gray-400">
                  검색 결과가 없습니다
                </p>
              </div>

              <template v-else>
                <div
                  v-for="(group, groupName) in groupedItems"
                  :key="groupName"
                  class="mb-2"
                >
                  <div
                    class="px-4 py-2 text-xs font-semibold uppercase tracking-wider text-gray-400"
                  >
                    {{ groupLabels[groupName as string] || groupName }}
                  </div>
                  <div>
                    <button
                      v-for="item in group"
                      :key="item.id"
                      class="flex w-full items-center gap-3 px-4 py-2.5 text-left transition-colors"
                      :class="
                        selectedIndex === getGlobalIndex(item)
                          ? 'bg-primary-50 text-primary-700 dark:bg-primary-900/20 dark:text-primary-300'
                          : 'text-gray-700 hover:bg-gray-50 dark:text-gray-300 dark:hover:bg-gray-800/50'
                      "
                      @click="executeItem(item)"
                      @mouseenter="selectedIndex = getGlobalIndex(item)"
                    >
                      <component
                        :is="item.icon"
                        class="h-5 w-5 shrink-0"
                        :class="
                          selectedIndex === getGlobalIndex(item)
                            ? 'text-primary-600 dark:text-primary-400'
                            : 'text-gray-400'
                        "
                      />
                      <span class="flex-1 text-sm">{{ item.label }}</span>
                      <kbd
                        v-if="item.shortcut"
                        class="rounded border border-gray-300 bg-gray-100 px-2 py-0.5 text-xs font-mono text-gray-600 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-400"
                      >
                        {{ item.shortcut }}
                      </kbd>
                    </button>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import {
  MagnifyingGlassIcon,
  HomeIcon,
  FilmIcon,
  ArrowUpTrayIcon,
  ChartBarIcon,
  SparklesIcon,
  LinkIcon,
  CalendarIcon,
  Cog6ToothIcon,
  CreditCardIcon,
  PlusIcon,
  SunIcon,
  MoonIcon,
  ComputerDesktopIcon,
} from '@heroicons/vue/24/outline'

interface CommandItem {
  id: string
  label: string
  icon: Component
  group: 'navigation' | 'actions' | 'settings'
  action: () => void
  keywords?: string[]
  shortcut?: string
}

const router = useRouter()
const themeStore = useThemeStore()

const isOpen = ref(false)
const searchQuery = ref('')
const selectedIndex = ref(0)
const searchInput = ref<HTMLInputElement | null>(null)

const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0

// Define all command items
const items = computed<CommandItem[]>(() => [
  // Navigation
  {
    id: 'nav-dashboard',
    label: '대시보드',
    icon: HomeIcon,
    group: 'navigation',
    action: () => navigateTo('/dashboard'),
    keywords: ['dashboard', 'home', '홈'],
  },
  {
    id: 'nav-videos',
    label: '영상 관리',
    icon: FilmIcon,
    group: 'navigation',
    action: () => navigateTo('/videos'),
    keywords: ['videos', 'video', '비디오', '영상'],
  },
  {
    id: 'nav-upload',
    label: '영상 업로드',
    icon: ArrowUpTrayIcon,
    group: 'navigation',
    action: () => navigateTo('/upload'),
    keywords: ['upload', '업로드', '올리기'],
  },
  {
    id: 'nav-analytics',
    label: '분석',
    icon: ChartBarIcon,
    group: 'navigation',
    action: () => navigateTo('/analytics'),
    keywords: ['analytics', 'stats', '통계', '분석'],
  },
  {
    id: 'nav-ai',
    label: 'AI 도구',
    icon: SparklesIcon,
    group: 'navigation',
    action: () => navigateTo('/ai'),
    keywords: ['ai', '인공지능', '도구'],
  },
  {
    id: 'nav-channels',
    label: '채널 관리',
    icon: LinkIcon,
    group: 'navigation',
    action: () => navigateTo('/channels'),
    keywords: ['channels', 'channel', '채널'],
  },
  {
    id: 'nav-schedules',
    label: '예약 관리',
    icon: CalendarIcon,
    group: 'navigation',
    action: () => navigateTo('/schedule'),
    keywords: ['schedule', 'calendar', '예약', '스케줄'],
  },
  {
    id: 'nav-settings',
    label: '설정',
    icon: Cog6ToothIcon,
    group: 'navigation',
    action: () => navigateTo('/settings'),
    keywords: ['settings', '설정', '환경설정'],
  },
  {
    id: 'nav-subscription',
    label: '구독/결제',
    icon: CreditCardIcon,
    group: 'navigation',
    action: () => navigateTo('/subscription'),
    keywords: ['subscription', 'payment', '구독', '결제', '요금'],
  },

  // Actions
  {
    id: 'action-new-upload',
    label: '새 영상 업로드',
    icon: PlusIcon,
    group: 'actions',
    action: () => navigateTo('/upload'),
    keywords: ['new', 'upload', '새로운', '업로드'],
  },
  {
    id: 'action-ai-title',
    label: 'AI 제목 생성',
    icon: SparklesIcon,
    group: 'actions',
    action: () => navigateTo('/ai'),
    keywords: ['ai', 'title', '제목', '생성'],
  },

  // Settings
  {
    id: 'setting-theme-light',
    label: themeStore.isDark ? '라이트 모드로 전환' : '다크 모드로 전환',
    icon: themeStore.isDark ? SunIcon : MoonIcon,
    group: 'settings',
    action: () => toggleTheme(),
    keywords: ['theme', 'dark', 'light', '테마', '다크', '라이트', '모드'],
  },
  {
    id: 'setting-theme-system',
    label: '시스템 테마 사용',
    icon: ComputerDesktopIcon,
    group: 'settings',
    action: () => setSystemTheme(),
    keywords: ['theme', 'system', '테마', '시스템'],
  },
])

// Recent items stored in localStorage
const recentItems = ref<string[]>([])

const RECENT_ITEMS_KEY = 'ongo-command-palette-recent'
const MAX_RECENT_ITEMS = 5

// Filter items based on search query
const filteredItems = computed(() => {
  if (!searchQuery.value.trim()) {
    // Show recent items when search is empty
    const recentIds = recentItems.value
    const recent = items.value.filter((item) => recentIds.includes(item.id))
    // If no recent items, show all navigation items
    return recent.length > 0 ? recent : items.value.filter((item) => item.group === 'navigation')
  }

  const query = searchQuery.value.toLowerCase()
  return items.value.filter((item) => {
    const labelMatch = item.label.toLowerCase().includes(query)
    const keywordMatch = item.keywords?.some((keyword) =>
      keyword.toLowerCase().includes(query),
    )
    return labelMatch || keywordMatch
  })
})

// Group filtered items
const groupedItems = computed(() => {
  const groups: Record<string, CommandItem[]> = {
    navigation: [],
    actions: [],
    settings: [],
  }

  filteredItems.value.forEach((item) => {
    groups[item.group].push(item)
  })

  // Remove empty groups
  Object.keys(groups).forEach((key) => {
    if (groups[key].length === 0) {
      delete groups[key]
    }
  })

  return groups
})

// Get the group label in Korean
const groupLabels: Record<string, string> = {
  navigation: '페이지',
  actions: '작업',
  settings: '설정',
}

// Get global index for an item in the filtered list
function getGlobalIndex(item: CommandItem): number {
  return filteredItems.value.findIndex((i) => i.id === item.id)
}

// Navigation helpers
function selectNext() {
  if (filteredItems.value.length === 0) return
  selectedIndex.value = (selectedIndex.value + 1) % filteredItems.value.length
}

function selectPrevious() {
  if (filteredItems.value.length === 0) return
  selectedIndex.value =
    selectedIndex.value <= 0 ? filteredItems.value.length - 1 : selectedIndex.value - 1
}

function executeSelected() {
  const item = filteredItems.value[selectedIndex.value]
  if (item) {
    executeItem(item)
  }
}

function executeItem(item: CommandItem) {
  addToRecent(item.id)
  item.action()
  closeModal()
}

// Actions
function navigateTo(path: string) {
  router.push(path)
}

function toggleTheme() {
  themeStore.setMode(themeStore.isDark ? 'light' : 'dark')
}

function setSystemTheme() {
  themeStore.setMode('system')
}

// Recent items management
function loadRecentItems() {
  try {
    const stored = localStorage.getItem(RECENT_ITEMS_KEY)
    if (stored) {
      recentItems.value = JSON.parse(stored)
    }
  } catch {
    recentItems.value = []
  }
}

function addToRecent(itemId: string) {
  const filtered = recentItems.value.filter((id) => id !== itemId)
  recentItems.value = [itemId, ...filtered].slice(0, MAX_RECENT_ITEMS)
  localStorage.setItem(RECENT_ITEMS_KEY, JSON.stringify(recentItems.value))
}

// Modal controls
function openModal() {
  isOpen.value = true
  nextTick(() => {
    searchInput.value?.focus()
  })
}

function closeModal() {
  isOpen.value = false
  searchQuery.value = ''
  selectedIndex.value = 0
}

// Keyboard shortcut handler
function handleKeydown(event: KeyboardEvent) {
  const isCmdOrCtrl = isMac ? event.metaKey : event.ctrlKey

  if (isCmdOrCtrl && event.key === 'k') {
    event.preventDefault()
    if (isOpen.value) {
      closeModal()
    } else {
      openModal()
    }
  }
}

// Reset selected index when search query changes
watch(searchQuery, () => {
  selectedIndex.value = 0
})

// Lifecycle hooks
onMounted(() => {
  loadRecentItems()
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>
