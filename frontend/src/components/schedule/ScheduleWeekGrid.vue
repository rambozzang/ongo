<template>
  <div class="card overflow-x-auto p-0">
    <!-- 요일 헤더 -->
    <div
      class="grid min-w-[700px] grid-cols-[60px_repeat(7,1fr)] border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-900"
    >
      <div class="border-r border-gray-200 px-2 py-2.5 dark:border-gray-700" />
      <div
        v-for="day in weekDays"
        :key="day.dateStr"
        class="px-2 py-2.5 text-center"
        :class="{ 'bg-primary-50/60 dark:bg-primary-900/20': day.isToday }"
      >
        <div class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ day.dayLabel }}</div>
        <div
          class="mt-0.5 inline-flex h-7 w-7 items-center justify-center rounded-full text-sm font-semibold"
          :class="day.isToday ? 'bg-primary-600 text-white' : 'text-gray-900 dark:text-gray-100'"
        >
          {{ day.day }}
        </div>
      </div>
    </div>

    <!-- 시간 행 -->
    <div class="min-w-[700px]">
      <div
        v-for="hour in hours"
        :key="hour"
        class="grid grid-cols-[60px_repeat(7,1fr)] border-b border-gray-100 dark:border-gray-700"
      >
        <div
          class="border-r border-gray-200 px-2 py-3 text-right text-xs text-gray-400 dark:border-gray-700 dark:text-gray-500"
        >
          {{ formatHourLabel(hour) }}
        </div>

        <div
          v-for="day in weekDays"
          :key="day.dateStr"
          class="relative min-h-[48px] cursor-pointer border-r border-gray-100 px-1 py-0.5 transition-colors hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-gray-700"
          :class="{ 'bg-primary-50/30 dark:bg-primary-900/20': day.isToday }"
          @click="$emit('add', day.date, hour)"
        >
          <div
            v-for="schedule in getSchedulesForHour(day.dateStr, hour)"
            :key="schedule.id"
            class="mb-0.5 flex items-center gap-1 rounded px-1.5 py-1 text-xs transition-shadow hover:shadow-sm"
            :style="{
              backgroundColor: getScheduleBgColor(schedule),
              borderLeft: `3px solid ${getScheduleBorderColor(schedule)}`,
            }"
            @click.stop="$emit('select', schedule)"
          >
            <ArrowPathIcon
              v-if="isRecurringSchedule(schedule)"
              class="h-3 w-3 shrink-0 text-primary-500 dark:text-primary-400"
              :title="$t('scheduleView.repeatingSchedule')"
            />
            <span class="truncate font-medium text-gray-800 dark:text-gray-200">
              {{ schedule.videoTitle }}
            </span>
            <span class="shrink-0 text-gray-500 dark:text-gray-400">
              {{ formatScheduleTime(schedule.scheduledAt) }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowPathIcon } from '@heroicons/vue/24/outline'
import { useScheduleLabels } from '@/composables/useScheduleLabels'
import {
  formatHourLabel,
  formatScheduleTime,
  getScheduleBgColor,
  getScheduleBorderColor,
  getWeekStart,
  isRecurringSchedule,
  toDateStr,
} from '@/utils/schedule'
import type { Schedule } from '@/types/schedule'

const props = defineProps<{
  currentDate: Date
  schedules: Schedule[]
}>()

defineEmits<{
  select: [schedule: Schedule]
  add: [date: Date, hour: number]
}>()

const { dayLabels } = useScheduleLabels()

const hours = Array.from({ length: 24 }, (_, i) => i)

interface WeekDay {
  date: Date
  dateStr: string
  day: number
  dayLabel: string
  isToday: boolean
}

const weekDays = computed<WeekDay[]>(() => {
  const start = getWeekStart(props.currentDate)
  const todayStr = toDateStr(new Date())
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(start)
    d.setDate(start.getDate() + i)
    const dateStr = toDateStr(d)
    return {
      date: d,
      dateStr,
      day: d.getDate(),
      dayLabel: dayLabels.value[d.getDay()],
      isToday: dateStr === todayStr,
    }
  })
})

/** 'YYYY-MM-DD:H' → 예약 목록 */
const schedulesByDateHour = computed(() => {
  const map = new Map<string, Schedule[]>()
  props.schedules.forEach((s) => {
    const d = new Date(s.scheduledAt)
    const key = `${toDateStr(d)}:${d.getHours()}`
    const bucket = map.get(key)
    if (bucket) bucket.push(s)
    else map.set(key, [s])
  })
  return map
})

function getSchedulesForHour(dateStr: string, hour: number): Schedule[] {
  return schedulesByDateHour.value.get(`${dateStr}:${hour}`) ?? []
}
</script>
