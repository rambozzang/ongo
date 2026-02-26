<script setup lang="ts">
import { HandThumbUpIcon, StarIcon } from '@heroicons/vue/24/outline'
import type { TopComment } from '@/types/commentSummary'

defineProps<{
  comment: TopComment
}>()

const sentimentConfig: Record<string, { bg: string; text: string; label: string }> = {
  POSITIVE: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300', label: 'positive' },
  NEGATIVE: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300', label: 'negative' },
  NEUTRAL: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300', label: 'neutral' },
}

const getSentimentStyle = (sentiment: string) => sentimentConfig[sentiment] ?? sentimentConfig.NEUTRAL
</script>

<template>
  <div
    class="flex items-start gap-3 rounded-lg border p-3 transition-colors"
    :class="
      comment.isHighlighted
        ? 'border-yellow-300 bg-yellow-50/50 dark:border-yellow-700 dark:bg-yellow-900/10'
        : 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-900'
    "
  >
    <!-- Highlight indicator -->
    <StarIcon
      v-if="comment.isHighlighted"
      class="mt-0.5 h-4 w-4 flex-shrink-0 text-yellow-500"
    />

    <div class="min-w-0 flex-1">
      <!-- Author & Sentiment -->
      <div class="mb-1 flex items-center gap-2">
        <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ comment.author }}
        </span>
        <span
          :class="[getSentimentStyle(comment.sentiment).bg, getSentimentStyle(comment.sentiment).text]"
          class="rounded-full px-2 py-0.5 text-[10px] font-medium"
        >
          {{ $t(`commentSummary.${getSentimentStyle(comment.sentiment).label}`) }}
        </span>
      </div>

      <!-- Comment text -->
      <p class="text-sm text-gray-700 dark:text-gray-300">{{ comment.text }}</p>

      <!-- Likes -->
      <div class="mt-1.5 flex items-center gap-1 text-xs text-gray-400 dark:text-gray-500">
        <HandThumbUpIcon class="h-3.5 w-3.5" />
        <span>{{ comment.likes.toLocaleString() }}</span>
      </div>
    </div>
  </div>
</template>
