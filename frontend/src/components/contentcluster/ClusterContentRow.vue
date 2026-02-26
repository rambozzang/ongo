<script setup lang="ts">
import { computed } from 'vue'
import type { ClusterContent } from '@/types/contentCluster'

interface Props {
  content: ClusterContent
}

const props = defineProps<Props>()

const platformLabel = computed(() => {
  const labels: Record<string, string> = {
    YOUTUBE: 'YouTube',
    TIKTOK: 'TikTok',
    INSTAGRAM: 'Instagram',
    NAVERCLIP: 'Naver Clip',
  }
  return labels[props.content.platform] || props.content.platform
})

const platformBadgeColor = computed(() => {
  const colors: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-gray-900 text-white dark:bg-gray-600 dark:text-gray-100',
    INSTAGRAM: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    NAVERCLIP: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }
  return colors[props.content.platform] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const formattedViews = computed(() => {
  const v = props.content.views
  if (v >= 10000) return `${(v / 10000).toFixed(1)}만`
  if (v >= 1000) return `${(v / 1000).toFixed(1)}천`
  return v.toLocaleString()
})

const formattedLikes = computed(() => {
  const v = props.content.likes
  if (v >= 10000) return `${(v / 10000).toFixed(1)}만`
  if (v >= 1000) return `${(v / 1000).toFixed(1)}천`
  return v.toLocaleString()
})

const similarityColor = computed(() => {
  const s = props.content.similarity * 100
  if (s >= 90) return 'bg-green-500 dark:bg-green-400'
  if (s >= 70) return 'bg-blue-500 dark:bg-blue-400'
  if (s >= 50) return 'bg-yellow-500 dark:bg-yellow-400'
  return 'bg-orange-500 dark:bg-orange-400'
})

const formattedDate = computed(() => {
  return new Date(props.content.publishedAt).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
})
</script>

<template>
  <div class="rounded-lg border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
    <div class="flex flex-col gap-3 tablet:flex-row tablet:items-center">
      <!-- Content Info -->
      <div class="flex-1 min-w-0">
        <div class="flex items-center gap-2 mb-1">
          <h4 class="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">
            {{ content.title }}
          </h4>
          <span :class="['inline-flex items-center rounded-full px-1.5 py-0.5 text-xs font-medium flex-shrink-0', platformBadgeColor]">
            {{ platformLabel }}
          </span>
        </div>
        <p class="text-xs text-gray-500 dark:text-gray-400">{{ formattedDate }}</p>
      </div>

      <!-- Stats -->
      <div class="flex items-center gap-4 tablet:gap-6">
        <div class="text-center">
          <p class="text-xs text-gray-500 dark:text-gray-400">조회수</p>
          <p class="text-sm font-bold text-gray-900 dark:text-gray-100">{{ formattedViews }}</p>
        </div>
        <div class="text-center">
          <p class="text-xs text-gray-500 dark:text-gray-400">좋아요</p>
          <p class="text-sm font-bold text-gray-900 dark:text-gray-100">{{ formattedLikes }}</p>
        </div>
        <div class="text-center">
          <p class="text-xs text-gray-500 dark:text-gray-400">참여율</p>
          <p class="text-sm font-bold text-gray-900 dark:text-gray-100">{{ content.engagement }}%</p>
        </div>
      </div>

      <!-- Similarity Bar -->
      <div class="flex items-center gap-2 tablet:w-36">
        <span class="text-xs text-gray-500 dark:text-gray-400 flex-shrink-0">유사도</span>
        <div class="flex-1 h-2 overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
          <div
            :class="['h-full rounded-full transition-all duration-500', similarityColor]"
            :style="{ width: `${content.similarity * 100}%` }"
          />
        </div>
        <span class="w-10 text-right text-xs font-semibold text-gray-700 dark:text-gray-300">
          {{ (content.similarity * 100).toFixed(0) }}%
        </span>
      </div>
    </div>
  </div>
</template>
