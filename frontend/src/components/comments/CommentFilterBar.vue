<template>
  <div class="card">
    <div class="flex flex-wrap gap-4">
      <!-- 플랫폼 필터 -->
      <div class="flex-1 min-w-[200px]">
        <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ $t('commentsView.filterPlatform') }}
        </label>
        <select
          :value="platform"
          class="input-field"
          @change="emit('update:platform', ($event.target as HTMLSelectElement).value as Platform | 'ALL')"
        >
          <option value="ALL">{{ $t('commentsView.filterAllPlatforms') }}</option>
          <option v-for="p in platformOptions" :key="p.value" :value="p.value">
            {{ p.label }}
          </option>
        </select>
      </div>

      <!-- 감정 필터 -->
      <div class="flex-1 min-w-[200px]">
        <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ $t('commentsView.filterSentiment') }}
        </label>
        <select
          :value="sentiment"
          class="input-field"
          @change="emit('update:sentiment', ($event.target as HTMLSelectElement).value as CommentSentiment | 'ALL')"
        >
          <option value="ALL">{{ $t('commentsView.filterAll') }}</option>
          <option value="positive">{{ $t('commentsView.sentimentPositive') }}</option>
          <option value="neutral">{{ $t('commentsView.sentimentNeutral') }}</option>
          <option value="negative">{{ $t('commentsView.sentimentNegative') }}</option>
          <option value="unanalyzed">{{ $t('commentsView.sentimentUnanalyzed') }}</option>
        </select>
      </div>

      <!-- 검색 -->
      <div class="flex-1 min-w-[200px]">
        <label class="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ $t('commentsView.filterSearch') }}
        </label>
        <input
          :value="searchText"
          type="text"
          :placeholder="$t('commentsView.filterSearchPlaceholder')"
          class="input-field"
          @input="emit('update:searchText', ($event.target as HTMLInputElement).value)"
          @keyup.enter="emit('submit')"
        />
      </div>
    </div>

    <!-- 정렬 -->
    <div class="mt-4 flex flex-wrap items-center gap-4">
      <div class="flex items-center gap-2">
        <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ $t('commentsView.sortLabel') }}
        </span>
        <button
          v-for="option in sortOptions"
          :key="option.value"
          class="rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
          :class="
            sortBy === option.value
              ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
              : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
          "
          @click="emit('update:sortBy', option.value)"
        >
          {{ option.label }}
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
export type CommentSortBy = 'recent' | 'likes' | 'replies'
</script>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CommentSentiment } from '@/types/comment'
import type { Platform } from '@/types/channel'

defineProps<{
  platform: Platform | 'ALL'
  sentiment: CommentSentiment | 'ALL'
  searchText: string
  sortBy: CommentSortBy
}>()

const emit = defineEmits<{
  'update:platform': [value: Platform | 'ALL']
  'update:sentiment': [value: CommentSentiment | 'ALL']
  'update:searchText': [value: string]
  'update:sortBy': [value: CommentSortBy]
  submit: []
}>()

const { t } = useI18n({ useScope: 'global' })

const platformOptions = [
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'TWITTER', label: 'X (Twitter)' },
  { value: 'FACEBOOK', label: 'Facebook' },
  { value: 'THREADS', label: 'Threads' },
  { value: 'LINKEDIN', label: 'LinkedIn' },
  { value: 'WORDPRESS', label: 'WordPress' },
  { value: 'TUMBLR', label: 'Tumblr' },
  { value: 'VIMEO', label: 'Vimeo' },
  { value: 'DAILYMOTION', label: 'Dailymotion' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'NAVER_CLIP', label: 'Naver Clip' },
  { value: 'PINTEREST', label: 'Pinterest' },
]

const sortOptions = computed<{ value: CommentSortBy; label: string }[]>(() => [
  { value: 'recent', label: t('commentsView.sortRecent') },
  { value: 'likes', label: t('commentsView.sortLikes') },
  { value: 'replies', label: t('commentsView.sortReplies') },
])
</script>
