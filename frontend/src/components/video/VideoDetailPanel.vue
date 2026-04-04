<template>
  <!-- Backdrop -->
  <div class="fixed inset-0 z-40 bg-black/50" @click="$emit('close')"></div>

  <!-- Slide Panel -->
  <div class="fixed inset-y-0 right-0 z-50 w-full max-w-lg overflow-y-auto bg-white shadow-xl dark:bg-gray-900">
    <!-- Header -->
    <div class="sticky top-0 z-10 flex items-center justify-between border-b border-gray-200 bg-white px-4 py-3 dark:border-gray-700 dark:bg-gray-900">
      <div class="flex items-center gap-2 min-w-0">
        <span
          class="inline-block h-3 w-3 rounded-full flex-shrink-0"
          :style="{ backgroundColor: PLATFORM_CONFIG[item.platform]?.color || '#666' }"
        ></span>
        <span class="truncate text-sm font-medium text-gray-900 dark:text-white">
          {{ PLATFORM_CONFIG[item.platform]?.label || item.platform }}
        </span>
        <span class="text-xs text-gray-500 dark:text-gray-400">· {{ item.channelName }}</span>
      </div>
      <button
        class="rounded-lg p-1.5 text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
        @click="$emit('close')"
      >
        <XMarkIcon class="h-5 w-5" />
      </button>
    </div>

    <!-- Content -->
    <div class="p-4 space-y-5">
      <!-- Thumbnail -->
      <a
        v-if="item.platformUrl"
        :href="item.platformUrl"
        target="_blank"
        rel="noopener noreferrer"
        class="block overflow-hidden rounded-lg"
      >
        <img
          v-if="item.thumbnailUrl"
          :src="item.thumbnailUrl"
          :alt="item.title"
          class="w-full aspect-video object-cover hover:opacity-90 transition-opacity"
        />
        <div v-else class="w-full aspect-video bg-gray-200 dark:bg-gray-700 flex items-center justify-center">
          <FilmIcon class="h-10 w-10 text-gray-400" />
        </div>
        <p class="mt-1 text-xs text-primary-600 dark:text-primary-400">{{ $t('videos.viewOnPlatform') }} &rarr;</p>
      </a>

      <!-- Title -->
      <h2 class="text-lg font-semibold text-gray-900 dark:text-white">{{ item.title }}</h2>

      <!-- Metrics Grid -->
      <div class="grid grid-cols-2 gap-3">
        <div class="rounded-lg bg-gray-50 p-3 dark:bg-gray-800">
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('videos.views') }}</p>
          <p class="text-xl font-bold tabular-nums text-gray-900 dark:text-white">{{ formatCount(item.viewCount) }}</p>
        </div>
        <div class="rounded-lg bg-gray-50 p-3 dark:bg-gray-800">
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('videos.likes') }}</p>
          <p class="text-xl font-bold tabular-nums text-gray-900 dark:text-white">{{ formatCount(item.likeCount) }}</p>
        </div>
        <div class="rounded-lg bg-gray-50 p-3 dark:bg-gray-800">
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('videos.comments') }}</p>
          <p class="text-xl font-bold tabular-nums text-gray-900 dark:text-white">{{ formatCount(item.commentCount) }}</p>
        </div>
        <div class="rounded-lg bg-gray-50 p-3 dark:bg-gray-800">
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('videos.shares') }}</p>
          <p class="text-xl font-bold tabular-nums text-gray-900 dark:text-white">{{ formatCount(item.shareCount) }}</p>
        </div>
      </div>

      <!-- Published Date -->
      <div v-if="item.publishedAt" class="text-sm text-gray-500 dark:text-gray-400">
        {{ $t('videos.publishedAt') }}: {{ formatDate(item.publishedAt) }}
      </div>

      <!-- Description -->
      <div v-if="item.description" class="space-y-1">
        <p
          class="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-line"
          :class="{ 'line-clamp-4': !showFullDesc }"
        >
          {{ item.description }}
        </p>
        <button
          v-if="item.description.length > 200"
          class="text-xs text-primary-600 hover:text-primary-700 dark:text-primary-400"
          @click="showFullDesc = !showFullDesc"
        >
          {{ showFullDesc ? '접기' : '더 보기' }}
        </button>
      </div>

      <!-- Comments Link -->
      <div class="border-t border-gray-200 pt-4 dark:border-gray-700">
        <router-link
          :to="`/comments?platform=${item.platform}&platformVideoId=${item.platformVideoId}`"
          class="btn-secondary inline-flex items-center gap-2 text-sm"
          @click="$emit('close')"
        >
          <ChatBubbleLeftIcon class="h-4 w-4" />
          {{ $t('videos.manageAllComments') }} &rarr;
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { XMarkIcon, FilmIcon, ChatBubbleLeftIcon } from '@heroicons/vue/24/outline'
import type { VideoFeedItem } from '@/types/video'
import { PLATFORM_CONFIG } from '@/types/channel'

defineProps<{
  item: VideoFeedItem
}>()

defineEmits<{
  close: []
}>()

const showFullDesc = ref(false)

function formatCount(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`
  return n.toString()
}

function formatDate(iso: string | null): string {
  if (!iso) return '-'
  try {
    return new Date(iso).toLocaleDateString()
  } catch {
    return iso
  }
}
</script>
