<template>
  <router-link
    :to="`/videos/${event.videoId}`"
    :class="[
      'block rounded-lg border-l-4 bg-white p-2 transition-all hover:shadow-md dark:bg-gray-800',
      statusBorderColor,
    ]"
    :title="event.title"
  >
    <div class="flex items-start gap-2">
      <!-- Status Indicator -->
      <div :class="['mt-1 h-2 w-2 shrink-0 rounded-full', statusDotColor]" />

      <div class="min-w-0 flex-1">
        <!-- Title -->
        <p class="truncate text-body font-medium text-gray-900 dark:text-gray-100">
          {{ event.title }}
        </p>

        <!-- Time & Platform -->
        <div class="mt-1 flex items-center gap-2">
          <span class="text-body-xs text-gray-500 dark:text-gray-400">
            {{ formattedTime }}
          </span>
          <PlatformBadge v-if="event.platform" :platform="event.platform" />
        </div>
      </div>
    </div>
  </router-link>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import type { CalendarEvent } from '@/types/schedule'

const props = defineProps<{
  event: CalendarEvent
}>()

const statusConfig = {
  scheduled: {
    borderColor: 'border-l-info',
    dotColor: 'bg-info',
  },
  published: {
    borderColor: 'border-l-success',
    dotColor: 'bg-success',
  },
  failed: {
    borderColor: 'border-l-error',
    dotColor: 'bg-error',
  },
}

const statusBorderColor = computed(() => statusConfig[props.event.status].borderColor)
const statusDotColor = computed(() => statusConfig[props.event.status].dotColor)

const formattedTime = computed(() => {
  const date = new Date(props.event.scheduledAt)
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${hours}:${minutes}`
})
</script>
