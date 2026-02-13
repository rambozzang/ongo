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
        <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
          {{ event.title }}
        </p>

        <!-- Time & Platform -->
        <div class="mt-1 flex items-center gap-2">
          <span class="text-xs text-gray-500 dark:text-gray-400">
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
import type { CalendarEvent } from '@/views/CalendarView.vue'

const props = defineProps<{
  event: CalendarEvent
}>()

const statusConfig = {
  scheduled: {
    borderColor: 'border-l-blue-500',
    dotColor: 'bg-blue-500',
  },
  published: {
    borderColor: 'border-l-green-500',
    dotColor: 'bg-green-500',
  },
  failed: {
    borderColor: 'border-l-red-500',
    dotColor: 'bg-red-500',
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
