<template>
  <div class="relative">
    <!-- Header -->
    <PageHeader :title="$t('calendar.title')" :description="$t('calendar.description')">
      <!-- View Mode Toggle -->
      <template #actions>
        <button
          class="btn-secondary inline-flex items-center gap-2"
          @click="showAiOptimalModal = true"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ $t('calendar.aiOptimalTime') }}
        </button>
        <button
          class="btn-primary inline-flex items-center gap-2"
          @click="showAiCalendarModal = true"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ $t('calendar.aiCalendarGenerate') }}
        </button>
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
      </template>
    </PageHeader>

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

    <!-- AI 최적 시간 모달 -->
    <BaseModal
      v-model="showAiOptimalModal"
      :title="$t('calendar.aiOptimalTime')"
      max-width="lg"
    >
      <div class="space-y-4">
        <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('calendar.aiOptimalTimeDesc') }}</p>

        <!-- Platform Select -->
        <div>
          <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('calendar.selectPlatform') }}
          </label>
          <select v-model="optimalPlatform" class="input-field">
            <option value="YOUTUBE">YouTube</option>
            <option value="TIKTOK">TikTok</option>
            <option value="INSTAGRAM">Instagram</option>
            <option value="NAVER_CLIP">Naver Clip</option>
          </select>
        </div>

        <button
          class="btn-primary inline-flex w-full items-center justify-center gap-2"
          :disabled="optimalLoading"
          @click="generateOptimalSlots"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ optimalLoading ? $t('calendar.generating') : $t('calendar.aiOptimalTime') }}
        </button>

        <!-- Results -->
        <div v-if="optimalSlots.length > 0" class="space-y-3">
          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ optimalPlatform }} - {{ $t('calendar.aiOptimalTime') }}
          </h3>
          <div class="grid grid-cols-1 gap-3 mobile:grid-cols-2">
            <div
              v-for="slot in optimalSlots"
              :key="slot.id"
              class="card rounded-lg p-3"
            >
              <div class="flex items-center justify-between">
                <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
                  {{ slot.dayOfWeek }} {{ slot.hour }}:00
                </span>
                <span class="rounded-full bg-primary-100 px-2 py-0.5 text-xs font-semibold text-primary-700 dark:bg-primary-900/30 dark:text-primary-400">
                  {{ $t('calendar.score') }} {{ slot.score }}
                </span>
              </div>
              <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                {{ $t('calendar.audience') }}: {{ slot.estimatedAudience.toLocaleString() }}
              </p>
              <p class="text-xs text-gray-500 dark:text-gray-400">
                {{ $t('calendar.competition') }}: {{ slot.competition }}
              </p>
              <p class="mt-1 text-xs text-gray-600 dark:text-gray-300">{{ slot.reason }}</p>
            </div>
          </div>
        </div>
      </div>
    </BaseModal>

    <!-- AI 캘린더 생성 모달 -->
    <BaseModal
      v-model="showAiCalendarModal"
      :title="$t('calendar.aiCalendarGenerate')"
      max-width="lg"
    >
      <div class="space-y-4">
        <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('calendar.aiCalendarGenerateDesc') }}</p>

        <!-- Date Range -->
        <div>
          <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('calendar.selectDateRange') }}
          </label>
          <div class="flex gap-2">
            <input v-model="calendarForm.startDate" type="date" class="input-field flex-1" />
            <input v-model="calendarForm.endDate" type="date" class="input-field flex-1" />
          </div>
        </div>

        <!-- Platforms -->
        <div>
          <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('calendar.selectPlatform') }}
          </label>
          <div class="flex flex-wrap gap-2">
            <label
              v-for="p in availablePlatforms"
              :key="p.value"
              class="flex cursor-pointer items-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm transition-colors"
              :class="calendarForm.platforms.includes(p.value)
                ? 'border-primary-500 bg-primary-50 text-primary-700 dark:bg-primary-900/20 dark:text-primary-400'
                : 'border-gray-300 text-gray-700 dark:border-gray-600 dark:text-gray-300'"
            >
              <input
                type="checkbox"
                :value="p.value"
                v-model="calendarForm.platforms"
                class="hidden"
              />
              {{ p.label }}
            </label>
          </div>
        </div>

        <!-- Frequency -->
        <div>
          <label class="mb-1.5 block text-sm font-medium text-gray-700 dark:text-gray-300">
            {{ $t('calendar.selectFrequency') }}
          </label>
          <select v-model="calendarForm.frequency" class="input-field">
            <option value="DAILY">{{ $t('calendar.frequencyDaily') }}</option>
            <option value="WEEKLY_2_3">{{ $t('calendar.frequencyWeekly') }}</option>
            <option value="WEEKLY_1_2">{{ $t('calendar.frequencyBiweekly') }}</option>
          </select>
        </div>

        <button
          class="btn-primary inline-flex w-full items-center justify-center gap-2"
          :disabled="calendarLoading || calendarForm.platforms.length === 0"
          @click="generateCalendar"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ calendarLoading ? $t('calendar.generating') : $t('calendar.aiCalendarGenerate') }}
        </button>

        <!-- Results -->
        <div v-if="calendarSuggestions.length > 0" class="space-y-3">
          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('calendar.aiCalendarGenerateDesc') }}
          </h3>
          <div class="max-h-64 space-y-2 overflow-y-auto">
            <div
              v-for="suggestion in calendarSuggestions"
              :key="suggestion.id"
              class="card flex items-center justify-between rounded-lg p-3"
            >
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2">
                  <span class="rounded bg-gray-100 px-1.5 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-700 dark:text-gray-400">
                    {{ suggestion.platform }}
                  </span>
                  <span class="text-xs text-gray-500 dark:text-gray-400">{{ suggestion.suggestedDate }}</span>
                </div>
                <p class="mt-1 truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                  {{ suggestion.title }}
                </p>
                <p v-if="suggestion.reason" class="text-xs text-gray-500 dark:text-gray-400">
                  {{ suggestion.reason }}
                </p>
              </div>
              <button
                v-if="suggestion.status !== 'ACCEPTED'"
                class="btn-primary ml-3 shrink-0 px-3 py-1 text-xs"
                @click="acceptSuggestion(suggestion.id)"
              >
                {{ $t('calendar.accept') }}
              </button>
              <span
                v-else
                class="ml-3 shrink-0 rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700 dark:bg-green-900/30 dark:text-green-400"
              >
                {{ $t('calendar.accepted') }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </BaseModal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ChevronLeftIcon, ChevronRightIcon, SparklesIcon } from '@heroicons/vue/24/outline'
