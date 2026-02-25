<template>
  <div>
    <!-- Desktop/Tablet Weekly Grid -->
    <div class="hidden sm:block">
      <div class="card overflow-hidden">
        <!-- Weekday Headers -->
        <div class="grid grid-cols-7 border-b border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-gray-800">
          <div
            v-for="(day, idx) in weekDays"
            :key="idx"
            class="border-r border-gray-200 px-2 py-3 text-center text-sm font-medium text-gray-700 last:border-r-0 dark:border-gray-700 dark:text-gray-300"
          >
            <div>{{ day.weekday }}</div>
            <div
              class="mt-1 text-xs"
              :class="day.isToday ? 'font-bold text-primary-600 dark:text-primary-400' : 'text-gray-500 dark:text-gray-400'"
            >
              {{ day.label }}
            </div>
            <SeasonEventBadge
              v-if="day.seasonEvent"
              :event="day.seasonEvent"
              class="mt-1"
            />
          </div>
        </div>

        <!-- Slots Grid -->
        <div class="grid grid-cols-7">
          <div
            v-for="(day, idx) in weekDays"
            :key="idx"
            class="min-h-[300px] border-r border-gray-200 last:border-r-0 dark:border-gray-700"
            :class="day.isToday ? 'bg-primary-50/30 dark:bg-primary-900/10' : 'bg-white dark:bg-gray-800'"
          >
            <div class="flex h-full flex-col gap-2 p-2">
              <ContentSlotCard
                v-for="(slot, slotIdx) in day.slots"
                :key="slot.id"
                :slot="slot"
                :index="slotIdx"
                @toggle-confirm="$emit('toggle-confirm', $event)"
                @regenerate="$emit('regenerate', $event)"
              />

              <!-- Empty state -->
              <div
                v-if="day.slots.length === 0"
                class="flex flex-1 items-center justify-center rounded border border-dashed border-gray-300 p-4 text-xs text-gray-400 dark:border-gray-600 dark:text-gray-500"
              >
                {{ $t('aiCalendar.grid.noContent') }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Mobile List View -->
    <div class="space-y-3 sm:hidden">
      <div
        v-for="(day, idx) in weekDays"
        :key="idx"
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
            {{ day.label }} ({{ day.weekday }})
          </h3>
          <div class="flex items-center gap-2">
            <span
              v-if="day.isToday"
              class="rounded-full bg-primary-100 px-2 py-0.5 text-xs font-medium text-primary-700 dark:bg-primary-900/30 dark:text-primary-400"
            >
              {{ $t('aiCalendar.grid.today') }}
            </span>
            <SeasonEventBadge v-if="day.seasonEvent" :event="day.seasonEvent" />
          </div>
        </div>

        <div class="space-y-2">
          <ContentSlotCard
            v-for="(slot, slotIdx) in day.slots"
            :key="slot.id"
            :slot="slot"
            :index="slotIdx"
            @toggle-confirm="$emit('toggle-confirm', $event)"
            @regenerate="$emit('regenerate', $event)"
          />

          <div
            v-if="day.slots.length === 0"
            class="rounded border border-dashed border-gray-300 p-3 text-center text-sm text-gray-400 dark:border-gray-600 dark:text-gray-500"
          >
            {{ $t('aiCalendar.grid.noContent') }}
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import ContentSlotCard from './ContentSlotCard.vue'
import SeasonEventBadge from './SeasonEventBadge.vue'
import type { ContentSlot, SeasonEvent } from '@/types/aiCalendar'

const props = defineProps<{
  slots: ContentSlot[]
  weekStart: Date
  seasonEvents: SeasonEvent[]
}>()

defineEmits<{
  'toggle-confirm': [slotId: string]
  'regenerate': [slotId: string]
}>()

const { locale } = useI18n({ useScope: 'global' })

const weekdayNames = computed(() =>
  locale.value === 'ko'
    ? ['월', '화', '수', '목', '금', '토', '일']
    : ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
)

interface WeekDay {
  date: Date
  weekday: string
  label: string
  isToday: boolean
  slots: ContentSlot[]
  seasonEvent?: SeasonEvent
}

const weekDays = computed<WeekDay[]>(() => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const days: WeekDay[] = []

  for (let i = 0; i < 7; i++) {
    const date = new Date(props.weekStart)
    date.setDate(props.weekStart.getDate() + i)
    date.setHours(0, 0, 0, 0)

    const dateStr = formatDateStr(date)
    const daySlots = props.slots.filter((s) => s.date === dateStr)

    const seasonEvent = props.seasonEvents.find((e) => e.date === dateStr)

    days.push({
      date,
      weekday: weekdayNames.value[i],
      label: `${date.getMonth() + 1}/${date.getDate()}`,
      isToday: date.getTime() === today.getTime(),
      slots: daySlots.sort((a, b) => a.time.localeCompare(b.time)),
      seasonEvent,
    })
  }

  return days
})

function formatDateStr(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}
</script>
