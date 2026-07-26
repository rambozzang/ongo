<template>
  <div class="space-y-6">
    <!-- 감정 요약 -->
    <div v-if="stats.total > 0" class="card">
      <h3 class="mb-3 text-body font-semibold text-gray-700 dark:text-gray-300">
        {{ $t('commentsView.sentimentSummary') }}
      </h3>
      <div class="mb-2 flex h-4 overflow-hidden rounded-full">
        <div
          v-if="stats.positive > 0"
          class="bg-success"
          :style="{ width: `${(stats.positive / stats.total) * 100}%` }"
        />
        <div
          v-if="stats.neutral > 0"
          class="bg-gray-400"
          :style="{ width: `${(stats.neutral / stats.total) * 100}%` }"
        />
        <div
          v-if="stats.negative > 0"
          class="bg-error"
          :style="{ width: `${(stats.negative / stats.total) * 100}%` }"
        />
      </div>
      <div class="flex flex-wrap gap-4 text-body">
        <div class="flex items-center gap-2">
          <span class="h-3 w-3 rounded-full bg-success" />
          <span class="text-gray-700 dark:text-gray-300">
            {{ $t('commentsView.sentimentPositive') }}: {{ stats.positive }}
            ({{ Math.round((stats.positive / stats.total) * 100) }}%)
          </span>
        </div>
        <div class="flex items-center gap-2">
          <span class="h-3 w-3 rounded-full bg-gray-400" />
          <span class="text-gray-700 dark:text-gray-300">
            {{ $t('commentsView.sentimentNeutral') }}: {{ stats.neutral }}
            ({{ Math.round((stats.neutral / stats.total) * 100) }}%)
          </span>
        </div>
        <div class="flex items-center gap-2">
          <span class="h-3 w-3 rounded-full bg-error" />
          <span class="text-gray-700 dark:text-gray-300">
            {{ $t('commentsView.sentimentNegative') }}: {{ stats.negative }}
            ({{ Math.round((stats.negative / stats.total) * 100) }}%)
          </span>
        </div>
      </div>
    </div>

    <!-- 감정 트렌드 차트 -->
    <div class="card">
      <div class="flex items-center justify-between mb-4">
        <h3 class="text-body font-semibold text-gray-700 dark:text-gray-300">
          {{ $t('commentsView.sentimentTrend') }}
        </h3>
        <div class="flex gap-2">
          <button
            v-for="d in dayOptions"
            :key="d"
            class="rounded-lg px-3 py-1 text-caption transition-colors"
            :class="days === d
              ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
              : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'"
            @click="emit('change-days', d)"
          >
            {{ $t('commentsView.days', { n: d }) }}
          </button>
        </div>
      </div>
      <!-- 로딩 → 빈 상태 → 트렌드 차트 -->
      <AsyncState
        :loading="loading"
        :empty="!trend"
        skeleton="list"
        :skeleton-count="3"
        :empty-icon="ChartBarIcon"
        :empty-title="$t('commentsView.noTrendData')"
        empty-variant="compact"
      >
        <div v-if="trend" class="space-y-3">
          <!-- 트렌드 요약 배지 -->
          <div class="flex items-center gap-2 mb-2">
            <span
              class="inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-caption"
              :class="{
                'bg-success-subtle text-success-strong': trend.summary.trend === 'IMPROVING',
                'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400': trend.summary.trend === 'STABLE',
                'bg-error-subtle text-error-strong': trend.summary.trend === 'WORSENING',
              }"
            >
              <ArrowTrendingUpIcon v-if="trend.summary.trend === 'IMPROVING'" class="h-3 w-3" />
              <MinusIcon v-else-if="trend.summary.trend === 'STABLE'" class="h-3 w-3" />
              <ArrowTrendingDownIcon v-else class="h-3 w-3" />
              {{ trendLabel }}
            </span>
            <span class="text-body-xs text-gray-500 dark:text-gray-400">
              {{ $t('commentsView.sentimentPositive') }} {{ trend.summary.totalPositive }} ·
              {{ $t('commentsView.sentimentNeutral') }} {{ trend.summary.totalNeutral }} ·
              {{ $t('commentsView.sentimentNegative') }} {{ trend.summary.totalNegative }}
            </span>
          </div>
          <!-- 간이 바 차트 -->
          <div class="space-y-1 max-h-48 overflow-y-auto">
            <div
              v-for="point in trend.data"
              :key="point.date"
              class="flex items-center gap-2 text-body-xs"
            >
              <span class="w-16 text-right text-gray-500 dark:text-gray-400 shrink-0">
                {{ formatTrendDate(point.date) }}
              </span>
              <div class="flex flex-1 h-3 rounded-full overflow-hidden bg-gray-100 dark:bg-gray-800">
                <div
                  v-if="point.positive > 0"
                  class="bg-success"
                  :style="{ width: `${getPercent(point.positive, point)}%` }"
                />
                <div
                  v-if="point.neutral > 0"
                  class="bg-gray-400"
                  :style="{ width: `${getPercent(point.neutral, point)}%` }"
                />
                <div
                  v-if="point.negative > 0"
                  class="bg-error"
                  :style="{ width: `${getPercent(point.negative, point)}%` }"
                />
              </div>
              <span class="w-8 text-right text-gray-500 dark:text-gray-400 shrink-0">
                {{ point.positive + point.neutral + point.negative }}
              </span>
            </div>
          </div>
          </div>
      </AsyncState>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  MinusIcon,
  ChartBarIcon,
} from '@heroicons/vue/24/outline'
import type { CommentStats, SentimentTrendPoint, SentimentTrendResponse } from '@/types/comment'
import AsyncState from '@/components/common/AsyncState.vue'

const props = defineProps<{
  stats: CommentStats
  trend: SentimentTrendResponse | null
  loading: boolean
  days: number
}>()

const emit = defineEmits<{
  'change-days': [days: number]
}>()

const { t } = useI18n({ useScope: 'global' })

const dayOptions = [7, 14, 30]

const trendLabel = computed(() => {
  switch (props.trend?.summary.trend) {
    case 'IMPROVING':
      return t('commentsView.trendImproving')
    case 'STABLE':
      return t('commentsView.trendStable')
    default:
      return t('commentsView.trendWorsening')
  }
})

const getPercent = (value: number, point: SentimentTrendPoint) => {
  const total = point.positive + point.neutral + point.negative
  return total > 0 ? Math.round((value / total) * 100) : 0
}

const formatTrendDate = (dateStr: string) => {
  const date = new Date(dateStr)
  return `${date.getMonth() + 1}/${date.getDate()}`
}
</script>
