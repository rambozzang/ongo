<template>
  <tr class="border-b border-gray-100 last:border-0 dark:border-gray-800">
    <!-- Video title -->
    <td class="py-3 pr-3">
      <div class="flex items-center gap-2">
        <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
          {{ recommendation.videoTitle }}
        </span>
        <span
          class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
          :class="platformBadge"
        >
          {{ recommendation.platform }}
        </span>
      </div>
    </td>

    <!-- Current -> Recommended time -->
    <td class="py-3 pr-3">
      <div class="flex items-center gap-1.5 text-sm">
        <span class="text-gray-500 dark:text-gray-400">
          {{ recommendation.currentSchedule ? formatDateTime(recommendation.currentSchedule) : '-' }}
        </span>
        <ArrowRightIcon class="h-3.5 w-3.5 flex-shrink-0 text-gray-400 dark:text-gray-500" />
        <span class="font-medium text-primary-600 dark:text-primary-400">
          {{ formatDateTime(recommendation.recommendedSchedule) }}
        </span>
      </div>
    </td>

    <!-- Improvement -->
    <td class="py-3 pr-3 text-right">
      <span class="text-sm font-semibold text-green-600 dark:text-green-400">
        +{{ recommendation.expectedImprovement }}%
      </span>
    </td>

    <!-- Confidence -->
    <td class="py-3 pr-3">
      <div class="flex items-center gap-2">
        <div class="h-1.5 w-16 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
          <div
            class="h-full rounded-full transition-all duration-500"
            :class="confidenceColor"
            :style="{ width: `${recommendation.confidence}%` }"
          />
        </div>
        <span class="text-xs text-gray-500 dark:text-gray-400">
          {{ recommendation.confidence }}%
        </span>
      </div>
    </td>

    <!-- Status -->
    <td class="py-3 pr-3">
      <span
        class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
        :class="statusBadge"
      >
        {{ statusLabel }}
      </span>
    </td>

    <!-- Actions -->
    <td class="py-3 text-right">
      <div v-if="recommendation.status === 'PENDING'" class="flex items-center justify-end gap-1">
        <button
          class="rounded-lg px-2.5 py-1 text-xs font-medium text-primary-600 transition-colors hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-900/20"
          @click="$emit('apply', recommendation.id)"
        >
          적용
        </button>
        <button
          class="rounded-lg px-2.5 py-1 text-xs font-medium text-gray-500 transition-colors hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
          @click="$emit('dismiss', recommendation.id)"
        >
          무시
        </button>
      </div>
    </td>
  </tr>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowRightIcon } from '@heroicons/vue/24/outline'
import type { ScheduleRecommendation } from '@/types/scheduleOptimizer'

const props = defineProps<{
  recommendation: ScheduleRecommendation
}>()

defineEmits<{
  apply: [id: number]
  dismiss: [id: number]
}>()

const platformBadge = computed(() => {
  const configs: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-400',
    INSTAGRAM: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
    NAVERCLIP: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
  }
  return configs[props.recommendation.platform.toUpperCase()] ?? 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300'
})

const statusConfig = computed(() => {
  const configs: Record<ScheduleRecommendation['status'], { label: string; color: string }> = {
    PENDING: {
      label: '대기중',
      color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
    },
    APPLIED: {
      label: '적용됨',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
    },
    DISMISSED: {
      label: '무시됨',
      color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    },
  }
  return configs[props.recommendation.status] ?? configs.PENDING
})

const statusBadge = computed(() => statusConfig.value.color)
const statusLabel = computed(() => statusConfig.value.label)

const confidenceColor = computed(() => {
  if (props.recommendation.confidence >= 80) return 'bg-green-500 dark:bg-green-400'
  if (props.recommendation.confidence >= 60) return 'bg-yellow-500 dark:bg-yellow-400'
  return 'bg-red-500 dark:bg-red-400'
})

function formatDateTime(dateStr: string): string {
  const date = new Date(dateStr)
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = date.getHours()
  const period = hour < 12 ? '오전' : '오후'
  const displayHour = hour === 0 ? 12 : hour > 12 ? hour - 12 : hour
  return `${month}/${day} ${period} ${displayHour}:00`
}
</script>
