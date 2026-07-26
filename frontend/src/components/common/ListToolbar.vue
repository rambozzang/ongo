<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { BarsArrowDownIcon, BarsArrowUpIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import SearchInput from '@/components/common/SearchInput.vue'

/**
 * 목록 화면 공통 툴바 — 검색 + 정렬 + 일괄 작업을 한 줄에 모은다.
 *
 * `useListControls`와 짝으로 쓴다.
 *
 * ```vue
 * <ListToolbar
 *   v-model="query"
 *   v-model:sort-key="sortKey"
 *   v-model:sort-dir="sortDir"
 *   :sort-options="sortOptions"
 *   :selected-count="selectedCount"
 *   :total-count="visibleCount"
 *   @clear-selection="clearSelection"
 * >
 *   <template #filters>...</template>
 *   <template #bulk-actions>...</template>
 * </ListToolbar>
 * ```
 *
 * 선택된 항목이 하나라도 생기면 툴바 전체가 **선택 모드**로 바뀐다.
 * (검색/정렬 대신 "N개 선택됨" + 일괄 액션 + 선택 해제를 노출)
 */

/** 툴바가 필요로 하는 최소 정렬 옵션 형태. `ListSortOption`이 그대로 들어맞는다. */
interface ToolbarSortOption {
  key: string
  label: string
}

const props = withDefaults(
  defineProps<{
    /** 검색어 (v-model) */
    modelValue: string
    /** 정렬 옵션 목록. 비어 있으면 정렬 UI를 감춘다. */
    sortOptions?: ToolbarSortOption[]
    /** 현재 정렬 키 (v-model:sort-key) */
    sortKey?: string
    /** 현재 정렬 방향 (v-model:sort-dir) */
    sortDir?: 'asc' | 'desc'
    /** 선택된 항목 수. 1 이상이면 선택 모드로 전환된다. */
    selectedCount?: number
    /** 현재 보이는 항목 수 — 검색 결과 개수 안내에 쓰인다. */
    totalCount?: number
    /** 검색 입력 placeholder */
    searchPlaceholder?: string
    /** 검색 입력의 접근성 라벨 (스크린리더 전용) */
    searchLabel?: string
  }>(),
  {
    sortOptions: () => [],
    sortKey: '',
    sortDir: 'asc',
    selectedCount: 0,
    totalCount: undefined,
    searchPlaceholder: undefined,
    searchLabel: undefined,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:sortKey': [value: string]
  'update:sortDir': [value: 'asc' | 'desc']
  'clear-selection': []
}>()

const { t } = useI18n({ useScope: 'global' })

const isSelectionMode = computed(() => props.selectedCount > 0)
const hasSort = computed(() => props.sortOptions.length > 0)
const hasQuery = computed(() => props.modelValue.trim().length > 0)

const sortDirLabel = computed(() => (props.sortDir === 'asc' ? t('list.sortAsc') : t('list.sortDesc')))

/**
 * 상시 마운트된 live region. v-if로 나타나는 요소는 낭독을 놓치는 경우가 있어
 * 컨테이너는 항상 두고 내용만 바꾼다.
 */
const liveMessage = computed(() => {
  if (isSelectionMode.value) return t('list.selectedCount', { count: props.selectedCount })
  if (hasQuery.value && props.totalCount !== undefined) {
    return t('list.resultCount', { count: props.totalCount })
  }
  return ''
})

const toggleSortDir = () => emit('update:sortDir', props.sortDir === 'asc' ? 'desc' : 'asc')
</script>

<template>
  <div class="mb-5">
    <!-- 선택 모드 -->
    <div
      v-if="isSelectionMode"
      class="flex flex-col gap-3 rounded-xl border border-primary-200 bg-primary-50 px-4 py-3 dark:border-primary-800 dark:bg-primary-900/30 tablet:flex-row tablet:items-center tablet:justify-between"
    >
      <p class="text-body font-semibold text-primary-700 dark:text-primary-200">
        {{ t('list.selectedCount', { count: selectedCount }) }}
      </p>
      <div class="flex flex-wrap items-center gap-2">
        <slot name="bulk-actions" />
        <button
          type="button"
          class="btn-secondary inline-flex items-center gap-1.5"
          @click="emit('clear-selection')"
        >
          <XMarkIcon class="h-4 w-4" aria-hidden="true" />
          {{ t('list.clearSelection') }}
        </button>
      </div>
    </div>

    <!-- 기본 모드 -->
    <div v-else class="flex flex-col gap-3 tablet:flex-row tablet:items-center">
      <label class="w-full tablet:max-w-sm">
        <span class="sr-only">{{ searchLabel || t('list.searchLabel') }}</span>
        <SearchInput
          :model-value="modelValue"
          :placeholder="searchPlaceholder || t('list.searchPlaceholder')"
          @update:model-value="emit('update:modelValue', $event)"
        />
      </label>

      <div class="flex flex-wrap items-center gap-2 tablet:ml-auto">
        <slot name="filters" />

        <div v-if="hasSort" class="flex items-center gap-1.5">
          <select
            :value="sortKey"
            class="input-field w-auto min-w-[8.5rem]"
            :aria-label="t('list.sortLabel')"
            @change="emit('update:sortKey', ($event.target as HTMLSelectElement).value)"
          >
            <option v-for="option in sortOptions" :key="option.key" :value="option.key">
              {{ option.label }}
            </option>
          </select>
          <button
            type="button"
            class="btn-secondary min-h-10 px-2.5"
            :title="sortDirLabel"
            :aria-label="sortDirLabel"
            @click="toggleSortDir"
          >
            <BarsArrowUpIcon v-if="sortDir === 'asc'" class="h-4 w-4" aria-hidden="true" />
            <BarsArrowDownIcon v-else class="h-4 w-4" aria-hidden="true" />
          </button>
        </div>

        <slot name="actions" />
      </div>
    </div>

    <span class="sr-only" aria-live="polite">{{ liveMessage }}</span>
  </div>
</template>
