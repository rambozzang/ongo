<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ $t('calendar.title') }}</h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('calendar.description') }}
        </p>
      </div>

      <!-- View Mode Toggle -->
      <div class="flex items-center gap-2">
        <button
          :class="[
            'rounded-lg px-3 py-2 text-sm font-medium transition-colors',
            viewMode === 'monthly'
              ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
              : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800',
          ]"
          @click="viewMode = 'monthly'"
        >
          {{ $t('calendar.monthly') }}
        </button>
        <button
          :class="[
            'rounded-lg px-3 py-2 text-sm font-medium transition-colors',
            viewMode === 'weekly'
              ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
              : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800',
          ]"
          @click="viewMode = 'weekly'"
        >
          {{ $t('calendar.weekly') }}
        </button>
      </div>
    </div>

    <PageGuide :title="$t('calendar.pageGuideTitle')" :items="($tm('calendar.pageGuide') as string[])" />

    <!-- Calendar Controls -->
    <div class="card flex items-center justify-between p-4">
      <button
        class="rounded-lg p-2 text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
        @click="previousPeriod"
      >
        <ChevronLeftIcon class="h-5 w-5" />
      </button>

      <div class="flex items-center gap-4">
        <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ formattedCurrentDate }}
        </h2>
        <button
          class="rounded-lg px-3 py-1.5 text-sm font-medium text-primary-700 hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-900/30"
          @click="goToToday"
        >
          {{ $t('calendar.today') }}
        </button>
      </div>

      <button
        class="rounded-lg p-2 text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
        @click="nextPeriod"
      >
        <ChevronRightIcon class="h-5 w-5" />
      </button>
    </div>

    <!-- Calendar Grid -->
    <CalendarGrid :events="events" :current-date="currentDate" :view-mode="viewMode" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ChevronLeftIcon, ChevronRightIcon } from '@heroicons/vue/24/outline'
import CalendarGrid from '@/components/schedule/CalendarGrid.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import { useScheduleStore } from '@/stores/schedule'
import { useLocale } from '@/composables/useLocale'
import type { Platform } from '@/types/channel'

export interface CalendarEvent {
  id: string
  videoId: string
  title: string
  platform: Platform | null
  scheduledAt: string
  status: 'scheduled' | 'published' | 'failed'
  color?: string
}

const scheduleStore = useScheduleStore()
const { t } = useLocale()

const currentDate = ref(new Date())
const viewMode = ref<'monthly' | 'weekly'>('monthly')

const formattedCurrentDate = computed(() => {
  const year = currentDate.value.getFullYear()
  const month = currentDate.value.getMonth() + 1
  const date = currentDate.value.getDate()

  if (viewMode.value === 'monthly') {
    return t('calendar.dateFormatMonthly', { year, month })
  } else {
    const weekStart = new Date(currentDate.value)
    weekStart.setDate(date - currentDate.value.getDay())
    const weekEnd = new Date(weekStart)
    weekEnd.setDate(weekStart.getDate() + 6)

    const startMonth = weekStart.getMonth() + 1
    const startDate = weekStart.getDate()
    const endMonth = weekEnd.getMonth() + 1
    const endDate = weekEnd.getDate()

    if (startMonth === endMonth) {
      return t('calendar.dateFormatWeeklySameMonth', { year, month: startMonth, startDate, endDate })
    } else {
      return t('calendar.dateFormatWeeklyCrossMonth', { year, startMonth, startDate, endMonth, endDate })
    }
  }
})

// 현재 달의 시작/끝 날짜 계산
function getDateRange() {
  const year = currentDate.value.getFullYear()
  const month = currentDate.value.getMonth()
  const startDate = new Date(year, month, 1).toISOString().split('T')[0]
  const endDate = new Date(year, month + 1, 0).toISOString().split('T')[0]
  return { startDate, endDate }
}

async function loadSchedules() {
  const { startDate, endDate } = getDateRange()
  await scheduleStore.fetchSchedules(startDate, endDate)
}

// 스케줄 데이터를 CalendarEvent 형식으로 변환
const events = computed<CalendarEvent[]>(() => {
  return scheduleStore.schedules.flatMap((schedule) => {
    // 각 플랫폼별로 이벤트 생성
    if (schedule.platforms && schedule.platforms.length > 0) {
      return schedule.platforms.map((sp, index) => ({
        id: `${schedule.id}-${index}`,
        videoId: String(schedule.videoId),
        title: schedule.videoTitle,
        platform: sp.platform,
        scheduledAt: sp.scheduledAt || schedule.scheduledAt,
        status: (sp.status?.toLowerCase() ?? schedule.status.toLowerCase()) as 'scheduled' | 'published' | 'failed',
      }))
    }
    return [{
      id: String(schedule.id),
      videoId: String(schedule.videoId),
      title: schedule.videoTitle,
      platform: null as Platform | null,
      scheduledAt: schedule.scheduledAt,
      status: schedule.status.toLowerCase() as 'scheduled' | 'published' | 'failed',
    }]
  })
})

// 날짜 변경 시 스케줄 다시 로드
watch(currentDate, () => {
  loadSchedules()
})

onMounted(() => {
  loadSchedules()
})

const previousPeriod = () => {
  const newDate = new Date(currentDate.value)
  if (viewMode.value === 'monthly') {
    newDate.setMonth(newDate.getMonth() - 1)
  } else {
    newDate.setDate(newDate.getDate() - 7)
  }
  currentDate.value = newDate
}

const nextPeriod = () => {
  const newDate = new Date(currentDate.value)
  if (viewMode.value === 'monthly') {
    newDate.setMonth(newDate.getMonth() + 1)
  } else {
    newDate.setDate(newDate.getDate() + 7)
  }
  currentDate.value = newDate
}

const goToToday = () => {
  currentDate.value = new Date()
}
</script>
