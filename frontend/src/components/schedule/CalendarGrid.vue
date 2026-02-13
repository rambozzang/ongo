<template>
  <div>
    <!-- Desktop/Tablet Grid View -->
    <div class="hidden sm:block">
      <div class="card overflow-hidden">
        <!-- Weekday Headers -->
        <div class="grid grid-cols-7 border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800">
          <div
            v-for="day in weekdays"
            :key="day"
            class="border-r border-gray-200 px-2 py-3 text-center text-sm font-medium text-gray-700 last:border-r-0 dark:border-gray-700 dark:text-gray-300"
          >
            {{ day }}
          </div>
        </div>

        <!-- Calendar Grid -->
        <div
          class="grid grid-cols-7"
          :class="viewMode === 'weekly' ? 'grid-rows-1' : 'grid-rows-6'"
        >
          <div
            v-for="(day, index) in calendarDays"
            :key="index"
            :class="[
              'border-b border-r border-gray-200 last:border-r-0 dark:border-gray-700',
              viewMode === 'weekly' ? 'min-h-[400px]' : 'min-h-[120px]',
              day.isCurrentMonth ? 'bg-white dark:bg-gray-800' : 'bg-gray-50 dark:bg-gray-900',
              day.isToday ? 'ring-2 ring-primary-500 ring-inset' : '',
            ]"
          >
            <div class="flex h-full flex-col p-2">
              <!-- Date -->
              <div
                :class="[
                  'mb-2 text-sm font-medium',
                  day.isCurrentMonth
                    ? day.isToday
                      ? 'text-primary-700 dark:text-primary-400'
                      : 'text-gray-900 dark:text-gray-100'
                    : 'text-gray-400 dark:text-gray-600',
                ]"
              >
                {{ day.date }}
              </div>

              <!-- Events -->
              <div class="flex flex-1 flex-col gap-1 overflow-y-auto">
                <CalendarEventCard
                  v-for="event in day.events"
                  :key="event.id"
                  :event="event"
                />

                <!-- Add Content Placeholder -->
                <router-link
                  v-if="day.events.length === 0 && day.isCurrentMonth"
                  to="/upload"
                  class="flex items-center justify-center rounded border border-dashed border-gray-300 p-2 text-xs text-gray-400 hover:border-primary-500 hover:text-primary-600 dark:border-gray-600 dark:text-gray-500 dark:hover:border-primary-400 dark:hover:text-primary-400"
                >
                  콘텐츠 추가
                </router-link>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Mobile List View -->
    <div class="space-y-4 sm:hidden">
      <div
        v-for="(day, index) in calendarDays.filter((d) => d.isCurrentMonth)"
        :key="index"
        class="card p-4"
      >
        <div class="mb-3 flex items-center justify-between">
          <h3
            :class="[
              'text-sm font-semibold',
              day.isToday
                ? 'text-primary-700 dark:text-primary-400'
                : 'text-gray-900 dark:text-gray-100',
            ]"
          >
            {{ day.fullDate }}
          </h3>
          <span
            v-if="day.isToday"
            class="rounded-full bg-primary-100 px-2 py-0.5 text-xs font-medium text-primary-700 dark:bg-primary-900/30 dark:text-primary-400"
          >
            오늘
          </span>
        </div>

        <div class="space-y-2">
          <CalendarEventCard v-for="event in day.events" :key="event.id" :event="event" />

          <router-link
            v-if="day.events.length === 0"
            to="/upload"
            class="flex items-center justify-center rounded border border-dashed border-gray-300 p-3 text-sm text-gray-400 hover:border-primary-500 hover:text-primary-600 dark:border-gray-600 dark:text-gray-500 dark:hover:border-primary-400 dark:hover:text-primary-400"
          >
            콘텐츠 추가
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import CalendarEventCard from './CalendarEventCard.vue'
import type { CalendarEvent } from '@/views/CalendarView.vue'

interface Props {
  events: CalendarEvent[]
  currentDate: Date
  viewMode: 'monthly' | 'weekly'
}

const props = defineProps<Props>()

const weekdays = ['일', '월', '화', '수', '목', '금', '토']

interface CalendarDay {
  date: number
  fullDate: string
  isCurrentMonth: boolean
  isToday: boolean
  events: CalendarEvent[]
}

const calendarDays = computed<CalendarDay[]>(() => {
  const year = props.currentDate.getFullYear()
  const month = props.currentDate.getMonth()
  const today = new Date()
  const todayDate = today.getDate()
  const todayMonth = today.getMonth()
  const todayYear = today.getFullYear()

  const days: CalendarDay[] = []

  if (props.viewMode === 'weekly') {
    // Weekly view: current week (Sunday - Saturday)
    const currentDay = props.currentDate.getDay()
    const weekStart = new Date(props.currentDate)
    weekStart.setDate(props.currentDate.getDate() - currentDay)

    for (let i = 0; i < 7; i++) {
      const date = new Date(weekStart)
      date.setDate(weekStart.getDate() + i)

      const dayDate = date.getDate()
      const dayMonth = date.getMonth()
      const dayYear = date.getFullYear()

      const dayEvents = props.events.filter((event) => {
        const eventDate = new Date(event.scheduledAt)
        return (
          eventDate.getDate() === dayDate &&
          eventDate.getMonth() === dayMonth &&
          eventDate.getFullYear() === dayYear
        )
      })

      days.push({
        date: dayDate,
        fullDate: `${dayMonth + 1}월 ${dayDate}일 (${weekdays[i]})`,
        isCurrentMonth: dayMonth === month,
        isToday: dayDate === todayDate && dayMonth === todayMonth && dayYear === todayYear,
        events: dayEvents,
      })
    }
  } else {
    // Monthly view: full month grid
    const firstDay = new Date(year, month, 1)
    const lastDay = new Date(year, month + 1, 0)
    const startDay = firstDay.getDay()
    const daysInMonth = lastDay.getDate()

    // Previous month days
    const prevMonthLastDay = new Date(year, month, 0).getDate()
    for (let i = startDay - 1; i >= 0; i--) {
      const date = prevMonthLastDay - i
      days.push({
        date,
        fullDate: `${month}월 ${date}일`,
        isCurrentMonth: false,
        isToday: false,
        events: [],
      })
    }

    // Current month days
    for (let date = 1; date <= daysInMonth; date++) {
      const dayEvents = props.events.filter((event) => {
        const eventDate = new Date(event.scheduledAt)
        return (
          eventDate.getDate() === date &&
          eventDate.getMonth() === month &&
          eventDate.getFullYear() === year
        )
      })

      days.push({
        date,
        fullDate: `${month + 1}월 ${date}일`,
        isCurrentMonth: true,
        isToday: date === todayDate && month === todayMonth && year === todayYear,
        events: dayEvents,
      })
    }

    // Next month days to fill the grid (6 rows × 7 columns = 42 cells)
    const remainingCells = 42 - days.length
    for (let date = 1; date <= remainingCells; date++) {
      days.push({
        date,
        fullDate: `${month + 2}월 ${date}일`,
        isCurrentMonth: false,
        isToday: false,
        events: [],
      })
    }
  }

  return days
})
</script>
