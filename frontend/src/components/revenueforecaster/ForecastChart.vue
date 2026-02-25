<template>
  <div ref="chartContainer" class="relative w-full">
    <svg
      :width="svgWidth"
      :height="svgHeight"
      class="overflow-visible"
      @mouseleave="handleMouseLeave"
    >
      <!-- Y-axis grid lines -->
      <line
        v-for="tick in yTicks"
        :key="'grid-' + tick"
        :x1="padding.left"
        :y1="yScale(tick)"
        :x2="svgWidth - padding.right"
        :y2="yScale(tick)"
        class="stroke-gray-200 dark:stroke-gray-700"
        stroke-dasharray="4 4"
        stroke-width="1"
      />

      <!-- Y-axis labels -->
      <text
        v-for="tick in yTicks"
        :key="'label-' + tick"
        :x="padding.left - 8"
        :y="yScale(tick) + 4"
        text-anchor="end"
        class="fill-gray-400 dark:fill-gray-500 text-[10px]"
      >
        {{ formatYLabel(tick) }}
      </text>

      <!-- Confidence band (shaded area between lower and upper bounds) -->
      <path
        v-if="confidenceBandPath"
        :d="confidenceBandPath"
        class="fill-blue-100/50 dark:fill-blue-900/30"
      />

      <!-- Actual data line (solid) -->
      <path
        v-if="actualLinePath"
        :d="actualLinePath"
        fill="none"
        class="stroke-blue-500 dark:stroke-blue-400"
        stroke-width="2.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />

      <!-- Forecast data line (dashed) -->
      <path
        v-if="forecastLinePath"
        :d="forecastLinePath"
        fill="none"
        class="stroke-blue-500 dark:stroke-blue-400"
        stroke-width="2"
        stroke-dasharray="6 4"
        stroke-linecap="round"
        stroke-linejoin="round"
      />

      <!-- Data point dots - actual -->
      <circle
        v-for="(point, index) in actualPoints"
        :key="'actual-' + index"
        :cx="point.x"
        :cy="point.y"
        r="4"
        class="fill-blue-500 dark:fill-blue-400 stroke-white dark:stroke-gray-900"
        stroke-width="2"
      />

      <!-- Data point dots - forecast -->
      <circle
        v-for="(point, index) in forecastPoints"
        :key="'forecast-' + index"
        :cx="point.x"
        :cy="point.y"
        r="4"
        class="fill-white dark:fill-gray-900 stroke-blue-500 dark:stroke-blue-400"
        stroke-width="2"
        stroke-dasharray="2 2"
      />

      <!-- X-axis labels -->
      <text
        v-for="(label, index) in xLabels"
        :key="'x-' + index"
        :x="xScale(index)"
        :y="svgHeight - padding.bottom + 20"
        text-anchor="middle"
        class="fill-gray-400 dark:fill-gray-500 text-[10px]"
      >
        {{ label }}
      </text>

      <!-- Hover overlay rectangles -->
      <rect
        v-for="(_, index) in chartData"
        :key="'hover-' + index"
        :x="xScale(index) - hoverRectWidth / 2"
        :y="padding.top"
        :width="hoverRectWidth"
        :height="chartHeight"
        fill="transparent"
        class="cursor-pointer"
        @mouseenter="handleMouseEnter(index)"
      />

      <!-- Hover line -->
      <line
        v-if="hoveredIndex !== null"
        :x1="xScale(hoveredIndex)"
        :y1="padding.top"
        :x2="xScale(hoveredIndex)"
        :y2="svgHeight - padding.bottom"
        class="stroke-gray-300 dark:stroke-gray-600"
        stroke-width="1"
        stroke-dasharray="3 3"
      />
    </svg>

    <!-- Tooltip -->
    <div
      v-if="hoveredIndex !== null && tooltipData"
      class="absolute z-10 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-3 shadow-lg text-xs pointer-events-none"
      :style="tooltipStyle"
    >
      <p class="mb-1.5 font-semibold text-gray-900 dark:text-gray-100">
        {{ tooltipData.month }}
      </p>
      <div v-if="tooltipData.actual !== undefined" class="mb-1 flex items-center gap-2">
        <span class="h-2 w-2 rounded-full bg-blue-500" />
        <span class="text-gray-500 dark:text-gray-400">{{ $t('revenueForecaster.chart.actual') }}:</span>
        <span class="font-medium text-gray-900 dark:text-gray-100">{{ formatTooltipValue(tooltipData.actual) }}</span>
      </div>
      <div v-if="tooltipData.forecast !== undefined" class="mb-1 flex items-center gap-2">
        <span class="h-2 w-2 rounded-full border-2 border-blue-500 bg-white dark:bg-gray-800" />
        <span class="text-gray-500 dark:text-gray-400">{{ $t('revenueForecaster.chart.forecast') }}:</span>
        <span class="font-medium text-gray-900 dark:text-gray-100">{{ formatTooltipValue(tooltipData.forecast) }}</span>
      </div>
      <div v-if="tooltipData.lowerBound !== undefined && tooltipData.upperBound !== undefined" class="flex items-center gap-2">
        <span class="h-2 w-2 rounded-full bg-blue-200 dark:bg-blue-800" />
        <span class="text-gray-500 dark:text-gray-400">{{ $t('revenueForecaster.chart.range') }}:</span>
        <span class="font-medium text-gray-900 dark:text-gray-100">
          {{ formatTooltipValue(tooltipData.lowerBound) }} ~ {{ formatTooltipValue(tooltipData.upperBound) }}
        </span>
      </div>
    </div>

    <!-- Legend -->
    <div class="mt-4 flex flex-wrap items-center gap-4 justify-center text-xs text-gray-500 dark:text-gray-400">
      <div class="flex items-center gap-1.5">
        <span class="inline-block h-0.5 w-5 bg-blue-500 dark:bg-blue-400" />
        <span>{{ $t('revenueForecaster.chart.actualLabel') }}</span>
      </div>
      <div class="flex items-center gap-1.5">
        <span class="inline-block h-0.5 w-5 border-t-2 border-dashed border-blue-500 dark:border-blue-400" />
        <span>{{ $t('revenueForecaster.chart.forecastLabel') }}</span>
      </div>
      <div class="flex items-center gap-1.5">
        <span class="inline-block h-3 w-5 rounded-sm bg-blue-100/50 dark:bg-blue-900/30" />
        <span>{{ $t('revenueForecaster.chart.confidenceBand') }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { RevenueDataPoint } from '@/types/revenueForecaster'

const { t } = useI18n({ useScope: 'global' })

const props = defineProps<{
  chartData: RevenueDataPoint[]
}>()

const chartContainer = ref<HTMLElement | null>(null)
const containerWidth = ref(800)
const hoveredIndex = ref<number | null>(null)

const svgHeight = 320
const padding = { top: 20, right: 20, bottom: 40, left: 60 }

const svgWidth = computed(() => containerWidth.value)
const chartWidth = computed(() => svgWidth.value - padding.left - padding.right)
const chartHeight = computed(() => svgHeight - padding.top - padding.bottom)

const hoverRectWidth = computed(() => {
  if (props.chartData.length <= 1) return chartWidth.value
  return chartWidth.value / (props.chartData.length - 1)
})

// Compute all values for y-axis scaling
const allValues = computed(() => {
  const values: number[] = []
  for (const d of props.chartData) {
    if (d.actual !== undefined) values.push(d.actual)
    if (d.forecast !== undefined) values.push(d.forecast)
    if (d.lowerBound !== undefined) values.push(d.lowerBound)
    if (d.upperBound !== undefined) values.push(d.upperBound)
  }
  return values
})

const yMin = computed(() => {
  if (allValues.value.length === 0) return 0
  return Math.min(...allValues.value) * 0.9
})

const yMax = computed(() => {
  if (allValues.value.length === 0) return 100
  return Math.max(...allValues.value) * 1.1
})

const yTicks = computed(() => {
  const min = yMin.value
  const max = yMax.value
  const range = max - min
  const step = Math.ceil(range / 5 / 10000) * 10000 || 10000
  const ticks: number[] = []
  let current = Math.floor(min / step) * step
  while (current <= max) {
    ticks.push(current)
    current += step
  }
  return ticks
})

function xScale(index: number): number {
  if (props.chartData.length <= 1) return padding.left
  return padding.left + (index / (props.chartData.length - 1)) * chartWidth.value
}

function yScale(value: number): number {
  const range = yMax.value - yMin.value || 1
  return svgHeight - padding.bottom - ((value - yMin.value) / range) * chartHeight.value
}

// X labels
const xLabels = computed(() =>
  props.chartData.map((d) => {
    const parts = d.month.split('-')
    return parts.length >= 2 ? `${parseInt(parts[1])}${t('revenueForecaster.chart.monthSuffix')}` : d.month
  }),
)

// Actual data points
const actualPoints = computed(() =>
  props.chartData
    .map((d, i) => (d.actual !== undefined ? { x: xScale(i), y: yScale(d.actual) } : null))
    .filter((p): p is { x: number; y: number } => p !== null),
)

// Forecast data points
const forecastPoints = computed(() =>
  props.chartData
    .map((d, i) =>
      d.forecast !== undefined && d.actual === undefined ? { x: xScale(i), y: yScale(d.forecast) } : null,
    )
    .filter((p): p is { x: number; y: number } => p !== null),
)

// Actual line path
const actualLinePath = computed(() => {
  const points = props.chartData
    .map((d, i) => (d.actual !== undefined ? { x: xScale(i), y: yScale(d.actual) } : null))
    .filter((p): p is { x: number; y: number } => p !== null)
  if (points.length < 2) return null
  return points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' ')
})

