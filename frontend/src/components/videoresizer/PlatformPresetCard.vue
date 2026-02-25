<template>
  <button
    type="button"
    class="group flex flex-col items-center gap-2 rounded-xl border-2 p-4 transition-all"
    :class="[
      isSelected
        ? 'border-primary-500 bg-primary-50 dark:border-primary-400 dark:bg-primary-900/20'
        : disabled
          ? 'cursor-not-allowed border-gray-200 bg-gray-50 opacity-50 dark:border-gray-700 dark:bg-gray-800/50'
          : 'border-gray-200 bg-white hover:border-primary-300 hover:shadow-md dark:border-gray-700 dark:bg-gray-800 dark:hover:border-primary-600',
    ]"
    :disabled="disabled"
    @click="emit('select')"
  >
    <!-- Platform Icon -->
    <div
      class="flex h-8 w-8 items-center justify-center rounded-full"
      :style="{ backgroundColor: platformColor + '20' }"
    >
      <span class="text-xs font-bold" :style="{ color: platformColor }">
        {{ platformIcon }}
      </span>
    </div>

    <!-- Aspect Ratio Preview Rectangle -->
    <div class="flex h-16 w-16 items-center justify-center">
      <div
        class="border-2 transition-colors"
        :class="isSelected
          ? 'border-primary-500 bg-primary-100 dark:border-primary-400 dark:bg-primary-900/40'
          : 'border-gray-300 bg-gray-100 dark:border-gray-600 dark:bg-gray-700'"
        :style="previewStyle"
      />
    </div>

    <!-- Label -->
    <span
      class="text-xs font-semibold"
      :class="isSelected
        ? 'text-primary-700 dark:text-primary-300'
        : 'text-gray-700 dark:text-gray-300'"
    >
      {{ preset.label }}
    </span>

    <!-- Aspect Ratio -->
    <span class="text-xs text-gray-500 dark:text-gray-400">
      {{ preset.aspectRatio }}
    </span>

    <!-- Dimensions -->
    <span class="text-[10px] text-gray-400 dark:text-gray-500">
      {{ preset.width }} x {{ preset.height }}
    </span>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ResizePreset, PlatformTarget } from '@/types/videoResizer'

const props = defineProps<{
  preset: ResizePreset
  isSelected: boolean
  disabled: boolean
}>()

const emit = defineEmits<{
  select: []
}>()

const PLATFORM_COLORS: Record<PlatformTarget, string> = {
  YOUTUBE: '#FF0000',
  TIKTOK: '#000000',
  INSTAGRAM_REELS: '#E1306C',
  NAVER_CLIP: '#03C75A',
  YOUTUBE_SHORTS: '#FF0000',
}

const PLATFORM_ICONS: Record<PlatformTarget, string> = {
  YOUTUBE: 'YT',
  TIKTOK: 'TT',
  INSTAGRAM_REELS: 'IG',
  NAVER_CLIP: 'NC',
  YOUTUBE_SHORTS: 'YS',
}

const platformColor = computed(() => PLATFORM_COLORS[props.preset.platform])
const platformIcon = computed(() => PLATFORM_ICONS[props.preset.platform])

const previewStyle = computed(() => {
  const [w, h] = props.preset.aspectRatio.split(':').map(Number)
  const maxSize = 48
  const ratio = w / h
  const width = ratio >= 1 ? maxSize : Math.round(maxSize * ratio)
  const height = ratio >= 1 ? Math.round(maxSize / ratio) : maxSize
  return {
    width: `${width}px`,
    height: `${height}px`,
    borderRadius: '4px',
  }
})
</script>
