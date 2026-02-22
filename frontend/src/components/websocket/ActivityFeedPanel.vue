<script setup lang="ts">
import { computed } from 'vue'
import {
  XMarkIcon,
  ArrowUpTrayIcon,
  VideoCameraIcon,
  ChatBubbleLeftIcon,
  BellIcon,
  ChartBarIcon,
  ClockIcon,
  UserIcon,
} from '@heroicons/vue/24/outline'
import type { WsMessage, OnlineMember } from '@/composables/useWebSocket'

const props = defineProps<{
  isOpen: boolean
  messages: WsMessage[]
  onlineMembers: OnlineMember[]
  connected: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const ACTIVITY_ICONS: Record<string, typeof ArrowUpTrayIcon> = {
  UPLOAD: ArrowUpTrayIcon,
  PUBLISH: VideoCameraIcon,
  COMMENT: ChatBubbleLeftIcon,
  NOTIFICATION: BellIcon,
  ANALYTICS: ChartBarIcon,
  SCHEDULE: ClockIcon,
}

const ACTIVITY_COLORS: Record<string, string> = {
  UPLOAD: 'text-blue-500 bg-blue-100 dark:bg-blue-900/30',
  PUBLISH: 'text-green-500 bg-green-100 dark:bg-green-900/30',
  COMMENT: 'text-amber-500 bg-amber-100 dark:bg-amber-900/30',
  NOTIFICATION: 'text-purple-500 bg-purple-100 dark:bg-purple-900/30',
  ANALYTICS: 'text-indigo-500 bg-indigo-100 dark:bg-indigo-900/30',
  SCHEDULE: 'text-teal-500 bg-teal-100 dark:bg-teal-900/30',
}

interface ActivityPayload {
  activityType: string
  userName: string
  message: string
  timestamp: number
}

const activityMessages = computed(() => {
  return props.messages
    .filter((m) => m.type === 'ACTIVITY')
    .map((m) => m.payload as ActivityPayload)
})

function getIcon(type: string) {
  return ACTIVITY_ICONS[type] || BellIcon
}

function getColorClass(type: string): string {
  return ACTIVITY_COLORS[type] || 'text-gray-500 bg-gray-100 dark:bg-gray-800'
}

function formatTime(timestamp: number): string {
  const diff = Date.now() - timestamp
  const seconds = Math.floor(diff / 1000)
  if (seconds < 60) return '방금 전'
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}분 전`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}시간 전`
  return `${Math.floor(hours / 24)}일 전`
}
</script>

<template>
  <!-- Backdrop -->
  <Transition name="fade">
    <div
      v-if="isOpen"
      class="fixed inset-0 z-40 bg-black/20"
      @click="emit('close')"
    />
  </Transition>

  <!-- Panel -->
  <Transition name="slide-right">
    <div
      v-if="isOpen"
      class="fixed right-0 top-0 z-50 flex h-full w-80 flex-col border-l border-gray-200 bg-white shadow-xl dark:border-gray-700 dark:bg-gray-900"
    >
      <!-- Header -->
      <div class="flex items-center justify-between border-b border-gray-200 px-4 py-3 dark:border-gray-700">
        <div class="flex items-center gap-2">
          <h2 class="text-sm font-semibold text-gray-900 dark:text-white">실시간 활동</h2>
          <span
            :class="[
              'inline-flex h-2 w-2 rounded-full',
              connected ? 'bg-green-500 animate-pulse' : 'bg-gray-400',
            ]"
          />
        </div>
        <button
          @click="emit('close')"
          class="rounded-lg p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800 dark:hover:text-gray-300"
        >
          <XMarkIcon class="h-5 w-5" />
        </button>
      </div>

      <!-- Online Members -->
      <div v-if="onlineMembers.length > 0" class="border-b border-gray-200 px-4 py-3 dark:border-gray-700">
        <h3 class="mb-2 text-xs font-medium uppercase text-gray-500 dark:text-gray-400">
          온라인 ({{ onlineMembers.length }})
        </h3>
        <div class="flex flex-wrap gap-2">
          <div
            v-for="member in onlineMembers"
            :key="member.userId"
            class="flex items-center gap-1.5 rounded-full bg-green-50 px-2.5 py-1 text-xs font-medium text-green-700 dark:bg-green-900/20 dark:text-green-400"
          >
            <span class="inline-flex h-1.5 w-1.5 rounded-full bg-green-500" />
            {{ member.name }}
          </div>
        </div>
      </div>

      <!-- Activity Feed -->
      <div class="flex-1 overflow-y-auto">
        <div v-if="activityMessages.length === 0" class="flex flex-col items-center justify-center py-16">
          <BellIcon class="mb-3 h-10 w-10 text-gray-300 dark:text-gray-600" />
          <p class="text-sm text-gray-400 dark:text-gray-500">아직 활동이 없습니다</p>
        </div>

        <div v-else class="divide-y divide-gray-100 dark:divide-gray-800">
          <div
            v-for="(activity, idx) in activityMessages"
            :key="idx"
            class="flex gap-3 px-4 py-3 transition-colors hover:bg-gray-50 dark:hover:bg-gray-800/50"
          >
            <div :class="['flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-lg', getColorClass(activity.activityType)]">
              <component :is="getIcon(activity.activityType)" class="h-4 w-4" />
            </div>
            <div class="min-w-0 flex-1">
              <p class="text-sm text-gray-900 dark:text-gray-100">
                <span class="font-medium">{{ activity.userName }}</span>
                {{ activity.message }}
              </p>
              <p class="mt-0.5 text-xs text-gray-400 dark:text-gray-500">
                {{ formatTime(activity.timestamp) }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.slide-right-enter-active,
.slide-right-leave-active {
  transition: transform 0.3s ease;
}
.slide-right-enter-from,
.slide-right-leave-to {
  transform: translateX(100%);
}
</style>
