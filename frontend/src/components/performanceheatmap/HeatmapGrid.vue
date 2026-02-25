<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
    <h3 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
      {{ $t('performanceHeatmap.heatmapTitle') }}
    </h3>

    <div class="overflow-x-auto">
      <div class="min-w-[640px]">
        <!-- Column headers (hours) -->
        <div class="flex">
          <div class="w-10 flex-shrink-0" />
          <div class="grid flex-1 grid-cols-24 gap-0.5">
            <div
              v-for="hour in 24"
              :key="hour - 1"
              class="text-center text-xs text-gray-400 dark:text-gray-500"
            >
              <span v-if="(hour - 1) % 3 === 0">{{ hour - 1 }}{{ $t('performanceHeatmap.hourSuffix') }}</span>
            </div>
          </div>
        </div>

        <!-- Rows (days) -->
        <div
          v-for="day in days"
          :key="day.key"
          class="mt-0.5 flex items-center"
        >
          <!-- Row header -->
          <div class="w-10 flex-shrink-0 text-right pr-2 text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ day.label }}
          </div>
          <!-- Cells -->
          <div class="grid flex-1 grid-cols-24 gap-0.5">
            <div
              v-for="hour in 24"
              :key="`${day.key}-${hour - 1}`"
              class="relative aspect-square cursor-pointer rounded-sm transition-transform hover:scale-110 hover:z-10"
              :style="getCellStyle(day.key, hour - 1)"
              :class="isBestSlot(day.key, hour - 1) ? 'ring-2 ring-yellow-400 dark:ring-yellow-300' : ''"
              @mouseenter="showTooltip($event, day.key, hour - 1)"
              @mouseleave="hideTooltip"
            >
              <StarIcon
                v-if="isBestSlot(day.key, hour - 1)"
                class="absolute inset-0 m-auto h-3 w-3 text-yellow-500 dark:text-yellow-300"
              />
            </div>
          </div>
        </div>

        <!-- Color scale legend -->
        <div class="mt-4 flex items-center justify-end gap-2">
          <span class="text-xs text-gray-400 dark:text-gray-500">{{ $t('performanceHeatmap.metricViews') }}</span>
          <div class="flex gap-0.5">
            <div
              v-for="level in 5"
              :key="level"
              class="h-3 w-6 rounded-sm"
              :style="{ backgroundColor: getScaleColor(level / 5) }"
            />
          </div>
          <span class="text-xs text-gray-400 dark:text-gray-500">{{ maxValue }}</span>
        </div>
      </div>
    </div>

    <!-- Tooltip -->
    <Teleport to="body">
      <div
        v-if="tooltip.visible"
        class="pointer-events-none fixed z-50 rounded-lg border border-gray-200 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 shadow-lg"
        :style="{ top: tooltip.y + 'px', left: tooltip.x + 'px' }"
      >
        <div class="text-xs font-medium text-gray-900 dark:text-gray-100">
          {{ getDayLabel(tooltip.day) }} {{ tooltip.hour }}{{ $t('performanceHeatmap.hourSuffix') }}
        </div>
        <div class="mt-1 text-xs text-gray-500 dark:text-gray-400">
          {{ $t('performanceHeatmap.tooltipValue') }}: <span class="font-semibold text-gray-900 dark:text-gray-100">{{ tooltip.value }}</span>
        </div>
        <div class="text-xs text-gray-500 dark:text-gray-400">
          {{ $t('performanceHeatmap.tooltipContentCount') }}: <span class="font-semibold text-gray-900 dark:text-gray-100">{{ tooltip.contentCount }}</span>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { StarIcon } from '@heroicons/vue/24/outline'
import type { HeatmapData, DayOfWeek } from '@/types/performanceHeatmap'

const { t } = useI18n({ useScope: 'global' })

const props = defineProps<{
  data: HeatmapData
  maxValue: number
}>()

const days = computed<{ key: DayOfWeek; label: string }[]>(() => [
  { key: 'MON', label: t('performanceHeatmap.dayMon') },
  { key: 'TUE', label: t('performanceHeatmap.dayTue') },
  { key: 'WED', label: t('performanceHeatmap.dayWed') },
  { key: 'THU', label: t('performanceHeatmap.dayThu') },
  { key: 'FRI', label: t('performanceHeatmap.dayFri') },
  { key: 'SAT', label: t('performanceHeatmap.daySat') },
  { key: 'SUN', label: t('performanceHeatmap.daySun') },
])

const tooltip = reactive({
  visible: false,
  x: 0,
  y: 0,
  day: '' as DayOfWeek,
  hour: 0,
  value: 0,
  contentCount: 0,
})

function getCell(day: DayOfWeek, hour: number) {
  return props.data.cells.find((c) => c.day === day && c.hour === hour)
}

function getCellStyle(day: DayOfWeek, hour: number) {
  const cell = getCell(day, hour)
  const value = cell?.value ?? 0
  const intensity = props.maxValue > 0 ? value / props.maxValue : 0
  return {
    backgroundColor: getScaleColor(intensity),
  }
}

function getScaleColor(intensity: number): string {
  const clamped = Math.max(0, Math.min(1, intensity))
  if (clamped === 0) return 'rgb(243, 244, 246)' // gray-100
  const r = Math.round(220 - clamped * 186)
  const g = Math.round(240 - clamped * 100)
  const b = Math.round(220 - clamped * 186)
  return `rgb(${r}, ${g}, ${b})`
}

function isBestSlot(day: DayOfWeek, hour: number): boolean {
  return props.data.bestSlots.some((s) => s.day === day && s.hour === hour)
}

function getDayLabel(day: DayOfWeek): string {
  const map: Record<DayOfWeek, string> = {
    MON: t('performanceHeatmap.dayMon'),
    TUE: t('performanceHeatmap.dayTue'),
    WED: t('performanceHeatmap.dayWed'),
    THU: t('performanceHeatmap.dayThu'),
    FRI: t('performanceHeatmap.dayFri'),
    SAT: t('performanceHeatmap.daySat'),
    SUN: t('performanceHeatmap.daySun'),
  }
  return map[day] ?? day
}

function showTooltip(event: MouseEvent, day: DayOfWeek, hour: number) {
  const cell = getCell(day, hour)
  tooltip.day = day
  tooltip.hour = hour
  tooltip.value = cell?.value ?? 0
  tooltip.contentCount = cell?.contentCount ?? 0
  tooltip.x = event.clientX + 12
  tooltip.y = event.clientY - 40
  tooltip.visible = true
}

function hideTooltip() {
  tooltip.visible = false
}
</script>

<style scoped>
.grid-cols-24 {
  grid-template-columns: repeat(24, minmax(0, 1fr));
}
</style>
