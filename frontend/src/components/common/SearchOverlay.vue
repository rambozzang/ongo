<template>
  <Teleport to="body">
    <Transition name="search-overlay">
      <div
        v-if="modelValue"
        class="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto px-4 pt-20 tablet:pt-32"
        role="dialog"
        aria-modal="true"
        aria-label="검색"
        @click.self="close"
      >
        <!-- Backdrop -->
        <div
          class="fixed inset-0 bg-black/50 backdrop-blur-sm transition-opacity"
          @click="close"
        ></div>

        <!-- Search Modal -->
        <div
          class="glass-elevated relative w-full max-w-xl rounded-xl p-0 transition-all"
          @click.stop
        >
          <!-- Search Input -->
          <div class="flex items-center border-b border-gray-200 px-4 py-3 dark:border-gray-700">
            <MagnifyingGlassIcon class="h-5 w-5 text-gray-400" />
            <input
              ref="searchInputRef"
              v-model="search.query.value"
              type="text"
              placeholder="검색어를 입력하세요..."
              role="combobox"
              aria-autocomplete="list"
              aria-label="검색어 입력"
              :aria-expanded="search.results.value.length > 0"
              class="ml-3 flex-1 bg-transparent text-lg text-gray-900 placeholder-gray-400 focus:outline-none dark:text-gray-100 dark:placeholder-gray-500"
              @keydown.down.prevent="moveSelection(1)"
              @keydown.up.prevent="moveSelection(-1)"
              @keydown.enter.prevent="selectCurrent"
              @keydown.esc="close"
            />
            <button
              v-if="search.query.value"
              class="ml-2 rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
              aria-label="검색어 지우기"
              @click="search.query.value = ''"
            >
              <XMarkIcon class="h-4 w-4" />
            </button>
          </div>

          <!-- Results Container -->
          <div class="max-h-96 overflow-y-auto scrollbar-hide">
            <!-- Loading State -->
            <div v-if="search.isLoading.value" class="flex items-center justify-center py-12">
              <div class="h-8 w-8 animate-spin rounded-full border-4 border-gray-200 border-t-primary-500 dark:border-gray-700"></div>
            </div>

            <!-- Search Results -->
            <div v-else-if="search.query.value && search.results.value.length > 0" class="py-2">
              <template v-for="(categoryResults, category) in search.groupedResults.value" :key="category">
                <div class="px-4 py-2">
                  <h3 class="text-xs font-semibold uppercase text-gray-500 dark:text-gray-400">
                    {{ category }}
                  </h3>
                </div>
                <div>
                  <button
                    v-for="(result, index) in categoryResults"
                    :key="result.id"
                    :ref="(el) => setResultRef(el as HTMLElement, getCategoryIndex(category, index))"
                    class="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors"
                    :class="selectedIndex === getCategoryIndex(category, index)
                      ? 'bg-primary-50 dark:bg-primary-900/30'
                      : 'hover:bg-gray-100 dark:hover:bg-gray-700'
                    "
                    @click="handleResultClick(result)"
                    @mouseenter="selectedIndex = getCategoryIndex(category, index)"
                  >
                    <component :is="result.icon" class="h-5 w-5 flex-shrink-0 text-gray-400" />
                    <div class="min-w-0 flex-1">
                      <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                        {{ result.title }}
                      </p>
                      <p v-if="result.subtitle" class="truncate text-xs text-gray-500 dark:text-gray-400">
                        {{ result.subtitle }}
                      </p>
                    </div>
                    <ChevronRightIcon class="h-4 w-4 flex-shrink-0 text-gray-400" />
                  </button>
                </div>
              </template>
            </div>

            <!-- No Results -->
            <div v-else-if="search.query.value && search.results.value.length === 0 && !search.isLoading.value" class="py-12 text-center">
              <MagnifyingGlassIcon class="mx-auto h-12 w-12 text-gray-300 dark:text-gray-600" />
              <p class="mt-3 text-sm text-gray-500 dark:text-gray-400">
                검색 결과가 없습니다
              </p>
            </div>

            <!-- Empty State: Recent Searches + Quick Actions -->
            <div v-else class="py-2">
              <!-- Recent Searches -->
              <div v-if="search.recentSearches.value.length > 0" class="mb-4">
                <div class="flex items-center justify-between px-4 py-2">
                  <h3 class="text-xs font-semibold uppercase text-gray-500 dark:text-gray-400">
                    최근 검색
                  </h3>
                  <button
                    class="text-xs text-gray-400 hover:text-gray-600 dark:hover:text-gray-300"
                    @click="search.clearRecentSearches()"
                  >
                    지우기
                  </button>
                </div>
                <div>
                  <button
                    v-for="(recentQuery, index) in search.recentSearches.value"
                    :key="index"
                    :ref="(el) => setResultRef(el as HTMLElement, index)"
                    class="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors"
                    :class="selectedIndex === index
                      ? 'bg-primary-50 dark:bg-primary-900/30'
                      : 'hover:bg-gray-100 dark:hover:bg-gray-700'
                    "
                    @click="handleRecentSearchClick(recentQuery)"
                    @mouseenter="selectedIndex = index"
                  >
                    <ClockIcon class="h-5 w-5 flex-shrink-0 text-gray-400" />
                    <p class="flex-1 truncate text-sm text-gray-900 dark:text-gray-100">
                      {{ recentQuery }}
                    </p>
                  </button>
                </div>
              </div>

              <!-- Quick Actions -->
              <div>
                <div class="px-4 py-2">
                  <h3 class="text-xs font-semibold uppercase text-gray-500 dark:text-gray-400">
                    빠른 이동
                  </h3>
                </div>
                <div>
                  <button
                    v-for="(action, index) in search.quickActions"
                    :key="action.id"
                    :ref="(el) => setResultRef(el as HTMLElement, search.recentSearches.value.length + index)"
                    class="flex w-full items-center gap-3 px-4 py-3 text-left transition-colors"
                    :class="selectedIndex === search.recentSearches.value.length + index
                      ? 'bg-primary-50 dark:bg-primary-900/30'
                      : 'hover:bg-gray-100 dark:hover:bg-gray-700'
                    "
                    @click="handleQuickActionClick(action)"
                    @mouseenter="selectedIndex = search.recentSearches.value.length + index"
                  >
                    <component :is="action.icon" class="h-5 w-5 flex-shrink-0" :class="action.iconColor" />
                    <p class="flex-1 text-sm font-medium text-gray-900 dark:text-gray-100">
                      {{ action.title }}
                    </p>
                    <ChevronRightIcon class="h-4 w-4 flex-shrink-0 text-gray-400" />
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- Footer -->
          <div class="flex items-center justify-end gap-4 border-t border-gray-200 px-4 py-3 text-xs text-gray-500 dark:border-gray-700 dark:text-gray-400">
            <div class="flex items-center gap-1">
              <kbd class="kbd">↑↓</kbd>
              <span>이동</span>
            </div>
            <div class="flex items-center gap-1">
              <kbd class="kbd">↵</kbd>
              <span>선택</span>
            </div>
            <div class="flex items-center gap-1">
              <kbd class="kbd">esc</kbd>
              <span>닫기</span>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import {
  MagnifyingGlassIcon,
  XMarkIcon,
  ChevronRightIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'
import { useSearch, type SearchResult, type QuickAction } from '@/composables/useSearch'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const search = useSearch()
const searchInputRef = ref<HTMLInputElement>()
const selectedIndex = ref(0)
const resultRefs = ref<HTMLElement[]>([])

const setResultRef = (el: HTMLElement | null, index: number) => {
  if (el) {
    resultRefs.value[index] = el
  }
}

// Close modal
const close = () => {
  emit('update:modelValue', false)
  search.query.value = ''
  selectedIndex.value = 0
}

// Focus input when opened
watch(() => props.modelValue, async (isOpen) => {
  if (isOpen) {
    await nextTick()
    searchInputRef.value?.focus()
    selectedIndex.value = 0
  }
})

// Reset selection when results change
watch(() => search.results.value.length, () => {
  selectedIndex.value = 0
})

// Get total items count
const getTotalItems = () => {
  if (search.query.value) {
    return search.results.value.length
  }
  return search.recentSearches.value.length + search.quickActions.length
}

// Move selection up/down
const moveSelection = (direction: number) => {
  const totalItems = getTotalItems()
  if (totalItems === 0) return

  selectedIndex.value = (selectedIndex.value + direction + totalItems) % totalItems

  // Scroll into view
  nextTick(() => {
    resultRefs.value[selectedIndex.value]?.scrollIntoView({
      block: 'nearest',
      behavior: 'smooth',
    })
  })
}

// Get category index for grouped results
const getCategoryIndex = (category: string, indexInCategory: number): number => {
  let totalIndex = 0
  for (const [cat, results] of Object.entries(search.groupedResults.value)) {
    if (cat === category) {
      return totalIndex + indexInCategory
    }
    totalIndex += (results as SearchResult[]).length
  }
  return totalIndex
}

// Select current item
const selectCurrent = () => {
  if (search.query.value) {
    const result = search.results.value[selectedIndex.value]
    if (result) {
      handleResultClick(result)
    }
  } else {
    const recentCount = search.recentSearches.value.length
    if (selectedIndex.value < recentCount) {
      handleRecentSearchClick(search.recentSearches.value[selectedIndex.value])
    } else {
      const action = search.quickActions[selectedIndex.value - recentCount]
      if (action) {
        handleQuickActionClick(action)
      }
    }
  }
}

// Handle result click
const handleResultClick = (result: SearchResult) => {
  search.navigateToResult(result)
  close()
}

// Handle recent search click
const handleRecentSearchClick = (query: string) => {
  search.query.value = query
}

// Handle quick action click
const handleQuickActionClick = (action: QuickAction) => {
  search.navigateToQuickAction(action)
  close()
}

// Global keyboard shortcut
const handleGlobalKeydown = (e: KeyboardEvent) => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    if (props.modelValue) {
      close()
    } else {
      emit('update:modelValue', true)
    }
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleGlobalKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
})
</script>

<style scoped>
.search-overlay-enter-active,
.search-overlay-leave-active {
  transition: opacity 200ms ease;
}

.search-overlay-enter-from,
.search-overlay-leave-to {
  opacity: 0;
}

.search-overlay-enter-active .glass-elevated,
.search-overlay-leave-active .glass-elevated {
  transition: transform 200ms ease, opacity 200ms ease;
}

.search-overlay-enter-from .glass-elevated,
.search-overlay-leave-to .glass-elevated {
  transform: scale(0.95);
  opacity: 0;
}

.kbd {
  @apply inline-flex h-5 min-w-[1.25rem] items-center justify-center rounded border border-gray-300 bg-gray-100 px-1.5 font-mono text-[10px] font-semibold text-gray-700 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-300;
}
</style>
