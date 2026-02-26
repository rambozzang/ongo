<script setup lang="ts">
interface Props {
  category: 'BADGE' | 'EXCLUSIVE' | 'SHOUTOUT' | 'MERCH' | 'MEETUP'
}

defineProps<Props>()

const categoryConfig: Record<string, { label: string; class: string }> = {
  BADGE: {
    label: '뱃지',
    class: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
  },
  EXCLUSIVE: {
    label: '독점 콘텐츠',
    class: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300',
  },
  SHOUTOUT: {
    label: '샤아웃',
    class: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-300',
  },
  MERCH: {
    label: '굿즈',
    class: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300',
  },
  MEETUP: {
    label: '밋업',
    class: 'bg-teal-100 text-teal-700 dark:bg-teal-900/30 dark:text-teal-300',
  },
}
</script>

<template>
  <span
    :class="categoryConfig[category].class"
    class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
  >
    {{ categoryConfig[category].label }}
  </span>
</template>
