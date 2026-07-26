<template>
  <BaseModal
    :model-value="schedule !== null"
    :title="$t('scheduleView.detail.title')"
    max-width="lg"
    @update:model-value="$emit('close')"
  >
    <div v-if="schedule" class="space-y-4">
      <!-- 썸네일 + 제목 (영상 상세로 이동) -->
      <router-link
        :to="`/videos/${schedule.videoId}`"
        class="flex items-center gap-3 rounded-lg p-1 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700/50"
      >
        <img
          v-if="schedule.thumbnailUrl"
          :src="schedule.thumbnailUrl"
          :alt="schedule.videoTitle"
          class="h-14 w-20 shrink-0 rounded-lg object-cover"
        />
        <div
          v-else
          class="flex h-14 w-20 shrink-0 items-center justify-center rounded-lg bg-gray-100 dark:bg-gray-800"
        >
          <CalendarIcon class="h-6 w-6 text-gray-400" />
        </div>
        <div>
          <p class="font-medium text-gray-900 dark:text-gray-100">{{ schedule.videoTitle }}</p>
          <span :class="getStatusBadgeClass(schedule.status)">
            {{ getStatusLabel(schedule.status) }}
          </span>
        </div>
      </router-link>

      <!-- 예약 정보 -->
      <div class="rounded-lg bg-gray-50 p-4 dark:bg-gray-900">
        <div class="grid gap-3 text-sm">
          <div class="flex justify-between">
            <span class="text-gray-500 dark:text-gray-400">
              {{ $t('scheduleView.detail.scheduledAt') }}
            </span>
            <span class="font-medium text-gray-900 dark:text-gray-100">
              {{ formatScheduleDate(schedule.scheduledAt) }}
              {{ formatScheduleTime(schedule.scheduledAt) }}
            </span>
          </div>
          <div v-if="schedule.recurrence && isRecurringSchedule(schedule)" class="flex justify-between">
            <span class="text-gray-500 dark:text-gray-400">
              {{ $t('scheduleView.detail.recurrence') }}
            </span>
            <span class="flex items-center gap-1 font-medium text-gray-900 dark:text-gray-100">
              <ArrowPathIcon class="h-3.5 w-3.5 text-primary-500" />
              {{ formatRecurrence(schedule.recurrence) }}
            </span>
          </div>
          <div class="flex justify-between">
            <span class="text-gray-500 dark:text-gray-400">
              {{ $t('scheduleView.detail.createdAt') }}
            </span>
            <span class="text-gray-700 dark:text-gray-300">
              {{ formatScheduleDate(schedule.createdAt) }}
            </span>
          </div>
        </div>
      </div>

      <!-- 플랫폼별 상태 -->
      <div>
        <h4 class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ $t('scheduleView.detail.platformStatus') }}
        </h4>
        <div class="space-y-2">
          <div
            v-for="sp in schedule.platforms"
            :key="sp.platform"
            class="flex items-center justify-between rounded-lg border border-gray-100 px-3 py-2 dark:border-gray-700"
          >
            <div class="flex items-center gap-2">
              <span
                class="h-2.5 w-2.5 rounded-full"
                :style="{ backgroundColor: getPlatformColor(sp.platform) }"
              />
              <span class="text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ PLATFORM_CONFIG[sp.platform].label }}
              </span>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-xs text-gray-500 dark:text-gray-400">
                {{ formatScheduleTime(sp.scheduledAt) }}
              </span>
              <span :class="getStatusBadgeClass(sp.status)">
                {{ getStatusLabel(sp.status) }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <template v-if="schedule?.status === 'SCHEDULED'" #footer>
      <button class="btn-danger" @click="$emit('cancel', schedule!)">
        {{ $t('scheduleView.detail.cancel') }}
      </button>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { ArrowPathIcon, CalendarIcon } from '@heroicons/vue/24/outline'
import BaseModal from '@/components/common/BaseModal.vue'
import { useScheduleLabels } from '@/composables/useScheduleLabels'
import {
  formatScheduleDate,
  formatScheduleTime,
  getPlatformColor,
  getStatusBadgeClass,
  isRecurringSchedule,
} from '@/utils/schedule'
import { PLATFORM_CONFIG } from '@/types/channel'
import type { Schedule } from '@/types/schedule'

defineProps<{
  schedule: Schedule | null
}>()

defineEmits<{
  close: []
  cancel: [schedule: Schedule]
}>()

const { getStatusLabel, formatRecurrence } = useScheduleLabels()
</script>
