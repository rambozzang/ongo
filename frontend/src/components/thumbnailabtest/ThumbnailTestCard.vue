<script setup lang="ts">
import { VideoCameraIcon } from '@heroicons/vue/24/outline'
import type { ThumbnailTest } from '@/types/thumbnailAbTest'
import TestStatusBadge from './TestStatusBadge.vue'
import ThumbnailVariantCompare from './ThumbnailVariantCompare.vue'

defineProps<{
  test: ThumbnailTest
}>()

const platformColors: Record<string, { bg: string; text: string }> = {
  youtube: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  instagram: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300' },
  tiktok: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  naverclip: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
}

const getPlatformStyle = (platform: string) => platformColors[platform.toLowerCase()] ?? platformColors.tiktok

const formatDate = (iso: string) => {
  try {
    const date = new Date(iso)
    return date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'short', day: 'numeric' })
  } catch {
    return iso
  }
}

const getCtrDiff = (a: number, b: number): string => {
  if (a === 0 && b === 0) return '-'
  const diff = Math.abs(a - b)
  return `+${diff.toFixed(1)}%p`
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <!-- Header -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <div class="flex items-center gap-2">
        <VideoCameraIcon class="h-4 w-4 text-gray-400 dark:text-gray-500" />
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ test.videoTitle }}
        </h3>
      </div>

      <span
        :class="[getPlatformStyle(test.platform).bg, getPlatformStyle(test.platform).text]"
        class="rounded-full px-2 py-0.5 text-xs font-medium capitalize"
      >
        {{ test.platform }}
      </span>

      <TestStatusBadge :status="test.status" />

      <span
        v-if="test.winner"
        class="ml-auto rounded-full bg-green-100 px-2.5 py-0.5 text-xs font-bold text-green-700 dark:bg-green-900/30 dark:text-green-300"
      >
        CTR {{ getCtrDiff(test.variantA.ctr, test.variantB.ctr) }}
      </span>
    </div>

    <!-- Variant Comparison -->
    <ThumbnailVariantCompare
      :variant-a="test.variantA"
      :variant-b="test.variantB"
      :winner="test.winner"
    />

    <!-- Footer: Dates -->
    <div class="mt-3 flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-800">
      <div class="flex items-center gap-4 text-xs text-gray-500 dark:text-gray-400">
        <span>
          시작: <span class="font-medium text-gray-700 dark:text-gray-300">{{ formatDate(test.startedAt) }}</span>
        </span>
        <span v-if="test.endedAt">
          종료: <span class="font-medium text-gray-700 dark:text-gray-300">{{ formatDate(test.endedAt) }}</span>
        </span>
        <span v-else-if="test.status === 'ACTIVE'" class="font-medium text-green-600 dark:text-green-400">
          진행 중
        </span>
        <span v-else-if="test.status === 'PENDING'" class="font-medium text-yellow-600 dark:text-yellow-400">
          대기 중
        </span>
      </div>
    </div>
  </div>
</template>
