<script setup lang="ts">
import type { NotificationCategory } from '@/types/notification'

defineProps<{
  activeCategory: NotificationCategory | null
  unreadCountByCategory: Record<string, number>
}>()

const emit = defineEmits<{
  (e: 'select', category: NotificationCategory | null): void
}>()

interface FilterTab {
  key: NotificationCategory | null
  label: string
}

const tabs: FilterTab[] = [
  { key: null, label: '전체' },
  { key: 'upload', label: '업로드' },
  { key: 'schedule', label: '예약' },
  { key: 'channel', label: '채널' },
  { key: 'ai', label: 'AI' },
  { key: 'analytics', label: '분석' },
  { key: 'subscription', label: '구독' },
]

function totalUnread(
  countMap: Record<string, number>,
): number {
  return Object.values(countMap).reduce((sum, c) => sum + c, 0)
}

function getBadgeCount(
  key: NotificationCategory | null,
  countMap: Record<string, number>,
): number {
  if (key === null) return totalUnread(countMap)
  return countMap[key] ?? 0
}
</script>

<template>
  <div class="flex flex-wrap gap-2">
    <button
      v-for="tab in tabs"
      :key="tab.key ?? 'all'"
      class="relative inline-flex items-center gap-1.5 rounded-md border px-3 py-1.5 text-body-sm font-semibold transition-colors"
      :class="[
        activeCategory === tab.key
          ? 'border-accent bg-accent text-accent-on'
          : 'border-line-control bg-surface-raised text-content-secondary hover:border-line-hover hover:text-content',
      ]"
      @click="emit('select', tab.key)"
    >
      {{ tab.label }}
      <span
        v-if="getBadgeCount(tab.key, unreadCountByCategory) > 0"
        class="inline-flex h-5 min-w-[1.25rem] items-center justify-center rounded-full px-1 text-body-xs font-bold"
        :class="[
          activeCategory === tab.key
            ? 'bg-white/25 text-white'
            : 'bg-bad text-accent-on',
        ]"
      >
        {{ getBadgeCount(tab.key, unreadCountByCategory) }}
      </span>
    </button>
  </div>
</template>
