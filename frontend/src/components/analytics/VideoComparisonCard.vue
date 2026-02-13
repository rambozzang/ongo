<template>
  <div
    class="overflow-hidden rounded-lg border transition-all"
    :class="borderClass"
  >
    <!-- Thumbnail -->
    <div class="relative aspect-video w-full overflow-hidden bg-gray-100 dark:bg-gray-900">
      <img
        v-if="video.thumbnailUrl"
        :src="video.thumbnailUrl"
        :alt="video.title"
        class="h-full w-full object-cover"
      />
      <div v-else class="flex h-full w-full items-center justify-center">
        <FilmIcon class="h-16 w-16 text-gray-400" />
      </div>
    </div>

    <!-- Content -->
    <div class="space-y-3 p-4">
      <!-- Title -->
      <h3 class="line-clamp-2 text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ video.title }}
      </h3>

      <!-- Date -->
      <p class="text-xs text-gray-500 dark:text-gray-400">
        게시일: {{ formatDate(video.publishedAt) }}
      </p>

      <!-- Platforms -->
      <div class="flex flex-wrap gap-1">
        <PlatformBadge
          v-for="platform in video.platforms"
          :key="platform"
          :platform="platform"
        />
      </div>

      <!-- Stats -->
      <div class="grid grid-cols-2 gap-3 pt-2 text-xs">
        <div>
          <p class="mb-1 text-gray-500 dark:text-gray-400">조회수</p>
          <p class="font-semibold text-gray-900 dark:text-gray-100">
            {{ formatNumber(video.totalViews) }}
          </p>
          <!-- Relative bar for views -->
          <div class="mt-1 h-2 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
            <div
              class="h-full rounded-full transition-all"
              :class="barClass"
              :style="{ width: `${viewsBarWidth}%` }"
            />
          </div>
        </div>

        <div>
          <p class="mb-1 text-gray-500 dark:text-gray-400">좋아요</p>
          <p class="font-semibold text-gray-900 dark:text-gray-100">
            {{ formatNumber(video.totalLikes) }}
          </p>
          <!-- Relative bar for likes -->
          <div class="mt-1 h-2 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
            <div
              class="h-full rounded-full transition-all"
              :class="barClass"
              :style="{ width: `${likesBarWidth}%` }"
            />
          </div>
        </div>
      </div>

      <!-- Engagement Rate -->
      <div class="border-t border-gray-100 dark:border-gray-700 pt-3">
        <p class="text-xs text-gray-500 dark:text-gray-400">참여율</p>
        <p class="mt-1 text-lg font-bold" :class="textClass">
          {{ engagementRate }}%
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { FilmIcon } from '@heroicons/vue/24/outline'
import type { TopVideo } from '@/types/analytics'
import PlatformBadge from '@/components/common/PlatformBadge.vue'

interface Props {
  video: TopVideo
  color?: 'primary' | 'amber'
}

const props = withDefaults(defineProps<Props>(), {
  color: 'primary',
})

// Color classes
const borderClass = computed(() => {
  return props.color === 'primary'
    ? 'border-primary-300 dark:border-primary-700'
    : 'border-amber-300 dark:border-amber-700'
})

const barClass = computed(() => {
  return props.color === 'primary'
    ? 'bg-primary-500'
    : 'bg-amber-500'
})

const textClass = computed(() => {
  return props.color === 'primary'
    ? 'text-primary-600 dark:text-primary-400'
    : 'text-amber-600 dark:text-amber-400'
})

// Engagement rate
const engagementRate = computed(() => {
  if (props.video.totalViews === 0) return '0.00'
  return ((props.video.totalLikes / props.video.totalViews) * 100).toFixed(2)
})

// Bar widths (100% for this card's own max value)
const viewsBarWidth = computed(() => {
  return 100
})

const likesBarWidth = computed(() => {
  if (props.video.totalViews === 0) return 0
  return (props.video.totalLikes / props.video.totalViews) * 100
})

// Formatters
function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`
  return n.toLocaleString()
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}.${month}.${day}`
}
</script>
