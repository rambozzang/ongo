<script setup lang="ts">
import { computed } from 'vue'
import {
  PencilIcon,
  TrashIcon,
  CalendarDaysIcon,
  VideoCameraIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'
import type { RecyclingQueue } from '@/types/recycling'

const props = defineProps<{
  queue: RecyclingQueue
}>()

const emit = defineEmits<{
  edit: [id: number]
  delete: [id: number]
  toggle: [id: number]
}>()

const PLATFORM_LABELS: Record<string, { label: string; color: string }> = {
  YOUTUBE: { label: 'YouTube', color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400' },
  TIKTOK: { label: 'TikTok', color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300' },
  INSTAGRAM: { label: 'Instagram', color: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400' },
  NAVER_CLIP: { label: 'Naver Clip', color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' },
}

const DAY_LABELS = ['일', '월', '화', '수', '목', '금', '토']

const FREQUENCY_LABELS: Record<string, string> = {
  weekly: '주간',
  biweekly: '격주',
  monthly: '월간',
}

const frequencyLabel = computed(() => FREQUENCY_LABELS[props.queue.frequency] || props.queue.frequency)

const scheduleDaysLabel = computed(() => {
  return props.queue.scheduleDays.map((d) => DAY_LABELS[d]).join(', ')
})

const filterSummary = computed(() => {
  const parts: string[] = []
  const criteria = props.queue.filterCriteria
  if (criteria.minViews) {
    parts.push(`${(criteria.minViews / 1000).toFixed(0)}K+ 조회수`)
  }
  if (criteria.maxAge) {
    parts.push(`${criteria.maxAge}개월 이내`)
  }
  if (criteria.categories && criteria.categories.length > 0) {
    parts.push(criteria.categories.join(', '))
  }
  return parts.length > 0 ? parts.join(' / ') : '필터 없음'
})

const nextScheduleFormatted = computed(() => {
  const date = new Date(props.queue.nextScheduledAt)
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${month}/${day} ${hours}:${minutes}`
})
</script>

<template>
  <div
    class="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5 transition-all hover:shadow-md"
    :class="{ 'opacity-60': !queue.isActive }"
  >
    <!-- Header -->
    <div class="flex items-start justify-between mb-4">
      <div class="flex-1 min-w-0">
        <h3 class="text-lg font-semibold text-gray-900 dark:text-white truncate">
          {{ queue.name }}
        </h3>
        <p class="text-sm text-gray-500 dark:text-gray-400 mt-1">
          {{ filterSummary }}
        </p>
      </div>

      <!-- Toggle Switch -->
      <button
        @click="emit('toggle', queue.id)"
        :class="[
          'relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-gray-800',
          queue.isActive ? 'bg-blue-600' : 'bg-gray-300 dark:bg-gray-600',
        ]"
        role="switch"
        :aria-checked="queue.isActive"
      >
        <span
          :class="[
            'pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out',
            queue.isActive ? 'translate-x-5' : 'translate-x-0',
          ]"
        />
      </button>
    </div>

    <!-- Info Grid -->
    <div class="grid grid-cols-2 gap-3 mb-4">
      <div class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
        <CalendarDaysIcon class="w-4 h-4 flex-shrink-0" />
        <span>{{ frequencyLabel }} ({{ scheduleDaysLabel }})</span>
      </div>
      <div class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
        <ClockIcon class="w-4 h-4 flex-shrink-0" />
        <span>{{ queue.scheduleTime }}</span>
      </div>
      <div class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
        <VideoCameraIcon class="w-4 h-4 flex-shrink-0" />
        <span>{{ queue.videoCount }}개 영상</span>
      </div>
      <div class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
        <CalendarDaysIcon class="w-4 h-4 flex-shrink-0" />
        <span>다음: {{ nextScheduleFormatted }}</span>
      </div>
    </div>

    <!-- Platforms -->
    <div class="flex flex-wrap gap-2 mb-4">
      <span
        v-for="platform in queue.platforms"
        :key="platform"
        :class="[
          'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium',
          PLATFORM_LABELS[platform]?.color || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
        ]"
      >
        {{ PLATFORM_LABELS[platform]?.label || platform }}
      </span>
    </div>

    <!-- Actions -->
    <div class="flex items-center justify-end gap-2 pt-3 border-t border-gray-100 dark:border-gray-700">
      <button
        @click="emit('edit', queue.id)"
        class="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-600 dark:text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-lg transition-colors"
      >
        <PencilIcon class="w-4 h-4" />
        수정
      </button>
      <button
        @click="emit('delete', queue.id)"
        class="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm text-gray-600 dark:text-gray-400 hover:text-red-600 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
      >
        <TrashIcon class="w-4 h-4" />
        삭제
      </button>
    </div>
  </div>
</template>
