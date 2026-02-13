<template>
  <div class="card">
    <div class="mb-4 flex items-center justify-between">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">예약 업로드</h3>
      <router-link to="/schedule" class="text-sm text-primary-600 hover:underline">
        캘린더 보기
      </router-link>
    </div>
    <div v-if="schedules.length === 0" class="py-8 text-center text-sm text-gray-400 dark:text-gray-500">
      예약된 업로드가 없습니다
    </div>
    <ul v-else class="divide-y divide-gray-100 dark:divide-gray-700">
      <li v-for="schedule in schedules" :key="schedule.id" class="py-3">
        <div class="flex items-start gap-3">
          <div
            class="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-lg bg-primary-50 dark:bg-primary-900/20 text-primary-600"
          >
            <CalendarIcon class="h-5 w-5" />
          </div>
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">{{ schedule.videoTitle }}</p>
            <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
              {{ formatDate(schedule.scheduledAt) }}
            </p>
            <div class="mt-1 flex flex-wrap gap-1">
              <PlatformBadge
                v-for="sp in schedule.platforms"
                :key="sp.platform"
                :platform="sp.platform"
              />
            </div>
          </div>
          <router-link
            :to="`/schedule?date=${scheduleDate(schedule.scheduledAt)}`"
            class="flex-shrink-0 text-xs text-primary-600 hover:underline"
          >
            보기
          </router-link>
        </div>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { CalendarIcon } from '@heroicons/vue/24/outline'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import type { Schedule } from '@/types/schedule'
import dayjs from 'dayjs'

defineProps<{
  schedules: Schedule[]
}>()

function formatDate(date: string): string {
  return dayjs(date).format('YYYY.MM.DD HH:mm')
}

function scheduleDate(date: string): string {
  return dayjs(date).format('YYYY-MM-DD')
}
</script>
