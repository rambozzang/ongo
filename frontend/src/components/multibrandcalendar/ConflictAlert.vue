<script setup lang="ts">
import { computed } from 'vue'
import {
  ExclamationTriangleIcon,
  InformationCircleIcon,
  ExclamationCircleIcon,
} from '@heroicons/vue/24/outline'
import type { CalendarConflict, BrandColor } from '@/types/multiBrandCalendar'

interface Props {
  conflict: CalendarConflict
}

const props = defineProps<Props>()

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

const severityConfig = computed(() => {
  const configs: Record<string, { icon: typeof ExclamationTriangleIcon; bgColor: string; borderColor: string; iconColor: string; textColor: string }> = {
    LOW: {
      icon: InformationCircleIcon,
      bgColor: 'bg-blue-50 dark:bg-blue-900/10',
      borderColor: 'border-blue-200 dark:border-blue-800',
      iconColor: 'text-blue-500 dark:text-blue-400',
      textColor: 'text-blue-800 dark:text-blue-300',
    },
    MEDIUM: {
      icon: ExclamationTriangleIcon,
      bgColor: 'bg-yellow-50 dark:bg-yellow-900/10',
      borderColor: 'border-yellow-200 dark:border-yellow-800',
      iconColor: 'text-yellow-500 dark:text-yellow-400',
      textColor: 'text-yellow-800 dark:text-yellow-300',
    },
    HIGH: {
      icon: ExclamationCircleIcon,
      bgColor: 'bg-red-50 dark:bg-red-900/10',
      borderColor: 'border-red-200 dark:border-red-800',
      iconColor: 'text-red-500 dark:text-red-400',
      textColor: 'text-red-800 dark:text-red-300',
    },
  }
  return configs[props.conflict.severity]
})

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    month: 'short',
    day: 'numeric',
    weekday: 'short',
  })
}

function formatTime(dateStr: string): string {
  return new Date(dateStr).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
  })
}

const suggestedResolution = computed(() => {
  if (props.conflict.severity === 'HIGH') {
    return '일정 충돌이 심각합니다. 하나의 일정을 다른 시간대로 변경하는 것을 권장합니다.'
  }
  if (props.conflict.severity === 'MEDIUM') {
    return '같은 날짜에 여러 브랜드가 겹칩니다. 게시 시간을 2시간 이상 간격으로 조정해보세요.'
  }
  return '소규모 겹침이 있지만, 서로 다른 플랫폼이라면 문제가 되지 않을 수 있습니다.'
})
</script>

<template>
  <div
    :class="['rounded-lg border p-4', severityConfig.bgColor, severityConfig.borderColor]"
  >
    <!-- Header -->
    <div class="flex items-start gap-3 mb-3">
      <component
        :is="severityConfig.icon"
        :class="['w-5 h-5 flex-shrink-0 mt-0.5', severityConfig.iconColor]"
      />
      <div class="flex-1">
        <p :class="['text-sm font-medium', severityConfig.textColor]">
          {{ conflict.message }}
        </p>
        <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
          {{ formatDate(conflict.date) }}
        </p>
      </div>
    </div>

    <!-- Conflicting items -->
    <div class="ml-8 space-y-2 mb-3">
      <div
        v-for="item in conflict.items"
        :key="item.id"
        class="flex items-center gap-2 text-sm"
      >
        <span
          class="w-2 h-2 rounded-full flex-shrink-0"
          :style="{ backgroundColor: colorMap[item.brandColor] }"
        />
        <span class="text-gray-700 dark:text-gray-300 font-medium">{{ item.brandName }}</span>
        <span class="text-gray-500 dark:text-gray-400">-</span>
        <span class="text-gray-600 dark:text-gray-400 truncate">{{ item.title }}</span>
        <span class="text-xs text-gray-400 dark:text-gray-500 ml-auto flex-shrink-0">
          {{ formatTime(item.scheduledAt) }}
        </span>
      </div>
    </div>

    <!-- Suggested resolution -->
    <div class="ml-8 p-2 rounded-md bg-white/60 dark:bg-gray-800/60">
      <p class="text-xs text-gray-600 dark:text-gray-400">
        <span class="font-medium">권장 조치:</span> {{ suggestedResolution }}
      </p>
    </div>
  </div>
</template>
