<script setup lang="ts">
import { FolderIcon, TrashIcon } from '@heroicons/vue/24/outline'
import type { HashtagGroup } from '@/types/hashtagAnalytics'

defineProps<{
  group: HashtagGroup
}>()

const emit = defineEmits<{
  delete: [id: number]
}>()

const platformColors: Record<string, { bg: string; text: string }> = {
  YOUTUBE: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  INSTAGRAM: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300' },
  TIKTOK: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  NAVERCLIP: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
}

const getPlatformStyle = (platform: string) => platformColors[platform] ?? platformColors.TIKTOK
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <!-- Header: Group name + platform badge -->
    <div class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <FolderIcon class="h-4 w-4 text-primary-500" />
        <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ group.name }}
        </span>
      </div>
      <span
        :class="[getPlatformStyle(group.platform).bg, getPlatformStyle(group.platform).text]"
        class="rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ group.platform }}
      </span>
    </div>

    <!-- Hashtag pills -->
    <div class="mb-3 flex flex-wrap gap-1.5">
      <span
        v-for="tag in group.hashtags"
        :key="tag"
        class="rounded-full bg-primary-50 px-2.5 py-1 text-xs font-medium text-primary-700 dark:bg-primary-900/20 dark:text-primary-300"
      >
        {{ tag }}
      </span>
    </div>

    <!-- Footer: Usage count + Delete -->
    <div class="flex items-center justify-between border-t border-gray-100 pt-3 dark:border-gray-800">
      <span class="text-xs text-gray-500 dark:text-gray-400">
        사용 횟수:
        <span class="font-semibold text-gray-700 dark:text-gray-300">
          {{ group.usageCount }}
        </span>
      </span>
      <button
        class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-900/20 dark:hover:text-red-400"
        title="그룹 삭제"
        @click.stop="emit('delete', group.id)"
      >
        <TrashIcon class="h-4 w-4" />
      </button>
    </div>
  </div>
</template>
