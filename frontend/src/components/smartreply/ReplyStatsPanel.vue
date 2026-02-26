<script setup lang="ts">
import { computed } from 'vue'
import type { SmartReplyStats } from '@/types/smartReply'

const props = defineProps<{
  stats: SmartReplyStats
}>()

const platformColors: Record<string, string> = {
  youtube: 'bg-red-500',
  instagram: 'bg-pink-500',
  tiktok: 'bg-gray-700 dark:bg-gray-400',
  naverclip: 'bg-green-500',
}

const maxPlatformCount = computed(() => {
  if (!props.stats.repliesByPlatform.length) return 1
  return Math.max(...props.stats.repliesByPlatform.map((p) => p.count), 1)
})

const maxKeywordCount = computed(() => {
  if (!props.stats.topKeywords.length) return 1
  return Math.max(...props.stats.topKeywords.map((k) => k.count), 1)
})

const sentimentTotal = computed(() => {
  const s = props.stats.sentimentBreakdown
  return s.positive + s.negative + s.neutral || 1
})

const formatResponseTime = (minutes: number) => {
  if (minutes < 1) return '< 1분'
  if (minutes < 60) return `${minutes}분`
  const hours = Math.floor(minutes / 60)
  const mins = minutes % 60
  return mins > 0 ? `${hours}시간 ${mins}분` : `${hours}시간`
}

const getPlatformColor = (platform: string) => platformColors[platform.toLowerCase()] ?? 'bg-gray-500'
</script>

<template>
  <div class="space-y-6">
    <!-- Key Metrics -->
    <div class="grid grid-cols-2 gap-4 desktop:grid-cols-4">
      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('smartReply.stats.totalReplies') }}</p>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ stats.totalRepliesSent.toLocaleString() }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('smartReply.stats.avgResponseTime') }}</p>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ formatResponseTime(stats.avgResponseTime) }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('smartReply.stats.automatedRate') }}</p>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ stats.automatedPercentage }}%
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('smartReply.stats.satisfaction') }}</p>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ stats.satisfactionScore.toFixed(1) }}
          <span class="text-sm font-normal text-gray-400">/ 5.0</span>
        </p>
      </div>
    </div>

    <!-- Sentiment Breakdown -->
    <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
      <h3 class="mb-4 text-sm font-semibold text-gray-700 dark:text-gray-300">
        {{ $t('smartReply.stats.sentimentBreakdown') }}
      </h3>
      <div class="space-y-3">
        <!-- Positive -->
        <div class="flex items-center gap-3">
          <span class="w-16 text-right text-xs font-medium text-gray-600 dark:text-gray-400">
            {{ $t('smartReply.sentiment.positive') }}
          </span>
          <div class="flex-1">
            <div class="h-5 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
              <div
                class="h-full rounded-full bg-green-500 transition-all duration-500"
                :style="{ width: `${(stats.sentimentBreakdown.positive / sentimentTotal) * 100}%` }"
              />
            </div>
          </div>
          <span class="w-12 text-right text-xs font-semibold text-gray-700 dark:text-gray-300">
            {{ stats.sentimentBreakdown.positive }}%
          </span>
        </div>

        <!-- Negative -->
        <div class="flex items-center gap-3">
          <span class="w-16 text-right text-xs font-medium text-gray-600 dark:text-gray-400">
            {{ $t('smartReply.sentiment.negative') }}
          </span>
          <div class="flex-1">
            <div class="h-5 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
              <div
                class="h-full rounded-full bg-red-500 transition-all duration-500"
                :style="{ width: `${(stats.sentimentBreakdown.negative / sentimentTotal) * 100}%` }"
              />
            </div>
          </div>
          <span class="w-12 text-right text-xs font-semibold text-gray-700 dark:text-gray-300">
            {{ stats.sentimentBreakdown.negative }}%
          </span>
        </div>

        <!-- Neutral -->
        <div class="flex items-center gap-3">
          <span class="w-16 text-right text-xs font-medium text-gray-600 dark:text-gray-400">
            {{ $t('smartReply.sentiment.neutral') }}
          </span>
          <div class="flex-1">
            <div class="h-5 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
              <div
                class="h-full rounded-full bg-gray-400 transition-all duration-500"
                :style="{ width: `${(stats.sentimentBreakdown.neutral / sentimentTotal) * 100}%` }"
              />
            </div>
          </div>
          <span class="w-12 text-right text-xs font-semibold text-gray-700 dark:text-gray-300">
            {{ stats.sentimentBreakdown.neutral }}%
          </span>
        </div>
      </div>
    </div>

    <!-- Platform Breakdown -->
    <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
      <h3 class="mb-4 text-sm font-semibold text-gray-700 dark:text-gray-300">
        {{ $t('smartReply.stats.platformBreakdown') }}
      </h3>
      <div class="space-y-3">
        <div
          v-for="platform in stats.repliesByPlatform"
          :key="platform.platform"
          class="flex items-center gap-3"
        >
          <span class="w-20 text-right text-xs font-medium capitalize text-gray-600 dark:text-gray-400">
            {{ platform.platform }}
          </span>
          <div class="flex-1">
            <div class="h-5 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
              <div
                :class="getPlatformColor(platform.platform)"
                class="h-full rounded-full transition-all duration-500"
                :style="{ width: `${(platform.count / maxPlatformCount) * 100}%` }"
              />
            </div>
          </div>
          <span class="w-14 text-right text-xs font-semibold text-gray-700 dark:text-gray-300">
            {{ platform.count.toLocaleString() }}
          </span>
        </div>
      </div>
    </div>

    <!-- Top Keywords -->
    <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
      <h3 class="mb-4 text-sm font-semibold text-gray-700 dark:text-gray-300">
        {{ $t('smartReply.stats.topKeywords') }}
      </h3>
      <div class="space-y-2.5">
        <div
          v-for="(kw, index) in stats.topKeywords"
          :key="kw.keyword"
          class="flex items-center gap-3"
        >
          <span class="w-5 text-right text-xs font-medium text-gray-400 dark:text-gray-500">
            {{ index + 1 }}
          </span>
          <span class="w-20 text-sm font-medium text-gray-800 dark:text-gray-200">
            {{ kw.keyword }}
          </span>
          <div class="flex-1">
            <div class="h-4 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
              <div
                class="h-full rounded-full bg-primary-500 transition-all duration-500"
                :style="{ width: `${(kw.count / maxKeywordCount) * 100}%` }"
              />
            </div>
          </div>
          <span class="w-10 text-right text-xs font-semibold text-gray-700 dark:text-gray-300">
            {{ kw.count }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
