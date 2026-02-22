<template>
  <span
    v-if="hasOverflow"
    class="inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 text-xs font-medium"
    :class="hasOverflow ? 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400' : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'"
    :title="overflowDetail"
  >
    <svg class="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
    초과
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Platform } from '@/types/channel'

interface PlatformMeta {
  title: string
  description: string
  tags: string[]
}

interface Props {
  platform: Platform
  meta: PlatformMeta
}

const props = defineProps<Props>()

const CHAR_LIMITS: Partial<Record<Platform, { title?: number; description?: number }>> = {
  YOUTUBE: { title: 100, description: 5000 },
  TIKTOK: { title: 150 },
  INSTAGRAM: { description: 2200 },
  NAVER_CLIP: { title: 100, description: 1000 },
}

const overflowDetail = computed(() => {
  const limits = CHAR_LIMITS[props.platform]
  if (!limits) return ''
  const parts: string[] = []
  if (limits.title && props.meta.title.length > limits.title) {
    parts.push(`제목 ${props.meta.title.length}/${limits.title}`)
  }
  if (limits.description && props.meta.description.length > limits.description) {
    parts.push(`설명 ${props.meta.description.length}/${limits.description}`)
  }
  return parts.join(', ')
})

const hasOverflow = computed(() => {
  const limits = CHAR_LIMITS[props.platform]
  if (!limits) return false
  if (limits.title && props.meta.title.length > limits.title) return true
  if (limits.description && props.meta.description.length > limits.description) return true
  return false
})
</script>
