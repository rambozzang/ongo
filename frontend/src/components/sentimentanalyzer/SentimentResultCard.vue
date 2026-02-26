<script setup lang="ts">
import {
  ChatBubbleLeftRightIcon,
  TagIcon,
} from '@heroicons/vue/24/outline'
import type { SentimentResult } from '@/types/sentimentAnalyzer'
import SentimentChart from './SentimentChart.vue'

defineProps<{
  result: SentimentResult
}>()

const emit = defineEmits<{
  click: [id: number]
}>()

const platformColors: Record<string, { bg: string; text: string }> = {
  YOUTUBE: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
  INSTAGRAM: { bg: 'bg-pink-100 dark:bg-pink-900/30', text: 'text-pink-700 dark:text-pink-300' },
  TIKTOK: { bg: 'bg-gray-100 dark:bg-gray-800', text: 'text-gray-700 dark:text-gray-300' },
  NAVERCLIP: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300' },
}

const getPlatformStyle = (platform: string) => platformColors[platform] ?? platformColors.TIKTOK

const getScoreColor = (score: number) => {
  if (score >= 70) return 'text-green-600 dark:text-green-400'
  if (score >= 40) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
}

const formatNumber = (n: number) => n.toLocaleString('ko-KR')
</script>

<template>
  <div
    class="cursor-pointer rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
    @click="emit('click', result.id)"
  >
    <!-- Header: Title + Platform -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <ChatBubbleLeftRightIcon class="h-4 w-4 text-primary-500" />
      <span class="text-sm font-bold text-gray-900 dark:text-gray-100 truncate">
        {{ result.contentTitle }}
      </span>
      <span
        :class="[getPlatformStyle(result.platform).bg, getPlatformStyle(result.platform).text]"
        class="rounded-full px-2 py-0.5 text-xs font-medium"
      >
        {{ result.platform }}
      </span>
    </div>

    <!-- Metrics -->
    <div class="mb-3 grid grid-cols-2 gap-3">
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">총 댓글수</p>
        <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ formatNumber(result.totalComments) }}
        </p>
      </div>
      <div>
        <p class="text-xs text-gray-500 dark:text-gray-400">평균 점수</p>
        <p class="text-sm font-semibold" :class="getScoreColor(result.avgSentimentScore)">
          {{ result.avgSentimentScore }}점
        </p>
      </div>
    </div>

    <!-- Sentiment Chart Bar -->
    <div class="mb-3">
      <SentimentChart
        :positive-count="result.positiveCount"
        :neutral-count="result.neutralCount"
        :negative-count="result.negativeCount"
      />
    </div>

    <!-- Keywords -->
    <div class="space-y-2 border-t border-gray-100 pt-3 dark:border-gray-800">
      <!-- Positive Keywords -->
      <div v-if="result.topPositiveKeywords.length > 0" class="flex flex-wrap items-center gap-1.5">
        <TagIcon class="h-3.5 w-3.5 text-green-500" />
        <span
          v-for="kw in result.topPositiveKeywords"
          :key="kw"
          class="rounded-full bg-green-50 px-2 py-0.5 text-xs font-medium text-green-600 dark:bg-green-900/20 dark:text-green-400"
        >
          {{ kw }}
        </span>
      </div>
      <!-- Negative Keywords -->
      <div v-if="result.topNegativeKeywords.length > 0" class="flex flex-wrap items-center gap-1.5">
        <TagIcon class="h-3.5 w-3.5 text-red-500" />
        <span
          v-for="kw in result.topNegativeKeywords"
          :key="kw"
          class="rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-600 dark:bg-red-900/20 dark:text-red-400"
        >
          {{ kw }}
        </span>
      </div>
    </div>
  </div>
</template>
