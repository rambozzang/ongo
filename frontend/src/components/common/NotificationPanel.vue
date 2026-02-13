<template>
  <Transition
    enter-active-class="transition duration-200 ease-out"
    enter-from-class="opacity-0 -translate-y-2"
    enter-to-class="opacity-100 translate-y-0"
    leave-active-class="transition duration-150 ease-in"
    leave-from-class="opacity-100 translate-y-0"
    leave-to-class="opacity-0 -translate-y-2"
  >
    <div
      v-if="isOpen"
      role="dialog"
      aria-label="알림 패널"
      class="absolute right-0 top-full z-50 mt-2 w-[380px] rounded-xl border border-gray-200 bg-white shadow-xl dark:border-gray-700 dark:bg-gray-800"
      @keydown.escape="emit('close')"
    >
      <!-- Header -->
      <div class="flex items-center justify-between border-b border-gray-100 px-4 py-3 dark:border-gray-700">
        <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">알림</h3>
        <button
          v-if="unreadCount > 0"
          class="text-sm text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
          @click="handleMarkAllAsRead"
        >
          모두 읽음
        </button>
      </div>

      <!-- Filter Tabs -->
      <div class="flex border-b border-gray-100 px-4 dark:border-gray-700">
        <button
          v-for="tab in filterTabs"
          :key="tab.value"
          class="relative px-3 py-2 text-sm font-medium transition-colors"
          :class="
            currentFilter === tab.value
              ? 'text-primary-600 dark:text-primary-400'
              : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300'
          "
          @click="currentFilter = tab.value"
        >
          {{ tab.label }}
          <div
            v-if="currentFilter === tab.value"
            class="absolute bottom-0 left-0 right-0 h-0.5 bg-primary-600 dark:bg-primary-400"
          />
        </button>
      </div>

      <!-- Notifications List -->
      <div class="max-h-[480px] overflow-y-auto">
        <template v-if="filteredNotifications.length > 0">
          <div
            v-for="notification in filteredNotifications"
            :key="notification.id"
            class="flex cursor-pointer gap-3 border-b border-gray-100 px-4 py-3 transition-colors hover:bg-gray-50 dark:border-gray-700 dark:hover:bg-gray-700/50"
            @click="handleNotificationClick(notification)"
          >
            <!-- Icon -->
            <div class="flex-shrink-0">
              <div
                class="flex h-10 w-10 items-center justify-center rounded-full"
                :class="getCategoryIconClass(notification.category)"
              >
                <component :is="getCategoryIcon(notification.category)" class="h-5 w-5" />
              </div>
            </div>

            <!-- Content -->
            <div class="min-w-0 flex-1">
              <p
                class="text-sm"
                :class="notification.isRead ? 'text-gray-700 dark:text-gray-300' : 'font-semibold text-gray-900 dark:text-gray-100'"
              >
                {{ notification.title }}
              </p>
              <p class="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
                {{ notification.message }}
              </p>
              <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
                {{ formatRelativeTime(notification.createdAt) }}
              </p>
            </div>

            <!-- Unread Indicator -->
            <div v-if="!notification.isRead" class="flex-shrink-0">
              <div class="h-2 w-2 rounded-full bg-primary-600 dark:bg-primary-400" />
            </div>
          </div>
        </template>

        <!-- Empty State -->
        <div v-else class="px-4 py-12 text-center">
          <BellSlashIcon class="mx-auto h-12 w-12 text-gray-300 dark:text-gray-600" />
          <p class="mt-3 text-sm text-gray-500 dark:text-gray-400">새로운 알림이 없습니다</p>
        </div>
      </div>

      <!-- Footer -->
      <div
        v-if="notifications.length > 0"
        class="border-t border-gray-100 px-4 py-3 dark:border-gray-700"
      >
        <button
          class="w-full text-center text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300"
          @click="handleClearAll"
        >
          모두 지우기
        </button>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  VideoCameraIcon,
  ClockIcon,
  WifiIcon,
  SparklesIcon,
  ChartBarIcon,
  CreditCardIcon,
  BellSlashIcon,
} from '@heroicons/vue/24/outline'
import { useNotificationCenterStore } from '@/stores/notificationCenter'
import { formatRelativeTime } from '@/utils/date'
import type { Notification, NotificationCategory } from '@/types/notification'

interface Props {
  isOpen: boolean
}

defineProps<Props>()

const emit = defineEmits<{
  close: []
}>()

const router = useRouter()
const notificationStore = useNotificationCenterStore()

const { notifications, unreadCount } = notificationStore

type FilterValue = 'all' | 'unread'

const currentFilter = ref<FilterValue>('all')

const filterTabs = [
  { value: 'all' as FilterValue, label: '전체' },
  { value: 'unread' as FilterValue, label: '안 읽음' },
]

const filteredNotifications = computed(() => {
  if (currentFilter.value === 'unread') {
    return notifications.filter((n) => !n.isRead)
  }
  return notifications
})

function getCategoryIcon(category: NotificationCategory) {
  const iconMap = {
    upload: VideoCameraIcon,
    schedule: ClockIcon,
    channel: WifiIcon,
    ai: SparklesIcon,
    analytics: ChartBarIcon,
    subscription: CreditCardIcon,
  }
  return iconMap[category]
}

function getCategoryIconClass(category: NotificationCategory) {
  const classMap = {
    upload: 'bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400',
    schedule: 'bg-amber-100 text-amber-600 dark:bg-amber-900/30 dark:text-amber-400',
    channel: 'bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400',
    ai: 'bg-violet-100 text-violet-600 dark:bg-violet-900/30 dark:text-violet-400',
    analytics: 'bg-cyan-100 text-cyan-600 dark:bg-cyan-900/30 dark:text-cyan-400',
    subscription: 'bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400',
  }
  return classMap[category]
}

function handleNotificationClick(notification: Notification) {
  // Mark as read
  notificationStore.markAsRead(notification.id)

  // Navigate if has reference
  if (notification.referenceType && notification.referenceId) {
    switch (notification.referenceType) {
      case 'video':
        router.push(`/videos/${notification.referenceId}`)
        break
      case 'schedule':
        router.push('/schedules')
        break
      case 'credit':
        router.push('/subscription')
        break
    }
  }

  // Close panel
  emit('close')
}

function handleMarkAllAsRead() {
  notificationStore.markAllAsRead()
}

function handleClearAll() {
  if (confirm('모든 알림을 삭제하시겠습니까?')) {
    notificationStore.clearAll()
  }
}
</script>
