<template>
  <div class="card overflow-hidden p-0">
    <!-- 요일 헤더 -->
    <div class="grid grid-cols-7 border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-900">
      <div
        v-for="day in dayLabels"
        :key="day"
        class="px-2 py-2.5 text-center text-xs font-medium text-gray-500 dark:text-gray-400"
      >
        {{ day }}
      </div>
    </div>

    <!-- 6행 x 7열 그리드 -->
    <div class="grid grid-cols-7">
      <div
        v-for="cell in monthCells"
        :key="cell.dateStr"
        class="group relative min-h-[100px] cursor-pointer border-b border-r border-gray-100 p-1.5 transition-colors hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-gray-700 tablet:min-h-[120px]"
        :class="{
          'bg-primary-50/40 dark:bg-primary-900/20': cell.isToday,
          'opacity-40': !cell.isCurrentMonth,
        }"
        @click="onCellClick(cell)"
      >
        <!-- 날짜 -->
        <div class="mb-1 flex items-center justify-between">
          <span
            class="inline-flex h-6 w-6 items-center justify-center rounded-full text-xs font-medium"
            :class="cell.isToday ? 'bg-primary-600 text-white' : 'text-gray-700 dark:text-gray-300'"
          >
            {{ cell.day }}
          </span>
          <button
            v-if="cell.isCurrentMonth"
            class="hidden rounded p-0.5 text-gray-300 hover:bg-gray-200 hover:text-gray-500 group-hover:block dark:text-gray-600 dark:hover:bg-gray-700 dark:hover:text-gray-400"
            :aria-label="$t('scheduleView.quickAdd.title')"
            @click.stop="$emit('add', cell.date)"
          >
            <PlusIcon class="h-3.5 w-3.5" />
          </button>
        </div>

        <!-- 예약 항목 -->
        <div class="space-y-0.5">
          <div
            v-for="schedule in cell.visibleSchedules"
            :key="schedule.id"
            class="flex items-center gap-1 rounded px-1 py-0.5 text-xs transition-colors hover:bg-gray-100 dark:hover:bg-gray-700"
            @click.stop="$emit('select', schedule)"
          >
            <!-- 플랫폼 색상 점 -->
            <div class="flex shrink-0 gap-0.5">
              <span
                v-for="sp in schedule.platforms.slice(0, 2)"
                :key="sp.platform"
                class="h-1.5 w-1.5 rounded-full"
                :style="{ backgroundColor: getPlatformColor(sp.platform) }"
              />
              <span
                v-if="schedule.platforms.length > 2"
                class="text-[10px] leading-none text-gray-400"
              >
                +{{ schedule.platforms.length - 2 }}
              </span>
            </div>

            <!-- 썸네일 (모바일 숨김) -->
            <img
              v-if="schedule.thumbnailUrl"
              :src="schedule.thumbnailUrl"
              :alt="schedule.videoTitle"
              class="hidden h-4 w-6 shrink-0 rounded-sm object-cover tablet:block"
            />

            <!-- 반복 예약 아이콘 -->
            <ArrowPathIcon
              v-if="isRecurringSchedule(schedule)"
              class="h-3 w-3 shrink-0 text-primary-500 dark:text-primary-400"
              :title="$t('scheduleView.repeatingSchedule')"
            />

            <span class="truncate text-gray-700 dark:text-gray-300">
              {{ schedule.videoTitle }}
            </span>
          </div>

          <!-- 초과 개수 -->
          <div
            v-if="cell.overflowCount > 0"
            class="px-1 text-[10px] font-medium text-gray-400 dark:text-gray-500"
          >
            +{{ cell.overflowCount }} {{ $t('scheduleView.more') }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { PlusIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import { useScheduleLabels } from '@/composables/useScheduleLabels'
import { getPlatformColor, isRecurringSchedule, toDateStr } from '@/utils/schedule'
import type { Schedule } from '@/types/schedule'

const MAX_VISIBLE_PER_DAY = 3

const props = defineProps<{
  currentDate: Date
  schedules: Schedule[]
}>()

const emit = defineEmits<{
  select: [schedule: Schedule]
  add: [date: Date]
}>()

const { dayLabels } = useScheduleLabels()

interface MonthCell {
  date: Date
  dateStr: string
  day: number
  isCurrentMonth: boolean
  isToday: boolean
  visibleSchedules: Schedule[]
  overflowCount: number
}

/** 날짜별 예약 인덱스 */
const schedulesByDate = computed(() => {
  const map = new Map<string, Schedule[]>()
  props.schedules.forEach((s) => {
    const key = toDateStr(new Date(s.scheduledAt))
    const bucket = map.get(key)
    if (bucket) bucket.push(s)
    else map.set(key, [s])
  })
  return map
})

const monthCells = computed<MonthCell[]>(() => {
  const year = props.currentDate.getFullYear()
  const month = props.currentDate.getMonth()
  const todayStr = toDateStr(new Date())

  const startDow = new Date(year, month, 1).getDay()
  const totalDays = new Date(year, month + 1, 0).getDate()

  const createCell = (date: Date, isCurrentMonth: boolean): MonthCell => {
    const dateStr = toDateStr(date)
    const all = schedulesByDate.value.get(dateStr) ?? []
    return {
      date,
      dateStr,
      day: date.getDate(),
      isCurrentMonth,
      isToday: dateStr === todayStr,
      visibleSchedules: all.slice(0, MAX_VISIBLE_PER_DAY),
      overflowCount: Math.max(0, all.length - MAX_VISIBLE_PER_DAY),
    }
  }

  const cells: MonthCell[] = []

  // 이전 달 잔여일
  const prevMonthLast = new Date(year, month, 0).getDate()
  for (let i = startDow - 1; i >= 0; i--) {
    cells.push(createCell(new Date(year, month - 1, prevMonthLast - i), false))
  }

  // 이번 달
  for (let day = 1; day <= totalDays; day++) {
    cells.push(createCell(new Date(year, month, day), true))
  }

  // 6행(42칸) 채우기
  const remaining = 42 - cells.length
  for (let i = 1; i <= remaining; i++) {
    cells.push(createCell(new Date(year, month + 1, i), false))
  }

  return cells
})

function onCellClick(cell: MonthCell) {
  if (cell.isCurrentMonth) {
    emit('add', cell.date)
  }
}
</script>
