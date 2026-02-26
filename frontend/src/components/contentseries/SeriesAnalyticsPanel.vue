<script setup lang="ts">
import { computed } from 'vue'
import {
  UserPlusIcon,
  ChartBarIcon,
  ArrowTrendingDownIcon,
  ArrowPathIcon,
  TrophyIcon,
  EyeIcon,
} from '@heroicons/vue/24/outline'
import type { SeriesAnalytics } from '@/types/contentSeries'

interface Props {
  analytics: SeriesAnalytics
}

const props = defineProps<Props>()

/* ---- SVG line chart calculations ---- */
const chartWidth = 600
const chartHeight = 200
const chartPadding = { top: 20, right: 20, bottom: 30, left: 50 }

const innerWidth = chartWidth - chartPadding.left - chartPadding.right
const innerHeight = chartHeight - chartPadding.top - chartPadding.bottom

const maxViews = computed(() => {
  if (props.analytics.viewsTrend.length === 0) return 1
  return Math.max(...props.analytics.viewsTrend.map((d) => d.views), 1)
})

const maxRetention = computed(() => {
  if (props.analytics.viewsTrend.length === 0) return 100
  return Math.max(...props.analytics.viewsTrend.map((d) => d.retention), 1)
})

const viewsPoints = computed(() => {
  const data = props.analytics.viewsTrend
  if (data.length === 0) return ''
  const stepX = data.length > 1 ? innerWidth / (data.length - 1) : 0
  return data
    .map((d, i) => {
      const x = chartPadding.left + i * stepX
      const y = chartPadding.top + innerHeight - (d.views / maxViews.value) * innerHeight
      return `${x},${y}`
    })
    .join(' ')
})

const retentionPoints = computed(() => {
  const data = props.analytics.viewsTrend
  if (data.length === 0) return ''
  const stepX = data.length > 1 ? innerWidth / (data.length - 1) : 0
  return data
    .map((d, i) => {
      const x = chartPadding.left + i * stepX
      const y = chartPadding.top + innerHeight - (d.retention / maxRetention.value) * innerHeight
      return `${x},${y}`
    })
    .join(' ')
})

const viewsAreaPath = computed(() => {
  const data = props.analytics.viewsTrend
  if (data.length === 0) return ''
  const stepX = data.length > 1 ? innerWidth / (data.length - 1) : 0
  const points = data.map((d, i) => {
    const x = chartPadding.left + i * stepX
    const y = chartPadding.top + innerHeight - (d.views / maxViews.value) * innerHeight
    return { x, y }
  })
  const baseline = chartPadding.top + innerHeight
  let path = `M ${points[0].x},${baseline}`
  points.forEach((p) => {
    path += ` L ${p.x},${p.y}`
  })
  path += ` L ${points[points.length - 1].x},${baseline} Z`
  return path
})

const yAxisLabels = computed(() => {
  const max = maxViews.value
  const labels = []
  for (let i = 0; i <= 4; i++) {
    const value = Math.round((max / 4) * i)
    const y = chartPadding.top + innerHeight - (i / 4) * innerHeight
    labels.push({ value: formatCompact(value), y })
  }
  return labels
})

const xAxisLabels = computed(() => {
  const data = props.analytics.viewsTrend
  if (data.length === 0) return []
  const stepX = data.length > 1 ? innerWidth / (data.length - 1) : 0
  const maxLabels = 10
  const step = Math.max(1, Math.floor(data.length / maxLabels))
  return data
    .filter((_, i) => i % step === 0 || i === data.length - 1)
    .map((d) => {
      const idx = props.analytics.viewsTrend.indexOf(d)
      return {
        label: `EP.${d.episode}`,
        x: chartPadding.left + idx * stepX,
      }
    })
})

