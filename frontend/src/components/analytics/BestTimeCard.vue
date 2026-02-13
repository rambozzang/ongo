<template>
  <div class="card space-y-4">
    <div class="flex flex-col gap-3 mobile:flex-row mobile:items-center mobile:justify-between">
      <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">추천 게시 시간</h3>
      <!-- Platform filter -->
      <div class="flex gap-1 rounded-lg bg-gray-100 dark:bg-gray-800 p-1">
        <button
          v-for="filter in platformFilters"
          :key="filter.value"
          class="rounded-md px-2 py-1 text-xs font-medium transition-colors"
          :class="
            selectedPlatform === filter.value
              ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm'
              : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
          "
          @click="onPlatformChange(filter.value)"
        >
          {{ filter.label }}
        </button>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loadingOptimal" class="flex items-center justify-center py-8">
      <div class="h-6 w-6 animate-spin rounded-full border-2 border-gray-300 border-t-primary-600" />
    </div>

    <template v-else>
      <!-- Countdown to next best time -->
      <div
        v-if="nextBestTimeCountdown"
        class="flex items-center gap-2 rounded-lg bg-green-50 dark:bg-green-900/20 p-3"
      >
        <svg class="h-5 w-5 text-green-600 dark:text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <div class="flex-1">
          <p class="text-xs font-medium text-green-700 dark:text-green-400">다음 추천 시간까지</p>
          <p class="text-sm font-bold text-green-800 dark:text-green-300">{{ nextBestTimeCountdown }}</p>
        </div>
      </div>

      <!-- Top 5 recommendations -->
      <div class="space-y-3">
        <div
          v-for="(rec, index) in displaySlots"
          :key="index"
          class="rounded-lg border p-3 transition-colors"
          :class="
            index < 2
              ? 'border-green-200 dark:border-green-800 bg-green-50/50 dark:bg-green-900/10 hover:border-green-400 dark:hover:border-green-600'
              : 'border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50 hover:border-yellow-300 dark:hover:border-yellow-700'
          "
        >
          <div class="mb-2 flex items-center justify-between">
            <div class="flex items-center gap-2">
              <div
                class="flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold"
                :class="getRankClass(index)"
              >
                {{ index + 1 }}
              </div>
              <div>
                <p class="text-sm font-bold text-gray-900 dark:text-gray-100">{{ rec.timeLabel }}</p>
                <p class="text-xs text-gray-500 dark:text-gray-400">{{ rec.dayLabel }}</p>
              </div>
            </div>
            <div class="text-right">
              <p class="text-lg font-bold" :class="index < 2 ? 'text-green-600 dark:text-green-400' : 'text-yellow-600 dark:text-yellow-400'">
                {{ normalizedScore(rec, index) }}%
              </p>
            </div>
          </div>

          <!-- Details -->
          <div class="mb-2 flex gap-4 text-xs text-gray-600 dark:text-gray-400">
            <span>예상 조회수: <strong class="text-gray-900 dark:text-gray-100">{{ formatNumber(rec.expectedViews) }}</strong></span>
            <span>참여율: <strong class="text-gray-900 dark:text-gray-100">{{ rec.engagementRate }}%</strong></span>
            <span>신뢰도: <strong class="text-gray-900 dark:text-gray-100">{{ rec.confidenceScore }}%</strong></span>
          </div>

          <!-- Engagement score bar -->
          <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="getBarClass(index)"
              :style="{ width: `${normalizedScore(rec, index)}%` }"
            />
          </div>
        </div>

        <!-- Empty state -->
        <div v-if="displaySlots.length === 0" class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
          분석 데이터가 부족합니다. 영상을 업로드한 후 데이터가 쌓이면 추천이 표시됩니다.
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import type { Platform } from '@/types/channel'
import type { HeatmapData, OptimalTimeSlot } from '@/types/analytics'
import { analyticsApi } from '@/api/analytics'

interface Props {
  data: HeatmapData[]
}

const props = defineProps<Props>()

type PlatformFilter = 'all' | Platform

const platformFilters = [
  { value: 'all' as const, label: '전체' },
  { value: 'YOUTUBE' as const, label: 'YouTube' },
  { value: 'TIKTOK' as const, label: 'TikTok' },
  { value: 'INSTAGRAM' as const, label: 'Instagram' },
]

