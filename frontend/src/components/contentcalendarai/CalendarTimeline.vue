<script setup lang="ts">
import { computed } from 'vue'
import { CalendarDaysIcon } from '@heroicons/vue/24/outline'
import type { CalendarSuggestion } from '@/types/contentCalendarAi'

interface Props {
  suggestions: CalendarSuggestion[]
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

const platformDotColor = (platform: string): string => {
  const colors: Record<string, string> = {
    YOUTUBE: 'bg-red-500',
    TIKTOK: 'bg-gray-800 dark:bg-gray-400',
    INSTAGRAM: 'bg-pink-500',
    NAVERCLIP: 'bg-green-500',
  }
  return colors[platform] || 'bg-gray-400'
}

const platformBorderColor = (platform: string): string => {
  const colors: Record<string, string> = {
    YOUTUBE: 'border-l-red-400 dark:border-l-red-600',
    TIKTOK: 'border-l-gray-700 dark:border-l-gray-400',
    INSTAGRAM: 'border-l-pink-400 dark:border-l-pink-600',
    NAVERCLIP: 'border-l-green-400 dark:border-l-green-600',
  }
  return colors[platform] || 'border-l-gray-300 dark:border-l-gray-600'
}

const statusDotColor = (status: string): string => {
  const colors: Record<string, string> = {
    PENDING: 'bg-yellow-400',
    ACCEPTED: 'bg-green-400',
    REJECTED: 'bg-red-400',
  }
  return colors[status] || 'bg-gray-400'
}

// Group suggestions by date
const groupedByDate = computed(() => {
  const groups: Record<string, CalendarSuggestion[]> = {}
  const sorted = [...props.suggestions].sort(
    (a, b) => new Date(a.suggestedDate).getTime() - new Date(b.suggestedDate).getTime()
  )
  for (const s of sorted) {
    if (!groups[s.suggestedDate]) {
      groups[s.suggestedDate] = []
    }
    groups[s.suggestedDate].push(s)
  }
  return groups
})

const dateEntries = computed(() => Object.entries(groupedByDate.value))

const formatDateLabel = (date: string): string => {
  return new Date(date).toLocaleDateString('ko-KR', {
    month: 'long',
    day: 'numeric',
    weekday: 'long',
  })
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
    <!-- Header -->
    <div class="border-b border-gray-200 px-5 py-4 dark:border-gray-700">
      <div class="flex items-center gap-2">
        <CalendarDaysIcon class="h-5 w-5 text-primary-500" />
        <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
          주간 타임라인
        </h3>
      </div>
    </div>

    <!-- Timeline -->
    <div v-if="dateEntries.length > 0" class="p-5">
      <div class="space-y-6">
        <div v-for="[date, items] in dateEntries" :key="date">
          <!-- Date Header -->
          <div class="mb-3 flex items-center gap-3">
            <div class="h-3 w-3 rounded-full bg-blue-500 dark:bg-blue-400 flex-shrink-0" />
            <h4 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
              {{ formatDateLabel(date) }}
            </h4>
          </div>

          <!-- Items for this date -->
          <div class="ml-1.5 space-y-2 border-l-2 border-gray-200 pl-5 dark:border-gray-700">
            <div
              v-for="item in items"
              :key="item.id"
              :class="[
                'rounded-lg border-l-4 bg-gray-50 px-4 py-3 dark:bg-gray-900/50',
                platformBorderColor(item.platform),
              ]"
            >
              <div class="flex items-start justify-between gap-2">
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-2 mb-1">
                    <span class="text-xs font-medium text-gray-500 dark:text-gray-400">
                      {{ item.suggestedTime }}
                    </span>
                    <span :class="['inline-block h-2 w-2 rounded-full', statusDotColor(item.status)]" />
                  </div>
                  <p class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
                    {{ item.title }}
                  </p>
                  <div class="mt-1 flex items-center gap-2">
                    <span :class="['inline-block h-2 w-2 rounded-full', platformDotColor(item.platform)]" />
                    <span class="text-xs text-gray-500 dark:text-gray-400">
                      {{ platformLabel(item.platform) }}
                    </span>
                  </div>
                </div>
                <div class="flex-shrink-0 text-right">
                  <p class="text-xs text-gray-500 dark:text-gray-400">참여율</p>
                  <p class="text-sm font-bold text-gray-900 dark:text-gray-100">
                    {{ item.expectedEngagement }}%
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-else class="py-10 text-center">
      <CalendarDaysIcon class="mx-auto mb-2 h-10 w-10 text-gray-400 dark:text-gray-600" />
      <p class="text-sm text-gray-500 dark:text-gray-400">타임라인 데이터가 없습니다</p>
    </div>
  </div>
</template>
