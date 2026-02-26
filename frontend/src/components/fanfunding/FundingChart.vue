<template>
  <div class="relative w-full">
    <svg
      :viewBox="`0 0 ${svgWidth} ${svgHeight}`"
      class="w-full"
      preserveAspectRatio="xMidYMid meet"
      @mouseleave="activeIndex = -1"
    >
      <!-- Y-axis grid lines -->
      <line
        v-for="(tick, i) in yTicks"
        :key="'grid-' + i"
        :x1="paddingLeft"
        :y1="tick.y"
        :x2="svgWidth - paddingRight"
        :y2="tick.y"
        class="stroke-gray-200 dark:stroke-gray-700"
        stroke-width="1"
        stroke-dasharray="4 3"
      />

      <!-- Y-axis labels -->
      <text
        v-for="(tick, i) in yTicks"
        :key="'ylabel-' + i"
        :x="paddingLeft - 8"
        :y="tick.y + 4"
        text-anchor="end"
        class="fill-gray-400 dark:fill-gray-500"
        font-size="11"
      >
        {{ tick.label }}
      </text>

      <!-- Bars -->
      <g v-for="(bar, i) in bars" :key="'bar-' + i">
        <rect
          :x="bar.x"
          :y="bar.y"
          :width="bar.width"
          :height="bar.height"
          :rx="2"
          class="transition-opacity duration-150"
          :class="[
            activeIndex === -1 || activeIndex === i
              ? 'opacity-100'
              : 'opacity-40',
          ]"
          :fill="activeIndex === i ? '#6366f1' : '#818cf8'"
          @mouseenter="activeIndex = i"
          @mouseleave="activeIndex = -1"
        />
      </g>

      <!-- X-axis labels (every ~5th label to avoid crowding) -->
      <text
        v-for="(bar, i) in bars"
        :key="'xlabel-' + i"
        :x="bar.x + bar.width / 2"
        :y="svgHeight - 4"
        text-anchor="middle"
        class="fill-gray-400 dark:fill-gray-500"
        font-size="10"
        :class="{ 'hidden': i % xLabelInterval !== 0 }"
      >
        {{ formatDateLabel(trends[i].date) }}
      </text>
    </svg>

    <!-- Tooltip -->
    <div
      v-if="activeIndex >= 0 && trends[activeIndex]"
      class="pointer-events-none absolute z-10 rounded-lg border border-gray-200 bg-white px-3 py-2 text-xs shadow-lg dark:border-gray-600 dark:bg-gray-800"
      :style="tooltipStyle"
    >
      <p class="font-medium text-gray-900 dark:text-gray-100">
        {{ trends[activeIndex].date }}
      </p>
      <p class="mt-1 text-gray-600 dark:text-gray-300">
        {{ $t('fanFunding.chartAmount') }}: {{ formatAmount(trends[activeIndex].amount) }}
      </p>
      <p class="text-gray-600 dark:text-gray-300">
        {{ $t('fanFunding.chartCount') }}: {{ trends[activeIndex].count }}{{ $t('fanFunding.countUnit') }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{
  trends: { date: string; amount: number; count: number }[]
}>()

const activeIndex = ref(-1)

const svgWidth = 720
const svgHeight = 280
const paddingLeft = 60
const paddingRight = 16
const paddingTop = 16
const paddingBottom = 28

const chartWidth = svgWidth - paddingLeft - paddingRight
const chartHeight = svgHeight - paddingTop - paddingBottom

const maxAmount = computed(() => {
  const max = Math.max(...props.trends.map((t) => t.amount), 1)
  return Math.ceil(max / 10000) * 10000
})

const xLabelInterval = computed(() => {
  const len = props.trends.length
  if (len <= 7) return 1
  if (len <= 14) return 2
  return 5
})

const yTicks = computed(() => {
  const tickCount = 4
  const step = maxAmount.value / tickCount
  return Array.from({ length: tickCount + 1 }, (_, i) => {
    const val = step * i
    const y = paddingTop + chartHeight - (val / maxAmount.value) * chartHeight
    return {
      y,
      label: val >= 10000 ? `${(val / 10000).toFixed(0)}만` : val.toLocaleString('ko-KR'),
    }
  })
})

const bars = computed(() => {
  const len = props.trends.length
  if (len === 0) return []
  const gap = 3
  const barWidth = Math.max(4, (chartWidth - gap * (len - 1)) / len)
  const totalWidth = barWidth * len + gap * (len - 1)
  const offsetX = paddingLeft + (chartWidth - totalWidth) / 2

  return props.trends.map((t, i) => {
    const ratio = t.amount / maxAmount.value
    const height = Math.max(2, ratio * chartHeight)
    return {
      x: offsetX + i * (barWidth + gap),
      y: paddingTop + chartHeight - height,
      width: barWidth,
      height,
    }
  })
})

const tooltipStyle = computed(() => {
  if (activeIndex.value < 0 || !bars.value[activeIndex.value]) return { display: 'none' }
  const bar = bars.value[activeIndex.value]
  const leftPercent = ((bar.x + bar.width / 2) / svgWidth) * 100
  const topPercent = ((bar.y) / svgHeight) * 100 - 15
  return {
    left: `${Math.min(Math.max(leftPercent, 10), 85)}%`,
    top: `${Math.max(topPercent, 0)}%`,
    transform: 'translateX(-50%)',
  }
})

function formatDateLabel(date: string): string {
  const parts = date.split('-')
  return `${parts[1]}/${parts[2]}`
}

function formatAmount(value: number): string {
  if (value >= 10000) {
    return `${(value / 10000).toFixed(1)}만원`
  }
  return `${value.toLocaleString('ko-KR')}원`
}
</script>
