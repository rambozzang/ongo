<template>
  <div class="flex flex-wrap gap-2">
    <!-- All filter -->
    <button
      class="filter-chip"
      :class="{ active: isAllSelected }"
      @click="toggleFilter('all')"
    >
      <span class="font-medium">전체</span>
      <span class="count-badge">{{ eventCounts.all }}</span>
    </button>

    <!-- Type filters -->
    <button
      v-for="type in filterTypes"
      :key="type.value"
      class="filter-chip"
      :class="{ active: selectedFilters.includes(type.value) }"
      @click="toggleFilter(type.value)"
    >
      <span class="font-medium">{{ type.label }}</span>
      <span class="count-badge">{{ eventCounts[type.value] }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ActivityEventType } from '@/stores/activityLog'

interface FilterType {
  value: ActivityEventType
  label: string
}

const props = defineProps<{
  selectedFilters: ActivityEventType[]
  eventCounts: Record<ActivityEventType | 'all', number>
}>()

const emit = defineEmits<{
  update: [filters: ActivityEventType[]]
}>()

const filterTypes: FilterType[] = [
  { value: 'upload', label: '업로드' },
  { value: 'publish', label: '게시' },
  { value: 'schedule', label: '예약' },
  { value: 'edit', label: '수정' },
  { value: 'delete', label: '삭제' },
  { value: 'ai_use', label: 'AI 사용' },
  { value: 'login', label: '로그인' },
  { value: 'channel_connect', label: '채널 연결' },
  { value: 'recycle', label: '재활용' },
]

const isAllSelected = computed(() => props.selectedFilters.length === 0)

function toggleFilter(type: ActivityEventType | 'all') {
  if (type === 'all') {
    // Deselect all
    emit('update', [])
    return
  }

  // Multi-select: toggle the filter
  const filters = [...props.selectedFilters]
  const index = filters.indexOf(type)

  if (index >= 0) {
    // Remove filter
    filters.splice(index, 1)
  } else {
    // Add filter
    filters.push(type)
  }

  emit('update', filters)
}
</script>

<style scoped>
.filter-chip {
  @apply inline-flex items-center gap-2 rounded-full border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-4 py-2 text-sm transition-all hover:border-primary-400 dark:hover:border-primary-600 hover:bg-gray-50 dark:hover:bg-gray-700;
}

.filter-chip.active {
  @apply border-primary-500 bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-400;
}

.count-badge {
  @apply inline-flex h-5 min-w-[1.25rem] items-center justify-center rounded-full bg-gray-200 dark:bg-gray-700 px-1.5 text-xs font-semibold text-gray-700 dark:text-gray-300;
}

.filter-chip.active .count-badge {
  @apply bg-primary-200 dark:bg-primary-800/50 text-primary-800 dark:text-primary-300;
}
</style>