const selectedPlatform = ref<PlatformFilter>('all')
const loadingOptimal = ref(false)
const optimalSlots = ref<OptimalTimeSlot[]>([])

async function fetchOptimalTimes() {
  loadingOptimal.value = true
  try {
    const platform = selectedPlatform.value === 'all' ? undefined : selectedPlatform.value
    const result = await analyticsApi.getOptimalTimes(platform)
    optimalSlots.value = result.slots
  } catch {
    optimalSlots.value = []
  } finally {
    loadingOptimal.value = false
  }
}

function onPlatformChange(value: PlatformFilter) {
  selectedPlatform.value = value
}

watch(selectedPlatform, () => {
  fetchOptimalTimes()
})

// Use optimal slots from API, fall back to heatmap-based when API returns empty
const displaySlots = computed<OptimalTimeSlot[]>(() => {
  if (optimalSlots.value.length > 0) return optimalSlots.value

  // Fallback: derive from heatmap data
  if (props.data.length === 0) return []
  const dayLabels = ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일']
  const sorted = [...props.data].sort((a, b) => b.value - a.value).slice(0, 5)
  const maxValue = sorted[0]?.value ?? 1
  return sorted.map((item) => ({
    dayOfWeek: item.dayOfWeek,
    dayLabel: dayLabels[item.dayOfWeek],
    hour: item.hour,
    timeLabel: `${String(item.hour).padStart(2, '0')}:00`,
    expectedViews: item.value,
    engagementRate: 0,
    confidenceScore: 0,
    score: Math.round((item.value / maxValue) * 100),
  }))
})

function normalizedScore(slot: OptimalTimeSlot, index: number): number {
  if (displaySlots.value.length === 0) return 0
  const maxScore = displaySlots.value[0]?.score ?? 1
  if (maxScore === 0) return 0
  return Math.round((slot.score / maxScore) * 100)
}

function formatNumber(value: number): string {
  if (value >= 10000) return `${(value / 10000).toFixed(1)}만`
  if (value >= 1000) return `${(value / 1000).toFixed(1)}천`
  return value.toLocaleString()
}

function getRankClass(index: number): string {
  if (index === 0) return 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400'
  if (index === 1) return 'bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300'
  if (index === 2) return 'bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-400'
  return 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400'
}

function getBarClass(index: number): string {
  if (index === 0) return 'bg-green-600 dark:bg-green-500'
  if (index === 1) return 'bg-green-500 dark:bg-green-600'
  return 'bg-yellow-400 dark:bg-yellow-600'
}

// Calculate countdown to next best time
const currentTime = ref(new Date())
let countdownInterval: number | null = null

const nextBestTimeCountdown = computed(() => {
  if (displaySlots.value.length === 0) return null

  const now = currentTime.value
  const currentDay = now.getDay()
  const currentHour = now.getHours()
  const currentMinute = now.getMinutes()

  let nextTime: Date | null = null

  for (const rec of displaySlots.value) {
    const targetDate = new Date(now)
    let daysUntil = rec.dayOfWeek - currentDay

    if (daysUntil < 0) {
      daysUntil += 7
    } else if (daysUntil === 0 && (rec.hour < currentHour || (rec.hour === currentHour && currentMinute > 0))) {
      daysUntil = 7
    }

    targetDate.setDate(targetDate.getDate() + daysUntil)
    targetDate.setHours(rec.hour, 0, 0, 0)

    if (!nextTime || targetDate < nextTime) {
      nextTime = targetDate
    }
  }

  if (!nextTime) return null

  const diff = nextTime.getTime() - now.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))

  if (days > 0) {
    return `${days}일 ${hours}시간 ${minutes}분`
  } else if (hours > 0) {
    return `${hours}시간 ${minutes}분`
  } else {
    return `${minutes}분`
  }
})

onMounted(() => {
  fetchOptimalTimes()
  countdownInterval = window.setInterval(() => {
    currentTime.value = new Date()
  }, 60000)
})

onUnmounted(() => {
  if (countdownInterval) {
    clearInterval(countdownInterval)
  }
})
</script>
