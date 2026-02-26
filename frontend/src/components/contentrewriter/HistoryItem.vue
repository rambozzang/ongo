<template>
  <button
    class="flex w-full items-center gap-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-3 py-2.5 text-left transition-colors hover:bg-gray-50 dark:hover:bg-gray-700/50"
    @click="emit('select', item)"
  >
    <!-- Format Icon -->
    <div
      class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg"
      :class="iconBgClass"
    >
      <component :is="formatIcon" class="h-4 w-4" :class="iconColorClass" />
    </div>

    <!-- Content -->
    <div class="min-w-0 flex-1">
      <div class="mb-0.5 flex items-center gap-2">
        <span class="text-xs font-semibold text-gray-900 dark:text-gray-100">
          {{ $t(`contentRewriter.format.${item.format}`) }}
        </span>
        <span class="rounded bg-gray-100 dark:bg-gray-700 px-1.5 py-0.5 text-[10px] text-gray-500 dark:text-gray-400">
          {{ item.platform }}
        </span>
      </div>
      <p class="truncate text-xs text-gray-500 dark:text-gray-400">
        {{ item.text }}
      </p>
    </div>

    <!-- Date -->
    <span class="flex-shrink-0 text-[10px] text-gray-400 dark:text-gray-500">
      {{ formattedDate }}
    </span>
  </button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  VideoCameraIcon,
  ChatBubbleLeftIcon,
  DocumentTextIcon,
  PhotoIcon,
  EnvelopeIcon,
  QueueListIcon,
} from '@heroicons/vue/24/outline'
import type { RewriteResult, OutputFormat } from '@/types/contentRewriter'

const props = defineProps<{
  item: RewriteResult
}>()

const emit = defineEmits<{
  select: [item: RewriteResult]
}>()

const iconMap: Record<OutputFormat, ReturnType<typeof VideoCameraIcon>> = {
  SHORT_VIDEO: VideoCameraIcon,
  TWEET: ChatBubbleLeftIcon,
  BLOG_POST: DocumentTextIcon,
  CAPTION: PhotoIcon,
  NEWSLETTER: EnvelopeIcon,
  THREAD: QueueListIcon,
}

const formatIcon = computed(() => iconMap[props.item.format] ?? DocumentTextIcon)

const iconBgClass = computed(() => {
  const classMap: Record<string, string> = {
    SHORT_VIDEO: 'bg-purple-100 dark:bg-purple-900/30',
    TWEET: 'bg-sky-100 dark:bg-sky-900/30',
    BLOG_POST: 'bg-green-100 dark:bg-green-900/30',
    CAPTION: 'bg-pink-100 dark:bg-pink-900/30',
    NEWSLETTER: 'bg-orange-100 dark:bg-orange-900/30',
    THREAD: 'bg-indigo-100 dark:bg-indigo-900/30',
  }
  return classMap[props.item.format] ?? 'bg-gray-100 dark:bg-gray-800'
})

const iconColorClass = computed(() => {
  const classMap: Record<string, string> = {
    SHORT_VIDEO: 'text-purple-600 dark:text-purple-400',
    TWEET: 'text-sky-600 dark:text-sky-400',
    BLOG_POST: 'text-green-600 dark:text-green-400',
    CAPTION: 'text-pink-600 dark:text-pink-400',
    NEWSLETTER: 'text-orange-600 dark:text-orange-400',
    THREAD: 'text-indigo-600 dark:text-indigo-400',
  }
  return classMap[props.item.format] ?? 'text-gray-500 dark:text-gray-400'
})

const formattedDate = computed(() => {
  const date = new Date(props.item.createdAt)
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${month}/${day} ${hours}:${minutes}`
})
</script>
