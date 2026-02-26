<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <!-- Header -->
    <div class="mb-3 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <span
          class="rounded-full px-2 py-0.5 text-xs font-semibold"
          :class="formatBadgeClass"
        >
          {{ $t(`contentRewriter.format.${result.format}`) }}
        </span>
        <span class="rounded bg-gray-100 dark:bg-gray-700 px-2 py-0.5 text-xs text-gray-600 dark:text-gray-400">
          {{ result.platform }}
        </span>
      </div>
      <div class="flex items-center gap-1">
        <button
          class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-300"
          :title="$t('contentRewriter.copyToClipboard')"
          @click="emit('copy', result)"
        >
          <ClipboardDocumentIcon class="h-4 w-4" />
        </button>
        <button
          class="rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-red-600 dark:hover:bg-gray-700 dark:hover:text-red-400"
          :title="$t('contentRewriter.deleteResult')"
          @click="emit('delete', result)"
        >
          <TrashIcon class="h-4 w-4" />
        </button>
      </div>
    </div>

    <!-- Generated Text -->
    <p class="mb-3 whitespace-pre-wrap rounded-lg bg-gray-50 dark:bg-gray-800 p-3 text-sm leading-relaxed text-gray-700 dark:text-gray-300">
      {{ result.text }}
    </p>

    <!-- Hashtags -->
    <div v-if="result.hashtags.length > 0" class="mb-3 flex flex-wrap gap-1">
      <span
        v-for="tag in result.hashtags"
        :key="tag"
        class="rounded-full bg-blue-50 dark:bg-blue-900/20 px-2 py-0.5 text-xs font-medium text-blue-700 dark:text-blue-400"
      >
        {{ tag }}
      </span>
    </div>

    <!-- Footer Meta -->
    <div class="flex flex-wrap items-center gap-4 border-t border-gray-100 dark:border-gray-800 pt-3 text-xs text-gray-500 dark:text-gray-400">
      <span class="flex items-center gap-1">
        <DocumentTextIcon class="h-3.5 w-3.5" />
        {{ result.characterCount }} {{ $t('contentRewriter.characters') }}
      </span>
      <span class="flex items-center gap-1">
        <ChartBarIcon class="h-3.5 w-3.5" />
        {{ $t('contentRewriter.engagement') }}: {{ result.estimatedEngagement }}%
      </span>
      <span class="ml-auto flex items-center gap-1">
        <ClockIcon class="h-3.5 w-3.5" />
        {{ formattedDate }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  ClipboardDocumentIcon,
  TrashIcon,
  DocumentTextIcon,
  ChartBarIcon,
  ClockIcon,
} from '@heroicons/vue/24/outline'
import type { RewriteResult } from '@/types/contentRewriter'

const props = defineProps<{
  result: RewriteResult
}>()

const emit = defineEmits<{
  copy: [result: RewriteResult]
  delete: [result: RewriteResult]
}>()

const formatBadgeClass = computed(() => {
  const classMap: Record<string, string> = {
    SHORT_VIDEO: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
    TWEET: 'bg-sky-100 text-sky-700 dark:bg-sky-900/30 dark:text-sky-400',
    BLOG_POST: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    CAPTION: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    NEWSLETTER: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
    THREAD: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-400',
  }
  return classMap[props.result.format] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
})

const formattedDate = computed(() => {
  const date = new Date(props.result.createdAt)
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${month}/${day} ${hours}:${minutes}`
})
</script>
