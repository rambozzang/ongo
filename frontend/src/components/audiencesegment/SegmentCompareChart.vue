<script setup lang="ts">
import { computed } from 'vue'
import type { AudienceSegment } from '@/types/audienceSegment'

interface Props {
  segments: AudienceSegment[]
}

const props = defineProps<Props>()

const segmentColors = [
  { fill: '#3b82f6', label: 'text-blue-600 dark:text-blue-400', bg: 'bg-blue-500' },
  { fill: '#ec4899', label: 'text-pink-600 dark:text-pink-400', bg: 'bg-pink-500' },
  { fill: '#10b981', label: 'text-emerald-600 dark:text-emerald-400', bg: 'bg-emerald-500' },
  { fill: '#f59e0b', label: 'text-amber-600 dark:text-amber-400', bg: 'bg-amber-500' },
  { fill: '#8b5cf6', label: 'text-violet-600 dark:text-violet-400', bg: 'bg-violet-500' },
]

const metrics = computed(() => [
  { key: 'avgRetention', label: '유지율 (%)', max: 100 },
  { key: 'avgCtr', label: 'CTR (%)', max: 20 },
  { key: 'avgEngagement', label: '참여율 (%)', max: 30 },
  { key: 'avgWatchTime', label: '시청시간 (분)', max: Math.max(...props.segments.map((s) => s.avgWatchTime), 20) },
  { key: 'growthRate', label: '성장률 (%)', max: Math.max(...props.segments.map((s) => Math.abs(s.growthRate)), 15) },
  { key: 'revenueContribution', label: '수익 기여 (%)', max: 100 },
])

const chartHeight = computed(() => metrics.value.length * 80 + 20)
const barGroupHeight = 80
const barHeight = computed(() => {
  const count = props.segments.length
  if (count <= 0) return 16
  const maxBarHeight = 20
  const gap = 4
  const available = barGroupHeight - 30
  const barSize = Math.min(maxBarHeight, (available - (count - 1) * gap) / count)
  return Math.max(8, barSize)
})

const labelWidth = 120
const chartWidth = 600
const barAreaWidth = chartWidth - labelWidth - 40

function getBarWidth(segment: AudienceSegment, metricKey: string, max: number): number {
  const value = (segment as unknown as Record<string, unknown>)[metricKey] as number
  const absValue = Math.abs(value)
  return max > 0 ? (absValue / max) * barAreaWidth : 0
}

function getMetricValue(segment: AudienceSegment, metricKey: string): number {
  return (segment as unknown as Record<string, unknown>)[metricKey] as number
}
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800 p-6">
    <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">
      세그먼트 비교
    </h3>

    <!-- Legend -->
    <div class="flex flex-wrap gap-4 mb-6">
      <div
        v-for="(segment, index) in segments"
        :key="segment.id"
        class="flex items-center gap-2"
      >
        <span
          class="inline-block w-3 h-3 rounded-sm"
          :class="segmentColors[index % segmentColors.length].bg"
        />
        <span class="text-sm text-gray-700 dark:text-gray-300">
          {{ segment.name }}
        </span>
      </div>
    </div>

    <!-- SVG Chart -->
    <div class="overflow-x-auto">
      <svg
        :width="chartWidth"
        :height="chartHeight"
        class="w-full max-w-full"
        :viewBox="`0 0 ${chartWidth} ${chartHeight}`"
        preserveAspectRatio="xMinYMin meet"
      >
        <!-- Metric rows -->
        <g v-for="(metric, mIndex) in metrics" :key="metric.key">
          <!-- Metric label -->
          <text
            :x="labelWidth - 8"
            :y="mIndex * barGroupHeight + 20"
            text-anchor="end"
            class="fill-gray-600 dark:fill-gray-400"
            font-size="12"
          >
            {{ metric.label }}
          </text>

          <!-- Background guideline -->
          <line
            :x1="labelWidth"
            :y1="mIndex * barGroupHeight + 10"
            :x2="labelWidth + barAreaWidth"
            :y2="mIndex * barGroupHeight + 10"
            stroke="currentColor"
            stroke-width="0.5"
            class="text-gray-200 dark:text-gray-700"
          />

          <!-- Bars for each segment -->
          <g v-for="(segment, sIndex) in segments" :key="segment.id">
            <!-- Bar -->
            <rect
              :x="labelWidth"
              :y="mIndex * barGroupHeight + 26 + sIndex * (barHeight + 4)"
              :width="Math.max(getBarWidth(segment, metric.key, metric.max), 2)"
              :height="barHeight"
              :fill="segmentColors[sIndex % segmentColors.length].fill"
              rx="3"
              ry="3"
              opacity="0.85"
            >
              <animate
                attributeName="width"
                from="0"
                :to="Math.max(getBarWidth(segment, metric.key, metric.max), 2)"
                dur="0.6s"
                fill="freeze"
              />
            </rect>

            <!-- Value label -->
            <text
              :x="labelWidth + Math.max(getBarWidth(segment, metric.key, metric.max), 2) + 6"
              :y="mIndex * barGroupHeight + 26 + sIndex * (barHeight + 4) + barHeight / 2 + 4"
              font-size="11"
              class="fill-gray-600 dark:fill-gray-400"
            >
              {{ getMetricValue(segment, metric.key).toFixed(1) }}
            </text>
          </g>
        </g>
      </svg>
    </div>

    <!-- Empty state -->
    <div
      v-if="segments.length === 0"
      class="flex items-center justify-center py-12 text-sm text-gray-500 dark:text-gray-400"
    >
      비교할 세그먼트를 선택해주세요
    </div>
  </div>
</template>
