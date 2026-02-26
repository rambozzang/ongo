<script setup lang="ts">
import {
  ArrowRightIcon,
  StarIcon,
} from '@heroicons/vue/24/outline'
import { StarIcon as StarSolidIcon } from '@heroicons/vue/24/solid'
import type { RepurposeTemplate } from '@/types/contentRepurposer'

defineProps<{
  template: RepurposeTemplate
}>()

const emit = defineEmits<{
  use: [id: number]
}>()

const platformColors: Record<string, { bg: string; text: string }> = {
  YOUTUBE: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  INSTAGRAM: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300' },
  TIKTOK: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  NAVER_CLIP: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
}

const formatLabels: Record<string, string> = {
  SHORT: 'Shorts',
  REEL: 'Reels',
  CLIP: 'Clip',
  STORY: 'Story',
}

const getPlatformStyle = (platform: string) => platformColors[platform] ?? platformColors.TIKTOK
const getFormatLabel = (format: string) => formatLabels[format] ?? format
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <!-- Header: Name + Default badge -->
    <div class="mb-3 flex items-center justify-between">
      <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
        {{ template.name }}
      </h3>
      <component
        :is="template.isDefault ? StarSolidIcon : StarIcon"
        class="h-4 w-4"
        :class="template.isDefault ? 'text-yellow-500' : 'text-gray-300 dark:text-gray-600'"
      />
    </div>

    <!-- Platform flow -->
    <div class="mb-3 flex items-center gap-2">
      <span
        :class="[getPlatformStyle(template.sourcePlatform).bg, getPlatformStyle(template.sourcePlatform).text]"
        class="rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ template.sourcePlatform }}
      </span>

      <ArrowRightIcon class="h-3.5 w-3.5 text-gray-400 dark:text-gray-500" />

      <span
        :class="[getPlatformStyle(template.targetPlatform).bg, getPlatformStyle(template.targetPlatform).text]"
        class="rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ template.targetPlatform }}
      </span>

      <span class="rounded-md bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700 dark:bg-blue-900/30 dark:text-blue-300">
        {{ getFormatLabel(template.targetFormat) }}
      </span>
    </div>

    <!-- Description -->
    <p class="mb-4 text-xs text-gray-600 dark:text-gray-400 line-clamp-2">
      {{ template.description }}
    </p>

    <!-- Use button -->
    <button
      class="w-full rounded-lg border border-primary-200 bg-primary-50 px-3 py-2 text-xs font-medium text-primary-700 transition-colors hover:bg-primary-100 dark:border-primary-800 dark:bg-primary-900/20 dark:text-primary-300 dark:hover:bg-primary-900/40"
      @click="emit('use', template.id)"
    >
      이 템플릿 사용
    </button>
  </div>
</template>
