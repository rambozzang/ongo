<template>
  <Teleport to="body">
    <Transition name="search-overlay">
      <div
        v-if="modelValue"
        class="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto px-4 pt-20 tablet:pt-32"
        role="dialog"
        aria-modal="true"
        aria-labelledby="search-overlay-title"
        @click.self="close"
      >
        <!-- Backdrop -->
        <div
          class="fixed inset-0 bg-[var(--surface-overlay)] transition-opacity"
          @click="close"
        ></div>

        <!-- Search Modal -->
        <div
          ref="panelRef"
          class="relative w-full max-w-xl overflow-hidden rounded-xl border border-line bg-surface-card shadow-lg transition-all"
          @click.stop
        >
          <h2 id="search-overlay-title" class="sr-only">검색</h2>
          <!-- Search Input -->
          <div class="flex items-center border-b border-line px-4 py-3">
            <MagnifyingGlassIcon class="h-5 w-5 text-content-tertiary" aria-hidden="true" />
            <input
              ref="searchInputRef"
              v-model="search.query.value"
              type="text"
              :placeholder="t('searchOverlay.placeholder')"
              role="combobox"
              aria-autocomplete="list"
              :aria-label="t('searchOverlay.inputLabel')"
              :aria-expanded="search.results.value.length > 0"
              aria-controls="search-overlay-results"
              class="ml-3 min-w-0 flex-1 bg-transparent text-lg text-content placeholder:text-content-tertiary focus:outline-none"
              @keydown.down.prevent="moveSelection(1)"
              @keydown.up.prevent="moveSelection(-1)"
              @keydown.enter.prevent="selectCurrent"
              @keydown.esc="close"
            />
            <button
              v-if="search.query.value"
              class="ml-2 inline-flex min-h-11 min-w-11 items-center justify-center rounded-lg text-content-tertiary hover:bg-surface-raised hover:text-content"
              :aria-label="t('searchOverlay.clear')"
              @click="search.query.value = ''"
            >
              <XMarkIcon class="h-4 w-4" />
            </button>
          </div>

          <!-- Results Container -->
          <div id="search-overlay-results" class="max-h-96 overflow-y-auto scrollbar-hide" role="region" :aria-label="t('searchOverlay.results')">
            <!-- Loading State -->
            <div v-if="search.isLoading.value" class="flex items-center justify-center py-12" role="status" :aria-label="t('action.loading')">
              <div class="h-8 w-8 animate-spin rounded-full border-4 border-line border-t-accent"></div>
            </div>

            <!-- Search Results -->
            <div v-else-if="search.query.value && search.results.value.length > 0" class="py-2">
              <template v-for="(categoryResults, category) in search.groupedResults.value" :key="category">
                <div class="px-4 py-2">
                  <h3 class="text-xs font-semibold uppercase text-content-tertiary">
                    {{ category }}
                  </h3>
                </div>
                <div>
                  <button
                    v-for="(result, index) in categoryResults"
                    :key="result.id"
                    :ref="(el) => setResultRef(el as HTMLElement, getCategoryIndex(category, index))"
                    class="flex min-h-11 w-full items-center gap-3 px-4 py-3 text-left transition-colors"
                    :class="selectedIndex === getCategoryIndex(category, index)
                      ? 'bg-accent-dim'
                      : 'hover:bg-surface-raised'
                    "
                    @click="handleResultClick(result)"
                    @mouseenter="selectedIndex = getCategoryIndex(category, index)"
                  >
                    <component :is="result.icon" class="h-5 w-5 flex-shrink-0 text-content-tertiary" aria-hidden="true" />
                    <div class="min-w-0 flex-1">
                      <p class="truncate text-sm font-medium text-content">
                        {{ result.title }}
                      </p>
                      <p v-if="result.subtitle" class="truncate text-xs text-content-tertiary">
                        {{ result.subtitle }}
                      </p>
                    </div>
                    <ChevronRightIcon class="h-4 w-4 flex-shrink-0 text-content-tertiary" aria-hidden="true" />
                  </button>
                </div>
              </template>
            </div>

            <!-- No Results -->
            <div v-else-if="search.query.value && search.results.value.length === 0 && !search.isLoading.value" class="py-12 text-center">
              <MagnifyingGlassIcon class="mx-auto h-12 w-12 text-content-quaternary" aria-hidden="true" />
              <p class="mt-3 text-sm text-content-secondary">
                {{ t('searchOverlay.noResults') }}
              </p>
            </div>

            <!-- Empty State: Recent Searches + Quick Actions -->
            <div v-else class="py-2">
              <!-- Recent Searches -->
              <div v-if="search.recentSearches.value.length > 0" class="mb-4">
                <div class="flex items-center justify-between px-4 py-2">
                  <h3 class="text-xs font-semibold uppercase text-content-tertiary">
                    {{ t('searchOverlay.recent') }}
                  </h3>
                  <button
                    class="min-h-11 px-2 text-xs text-content-tertiary hover:text-content"
                    @click="search.clearRecentSearches()"
                  >
                    {{ t('searchOverlay.clearRecent') }}
                  </button>
                </div>
                <div>
                  <button
                    v-for="(recentQuery, index) in search.recentSearches.value"
                    :key="index"
                    :ref="(el) => setResultRef(el as HTMLElement, index)"
                    class="flex min-h-11 w-full items-center gap-3 px-4 py-3 text-left transition-colors"
                    :class="selectedIndex === index
                      ? 'bg-accent-dim'
                      : 'hover:bg-surface-raised'
                    "
                    @click="handleRecentSearchClick(recentQuery)"
                    @mouseenter="selectedIndex = index"
                  >
                    <ClockIcon class="h-5 w-5 flex-shrink-0 text-content-tertiary" aria-hidden="true" />
                    <p class="flex-1 truncate text-sm text-content">
                      {{ recentQuery }}
                    </p>
                  </button>
                </div>
              </div>

              <!-- Quick Actions -->
              <div>
                <div class="px-4 py-2">
                  <h3 class="text-xs font-semibold uppercase text-content-tertiary">
                    {{ t('searchOverlay.quickActions') }}
                  </h3>
                </div>
                <div>
                  <button
                    v-for="(action, index) in search.quickActions"
                    :key="action.id"
                    :ref="(el) => setResultRef(el as HTMLElement, search.recentSearches.value.length + index)"
                    class="flex min-h-11 w-full items-center gap-3 px-4 py-3 text-left transition-colors"
                    :class="selectedIndex === search.recentSearches.value.length + index
                      ? 'bg-accent-dim'
                      : 'hover:bg-surface-raised'
                    "
                    @click="handleQuickActionClick(action)"
                    @mouseenter="selectedIndex = search.recentSearches.value.length + index"
                  >
                    <component :is="action.icon" class="h-5 w-5 flex-shrink-0" :class="action.iconColor" aria-hidden="true" />
                    <p class="flex-1 text-sm font-medium text-content">
                      {{ action.title }}
                    </p>
                    <ChevronRightIcon class="h-4 w-4 flex-shrink-0 text-content-tertiary" aria-hidden="true" />
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- Footer -->
          <div class="flex items-center justify-end gap-4 border-t border-line px-4 py-3 text-xs text-content-tertiary">
            <div class="flex items-center gap-1">
              <kbd class="kbd">↑↓</kbd>
              <span>{{ t('searchOverlay.move') }}</span>
            </div>
            <div class="flex items-center gap-1">
              <kbd class="kbd">↵</kbd>
              <span>{{ t('searchOverlay.select') }}</span>
            </div>
            <div class="flex items-center gap-1">
              <kbd class="kbd">esc</kbd>
              <span>{{ t('searchOverlay.close') }}</span>
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
import { useFocusTrap } from '@/composables/useAccessibility'
import { useLocale } from '@/composables/useLocale'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const search = useSearch()
const { t } = useLocale()
const searchInputRef = ref<HTMLInputElement>()
const panelRef = ref<HTMLElement | null>(null)
const previousActiveElement = ref<HTMLElement | null>(null)
const previousBodyOverflow = ref('')
const { activate: activateFocusTrap, deactivate: deactivateFocusTrap, updateFocusableElements } = useFocusTrap(panelRef)
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
    previousActiveElement.value = document.activeElement as HTMLElement
    previousBodyOverflow.value = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    await nextTick()
    searchInputRef.value?.focus()
    activateFocusTrap()
    selectedIndex.value = 0
  } else {
    deactivateFocusTrap()
    document.body.style.overflow = previousBodyOverflow.value
    previousActiveElement.value?.focus()
  }
})

// Reset selection when results change
watch(() => [search.query.value, search.results.value.length, search.recentSearches.value.length], async () => {
  selectedIndex.value = 0
  if (props.modelValue) {
    await nextTick()
    updateFocusableElements()
  }
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
  deactivateFocusTrap()
  document.body.style.overflow = previousBodyOverflow.value
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

.search-overlay-enter-active .bg-surface-card,
.search-overlay-leave-active .bg-surface-card {
  transition: transform 200ms ease, opacity 200ms ease;
}

.search-overlay-enter-from .bg-surface-card,
.search-overlay-leave-to .bg-surface-card {
  transform: scale(0.95);
  opacity: 0;
}

.kbd {
  @apply inline-flex h-5 min-w-[1.25rem] items-center justify-center rounded border border-line-control bg-surface-raised px-1.5 font-mono text-[10px] font-semibold text-content-secondary;
}
</style>
