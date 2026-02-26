<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  confidence: number
}>()

const gaugeColor = computed(() => {
  if (props.confidence > 95) return '#22c55e'
  if (props.confidence >= 80) return '#eab308'
  return '#ef4444'
})

const gaugeColorClass = computed(() => {
  if (props.confidence > 95) return 'text-green-500'
  if (props.confidence >= 80) return 'text-yellow-500'
  return 'text-red-500'
})

const isSignificant = computed(() => props.confidence >= 95)

// SVG semicircle arc calculation
// Arc from -180 to 0 degrees (left to right semicircle)
const radius = 80
const strokeWidth = 12
const cx = 100
const cy = 95

const totalArc = Math.PI // 180 degrees
const filledArc = computed(() => (props.confidence / 100) * totalArc)

const describeArc = (startAngle: number, endAngle: number) => {
  const startX = cx + radius * Math.cos(Math.PI + startAngle)
  const startY = cy + radius * Math.sin(Math.PI + startAngle)
  const endX = cx + radius * Math.cos(Math.PI + endAngle)
  const endY = cy + radius * Math.sin(Math.PI + endAngle)
  const largeArcFlag = endAngle - startAngle > Math.PI ? 1 : 0
  return `M ${startX} ${startY} A ${radius} ${radius} 0 ${largeArcFlag} 1 ${endX} ${endY}`
}

const bgArcPath = describeArc(0, totalArc)
const filledArcPath = computed(() => describeArc(0, filledArc.value))
</script>

<template>
  <div class="flex flex-col items-center">
    <svg viewBox="0 0 200 120" class="w-full max-w-[200px]">
      <!-- Background arc -->
      <path
        :d="bgArcPath"
        fill="none"
        stroke="currentColor"
        :stroke-width="strokeWidth"
        stroke-linecap="round"
        class="text-gray-200 dark:text-gray-700"
      />
      <!-- Filled arc -->
      <path
        :d="filledArcPath"
        fill="none"
        :stroke="gaugeColor"
        :stroke-width="strokeWidth"
        stroke-linecap="round"
        class="transition-all duration-700"
      />
      <!-- Center text -->
      <text
        :x="cx"
        :y="cy - 10"
        text-anchor="middle"
        :fill="gaugeColor"
        class="text-2xl font-bold"
        style="font-size: 28px; font-weight: 700;"
      >
        {{ confidence }}%
      </text>
      <text
        :x="cx"
        :y="cy + 12"
        text-anchor="middle"
        fill="currentColor"
        class="text-gray-500 dark:text-gray-400"
        style="font-size: 10px;"
      >
        신뢰도
      </text>
    </svg>

    <!-- Significance text -->
    <div class="mt-2 text-center">
      <span
        v-if="isSignificant"
        class="inline-flex items-center gap-1 rounded-full bg-green-100 px-3 py-1 text-xs font-semibold text-green-700 dark:bg-green-900/30 dark:text-green-300"
      >
        통계적으로 유의미
      </span>
      <span
        v-else
        :class="gaugeColorClass"
        class="inline-flex items-center gap-1 rounded-full bg-gray-100 px-3 py-1 text-xs font-semibold dark:bg-gray-800"
      >
        아직 유의미하지 않음
      </span>
    </div>
  </div>
</template>
