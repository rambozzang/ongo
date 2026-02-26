<script setup lang="ts">
import { computed } from 'vue'
import { ClockIcon } from '@heroicons/vue/24/outline'
import type { CalendarSlot } from '@/types/contentCalendarAi'

interface Props {
  slots: CalendarSlot[]
}

const props = defineProps<Props>()

const platformLabel = (platform: string): string => {
  const labels: Record<string, string> = {
    YOUTUBE: 'YouTube',
    TIKTOK: 'TikTok',
    INSTAGRAM: 'Instagram',
    NAVERCLIP: 'Naver Clip',
  }
  return labels[platform] || platform
}

const platformBadgeColor = (platform: string): string => {
  const colors: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-gray-900 text-white dark:bg-gray-600 dark:text-gray-100',
    INSTAGRAM: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    NAVERCLIP: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }
  return colors[platform] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
}

const scoreColor = (score: number): string => {
  if (score >= 90) return 'text-green-600 dark:text-green-400'
  if (score >= 75) return 'text-yellow-600 dark:text-yellow-400'
  if (score >= 60) return 'text-orange-600 dark:text-orange-400'
  return 'text-red-600 dark:text-red-400'
}

const scoreBgColor = (score: number): string => {
  if (score >= 90) return 'bg-green-500 dark:bg-green-400'
  if (score >= 75) return 'bg-yellow-500 dark:bg-yellow-400'
  if (score >= 60) return 'bg-orange-500 dark:bg-orange-400'
  return 'bg-red-500 dark:bg-red-400'
}

const formattedDate = (date: string): string => {
  return new Date(date).toLocaleDateString('ko-KR', {
    month: 'short',
    day: 'numeric',
    weekday: 'short',
  })
}

const sortedSlots = computed(() => {
  return [...props.slots].sort((a, b) => b.score - a.score)
})
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
    <!-- Header -->
    <div class="border-b border-gray-200 px-5 py-4 dark:border-gray-700">
      <div class="flex items-center gap-2">
        <ClockIcon class="h-5 w-5 text-primary-500" />
        <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
          최적 시간 슬롯
        </h3>
      </div>
      <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
        AI가 분석한 최적 업로드 시간대입니다
      </p>
    </div>

    <!-- Grid -->
    <div v-if="sortedSlots.length > 0" class="divide-y divide-gray-100 dark:divide-gray-700">
      <div
        v-for="slot in sortedSlots"
        :key="`${slot.date}-${slot.time}-${slot.platform}`"
        class="flex items-center gap-4 px-5 py-3 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700/50"
      >
        <!-- Date/Time -->
        <div class="flex-shrink-0 w-28">
          <p class="text-sm font-medium text-gray-900 dark:text-gray-100">
            {{ formattedDate(slot.date) }}
          </p>
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ slot.time }}</p>
        </div>

        <!-- Platform -->
        <span :class="['inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium flex-shrink-0', platformBadgeColor(slot.platform)]">
          {{ platformLabel(slot.platform) }}
        </span>

        <!-- Score Bar -->
        <div class="flex flex-1 items-center gap-2">
          <div class="flex-1 h-2 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
            <div
              :class="['h-full rounded-full transition-all duration-500', scoreBgColor(slot.score)]"
              :style="{ width: `${slot.score}%` }"
            />
          </div>
          <span :class="['w-10 text-right text-sm font-bold', scoreColor(slot.score)]">
            {{ slot.score }}
          </span>
        </div>

        <!-- Reason (hidden on mobile) -->
        <p class="hidden text-xs text-gray-500 dark:text-gray-400 tablet:block tablet:w-48 tablet:flex-shrink-0">
          {{ slot.reason }}
        </p>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else class="py-10 text-center">
      <ClockIcon class="mx-auto mb-2 h-10 w-10 text-gray-400 dark:text-gray-600" />
      <p class="text-sm text-gray-500 dark:text-gray-400">시간 슬롯 데이터가 없습니다</p>
    </div>
  </div>
</template>
