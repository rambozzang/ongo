<script setup lang="ts">
import type { PlaylistPlatform } from '@/types/playlistManager'

interface Props {
  selected: string
}

interface Emits {
  (e: 'update:selected', value: string): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const platforms: { key: string; label: string; activeColor: string }[] = [
  {
    key: 'ALL',
    label: '전체',
    activeColor: 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900',
  },
  {
    key: 'YOUTUBE',
    label: 'YouTube',
    activeColor: 'bg-red-600 text-white dark:bg-red-500 dark:text-white',
  },
  {
    key: 'TIKTOK',
    label: 'TikTok',
    activeColor: 'bg-pink-600 text-white dark:bg-pink-500 dark:text-white',
  },
  {
    key: 'INSTAGRAM',
    label: 'Instagram',
    activeColor: 'bg-purple-600 text-white dark:bg-purple-500 dark:text-white',
  },
  {
    key: 'NAVER_CLIP',
    label: 'Naver Clip',
    activeColor: 'bg-green-600 text-white dark:bg-green-500 dark:text-white',
  },
]

function handleSelect(key: string) {
  emit('update:selected', key)
}
</script>

<template>
  <div class="flex items-center gap-2 flex-wrap">
    <button
      v-for="platform in platforms"
      :key="platform.key"
      @click="handleSelect(platform.key)"
      class="inline-flex items-center px-4 py-2 rounded-full text-sm font-medium transition-all duration-200"
      :class="selected === platform.key
        ? platform.activeColor + ' shadow-md'
        : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700'
      "
    >
      {{ platform.label }}
    </button>
  </div>
</template>
