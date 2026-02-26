<script setup lang="ts">
import { computed } from 'vue'
import {
  TrashIcon,
  EyeIcon,
  PlayCircleIcon,
  FilmIcon,
} from '@heroicons/vue/24/outline'
import type { ContentSeries } from '@/types/contentSeries'

interface Props {
  series: ContentSeries
}

interface Emits {
  (e: 'select', id: number): void
  (e: 'delete', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const statusConfig = computed(() => {
  const configs: Record<string, { label: string; color: string }> = {
    ACTIVE: {
      label: '진행중',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
    },
    PAUSED: {
      label: '일시정지',
      color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
    },
    COMPLETED: {
      label: '완료',
      color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-300',
    },
    ARCHIVED: {
      label: '보관',
      color: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300',
    },
  }
  return configs[props.series.status] ?? configs.ARCHIVED
})

const platformConfig = computed(() => {
  const configs: Record<string, { label: string; color: string }> = {
    youtube: {
      label: 'YouTube',
      color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
    },
    tiktok: {
      label: 'TikTok',
      color: 'bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-400',
    },
    instagram: {
      label: 'Instagram',
      color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
    },
    naverclip: {
      label: 'Naver Clip',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    },
  }
  return configs[props.series.platform] ?? { label: props.series.platform, color: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300' }
})

const frequencyLabel = computed(() => {
  const labels: Record<string, string> = {
    DAILY: '매일',
    WEEKLY: '매주',
    BIWEEKLY: '격주',
    MONTHLY: '매월',
    CUSTOM: '커스텀',
  }
  return labels[props.series.frequency] ?? props.series.frequency
})

const progressPercent = computed(() => {
  if (props.series.totalEpisodes === 0) return 0
  return Math.min(Math.round((props.series.publishedEpisodes / props.series.totalEpisodes) * 100), 100)
})

function formatNumber(num: number): string {
  if (num >= 1000000) {
    return `${(num / 1000000).toFixed(1)}M`
  }
  if (num >= 1000) {
    return `${(num / 1000).toFixed(1)}K`
  }
  return num.toLocaleString('ko-KR')
}
</script>

<template>
  <div
    class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm hover:shadow-lg dark:hover:shadow-gray-900/50 transition-all duration-200 cursor-pointer group relative"
    @click="emit('select', series.id)"
  >
    <!-- Cover image or placeholder -->
    <div class="relative mb-3 rounded-lg overflow-hidden bg-gray-100 dark:bg-gray-800 h-36 flex items-center justify-center">
      <img
        v-if="series.coverImageUrl"
        :src="series.coverImageUrl"
        :alt="series.title"
        class="w-full h-full object-cover"
      />
      <div v-else class="flex flex-col items-center gap-2 text-gray-400 dark:text-gray-500">
        <FilmIcon class="w-10 h-10" />
        <span class="text-xs">{{ $t('contentSeries.noCover') }}</span>
      </div>

      <!-- Status badge -->
      <span
        :class="['absolute top-2 left-2 inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', statusConfig.color]"
      >
        {{ statusConfig.label }}
      </span>

      <!-- Platform badge -->
      <span
        :class="['absolute top-2 right-2 inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', platformConfig.color]"
      >
        {{ platformConfig.label }}
      </span>
    </div>

    <!-- Title & description -->
    <div class="mb-3">
      <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100 truncate">
        {{ series.title }}
      </h3>
      <p class="text-sm text-gray-500 dark:text-gray-400 line-clamp-2 mt-1">
        {{ series.description }}
      </p>
    </div>

    <!-- Frequency & Episode progress -->
    <div class="mb-3">
      <div class="flex items-center justify-between text-sm mb-1">
        <span class="inline-flex items-center gap-1 text-gray-600 dark:text-gray-400">
          <PlayCircleIcon class="w-4 h-4" />
          {{ frequencyLabel }}
        </span>
        <span class="text-gray-700 dark:text-gray-300 font-medium">
          {{ series.publishedEpisodes }}/{{ series.totalEpisodes }} {{ $t('contentSeries.episodes') }}
        </span>
      </div>
      <div class="w-full h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
        <div
          class="h-full bg-gradient-to-r from-blue-500 to-purple-500 dark:from-blue-400 dark:to-purple-400 rounded-full transition-all duration-500"
          :style="{ width: `${progressPercent}%` }"
        ></div>
      </div>
    </div>

    <!-- Key stats -->
    <div class="grid grid-cols-3 gap-2 mb-3 text-center">
      <div class="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('contentSeries.avgViews') }}</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(series.avgViews) }}</p>
      </div>
      <div class="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('contentSeries.totalViews') }}</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ formatNumber(series.totalViews) }}</p>
      </div>
      <div class="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('contentSeries.avgRetention') }}</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ series.avgRetention }}%</p>
      </div>
    </div>

    <!-- Tags -->
    <div v-if="series.tags.length > 0" class="flex flex-wrap gap-1 mb-3">
      <span
        v-for="tag in series.tags.slice(0, 5)"
        :key="tag"
        class="inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400"
      >
        #{{ tag }}
      </span>
      <span
        v-if="series.tags.length > 5"
        class="inline-flex items-center px-2 py-0.5 rounded-full text-xs bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400"
      >
        +{{ series.tags.length - 5 }}
      </span>
    </div>

    <!-- Actions -->
    <div class="flex items-center justify-end gap-1 border-t border-gray-200 dark:border-gray-700 pt-3">
      <button
        @click.stop="emit('select', series.id)"
        class="inline-flex items-center gap-1 px-3 py-1.5 text-sm text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 rounded-lg transition-colors"
      >
        <EyeIcon class="w-4 h-4" />
        {{ $t('contentSeries.viewDetails') }}
      </button>
      <button
        @click.stop="emit('delete', series.id)"
        class="inline-flex items-center gap-1 px-3 py-1.5 text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
      >
        <TrashIcon class="w-4 h-4" />
        {{ $t('contentSeries.delete') }}
      </button>
    </div>
  </div>
</template>
