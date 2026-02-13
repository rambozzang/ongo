<template>
  <div
    class="card cursor-pointer transition-all duration-200 hover:-translate-y-0.5 hover:shadow-md"
    @click="$router.push('/schedule')"
  >
    <div class="flex items-center justify-between">
      <p class="text-sm font-medium text-gray-500 dark:text-gray-400">이번 주 일정</p>
      <CalendarDaysIcon class="h-5 w-5 text-gray-400 dark:text-gray-500" />
    </div>
    <p class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
      {{ scheduledCount }}건
    </p>
    <div class="mt-3">
      <div class="grid grid-cols-7 gap-1">
        <div
          v-for="(day, index) in weekDays"
          :key="index"
          class="text-center"
        >
          <div class="text-[10px] text-gray-400 dark:text-gray-500">
            {{ day }}
          </div>
          <div
            class="mt-1 flex aspect-square items-center justify-center rounded-md text-xs transition-colors"
            :class="getDayClass(index)"
          >
            {{ getDayNumber(index) }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CalendarDaysIcon } from '@heroicons/vue/24/outline'
import type { Schedule } from '@/types/schedule'
import dayjs from 'dayjs'
import isoWeek from 'dayjs/plugin/isoWeek'
import 'dayjs/locale/ko'

dayjs.extend(isoWeek)
dayjs.locale('ko')

const props = defineProps<{
  schedules: Schedule[]
}>()

const weekDays = ['월', '화', '수', '목', '금', '토', '일']

const scheduledCount = computed(() => {
  const startOfWeek = dayjs().startOf('isoWeek')
  const endOfWeek = dayjs().endOf('isoWeek')

  return props.schedules.filter((schedule) => {
    const scheduleDate = dayjs(schedule.scheduledAt)
    return scheduleDate.isAfter(startOfWeek) && scheduleDate.isBefore(endOfWeek)
  }).length
})

const scheduledDays = computed(() => {
  const startOfWeek = dayjs().startOf('isoWeek')
  const days = new Set<number>()

  props.schedules.forEach((schedule) => {
    const scheduleDate = dayjs(schedule.scheduledAt)
    const dayOfWeek = scheduleDate.isoWeekday() // 1 = Monday, 7 = Sunday
    const weekStart = scheduleDate.startOf('isoWeek')

    if (weekStart.isSame(startOfWeek, 'day')) {
      days.add(dayOfWeek - 1) // Convert to 0-indexed
    }
  })

  return days
})

function getDayNumber(index: number): number {
  const startOfWeek = dayjs().startOf('isoWeek')
  return startOfWeek.add(index, 'day').date()
}

function getDayClass(index: number): string {
  const startOfWeek = dayjs().startOf('isoWeek')
  const currentDay = startOfWeek.add(index, 'day')
  const today = dayjs()

  const isToday = currentDay.isSame(today, 'day')
  const hasSchedule = scheduledDays.value.has(index)

  if (isToday && hasSchedule) {
    return 'bg-primary-500 text-white font-semibold'
  }
  if (isToday) {
    return 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400 font-semibold'
  }
  if (hasSchedule) {
    return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400 font-medium'
  }

  return 'text-gray-500 dark:text-gray-400'
}
</script>
