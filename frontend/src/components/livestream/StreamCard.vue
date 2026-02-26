<script setup lang="ts">
import {
  VideoCameraIcon,
  UserGroupIcon,
  ChatBubbleLeftRightIcon,
  ArrowTrendingUpIcon,
  CalendarIcon,
} from '@heroicons/vue/24/outline'
import type { LiveStream } from '@/types/liveStream'
import StreamStatusIndicator from './StreamStatusIndicator.vue'

defineProps<{
  stream: LiveStream
}>()

const emit = defineEmits<{
  select: [id: number]
}>()

const platformColors: Record<string, { bg: string; text: string }> = {
  YOUTUBE: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  INSTAGRAM: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300' },
  TIKTOK: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  NAVERCLIP: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
}

const getPlatformStyle = (platform: string) =>
  platformColors[platform.toUpperCase()] ?? platformColors.YOUTUBE

const formatDateTime = (iso: string) => {
  try {
    const date = new Date(iso)
    return date.toLocaleDateString('ko-KR', {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return iso
  }
}
</script>

<template>
  <div
    class="cursor-pointer rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
    @click="emit('select', stream.id)"
  >
    <!-- Header: Title + Status -->
    <div class="mb-3 flex items-start justify-between gap-2">
      <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 line-clamp-2">
        {{ stream.title }}
      </h3>
      <StreamStatusIndicator :status="stream.status" />
    </div>

    <!-- Platform badge -->
    <div class="mb-3 flex items-center gap-2">
      <span
        :class="[getPlatformStyle(stream.platform).bg, getPlatformStyle(stream.platform).text]"
        class="rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ stream.platform }}
      </span>
    </div>

    <!-- Scheduled time -->
    <div class="mb-3 flex items-center gap-1.5 text-xs text-gray-500 dark:text-gray-400">
      <CalendarIcon class="h-3.5 w-3.5" />
      <span>{{ formatDateTime(stream.scheduledAt) }}</span>
    </div>

    <!-- Stats row -->
    <div class="grid grid-cols-3 gap-2 border-t border-gray-100 pt-3 dark:border-gray-800">
      <!-- Viewers -->
      <div class="text-center">
        <div class="flex items-center justify-center gap-1 text-gray-400 dark:text-gray-500">
          <UserGroupIcon class="h-3.5 w-3.5" />
        </div>
        <p class="mt-0.5 text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ stream.viewerCount.toLocaleString() }}
        </p>
        <p class="text-[10px] text-gray-400 dark:text-gray-500">시청자</p>
      </div>

      <!-- Chat messages -->
      <div class="text-center">
        <div class="flex items-center justify-center gap-1 text-gray-400 dark:text-gray-500">
          <ChatBubbleLeftRightIcon class="h-3.5 w-3.5" />
        </div>
        <p class="mt-0.5 text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ stream.chatMessages.toLocaleString() }}
        </p>
        <p class="text-[10px] text-gray-400 dark:text-gray-500">채팅</p>
      </div>

      <!-- Peak viewers -->
      <div class="text-center">
        <div class="flex items-center justify-center gap-1 text-gray-400 dark:text-gray-500">
          <ArrowTrendingUpIcon class="h-3.5 w-3.5" />
        </div>
        <p class="mt-0.5 text-sm font-bold text-gray-900 dark:text-gray-100">
          {{ stream.peakViewers.toLocaleString() }}
        </p>
        <p class="text-[10px] text-gray-400 dark:text-gray-500">최대</p>
      </div>
    </div>
  </div>
</template>
