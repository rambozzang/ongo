<script setup lang="ts">
import { UserCircleIcon, TagIcon } from '@heroicons/vue/24/outline'
import type { CommentSentiment } from '@/types/sentimentAnalyzer'

defineProps<{
  comment: CommentSentiment
}>()

const sentimentConfig: Record<string, { label: string; bg: string; text: string }> = {
  POSITIVE: { label: '긍정', bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
  NEUTRAL: { label: '중립', bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  NEGATIVE: { label: '부정', bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
}

const getSentimentStyle = (sentiment: string) => sentimentConfig[sentiment] ?? sentimentConfig.NEUTRAL

const getScoreColor = (score: number) => {
  if (score >= 70) return 'text-green-600 dark:text-green-400'
  if (score >= 40) return 'text-gray-600 dark:text-gray-400'
  return 'text-red-600 dark:text-red-400'
}
</script>

<template>
  <div class="flex items-start gap-3 rounded-lg border border-gray-100 bg-white p-3 dark:border-gray-800 dark:bg-gray-900">
    <!-- Author -->
    <div class="flex-shrink-0">
      <UserCircleIcon class="h-8 w-8 text-gray-400 dark:text-gray-600" />
    </div>

    <!-- Content -->
    <div class="min-w-0 flex-1">
      <div class="mb-1 flex flex-wrap items-center gap-2">
        <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ comment.authorName }}
        </span>
        <span
          :class="[getSentimentStyle(comment.sentiment).bg, getSentimentStyle(comment.sentiment).text]"
          class="rounded-full px-2 py-0.5 text-xs font-medium"
        >
          {{ getSentimentStyle(comment.sentiment).label }}
        </span>
        <span class="text-xs font-semibold" :class="getScoreColor(comment.score)">
          {{ comment.score }}점
        </span>
      </div>

      <p class="mb-2 text-sm text-gray-700 dark:text-gray-300">
        {{ comment.commentText }}
      </p>

      <!-- Keywords -->
      <div v-if="comment.keywords.length > 0" class="flex flex-wrap items-center gap-1.5">
        <TagIcon class="h-3.5 w-3.5 text-gray-400" />
        <span
          v-for="keyword in comment.keywords"
          :key="keyword"
          class="rounded-full bg-blue-50 px-2 py-0.5 text-xs font-medium text-blue-600 dark:bg-blue-900/20 dark:text-blue-400"
        >
          {{ keyword }}
        </span>
      </div>
    </div>
  </div>
</template>
