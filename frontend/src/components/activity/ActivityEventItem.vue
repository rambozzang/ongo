<template>
  <div
    class="group relative flex gap-4 rounded-lg px-4 py-3 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700/50 cursor-pointer"
    @click="handleClick"
  >
    <!-- Icon with colored circle -->
    <div
      class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full"
      :class="iconBgClass"
    >
      <component :is="iconComponent" class="h-4 w-4 text-white" />
    </div>

    <!-- Content -->
    <div class="flex-1 min-w-0">
      <p class="text-sm text-gray-900 dark:text-gray-100">
        {{ event.description }}
      </p>
      <div class="mt-1 flex items-center gap-2">
        <span class="text-xs text-gray-500 dark:text-gray-400">{{ relativeTime }}</span>
        <span v-if="platformBadge" class="badge-sm" :class="platformBadgeClass">
          {{ platformBadge }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  ArrowUpTrayIcon,
  CheckCircleIcon,
  ClockIcon,
  PencilIcon,
  TrashIcon,
  SparklesIcon,
  ArrowRightOnRectangleIcon,
  LinkIcon,
  ArrowPathIcon,
} from '@heroicons/vue/24/outline'
import type { ActivityEvent } from '@/stores/activityLog'

const props = defineProps<{
  event: ActivityEvent
}>()

const emit = defineEmits<{
  click: [event: ActivityEvent]
}>()

// Icon mapping by type
const iconMap = {
  upload: ArrowUpTrayIcon,
  publish: CheckCircleIcon,
  schedule: ClockIcon,
  edit: PencilIcon,
  delete: TrashIcon,
  ai_use: SparklesIcon,
  login: ArrowRightOnRectangleIcon,
  channel_connect: LinkIcon,
  recycle: ArrowPathIcon,
}

const iconComponent = computed(() => iconMap[props.event.type])

// Background color classes by type
const iconBgClassMap = {
  upload: 'bg-blue-500',
  publish: 'bg-green-500',
  schedule: 'bg-purple-500',
  edit: 'bg-yellow-500',
  delete: 'bg-red-500',
  ai_use: 'bg-indigo-500',
  login: 'bg-gray-500',
  channel_connect: 'bg-teal-500',
  recycle: 'bg-orange-500',
}

const iconBgClass = computed(() => iconBgClassMap[props.event.type])

// Platform badge
const platformBadge = computed(() => {
  const platform = props.event.metadata?.platform
  if (!platform) return null

  const platformLabels: Record<string, string> = {
    YOUTUBE: 'YouTube',
    TIKTOK: 'TikTok',
    INSTAGRAM: 'Instagram',
    NAVER_CLIP: 'Naver',
  }

  return platformLabels[platform] || platform
})

const platformBadgeClass = computed(() => {
  const platform = props.event.metadata?.platform
  if (!platform) return ''

  const platformClasses: Record<string, string> = {
    YOUTUBE: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
    TIKTOK: 'bg-gray-100 text-gray-700 dark:bg-gray-900/30 dark:text-gray-400',
    INSTAGRAM: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    NAVER_CLIP: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
  }

  return platformClasses[platform] || ''
})

// Relative time formatting
const relativeTime = computed(() => {
  const now = new Date()
  const eventDate = new Date(props.event.createdAt)
  const diffMs = now.getTime() - eventDate.getTime()
  const diffSecs = Math.floor(diffMs / 1000)
  const diffMins = Math.floor(diffSecs / 60)
  const diffHours = Math.floor(diffMins / 60)
  const diffDays = Math.floor(diffHours / 24)

  if (diffSecs < 60) return '방금 전'
  if (diffMins < 60) return `${diffMins}분 전`
  if (diffHours < 24) return `${diffHours}시간 전`
  if (diffDays < 7) return `${diffDays}일 전`

  // Format date
  return `${eventDate.getMonth() + 1}월 ${eventDate.getDate()}일`
})

function handleClick() {
  emit('click', props.event)
}
</script>
