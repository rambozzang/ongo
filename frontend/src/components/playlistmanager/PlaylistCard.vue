<script setup lang="ts">
import { computed } from 'vue'
import {
  TrashIcon,
  ArrowPathIcon,
  PlayIcon,
  EyeIcon,
  ClockIcon,
  CheckBadgeIcon,
  ExclamationCircleIcon,
} from '@heroicons/vue/24/outline'
import type { Playlist, PlaylistPlatform, PlaylistVisibility } from '@/types/playlistManager'

interface Props {
  playlist: Playlist
}

interface Emits {
  (e: 'click', id: number): void
  (e: 'delete', id: number): void
  (e: 'sync', id: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const platformConfig = computed(() => {
  const configs: Record<PlaylistPlatform, { label: string; color: string; bg: string }> = {
    YOUTUBE: {
      label: 'YouTube',
      color: 'text-red-700 dark:text-red-400',
      bg: 'bg-red-100 dark:bg-red-900/30',
    },
    TIKTOK: {
      label: 'TikTok',
      color: 'text-pink-700 dark:text-pink-400',
      bg: 'bg-pink-100 dark:bg-pink-900/30',
    },
    INSTAGRAM: {
      label: 'Instagram',
      color: 'text-purple-700 dark:text-purple-400',
      bg: 'bg-purple-100 dark:bg-purple-900/30',
    },
    NAVER_CLIP: {
      label: 'Naver Clip',
      color: 'text-green-700 dark:text-green-400',
      bg: 'bg-green-100 dark:bg-green-900/30',
    },
  }
  return configs[props.playlist.platform]
})

const visibilityConfig = computed(() => {
  const configs: Record<PlaylistVisibility, { label: string; color: string }> = {
    PUBLIC: {
      label: '공개',
      color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    },
    UNLISTED: {
      label: '미등록',
      color: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
    },
    PRIVATE: {
      label: '비공개',
      color: 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300',
    },
  }
  return configs[props.playlist.visibility]
})

function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toLocaleString('ko-KR')
}

function formatDuration(seconds: number): string {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  if (hours > 0) return `${hours}시간 ${minutes}분`
  return `${minutes}분`
}

function formatSyncTime(dateStr?: string): string {
  if (!dateStr) return '동기화 안됨'
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 60) return `${diffMin}분 전 동기화`
  const diffHours = Math.floor(diffMin / 60)
  if (diffHours < 24) return `${diffHours}시간 전 동기화`
  const diffDays = Math.floor(diffHours / 24)
  return `${diffDays}일 전 동기화`
}
</script>

<template>
  <div
    class="bg-white/80 dark:bg-gray-900/80 backdrop-blur rounded-2xl border border-gray-200 dark:border-gray-700 shadow-lg p-6 hover:shadow-xl transition-all duration-200 cursor-pointer group relative"
    @click="emit('click', playlist.id)"
  >
    <!-- Top: Platform badge & visibility -->
    <div class="flex items-center justify-between mb-4">
      <span
        :class="['inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium', platformConfig.bg, platformConfig.color]"
      >
        {{ platformConfig.label }}
      </span>
      <span
        :class="['inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium', visibilityConfig.color]"
      >
        {{ visibilityConfig.label }}
      </span>
    </div>

    <!-- Thumbnail or placeholder -->
    <div class="relative mb-4 rounded-xl overflow-hidden bg-gray-100 dark:bg-gray-800 h-32 flex items-center justify-center">
      <img
        v-if="playlist.thumbnailUrl"
        :src="playlist.thumbnailUrl"
        :alt="playlist.title"
        class="w-full h-full object-cover"
      />
      <div v-else class="flex flex-col items-center gap-2 text-gray-400 dark:text-gray-500">
        <PlayIcon class="w-10 h-10" />
        <span class="text-xs">{{ playlist.videoCount }}개 동영상</span>
      </div>
    </div>

    <!-- Title -->
    <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100 truncate mb-3">
      {{ playlist.title }}
    </h3>

    <!-- Stats row -->
    <div class="grid grid-cols-3 gap-2 mb-4 text-center">
      <div class="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
        <div class="flex items-center justify-center gap-1 text-gray-500 dark:text-gray-400 mb-0.5">
          <PlayIcon class="w-3 h-3" />
        </div>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ playlist.videoCount }}
        </p>
        <p class="text-[10px] text-gray-500 dark:text-gray-400">동영상</p>
      </div>
      <div class="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
        <div class="flex items-center justify-center gap-1 text-gray-500 dark:text-gray-400 mb-0.5">
          <EyeIcon class="w-3 h-3" />
        </div>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ formatNumber(playlist.totalViews) }}
        </p>
        <p class="text-[10px] text-gray-500 dark:text-gray-400">조회수</p>
      </div>
      <div class="bg-gray-50 dark:bg-gray-800 rounded-lg p-2">
        <div class="flex items-center justify-center gap-1 text-gray-500 dark:text-gray-400 mb-0.5">
          <ClockIcon class="w-3 h-3" />
        </div>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ formatDuration(playlist.totalDuration) }}
        </p>
        <p class="text-[10px] text-gray-500 dark:text-gray-400">총 시간</p>
      </div>
    </div>

    <!-- Tags -->
    <div v-if="playlist.tags.length > 0" class="flex flex-wrap gap-1 mb-4">
      <span
        v-for="tag in playlist.tags.slice(0, 4)"
        :key="tag"
        class="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400"
      >
        #{{ tag }}
      </span>
      <span
        v-if="playlist.tags.length > 4"
        class="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400"
      >
        +{{ playlist.tags.length - 4 }}
      </span>
    </div>

    <!-- Sync status & Actions -->
    <div class="flex items-center justify-between border-t border-gray-200 dark:border-gray-700 pt-3">
      <!-- Sync indicator -->
      <div class="flex items-center gap-1.5 text-xs">
        <CheckBadgeIcon
          v-if="playlist.isSynced"
          class="w-4 h-4 text-green-500 dark:text-green-400"
        />
        <ExclamationCircleIcon
          v-else
          class="w-4 h-4 text-yellow-500 dark:text-yellow-400"
        />
        <span class="text-gray-500 dark:text-gray-400">
          {{ formatSyncTime(playlist.lastSyncedAt) }}
        </span>
      </div>

      <!-- Action buttons -->
      <div class="flex items-center gap-1">
        <button
          @click.stop="emit('sync', playlist.id)"
          class="p-1.5 text-gray-400 hover:text-blue-500 hover:bg-blue-50 dark:hover:bg-blue-900/20 dark:hover:text-blue-400 rounded-lg transition-colors opacity-0 group-hover:opacity-100"
          title="동기화"
        >
          <ArrowPathIcon class="w-4 h-4" />
        </button>
        <button
          @click.stop="emit('delete', playlist.id)"
          class="p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 dark:hover:text-red-400 rounded-lg transition-colors opacity-0 group-hover:opacity-100"
          title="삭제"
        >
          <TrashIcon class="w-4 h-4" />
        </button>
      </div>
    </div>
  </div>
</template>
