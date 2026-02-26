<script setup lang="ts">
import { computed } from 'vue'
import {
  EyeIcon,
  HeartIcon,
  ChatBubbleLeftIcon,
  CalendarIcon,
} from '@heroicons/vue/24/outline'
import type { SeriesEpisode } from '@/types/contentSeries'

interface Props {
  episodes: SeriesEpisode[]
}

interface Emits {
  (e: 'selectEpisode', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const sortedEpisodes = computed(() => {
  return [...props.episodes].sort((a, b) => a.episodeNumber - b.episodeNumber)
})

function statusConfig(status: SeriesEpisode['status']): { label: string; color: string } {
  const configs: Record<SeriesEpisode['status'], { label: string; color: string }> = {
    PLANNED: {
      label: '예정',
      color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    },
    DRAFTED: {
      label: '초안',
      color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300',
    },
    PUBLISHED: {
      label: '공개',
      color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300',
    },
    SKIPPED: {
      label: '건너뜀',
      color: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300',
    },
  }
  return configs[status] ?? configs.PLANNED
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

function formatNumber(num?: number): string {
  if (num == null) return '-'
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toLocaleString('ko-KR')
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 shadow-sm overflow-hidden">
    <!-- Header -->
    <div class="px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
      <div class="flex items-center justify-between">
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('contentSeries.episodeList') }}
        </h3>
        <span class="text-xs text-gray-500 dark:text-gray-400">
          {{ episodes.length }}{{ $t('contentSeries.episodeCount') }}
        </span>
      </div>
    </div>

    <!-- Empty state -->
    <div v-if="episodes.length === 0" class="p-8 text-center">
      <CalendarIcon class="w-10 h-10 text-gray-400 dark:text-gray-500 mx-auto mb-2" />
      <p class="text-sm text-gray-500 dark:text-gray-400">{{ $t('contentSeries.noEpisodes') }}</p>
    </div>

    <!-- Episode list (compact table) -->
    <div v-else class="divide-y divide-gray-200 dark:divide-gray-700 max-h-96 overflow-y-auto">
      <div
        v-for="episode in sortedEpisodes"
        :key="episode.id"
        class="flex items-center gap-3 px-4 py-3 hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors cursor-pointer"
        @click="emit('selectEpisode', episode.id)"
      >
        <!-- Episode number -->
        <div class="flex-shrink-0 w-9 h-9 rounded-full bg-gray-100 dark:bg-gray-800 flex items-center justify-center">
          <span class="text-sm font-semibold text-gray-700 dark:text-gray-300">
            {{ episode.episodeNumber }}
          </span>
        </div>

        <!-- Title & date -->
        <div class="flex-1 min-w-0">
          <p class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
            {{ episode.title }}
          </p>
          <p class="text-xs text-gray-500 dark:text-gray-400 mt-0.5 flex items-center gap-1">
            <CalendarIcon class="w-3 h-3" />
            {{ episode.publishedDate ? formatDate(episode.publishedDate) : formatDate(episode.scheduledDate) }}
          </p>
        </div>

        <!-- Status badge -->
        <span
          :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium flex-shrink-0', statusConfig(episode.status).color]"
        >
          {{ statusConfig(episode.status).label }}
        </span>

        <!-- Stats (only for published) -->
        <div v-if="episode.status === 'PUBLISHED'" class="hidden tablet:flex items-center gap-3 flex-shrink-0 text-xs text-gray-500 dark:text-gray-400">
          <span class="inline-flex items-center gap-1" :title="$t('contentSeries.views')">
            <EyeIcon class="w-3.5 h-3.5" />
            {{ formatNumber(episode.views) }}
          </span>
          <span class="inline-flex items-center gap-1" :title="$t('contentSeries.likes')">
            <HeartIcon class="w-3.5 h-3.5" />
            {{ formatNumber(episode.likes) }}
          </span>
          <span class="inline-flex items-center gap-1" :title="$t('contentSeries.comments')">
            <ChatBubbleLeftIcon class="w-3.5 h-3.5" />
            {{ formatNumber(episode.comments) }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
