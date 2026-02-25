<template>
  <div class="card">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
        {{ $t('prediction.heatmapTitle') }}
      </h3>
      <div class="flex gap-1 rounded-lg bg-gray-100 dark:bg-gray-800 p-1">
        <button
          v-for="p in platforms"
          :key="p.value"
          class="rounded-md px-2 py-1 text-xs font-medium transition-colors"
          :class="
            selectedPlatform === p.value
              ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm'
              : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
          "
          @click="$emit('platformChange', p.value)"
        >
          {{ p.label }}
        </button>
      </div>
    </div>

    <p class="mb-3 text-xs text-gray-400 dark:text-gray-500">
      {{ $t('prediction.heatmapDescription') }}
    </p>

    <div v-if="loading" class="flex items-center justify-center py-12">
      <LoadingSpinner />
    </div>

    <div v-else class="overflow-x-auto">
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
            <div class="w-12 flex-shrink-0 pr-2 text-right text-xs font-medium text-gray-500 dark:text-gray-400">
              {{ dayLabel }}
            </div>
            <div class="grid flex-1 grid-cols-24 gap-1">
              <div
                v-for="hour in 24"
                :key="dayIndex + '-' + hour"
                class="relative cursor-pointer rounded-sm transition-all hover:scale-110 hover:shadow-lg"
                :class="[
                  'h-5 w-5 tablet:h-6 tablet:w-6 desktop:h-7 desktop:w-7',
                  isBestSlot(dayIndex, hour - 1) ? 'ring-2 ring-yellow-400 ring-offset-1 dark:ring-offset-gray-900' : '',
                ]"
                :style="{ backgroundColor: getCellColor(dayIndex, hour - 1) }"
                :title="getCellTooltip(dayIndex, hour - 1)"
              >
                <svg
                  v-if="isBestSlot(dayIndex, hour - 1)"
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

        <!-- Optimal time recommendations -->
        <div v-if="optimalTimes.length > 0" class="mt-4 space-y-2">
          <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('prediction.recommendedTimes') }}
          </h4>
          <div class="flex flex-wrap gap-2">
            <div
              v-for="(slot, idx) in optimalTimes"
              :key="idx"
              class="inline-flex items-center gap-2 rounded-lg border border-primary-200 dark:border-primary-800 bg-primary-50 dark:bg-primary-900/20 px-3 py-1.5"
            >
              <ClockIcon class="h-4 w-4 text-primary-500" />
              <div>
                <span class="text-sm font-medium text-primary-700 dark:text-primary-400">
                  {{ dayLabelsKo[slot.dayOfWeek] }} {{ slot.hour }}:00
                </span>
                <span class="ml-2 text-xs text-primary-500 dark:text-primary-400">
                  {{ $t('prediction.reachScore') }}: {{ slot.expectedReach }}%
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Legend -->
        <div class="mt-4 flex items-center justify-end gap-1.5 text-[10px] text-gray-400 dark:text-gray-500">
          <span>{{ $t('prediction.low') }}</span>
          <div
            v-for="level in 5"
            :key="'legend-' + level"
            class="h-3 w-5 rounded-sm"
            :style="{ backgroundColor: getLegendColor(level) }"
          />
          <span>{{ $t('prediction.high') }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ClockIcon } from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useThemeStore } from '@/stores/theme'
import { useI18n } from 'vue-i18n'
import type { HeatmapCell, OptimalTimeRecommendation } from '@/types/prediction'

interface Props {
  data: HeatmapCell[]
  optimalTimes: OptimalTimeRecommendation[]
  selectedPlatform: string
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
})

defineEmits<{
  platformChange: [platform: string]
}>()

const { t } = useI18n({ useScope: 'global' })
const themeStore = useThemeStore()

const platforms = [
  { value: 'all', label: 'All' },
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'NAVER_CLIP', label: 'Naver' },
]

const dayLabelsKo = ['일', '월', '화', '수', '목', '금', '토']
const dayLabels = computed(() => (t('prediction.dayLabels') as string).split(','))

const hourLabels = computed(() => {
  const labels: string[] = []
  for (let i = 0; i < 24; i++) {
    labels.push(i % 3 === 0 ? String(i) : '')
  }
  return labels
})

const dataLookup = computed(() => {
  const map = new Map<string, number>()
  for (const d of props.data) {
    map.set(`${d.dayOfWeek}-${d.hour}`, d.reachScore)
  }
  return map
})

const maxValue = computed(() => {
  if (props.data.length === 0) return 1
  return Math.max(...props.data.map((d) => d.reachScore), 1)
})

function getCellValue(dayIndex: number, hour: number): number {
  return dataLookup.value.get(`${dayIndex}-${hour}`) ?? 0
}

function getCellColor(dayIndex: number, hour: number): string {
  const value = getCellValue(dayIndex, hour)
  if (value === 0) return themeStore.isDark ? '#1f2937' : '#f3f4f6'
  const intensity = value / maxValue.value

  if (intensity < 0.2) return themeStore.isDark ? '#312e81' : '#e0e7ff'
  if (intensity < 0.4) return themeStore.isDark ? '#3730a3' : '#c7d2fe'
  if (intensity < 0.6) return themeStore.isDark ? '#4338ca' : '#a5b4fc'
  if (intensity < 0.8) return themeStore.isDark ? '#4f46e5' : '#818cf8'
  return themeStore.isDark ? '#6366f1' : '#6366f1'
}

function getLegendColor(level: number): string {
  const intensity = level / 5
  if (intensity < 0.2) return themeStore.isDark ? '#312e81' : '#e0e7ff'
  if (intensity < 0.4) return themeStore.isDark ? '#3730a3' : '#c7d2fe'
  if (intensity < 0.6) return themeStore.isDark ? '#4338ca' : '#a5b4fc'
  if (intensity < 0.8) return themeStore.isDark ? '#4f46e5' : '#818cf8'
  return themeStore.isDark ? '#6366f1' : '#6366f1'
}

function getCellTooltip(dayIndex: number, hour: number): string {
  const value = getCellValue(dayIndex, hour)
  const dayLabel = dayLabelsKo[dayIndex]
  return `${dayLabel} ${hour}:00 - ${t('prediction.reachScore')}: ${value}%`
}

function isBestSlot(dayIndex: number, hour: number): boolean {
  return props.optimalTimes.some(
    (slot) => slot.dayOfWeek === dayIndex && slot.hour === hour,
  )
}
</script>

<style scoped>
.grid-cols-24 {
  grid-template-columns: repeat(24, minmax(0, 1fr));
}
</style>