function formatCompact(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(0)}K`
  return num.toString()
}

function formatNumber(num: number): string {
  return num.toLocaleString('ko-KR')
}
</script>

<template>
  <div class="space-y-6">
    <!-- Views Trend Chart -->
    <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
      <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-4">
        {{ $t('contentSeries.viewsTrend') }}
      </h3>

      <div v-if="analytics.viewsTrend.length === 0" class="flex items-center justify-center h-48 text-sm text-gray-400 dark:text-gray-500">
        {{ $t('contentSeries.noTrendData') }}
      </div>

      <div v-else class="overflow-x-auto">
        <svg
          :viewBox="`0 0 ${chartWidth} ${chartHeight}`"
          class="w-full h-auto min-w-[400px]"
          preserveAspectRatio="xMidYMid meet"
        >
          <!-- Grid lines -->
          <line
            v-for="label in yAxisLabels"
            :key="'grid-' + label.value"
            :x1="chartPadding.left"
            :y1="label.y"
            :x2="chartPadding.left + innerWidth"
            :y2="label.y"
            stroke="currentColor"
            class="text-gray-200 dark:text-gray-700"
            stroke-width="0.5"
            stroke-dasharray="4,4"
          />

          <!-- Y axis labels -->
          <text
            v-for="label in yAxisLabels"
            :key="'y-' + label.value"
            :x="chartPadding.left - 8"
            :y="label.y + 4"
            text-anchor="end"
            class="fill-gray-400 dark:fill-gray-500"
            font-size="10"
          >
            {{ label.value }}
          </text>

          <!-- X axis labels -->
          <text
            v-for="label in xAxisLabels"
            :key="'x-' + label.label"
            :x="label.x"
            :y="chartPadding.top + innerHeight + 18"
            text-anchor="middle"
            class="fill-gray-400 dark:fill-gray-500"
            font-size="10"
          >
            {{ label.label }}
          </text>

          <!-- Views area fill -->
          <path
            :d="viewsAreaPath"
            class="fill-blue-100 dark:fill-blue-900/30"
          />

          <!-- Views line -->
          <polyline
            :points="viewsPoints"
            fill="none"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            class="stroke-blue-500 dark:stroke-blue-400"
          />

          <!-- Retention line -->
          <polyline
            :points="retentionPoints"
            fill="none"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-dasharray="6,3"
            class="stroke-purple-500 dark:stroke-purple-400"
          />

          <!-- Data dots (views) -->
          <circle
            v-for="(d, i) in analytics.viewsTrend"
            :key="'dot-v-' + i"
            :cx="chartPadding.left + (analytics.viewsTrend.length > 1 ? i * (innerWidth / (analytics.viewsTrend.length - 1)) : 0)"
            :cy="chartPadding.top + innerHeight - (d.views / maxViews) * innerHeight"
            r="3"
            class="fill-blue-500 dark:fill-blue-400"
          />
        </svg>

        <!-- Legend -->
        <div class="flex items-center justify-center gap-6 mt-2 text-xs text-gray-500 dark:text-gray-400">
          <div class="flex items-center gap-1.5">
            <span class="w-4 h-0.5 bg-blue-500 dark:bg-blue-400 rounded-full"></span>
            {{ $t('contentSeries.views') }}
          </div>
          <div class="flex items-center gap-1.5">
            <span class="w-4 h-0.5 bg-purple-500 dark:bg-purple-400 rounded-full" style="background: repeating-linear-gradient(90deg, currentColor 0 4px, transparent 4px 7px);"></span>
            {{ $t('contentSeries.retention') }}
          </div>
        </div>
      </div>
    </div>

    <!-- Key Analytics Stats -->
    <div class="grid grid-cols-2 tablet:grid-cols-4 gap-4">
      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <div class="flex items-center gap-2 mb-2">
          <div class="p-2 rounded-lg bg-green-100 dark:bg-green-900/30">
            <UserPlusIcon class="w-4 h-4 text-green-600 dark:text-green-400" />
          </div>
          <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('contentSeries.subscriberGrowth') }}</span>
        </div>
        <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
          +{{ formatNumber(analytics.subscriberGrowth) }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <div class="flex items-center gap-2 mb-2">
          <div class="p-2 rounded-lg bg-blue-100 dark:bg-blue-900/30">
            <ChartBarIcon class="w-4 h-4 text-blue-600 dark:text-blue-400" />
          </div>
          <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('contentSeries.avgEngagement') }}</span>
        </div>
        <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
          {{ analytics.avgEngagement }}%
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <div class="flex items-center gap-2 mb-2">
          <div class="p-2 rounded-lg bg-red-100 dark:bg-red-900/30">
            <ArrowTrendingDownIcon class="w-4 h-4 text-red-600 dark:text-red-400" />
          </div>
          <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('contentSeries.dropOffRate') }}</span>
        </div>
        <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
          {{ analytics.dropOffRate }}%
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <div class="flex items-center gap-2 mb-2">
          <div class="p-2 rounded-lg bg-purple-100 dark:bg-purple-900/30">
            <ArrowPathIcon class="w-4 h-4 text-purple-600 dark:text-purple-400" />
          </div>
          <span class="text-xs text-gray-500 dark:text-gray-400">{{ $t('contentSeries.audienceReturnRate') }}</span>
        </div>
        <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
          {{ analytics.audienceReturnRate }}%
        </p>
      </div>
    </div>

    <!-- Best Performing Episode -->
    <div
      v-if="analytics.bestPerformingEpisode"
      class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm"
    >
      <div class="flex items-center gap-2 mb-3">
        <TrophyIcon class="w-5 h-5 text-yellow-500" />
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('contentSeries.bestEpisode') }}
        </h3>
      </div>
      <div class="flex items-center gap-4">
        <div class="flex-shrink-0 w-10 h-10 rounded-full bg-yellow-100 dark:bg-yellow-900/30 flex items-center justify-center">
          <span class="text-sm font-bold text-yellow-700 dark:text-yellow-300">
            {{ analytics.bestPerformingEpisode.episodeNumber }}
          </span>
        </div>
        <div class="flex-1 min-w-0">
          <p class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">
            {{ analytics.bestPerformingEpisode.title }}
          </p>
          <div class="flex items-center gap-4 mt-1 text-xs text-gray-500 dark:text-gray-400">
            <span class="inline-flex items-center gap-1">
              <EyeIcon class="w-3.5 h-3.5" />
              {{ formatNumber(analytics.bestPerformingEpisode.views ?? 0) }} {{ $t('contentSeries.views') }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
