<script setup lang="ts">
import { computed } from 'vue'
import {
  PencilIcon,
  TrashIcon,
  UserIcon,
  CalendarIcon,
} from '@heroicons/vue/24/outline'
import type { BrandScheduleItem, BrandColor } from '@/types/multiBrandCalendar'

interface Props {
  item: BrandScheduleItem
}

interface Emits {
  (e: 'edit', id: number): void
  (e: 'delete', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const colorMap: Record<BrandColor, string> = {
  RED: '#EF4444',
  BLUE: '#3B82F6',
  GREEN: '#10B981',
  PURPLE: '#8B5CF6',
  ORANGE: '#F97316',
  PINK: '#EC4899',
  TEAL: '#14B8A6',
  YELLOW: '#EAB308',
}

const borderColor = computed(() => colorMap[props.item.brandColor])

const statusConfig = computed(() => {
  const configs: Record<string, { label: string; color: string }> = {
    DRAFT: {
      label: '초안',
      color: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300',
    },
    SCHEDULED: {
      label: '예약됨',
      color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
    },
    PUBLISHING: {
      label: '발행 중',
      color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
    },
    PUBLISHED: {
      label: '발행 완료',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
    },
    FAILED: {
      label: '실패',
      color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
    },
  }
  return configs[props.item.status] ?? configs.DRAFT
})

const platformConfig = computed(() => {
  const configs: Record<string, { label: string; color: string }> = {
    YOUTUBE: {
      label: 'YouTube',
      color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
    },
    TIKTOK: {
      label: 'TikTok',
      color: 'bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-400',
    },
    INSTAGRAM: {
      label: 'Instagram',
      color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
    },
    NAVER_CLIP: {
      label: 'Naver Clip',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    },
  }
  return configs[props.item.platform] ?? { label: props.item.platform, color: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300' }
})

function formatDateTime(dateStr: string): string {
  return new Date(dateStr).toLocaleString('ko-KR', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<template>
  <div
    class="flex items-stretch rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 shadow-sm hover:shadow-md transition-all duration-200 overflow-hidden group"
  >
    <!-- Left color border -->
    <div class="w-1.5 flex-shrink-0" :style="{ backgroundColor: borderColor }" />

    <!-- Content -->
    <div class="flex-1 p-4">
      <div class="flex items-start justify-between mb-2">
        <div class="flex-1 min-w-0">
          <!-- Brand name -->
          <div class="flex items-center gap-2 mb-1">
            <span
              class="w-2 h-2 rounded-full flex-shrink-0"
              :style="{ backgroundColor: borderColor }"
            />
            <span class="text-xs font-medium text-gray-500 dark:text-gray-400">
              {{ item.brandName }}
            </span>
          </div>

          <!-- Title -->
          <h4 class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
            {{ item.title }}
          </h4>
        </div>

        <!-- Action buttons -->
        <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity ml-2">
          <button
            @click="emit('edit', item.id)"
            class="p-1.5 text-gray-400 hover:text-blue-600 dark:hover:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-lg transition-colors"
            title="수정"
          >
            <PencilIcon class="w-4 h-4" />
          </button>
          <button
            @click="emit('delete', item.id)"
            class="p-1.5 text-gray-400 hover:text-red-600 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
            title="삭제"
          >
            <TrashIcon class="w-4 h-4" />
          </button>
        </div>
      </div>

      <!-- Badges row -->
      <div class="flex flex-wrap items-center gap-2 mb-2">
        <!-- Platform -->
        <span
          :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', platformConfig.color]"
        >
          {{ platformConfig.label }}
        </span>

        <!-- Status -->
        <span
          :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', statusConfig.color]"
        >
          {{ statusConfig.label }}
        </span>
      </div>

      <!-- Date/Time & Assigned Editor -->
      <div class="flex items-center gap-4 text-xs text-gray-500 dark:text-gray-400">
        <span class="inline-flex items-center gap-1">
          <CalendarIcon class="w-3.5 h-3.5" />
          {{ formatDateTime(item.scheduledAt) }}
        </span>
        <span v-if="item.assignedTo" class="inline-flex items-center gap-1">
          <UserIcon class="w-3.5 h-3.5" />
          {{ item.assignedTo }}
        </span>
      </div>
    </div>
  </div>
</template>