// Forecast line path (includes last actual point for continuity)
const forecastLinePath = computed(() => {
  const points: { x: number; y: number }[] = []
  let lastActualIndex = -1
  props.chartData.forEach((d, i) => {
    if (d.actual !== undefined) lastActualIndex = i
  })
  props.chartData.forEach((d, i) => {
    if (i === lastActualIndex && d.actual !== undefined) {
      points.push({ x: xScale(i), y: yScale(d.actual) })
    } else if (d.forecast !== undefined && d.actual === undefined) {
      points.push({ x: xScale(i), y: yScale(d.forecast) })
    }
  })
  if (points.length < 2) return null
  return points.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' ')
})

// Confidence band path
const confidenceBandPath = computed(() => {
  const upper: { x: number; y: number }[] = []
  const lower: { x: number; y: number }[] = []
  props.chartData.forEach((d, i) => {
    if (d.upperBound !== undefined && d.lowerBound !== undefined) {
      upper.push({ x: xScale(i), y: yScale(d.upperBound) })
      lower.push({ x: xScale(i), y: yScale(d.lowerBound) })
    }
  })
  if (upper.length < 2) return null
  const upperPath = upper.map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x},${p.y}`).join(' ')
  const lowerReversed = [...lower].reverse()
  const lowerPath = lowerReversed.map((p) => `L${p.x},${p.y}`).join(' ')
  return `${upperPath} ${lowerPath} Z`
})

// Tooltip
const tooltipData = computed(() => {
  if (hoveredIndex.value === null) return null
  return props.chartData[hoveredIndex.value] ?? null
})

const tooltipStyle = computed(() => {
  if (hoveredIndex.value === null) return {}
  const x = xScale(hoveredIndex.value)
  const isRightHalf = x > svgWidth.value / 2
  return {
    top: `${padding.top}px`,
    ...(isRightHalf ? { right: `${svgWidth.value - x + 12}px` } : { left: `${x + 12}px` }),
  }
})

function formatYLabel(value: number): string {
  if (value >= 10000) {
    return `${(value / 10000).toFixed(0)}${t('revenueForecaster.chart.manwon')}`
  }
  return value.toLocaleString()
}

function formatTooltipValue(value: number): string {
  if (value >= 10000) {
    return `${(value / 10000).toFixed(1)}${t('revenueForecaster.chart.manwon')}`
  }
  return `${value.toLocaleString()}${t('revenueForecaster.chart.won')}`
}

function handleMouseEnter(index: number) {
  hoveredIndex.value = index
}

function handleMouseLeave() {
  hoveredIndex.value = null
}

function updateWidth() {
  if (chartContainer.value) {
    containerWidth.value = chartContainer.value.clientWidth
  }
}

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  updateWidth()
  resizeObserver = new ResizeObserver(updateWidth)
  if (chartContainer.value) {
    resizeObserver.observe(chartContainer.value)
  }
})

onUnmounted(() => {
  resizeObserver?.disconnect()
})
</script>
