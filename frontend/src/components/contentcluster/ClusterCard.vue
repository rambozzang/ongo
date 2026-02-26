<script setup lang="ts">
import { computed } from 'vue'
import {
  FolderIcon,
  EyeIcon,
  HandThumbUpIcon,
} from '@heroicons/vue/24/outline'
import type { ContentCluster } from '@/types/contentCluster'

interface Props {
  cluster: ContentCluster
}

const props = defineProps<Props>()

const emit = defineEmits<{
  select: [cluster: ContentCluster]
}>()

const platformLabel = computed(() => {
  const labels: Record<string, string> = {
    YOUTUBE: 'YouTube',
    TIKTOK: 'TikTok',
    INSTAGRAM: 'Instagram',
    NAVERCLIP: 'Naver Clip',
  }
  return labels[props.cluster.platform] || props.cluster.platform
})

const platformBadgeColor = computed(() => {
  const colors: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-gray-900 text-white dark:bg-gray-600 dark:text-gray-100',
    INSTAGRAM: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    NAVERCLIP: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }
  return colors[props.cluster.platform] || 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const formattedViews = computed(() => {
  const v = props.cluster.avgViews
  if (v >= 10000) return `${(v / 10000).toFixed(1)}만`
  if (v >= 1000) return `${(v / 1000).toFixed(1)}천`
  return v.toLocaleString()
})
</script>

<template>
  <div
    class="cursor-pointer rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition-all hover:border-blue-300 hover:shadow-md dark:border-gray-700 dark:bg-gray-800 dark:hover:border-blue-600"
    @click="emit('select', cluster)"
  >
    <!-- Header -->
    <div class="mb-3 flex items-start justify-between">
      <div class="flex items-start gap-3 flex-1 min-w-0">
        <FolderIcon class="h-5 w-5 flex-shrink-0 mt-0.5 text-blue-500" />
        <div class="flex-1 min-w-0">
          <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100 truncate">
            {{ cluster.name }}
          </h3>
          <p class="mt-1 text-sm text-gray-600 dark:text-gray-400 line-clamp-2">
            {{ cluster.description }}
          </p>
        </div>
      </div>
    </div>

    <!-- Platform Badge -->
    <div class="mb-3">
      <span :class="['inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', platformBadgeColor]">
        {{ platformLabel }}
      </span>
    </div>

    <!-- Stats -->
    <div class="mb-3 grid grid-cols-3 gap-3">
      <div class="text-center">
        <div class="flex items-center justify-center gap-1">
          <FolderIcon class="h-3.5 w-3.5 text-gray-400" />
          <span class="text-xs text-gray-500 dark:text-gray-400">콘텐츠</span>
        </div>
        <p class="mt-0.5 text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ cluster.contentCount }}
        </p>
      </div>
      <div class="text-center">
        <div class="flex items-center justify-center gap-1">
          <EyeIcon class="h-3.5 w-3.5 text-gray-400" />
          <span class="text-xs text-gray-500 dark:text-gray-400">평균 조회</span>
        </div>
        <p class="mt-0.5 text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ formattedViews }}
        </p>
      </div>
      <div class="text-center">
        <div class="flex items-center justify-center gap-1">
          <HandThumbUpIcon class="h-3.5 w-3.5 text-gray-400" />
          <span class="text-xs text-gray-500 dark:text-gray-400">참여율</span>
        </div>
        <p class="mt-0.5 text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ cluster.avgEngagement }}%
        </p>
      </div>
    </div>

    <!-- Tags -->
    <div class="flex flex-wrap gap-1.5">
      <span
        v-for="tag in cluster.tags"
        :key="tag"
        class="inline-flex items-center rounded bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-700 dark:bg-gray-700 dark:text-gray-300"
      >
        #{{ tag }}
      </span>
    </div>
  </div>
</template>
