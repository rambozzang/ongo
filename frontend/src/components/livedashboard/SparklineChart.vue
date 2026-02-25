<template>
  <svg
    :width="width"
    :height="height"
    :viewBox="`0 0 ${width} ${height}`"
    class="overflow-visible"
  >
    <defs>
      <linearGradient :id="gradientId" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" :stop-color="strokeColor" stop-opacity="0.3" />
        <stop offset="100%" :stop-color="strokeColor" stop-opacity="0.02" />
      </linearGradient>
    </defs>

    <!-- Gradient fill area -->
    <polygon
      v-if="polylinePoints.length > 0"
      :points="areaPoints"
      :fill="`url(#${gradientId})`"
    />

    <!-- Line -->
    <polyline
      v-if="polylinePoints.length > 0"
      :points="linePoints"
      fill="none"
      :stroke="strokeColor"
      stroke-width="1.5"
      stroke-linecap="round"
      stroke-linejoin="round"
    />

    <!-- Last point dot -->
    <circle
      v-if="lastPoint"
      :cx="lastPoint.x"
      :cy="lastPoint.y"
      r="3"
      :fill="strokeColor"
      class="animate-pulse"
    />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { LiveMetricPoint } from '@/types/liveDashboard'

const props = withDefaults(defineProps<{
  points: LiveMetricPoint[]
  trend: 'UP' | 'DOWN' | 'STABLE'
  width?: number
  height?: number
}>(), {
  width: 120,
  height: 40,
})

const gradientId = computed(() => `sparkline-gradient-${Math.random().toString(36).slice(2, 9)}`)

const strokeColor = computed(() => {
  switch (props.trend) {
    case 'UP': return '#22c55e'
    case 'DOWN': return '#ef4444'
    default: return '#9ca3af'
  }
})

const polylinePoints = computed(() => {
  const data = props.points.slice(-24)
  if (data.length < 2) return []

  const values = data.map((p) => p.value)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min || 1
  const padding = 4

  return data.map((p, i) => ({
    x: padding + ((props.width - padding * 2) / (data.length - 1)) * i,
    y: padding + (props.height - padding * 2) * (1 - (p.value - min) / range),
  }))
})

const linePoints = computed(() =>
  polylinePoints.value.map((p) => `${p.x},${p.y}`).join(' '),
)

const areaPoints = computed(() => {
  if (polylinePoints.value.length === 0) return ''
  const pts = polylinePoints.value
  const first = pts[0]
  const last = pts[pts.length - 1]
  return [
    `${first.x},${props.height}`,
    ...pts.map((p) => `${p.x},${p.y}`),
    `${last.x},${props.height}`,
  ].join(' ')
})

const lastPoint = computed(() => {
  if (polylinePoints.value.length === 0) return null
  return polylinePoints.value[polylinePoints.value.length - 1]
})
</script>
