<script setup lang="ts">
import { computed } from 'vue'
import type { Notification, NotificationType } from '@/types/notification'
import {
  CloudArrowUpIcon,
  ExclamationTriangleIcon,
  CalendarIcon,
  ClockIcon,
  LinkIcon,
  ExclamationCircleIcon,
  SparklesIcon,
  CreditCardIcon,
  ChartBarIcon,
  DocumentChartBarIcon,
  CheckCircleIcon,
  XCircleIcon,
  TrashIcon,
} from '@heroicons/vue/24/outline'

const props = defineProps<{
  notification: Notification
}>()

const emit = defineEmits<{
  (e: 'markAsRead', id: number): void
  (e: 'delete', id: number): void
  (e: 'click', notification: Notification): void
}>()

interface NotificationMeta {
  icon: typeof CloudArrowUpIcon
  bgColor: string
  iconColor: string
}

const typeConfig: Record<NotificationType, NotificationMeta> = {
  upload_success: {
    icon: CloudArrowUpIcon,
    bgColor: 'bg-green-100 dark:bg-green-900/30',
    iconColor: 'text-green-600 dark:text-green-400',
  },
  upload_failed: {
    icon: ExclamationCircleIcon,
    bgColor: 'bg-red-100 dark:bg-red-900/30',
    iconColor: 'text-red-600 dark:text-red-400',
  },
  platform_published: {
    icon: CheckCircleIcon,
    bgColor: 'bg-blue-100 dark:bg-blue-900/30',
    iconColor: 'text-blue-600 dark:text-blue-400',
  },
  platform_failed: {
    icon: XCircleIcon,
    bgColor: 'bg-red-100 dark:bg-red-900/30',
    iconColor: 'text-red-600 dark:text-red-400',
  },
  schedule_reminder: {
    icon: ClockIcon,
    bgColor: 'bg-amber-100 dark:bg-amber-900/30',
    iconColor: 'text-amber-600 dark:text-amber-400',
  },
  schedule_completed: {
    icon: CalendarIcon,
    bgColor: 'bg-green-100 dark:bg-green-900/30',
    iconColor: 'text-green-600 dark:text-green-400',
  },
  token_expiring: {
    icon: LinkIcon,
    bgColor: 'bg-amber-100 dark:bg-amber-900/30',
    iconColor: 'text-amber-600 dark:text-amber-400',
  },
  token_expired: {
    icon: ExclamationTriangleIcon,
    bgColor: 'bg-red-100 dark:bg-red-900/30',
    iconColor: 'text-red-600 dark:text-red-400',
  },
  credit_low: {
    icon: SparklesIcon,
    bgColor: 'bg-amber-100 dark:bg-amber-900/30',
    iconColor: 'text-amber-600 dark:text-amber-400',
  },
  credit_depleted: {
    icon: SparklesIcon,
    bgColor: 'bg-red-100 dark:bg-red-900/30',
    iconColor: 'text-red-600 dark:text-red-400',
  },
  milestone: {
    icon: ChartBarIcon,
    bgColor: 'bg-purple-100 dark:bg-purple-900/30',
    iconColor: 'text-purple-600 dark:text-purple-400',
  },
  report_ready: {
    icon: DocumentChartBarIcon,
    bgColor: 'bg-indigo-100 dark:bg-indigo-900/30',
    iconColor: 'text-indigo-600 dark:text-indigo-400',
  },
  payment_reminder: {
    icon: CreditCardIcon,
    bgColor: 'bg-blue-100 dark:bg-blue-900/30',
    iconColor: 'text-blue-600 dark:text-blue-400',
  },
  payment_failed: {
    icon: CreditCardIcon,
    bgColor: 'bg-red-100 dark:bg-red-900/30',
    iconColor: 'text-red-600 dark:text-red-400',
  },
}

const meta = computed(() => typeConfig[props.notification.type])

const timeAgo = computed(() => {
  const now = Date.now()
  const created = new Date(props.notification.createdAt).getTime()
  const diffMs = now - created

  const minutes = Math.floor(diffMs / 60000)
  const hours = Math.floor(diffMs / 3600000)
  const days = Math.floor(diffMs / 86400000)

  if (minutes < 1) return '방금 전'
  if (minutes < 60) return `${minutes}분 전`
  if (hours < 24) return `${hours}시간 전`
  if (days < 7) return `${days}일 전`
  if (days < 30) return `${Math.floor(days / 7)}주 전`
  return `${Math.floor(days / 30)}개월 전`
})

function handleClick() {
  emit('click', props.notification)
  if (!props.notification.isRead) {
    emit('markAsRead', props.notification.id)
  }
}

function handleDelete(event: Event) {
  event.stopPropagation()
  emit('delete', props.notification.id)
}
</script>

<template>
  <div
    class="group relative flex gap-3 rounded-lg border p-4 transition-colors cursor-pointer"
    :class="[
      notification.isRead
        ? 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700'
        : 'bg-blue-50/50 dark:bg-blue-900/10 border-blue-200 dark:border-blue-800/40',
      'hover:bg-gray-50 dark:hover:bg-gray-700/50',
    ]"
    @click="handleClick"
  >
    <!-- Unread indicator -->
    <div
      v-if="!notification.isRead"
      class="absolute left-1.5 top-1/2 -translate-y-1/2 h-2 w-2 rounded-full bg-blue-500"
    />

    <!-- Icon -->
    <div
      class="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full"
      :class="meta.bgColor"
    >
      <component
        :is="meta.icon"
        class="h-5 w-5"
        :class="meta.iconColor"
      />
    </div>

    <!-- Content -->
    <div class="min-w-0 flex-1">
      <div class="flex items-start justify-between gap-2">
        <p
          class="text-sm font-semibold"
          :class="notification.isRead ? 'text-gray-700 dark:text-gray-300' : 'text-gray-900 dark:text-white'"
        >
          {{ notification.title }}
        </p>
        <span class="flex-shrink-0 text-xs text-gray-500 dark:text-gray-400">
          {{ timeAgo }}
        </span>
      </div>
      <p class="mt-0.5 text-sm text-gray-600 dark:text-gray-400 line-clamp-2">
        {{ notification.message }}
      </p>
    </div>

    <!-- Delete button (visible on hover) -->
    <button
      class="absolute right-2 top-2 hidden rounded-md p-1 text-gray-400 hover:bg-gray-200 hover:text-gray-600 group-hover:block dark:hover:bg-gray-600 dark:hover:text-gray-300"
      title="삭제"
      @click="handleDelete"
    >
      <TrashIcon class="h-4 w-4" />
    </button>
  </div>
</template>
