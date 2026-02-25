<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Bars3Icon, TrashIcon, EyeIcon, HeartIcon } from '@heroicons/vue/24/outline'
import type { ShowcaseContent, PlatformType } from '@/types/portfolio'

defineProps<{
  contents: ShowcaseContent[]
}>()

const emit = defineEmits<{
  (e: 'reorder', fromIndex: number, toIndex: number): void
  (e: 'remove', contentId: number): void
}>()

const { t } = useI18n()
const dragIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

const platformBadge: Record<PlatformType, { label: string; class: string }> = {
  youtube: { label: 'YT', class: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400' },
  tiktok: { label: 'TT', class: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300' },
  instagram: { label: 'IG', class: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400' },
  naver: { label: 'NV', class: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' },
}

function formatNumber(num: number): string {
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toLocaleString()
}

function onDragStart(index: number) {
  dragIndex.value = index
}

function onDragOver(e: DragEvent, index: number) {
  e.preventDefault()
  dragOverIndex.value = index
}

function onDragEnd() {
  if (dragIndex.value !== null && dragOverIndex.value !== null && dragIndex.value !== dragOverIndex.value) {
    emit('reorder', dragIndex.value, dragOverIndex.value)
  }
  dragIndex.value = null
  dragOverIndex.value = null
}
</script>

<template>
  <div class="card p-6">
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ t('portfolio.showcaseContents') }}
      </h2>
      <span class="text-xs text-gray-400 dark:text-gray-500">{{ t('portfolio.dragToReorder') }}</span>
    </div>

    <div v-if="contents.length === 0" class="py-8 text-center text-gray-400 dark:text-gray-500">
      {{ t('portfolio.noShowcase') }}
    </div>

    <div v-else class="space-y-3">
      <div
        v-for="(content, index) in contents"
        :key="content.id"
        draggable="true"
        :class="[
          'flex items-center gap-3 rounded-lg border p-3 transition-all',
          dragOverIndex === index
            ? 'border-primary-400 bg-primary-50 dark:border-primary-500 dark:bg-primary-900/20'
            : 'border-gray-200 bg-white hover:border-gray-300 dark:border-gray-700 dark:bg-gray-800 dark:hover:border-gray-600',
          dragIndex === index ? 'opacity-50' : '',
        ]"
        @dragstart="onDragStart(index)"
        @dragover="onDragOver($event, index)"
        @dragend="onDragEnd"
      >
        <!-- Drag Handle -->
        <div class="cursor-grab text-gray-400 active:cursor-grabbing dark:text-gray-500">
          <Bars3Icon class="h-5 w-5" />
        </div>

        <!-- Thumbnail placeholder -->
        <div class="flex h-14 w-20 shrink-0 items-center justify-center rounded bg-gray-100 dark:bg-gray-700">
          <span :class="['text-xs font-bold', platformBadge[content.platform].class, 'rounded px-1.5 py-0.5']">
            {{ platformBadge[content.platform].label }}
          </span>
        </div>

        <!-- Info -->
        <div class="min-w-0 flex-1">
          <div class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
            {{ content.title }}
          </div>
          <div class="mt-1 flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
            <span class="inline-flex items-center gap-1">
              <EyeIcon class="h-3.5 w-3.5" />
              {{ formatNumber(content.viewCount) }}
            </span>
            <span class="inline-flex items-center gap-1">
              <HeartIcon class="h-3.5 w-3.5" />
              {{ formatNumber(content.likeCount) }}
            </span>
            <span>{{ content.publishedAt }}</span>
          </div>
        </div>

        <!-- Delete -->
        <button
          class="shrink-0 text-gray-400 hover:text-red-500 dark:text-gray-500 dark:hover:text-red-400"
          @click="emit('remove', content.id)"
        >
          <TrashIcon class="h-4 w-4" />
        </button>
      </div>
    </div>
  </div>
</template>
