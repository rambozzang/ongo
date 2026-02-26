<template>
  <div
    class="cursor-pointer rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
    :class="selected ? 'ring-2 ring-primary-500 dark:ring-primary-400' : ''"
    @click="$emit('select', portfolio.id)"
  >
    <!-- Header -->
    <div class="mb-3 flex items-start justify-between">
      <div class="min-w-0 flex-1">
        <h3 class="truncate text-base font-semibold text-gray-900 dark:text-gray-100">
          {{ portfolio.title }}
        </h3>
        <p class="mt-1 line-clamp-2 text-sm text-gray-500 dark:text-gray-400">
          {{ portfolio.description }}
        </p>
      </div>
    </div>

    <!-- Badges row -->
    <div class="mb-3 flex items-center gap-2">
      <TemplateBadge :template="portfolio.template" />
      <span
        class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
        :class="portfolio.isPublished
          ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300'
          : 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'"
      >
        {{ portfolio.isPublished ? '공개' : '비공개' }}
      </span>
    </div>

    <!-- Stats row -->
    <div class="mb-3 grid grid-cols-2 gap-2">
      <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800">
        <p class="text-xs text-gray-500 dark:text-gray-400">조회수</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ formatNumber(portfolio.viewCount) }}
        </p>
      </div>
      <div class="rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-800">
        <p class="text-xs text-gray-500 dark:text-gray-400">섹션</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ portfolio.sections.length }}개
        </p>
      </div>
    </div>

    <!-- Footer -->
    <div class="flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-700">
      <span class="text-xs text-gray-400 dark:text-gray-500">
        {{ formatDate(portfolio.updatedAt) }}
      </span>
      <div class="flex items-center gap-1">
        <button
          v-if="portfolio.isPublished && portfolio.publicUrl"
          class="rounded-lg px-2.5 py-1 text-xs font-medium text-primary-600 transition-colors hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-900/20"
          @click.stop="$emit('copyLink', portfolio.publicUrl!)"
        >
          링크 복사
        </button>
        <button
          class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-900/20 dark:hover:text-red-400"
          @click.stop="$emit('delete', portfolio.id)"
        >
          <TrashIcon class="h-4 w-4" />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { TrashIcon } from '@heroicons/vue/24/outline'
import TemplateBadge from '@/components/portfoliobuilder/TemplateBadge.vue'
import type { Portfolio } from '@/types/portfolioBuilder'

defineProps<{
  portfolio: Portfolio
  selected?: boolean
}>()

defineEmits<{
  select: [id: number]
  delete: [id: number]
  copyLink: [url: string]
}>()

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toLocaleString('ko-KR')
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}
</script>
