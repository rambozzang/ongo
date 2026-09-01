<template>
  <div class="space-y-3">
    <!--
      **문구는 실제 값과 같아야 한다.** 서버가 주는 값은 참여율이 아니라 그 시간대에
      게시한 영상들의 **조회수 합계**(`getHeatmapData`)다. "평균 참여율" 이라고 쓰면
      화면이 재지도 않은 지표를 주장하게 된다.
    -->
    <p class="text-body-xs text-gray-400 dark:text-gray-500">
      게시 요일/시간대별 조회수 합계 - 색이 진할수록 조회수가 많습니다
    </p>
    <div class="overflow-x-auto">
      <div class="min-w-[640px]">
        <!-- Hour labels header -->
        <div class="mb-1 flex">
          <div class="w-12 flex-shrink-0" />
          <div class="grid flex-1 grid-cols-24 gap-1">
            <div
              v-for="hour in hourLabels"
              :key="'h-' + hour"
              class="text-center text-[10px] text-gray-400 dark:text-gray-500"
            >
              {{ hour }}
            </div>
          </div>
        </div>
        <!-- Day rows -->
        <div class="space-y-1">
          <div
            v-for="(dayLabel, dayIndex) in dayLabels"
            :key="dayIndex"
            class="flex items-center"
          >
            <div class="w-12 flex-shrink-0 pr-2 text-right text-caption text-gray-500 dark:text-gray-400">
              {{ dayLabel }}
            </div>
            <div class="grid flex-1 grid-cols-24 gap-1">
              <div
                v-for="hour in 24"
                :key="dayIndex + '-' + hour"
                class="relative cursor-pointer rounded-sm transition-all hover:scale-110 hover:shadow-lg"
                :class="[
                  cellSize,
                  isBestTime(dayIndex, hour - 1) ? 'ring-2 ring-yellow-400 ring-offset-1 dark:ring-offset-gray-900' : '',
                ]"
                :style="{
                  backgroundColor: getCellColor(dayIndex, hour - 1),
                }"
                :title="getCellTooltip(dayIndex, hour - 1)"
              >
                <!-- Star icon for best time -->
                <svg
                  v-if="isBestTime(dayIndex, hour - 1)"
                  class="absolute inset-0 m-auto h-3 w-3 text-yellow-400"
                  fill="currentColor"
                  viewBox="0 0 20 20"
                >
                  <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
                </svg>
              </div>
            </div>
          </div>
        </div>
        <!-- Legend -->
        <div class="mt-4 flex items-center justify-end gap-1.5 text-[10px] text-gray-400 dark:text-gray-500">
          <span>낮음</span>
          <div
            v-for="level in 5"
            :key="'legend-' + level"
            class="h-3 w-5 rounded-sm"
            :style="{ backgroundColor: getLegendColor(level) }"
          />
          <span>높음</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useThemeStore } from '@/stores/theme'
import type { HeatmapData } from '@/types/analytics'

interface Props {
  data: HeatmapData[]
  bestTime?: { dayOfWeek: number; hour: number } | null
}

const props = withDefaults(defineProps<Props>(), {
  bestTime: null,
})

const themeStore = useThemeStore()

// Day and hour labels
const dayLabels = ['일', '월', '화', '수', '목', '금', '토']
const hourLabels = computed(() => {
  const labels: string[] = []
  for (let i = 0; i < 24; i++) {
    // Show every 3rd hour
    if (i % 3 === 0) {
      labels.push(String(i))
    } else {
      labels.push('')
    }
  }
  return labels
})

// Responsive cell size
const cellSize = 'h-5 w-5 tablet:h-6 tablet:w-6 desktop:h-7 desktop:w-7'

// Create lookup map
const dataLookup = computed(() => {
  const map = new Map<string, number>()
  for (const d of props.data) {
    map.set(`${d.dayOfWeek}-${d.hour}`, d.value)
  }
  return map
})

// Max value for normalization
const maxValue = computed(() => {
  if (props.data.length === 0) return 1
  return Math.max(...props.data.map((d) => d.value), 1)
})

// Get cell value
function getCellValue(dayIndex: number, hour: number): number {
  return dataLookup.value.get(`${dayIndex}-${hour}`) ?? 0
}

// Get cell color based on engagement intensity
function getCellColor(dayIndex: number, hour: number): string {
  const value = getCellValue(dayIndex, hour)
  if (value === 0) {
    return themeStore.isDark ? '#1f2937' : '#f3f4f6' // gray-800 / gray-100
  }
  const intensity = value / maxValue.value

  // Map to green color scale
  if (intensity < 0.2) {
    return themeStore.isDark ? '#166534' : '#bbf7d0' // green-200 in light
  } else if (intensity < 0.4) {
    return themeStore.isDark ? '#15803d' : '#86efac' // green-300 in light
  } else if (intensity < 0.6) {
    return themeStore.isDark ? '#16a34a' : '#4ade80' // green-400
  } else if (intensity < 0.8) {
    return themeStore.isDark ? '#22c55e' : '#16a34a' // green-600
  } else {
    return themeStore.isDark ? '#15803d' : '#166534' // green-800
  }
}

// Get legend color
function getLegendColor(level: number): string {
  const intensity = level / 5
  if (intensity < 0.2) {
    return themeStore.isDark ? '#166534' : '#bbf7d0'
  } else if (intensity < 0.4) {
    return themeStore.isDark ? '#15803d' : '#86efac'
  } else if (intensity < 0.6) {
    return themeStore.isDark ? '#16a34a' : '#4ade80'
  } else if (intensity < 0.8) {
    return themeStore.isDark ? '#22c55e' : '#16a34a'
  } else {
    return themeStore.isDark ? '#15803d' : '#166534'
  }
}

/**
 * 칸 설명.
 *
 * 예전에는 `평균 참여율: X%` 였는데, 그 `X` 는 참여율이 아니라 **최대값 대비 비율**을
 * 백분율로 찍은 것이었다. 참여율을 잰 적이 없는데 화면은 참여율을 말했다.
 * 측정된 값(조회수 합계)을 그대로 보여준다.
 *
 * 데이터가 없는 칸과 **실제로 0 회였던 칸**은 다르다. 앞은 "게시 기록 없음", 뒤는 0 이다.
 */
function getCellTooltip(dayIndex: number, hour: number): string {
  const dayLabel = dayLabels[dayIndex]
  const measured = dataLookup.value.get(`${dayIndex}-${hour}`)
  if (measured === undefined) {
    return `${dayLabel}요일 ${hour}시 - 게시 기록 없음`
  }
  return `${dayLabel}요일 ${hour}시 - 조회수 ${measured.toLocaleString()}회`
}

// Check if this is the best time
function isBestTime(dayIndex: number, hour: number): boolean {
  if (!props.bestTime) return false
  return props.bestTime.dayOfWeek === dayIndex && props.bestTime.hour === hour
}
</script>

<style scoped>
.grid-cols-24 {
  grid-template-columns: repeat(24, minmax(0, 1fr));
}
</style>
