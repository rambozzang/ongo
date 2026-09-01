<template>
  <div class="card space-y-4">
    <div class="flex flex-col gap-3 mobile:flex-row mobile:items-center mobile:justify-between">
      <h3 class="text-h3 text-gray-900 dark:text-gray-100">추천 게시 시간</h3>
      <!-- Platform filter -->
      <div class="flex gap-1 rounded-lg bg-gray-100 dark:bg-gray-800 p-1">
        <button
          v-for="filter in platformFilters"
          :key="filter.value"
          class="rounded-md px-2 py-1 text-caption transition-colors"
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

    <!-- 로딩 → 빈 상태 → 추천 목록 -->
    <AsyncState
      :loading="loadingOptimal"
      :empty="displaySlots.length === 0"
      skeleton="list"
      :skeleton-count="3"
      :empty-icon="ClockIcon"
      :empty-title="t('analyticsView.optimalTimesEmptyTitle')"
      :empty-description="optimalUnavailableReason ?? t('analyticsView.optimalTimesEmptyDescription')"
      empty-variant="compact"
    >
      <div class="space-y-4">
        <!-- Countdown to next best time -->
        <div
          v-if="nextBestTimeCountdown"
          class="flex items-center gap-2 rounded-lg bg-success-subtle p-3"
        >
          <svg class="h-5 w-5 text-success-strong" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <div class="flex-1">
            <p class="text-caption text-success-strong">다음 추천 시간까지</p>
            <p class="text-body font-bold text-success-strong">{{ nextBestTimeCountdown }}</p>
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
                ? 'border-success bg-success-subtle hover:border-success'
                : 'border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50 hover:border-warning'
            "
          >
            <div class="mb-2 flex items-center justify-between">
              <div class="flex items-center gap-2">
                <div
                  class="flex h-7 w-7 items-center justify-center rounded-full text-body-xs font-bold"
                  :class="getRankClass(index)"
                >
                  {{ index + 1 }}
                </div>
                <div>
                  <p class="text-body font-bold text-gray-900 dark:text-gray-100">{{ rec.timeLabel }}</p>
                  <p class="text-body-xs text-gray-500 dark:text-gray-400">{{ rec.dayLabel }}</p>
                </div>
              </div>
              <div class="text-right">
                <p class="text-title font-bold" :class="index < 2 ? 'text-success-strong' : 'text-warning-strong'">
                  {{ normalizedScore(rec) }}%
                </p>
              </div>
            </div>

            <!-- Details -->
            <div class="mb-2 flex gap-4 text-body-xs text-gray-600 dark:text-gray-400">
              <span>예상 조회수: <strong class="text-gray-900 dark:text-gray-100">{{ formatNumber(rec.expectedViews) }}</strong></span>
              <!--
                `?? 0` 을 하지 않는다. 참여 지표를 보고하지 않는 플랫폼의 슬롯은 서버가
                `null` 을 주는데, 0 으로 채우면 "참여가 없던 시간대" 라는 관측이 된다.
                단위(`%`)도 값이 있을 때만 붙인다 — 밖에 두면 "측정 불가%" 가 된다.
              -->
              <span>참여율: <strong class="text-gray-900 dark:text-gray-100">{{
                rec.engagementRate == null ? $t('analyticsView.notMeasured') : `${rec.engagementRate}%`
              }}</strong></span>
              <span>신뢰도: <strong class="text-gray-900 dark:text-gray-100">{{ rec.confidenceScore }}%</strong></span>
            </div>

            <!-- Engagement score bar -->
            <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
              <div
                class="h-full rounded-full transition-all duration-500"
                :class="getBarClass(index)"
                :style="{ width: `${normalizedScore(rec)}%` }"
              />
            </div>
          </div>
        </div>
      </div>
    </AsyncState>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ClockIcon } from '@heroicons/vue/24/outline'
import type { Platform } from '@/types/channel'
import type { HeatmapData, OptimalTimeSlot } from '@/types/analytics'
import { analyticsApi } from '@/api/analytics'
import AsyncState from '@/components/common/AsyncState.vue'

interface Props {
  data: HeatmapData[]
}

// 히트맵 폴백을 없애면서 `data` 를 읽는 곳이 사라졌다. prop 자체는 부모 계약이라 남긴다.
defineProps<Props>()

type PlatformFilter = 'all' | Platform

const platformFilters = [
  { value: 'all' as const, label: '전체' },
  { value: 'YOUTUBE' as const, label: 'YouTube' },
  { value: 'TIKTOK' as const, label: 'TikTok' },
  { value: 'INSTAGRAM' as const, label: 'Instagram' },
]

const selectedPlatform = ref<PlatformFilter>('all')
const loadingOptimal = ref(false)
const { t } = useI18n({ useScope: 'global' })

const optimalSlots = ref<OptimalTimeSlot[]>([])
/** 서버가 추천을 못 만든 이유. 화면이 그대로 보여준다. */
const optimalUnavailableReason = ref<string | null>(null)

async function fetchOptimalTimes() {
  loadingOptimal.value = true
  try {
    const platform = selectedPlatform.value === 'all' ? undefined : selectedPlatform.value
    const result = await analyticsApi.getOptimalTimes(platform)
    optimalSlots.value = result.slots
    optimalUnavailableReason.value = result.unavailableReason ?? null
  } catch {
    optimalSlots.value = []
    optimalUnavailableReason.value = null
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

/*
 * **히트맵 폴백을 제거했다.**
 *
 * 예전에는 API 가 빈 결과를 주면 히트맵에서 상위 5개를 뽑아 슬롯을 만들고
 * `engagementRate: 0, confidenceScore: 0` 을 채웠다. 화면은 그것을
 * `참여율 0% / 신뢰도 0%` 로 **측정값처럼** 보여줬다 — 재지 않았을 뿐인데
 * "참여가 없는 시간대" 라는 관측이 된다.
 *
 * 게다가 그 히트맵 슬롯은 서버가 게시 시각을 확인하지 못해 추천을 만들지 못한
 * 상황에서 나온다. 근거가 없어서 못 만든 자리를 다른 근거 없는 숫자로 채우는 셈이다.
 *
 * 이제 서버 슬롯만 쓴다. 없으면 빈 상태와 사유를 보여준다.
 */
const displaySlots = computed<OptimalTimeSlot[]>(() => optimalSlots.value)

function normalizedScore(slot: OptimalTimeSlot): number {
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