import CalendarGrid from '@/components/schedule/CalendarGrid.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import BaseModal from '@/components/common/BaseModal.vue'
import { useScheduleStore } from '@/stores/schedule'
import { useLocale } from '@/composables/useLocale'
import { useNotificationStore } from '@/stores/notification'
import { scheduleOptimizerApi, type OptimalSlot } from '@/api/scheduleOptimizer'
import { contentCalendarAiApi, type ContentCalendarSuggestion } from '@/api/contentCalendarAi'
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
const notificationStore = useNotificationStore()
const { t } = useLocale()

const currentDate = ref(new Date())
const viewMode = ref<'monthly' | 'weekly'>('monthly')

// AI 최적 시간
const showAiOptimalModal = ref(false)
const optimalPlatform = ref('YOUTUBE')
const optimalLoading = ref(false)
const optimalSlots = ref<OptimalSlot[]>([])

// AI 캘린더 생성
const showAiCalendarModal = ref(false)
const calendarLoading = ref(false)
const calendarSuggestions = ref<ContentCalendarSuggestion[]>([])

const today = new Date()
const calendarForm = ref({
  startDate: today.toISOString().split('T')[0],
  endDate: new Date(today.getFullYear(), today.getMonth() + 1, today.getDate()).toISOString().split('T')[0],
  platforms: ['YOUTUBE'] as string[],
  frequency: 'WEEKLY_2_3',
})

const availablePlatforms = [
  { value: 'YOUTUBE', label: 'YouTube' },
  { value: 'TIKTOK', label: 'TikTok' },
  { value: 'INSTAGRAM', label: 'Instagram' },
  { value: 'NAVER_CLIP', label: 'Naver Clip' },
]

async function generateOptimalSlots() {
  optimalLoading.value = true
  try {
    optimalSlots.value = await scheduleOptimizerApi.generateSlots(optimalPlatform.value)
  } catch {
    notificationStore.error('AI 최적 시간 분석에 실패했습니다')
  } finally {
    optimalLoading.value = false
  }
}

async function generateCalendar() {
  calendarLoading.value = true
  try {
    calendarSuggestions.value = await contentCalendarAiApi.generate({
      startDate: calendarForm.value.startDate,
      endDate: calendarForm.value.endDate,
      platforms: calendarForm.value.platforms,
      frequency: calendarForm.value.frequency,
    })
  } catch {
    notificationStore.error('AI 캘린더 생성에 실패했습니다')
  } finally {
    calendarLoading.value = false
  }
}

async function acceptSuggestion(id: number) {
  try {
    const updated = await contentCalendarAiApi.acceptSuggestion(id)
    const idx = calendarSuggestions.value.findIndex((s) => s.id === id)
    if (idx !== -1) {
      calendarSuggestions.value[idx] = updated
    }
  } catch {
    notificationStore.error('적용에 실패했습니다')
  }
}

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
