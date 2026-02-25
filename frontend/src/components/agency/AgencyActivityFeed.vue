<template>
  <div class="rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-800">
    <h3 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
      {{ $t('agency.activityFeed') }}
    </h3>

    <div v-if="activities.length === 0" class="py-8 text-center text-sm text-gray-400 dark:text-gray-500">
      {{ $t('agency.noActivities') }}
    </div>

    <div v-else class="space-y-0">
      <div
        v-for="(activity, idx) in activities"
        :key="activity.id"
        class="relative flex gap-3 pb-4"
      >
        <!-- Timeline line -->
        <div v-if="idx < activities.length - 1" class="absolute bottom-0 left-[15px] top-8 w-px bg-gray-200 dark:bg-gray-700" />

        <!-- Icon -->
        <div class="relative z-10 flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full" :class="activityIconClass(activity.type)">
          <component :is="activityIcon(activity.type)" class="h-4 w-4" />
        </div>

        <!-- Content -->
        <div class="min-w-0 flex-1 pt-0.5">
          <p class="text-sm text-gray-900 dark:text-gray-100">
            <span class="font-medium">{{ activity.creatorName }}</span>
            <span class="text-gray-500 dark:text-gray-400">
              {{ ' ' + $t(`agency.action.${activity.action}`) + ' ' }}
            </span>
            <span class="font-medium">{{ activity.target }}</span>
          </p>
          <p class="mt-0.5 text-xs text-gray-400 dark:text-gray-500">
            {{ timeAgo(activity.createdAt) }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { type Component } from 'vue'
import {
  ArrowUpTrayIcon,
  CalendarDaysIcon,
  ChartBarIcon,
  LinkIcon,
  Cog6ToothIcon,
} from '@heroicons/vue/24/outline'
import type { AgencyActivity } from '@/types/agency'

defineProps<{
  activities: AgencyActivity[]
}>()

function activityIcon(type: string): Component {
  const map: Record<string, Component> = {
    upload: ArrowUpTrayIcon,
    schedule: CalendarDaysIcon,
    analytics: ChartBarIcon,
    channel: LinkIcon,
    setting: Cog6ToothIcon,
  }
  return map[type] ?? ChartBarIcon
}

function activityIconClass(type: string): string {
  const map: Record<string, string> = {
    upload: 'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400',
    schedule: 'bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400',
    analytics: 'bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400',
    channel: 'bg-pink-100 text-pink-600 dark:bg-pink-900/30 dark:text-pink-400',
    setting: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400',
  }
  return map[type] ?? 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
}

function timeAgo(dateString: string): string {
  const diff = Date.now() - new Date(dateString).getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 60) return `${minutes}분 전`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}시간 전`
  const days = Math.floor(hours / 24)
  return `${days}일 전`
}
</script>
