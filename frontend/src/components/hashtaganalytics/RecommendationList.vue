<script setup lang="ts">
import { SparklesIcon, UsersIcon } from '@heroicons/vue/24/outline'
import type { HashtagRecommendation } from '@/types/hashtagAnalytics'

defineProps<{
  recommendations: HashtagRecommendation[]
}>()

const competitionConfig: Record<string, { bg: string; text: string; label: string }> = {
  LOW: { bg: 'bg-green-100 dark:bg-green-900/30', text: 'text-green-700 dark:text-green-300', label: '낮음' },
  MEDIUM: { bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300', label: '보통' },
  HIGH: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300', label: '높음' },
}

const getCompetitionStyle = (comp: string) => competitionConfig[comp] ?? competitionConfig.MEDIUM

const formatNumber = (n: number) => n.toLocaleString('ko-KR')
</script>

<template>
  <div class="space-y-3">
    <div
      v-for="(rec, index) in recommendations"
      :key="index"
      class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900"
    >
      <!-- Header: Hashtag + Competition badge -->
      <div class="mb-3 flex items-center justify-between">
        <div class="flex items-center gap-2">
          <SparklesIcon class="h-4 w-4 text-primary-500" />
          <span class="text-sm font-bold text-gray-900 dark:text-gray-100">
            {{ rec.hashtag }}
          </span>
        </div>
        <span
          :class="[getCompetitionStyle(rec.competition).bg, getCompetitionStyle(rec.competition).text]"
          class="rounded-full px-2 py-0.5 text-xs font-medium"
        >
          경쟁: {{ getCompetitionStyle(rec.competition).label }}
        </span>
      </div>

      <!-- Relevance score bar -->
      <div class="mb-3">
        <div class="mb-1 flex items-center justify-between">
          <span class="text-xs text-gray-500 dark:text-gray-400">관련도</span>
          <span class="text-xs font-semibold text-gray-700 dark:text-gray-300">
            {{ rec.relevanceScore }}%
          </span>
        </div>
        <div class="h-2 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
          <div
            class="h-full rounded-full bg-primary-500 transition-all duration-500"
            :style="{ width: `${rec.relevanceScore}%` }"
          />
        </div>
      </div>

      <!-- Expected reach -->
      <div class="mb-3 flex items-center gap-2">
        <UsersIcon class="h-4 w-4 text-gray-400" />
        <span class="text-xs text-gray-500 dark:text-gray-400">예상 도달:</span>
        <span class="text-xs font-semibold text-gray-900 dark:text-gray-100">
          {{ formatNumber(rec.expectedReach) }}
        </span>
      </div>

      <!-- Reason -->
      <div class="rounded-lg bg-gray-50 p-2.5 dark:bg-gray-800/50">
        <p class="text-xs text-gray-600 dark:text-gray-400">{{ rec.reason }}</p>
      </div>
    </div>

    <!-- Empty state -->
    <div
      v-if="recommendations.length === 0"
      class="rounded-xl border border-gray-200 bg-white py-12 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
    >
      <SparklesIcon class="mx-auto mb-3 h-10 w-10 text-gray-400 dark:text-gray-600" />
      <p class="text-sm text-gray-500 dark:text-gray-400">
        주제와 플랫폼을 선택하고 분석을 시작하세요
      </p>
    </div>
  </div>
</template>
