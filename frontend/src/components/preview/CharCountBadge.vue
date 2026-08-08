<template>
  <span
    v-if="hasOverflow"
    class="inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 text-caption"
    :class="hasOverflow ? 'bg-error-subtle text-error-strong' : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'"
    :title="overflowDetail"
  >
    <svg class="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
    </svg>
    {{ t('preview.exceeded') }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Platform } from '@/types/channel'

const { t } = useI18n({ useScope: 'global' })

interface PlatformMeta {
  title: string
  description: string
  tags: string[]
}

interface Props {
  platform: Platform
  meta: PlatformMeta
  limits?: { title?: number; description?: number; tags?: number }
}

const props = defineProps<Props>()

const CHAR_LIMITS: Partial<Record<Platform, { title?: number; description?: number; tags?: number }>> = {
  YOUTUBE: { title: 100, description: 5000, tags: 500 },
  TIKTOK: { title: 2200, tags: 30 },
  INSTAGRAM: { description: 2200, tags: 30 },
  NAVER_CLIP: { title: 100, description: 1000, tags: 30 },
  THREADS: { title: 500, tags: 30 },
  TWITTER: { title: 280, tags: 30 },
  FACEBOOK: { title: 255, description: 5000, tags: 30 },
}

const overflowDetail = computed(() => {
  const limits = props.limits ?? CHAR_LIMITS[props.platform]
  if (!limits) return ''
  const parts: string[] = []
  if (limits.title && props.meta.title.length > limits.title) {
    parts.push(t('preview.titleCount', { current: props.meta.title.length, max: limits.title }))
  }
  if (limits.description && props.meta.description.length > limits.description) {
    parts.push(t('preview.descCount', { current: props.meta.description.length, max: limits.description }))
  }
  if (limits.tags && props.meta.tags.length > limits.tags) {
    parts.push(t('preview.tagCount', { current: props.meta.tags.length, max: limits.tags }))
  }
  return parts.join(', ')
})

const hasOverflow = computed(() => {
  const limits = props.limits ?? CHAR_LIMITS[props.platform]
  if (!limits) return false
  if (limits.title && props.meta.title.length > limits.title) return true
  if (limits.description && props.meta.description.length > limits.description) return true
  if (limits.tags && props.meta.tags.length > limits.tags) return true
  return false
})
</script>
