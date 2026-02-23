<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useNotificationCenterStore } from '@/stores/notificationCenter'
import NotificationItem from '@/components/notifications/NotificationItem.vue'
import NotificationFilter from '@/components/notifications/NotificationFilter.vue'
import NotificationSettings from '@/components/notifications/NotificationSettings.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import type { Notification, NotificationCategory } from '@/types/notification'
import {
  BellIcon,
  Cog6ToothIcon,
  CheckIcon,
  BellSlashIcon,
} from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })

const router = useRouter()
const store = useNotificationCenterStore()

const showSettings = ref(false)

onMounted(() => {
  store.fetchNotifications()
})

function handleFilterSelect(category: NotificationCategory | null) {
  store.filterByCategory(category)
}

function handleMarkAsRead(id: number) {
  store.markAsRead(id)
}

function handleDelete(id: number) {
  store.deleteNotification(id)
}

function handleClick(notification: Notification) {
  store.markAsRead(notification.id)

  if (notification.referenceType && notification.referenceId) {
    switch (notification.referenceType) {
      case 'video':
        router.push({ name: 'video-detail', params: { id: notification.referenceId } })
        break
      case 'schedule':
        router.push({ name: 'schedule' })
        break
      case 'channel':
        router.push({ name: 'channels' })
        break
      case 'credit':
        router.push({ name: 'ai' })
        break
      case 'report':
        router.push({ name: 'analytics' })
        break
      case 'payment':
        router.push({ name: 'subscription' })
        break
    }
  }
}

function handleUpdateSetting(category: NotificationCategory, field: 'inApp' | 'email' | 'kakao', value: boolean) {
  store.updateSetting(category, field, value)
}
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ $t('notifications.title') }}</h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ store.unreadCount > 0 ? $t('notifications.unreadCount', { count: store.unreadCount }) : $t('notifications.allRead') }}
        </p>
      </div>

      <div class="flex items-center gap-3">
        <button
          v-if="store.unreadCount > 0"
          class="inline-flex items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
          @click="store.markAllAsRead()"
        >
          <CheckIcon class="h-4 w-4" />
          {{ $t('notifications.markAllRead') }}
        </button>
        <button
          class="inline-flex items-center gap-1.5 rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm font-medium transition-colors hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:hover:bg-gray-700"
          :class="showSettings ? 'text-indigo-600 dark:text-indigo-400 border-indigo-300 dark:border-indigo-600' : 'text-gray-700 dark:text-gray-300'"
          @click="showSettings = !showSettings"
        >
          <Cog6ToothIcon class="h-4 w-4" />
          {{ $t('notifications.settings') }}
        </button>
      </div>
    </div>

    <PageGuide :title="$t('notifications.pageGuideTitle')" :items="($tm('notifications.pageGuide') as string[])" />

    <!-- Settings panel (collapsible) -->
    <Transition
      enter-active-class="transition-all duration-300 ease-out"
      enter-from-class="opacity-0 -translate-y-2 max-h-0"
      enter-to-class="opacity-100 translate-y-0 max-h-[600px]"
      leave-active-class="transition-all duration-200 ease-in"
      leave-from-class="opacity-100 translate-y-0 max-h-[600px]"
      leave-to-class="opacity-0 -translate-y-2 max-h-0"
    >
      <div v-if="showSettings" class="overflow-hidden">
        <NotificationSettings
          :settings="store.settings"
          @update="handleUpdateSetting"
        />
      </div>
    </Transition>

    <!-- Filter tabs -->
    <NotificationFilter
      :active-category="store.activeCategory"
      :unread-count-by-category="store.unreadCountByCategory"
      @select="handleFilterSelect"
    />

    <!-- Notification list grouped by date -->
    <div v-if="store.filteredNotifications.length > 0" class="space-y-6">
      <div v-for="group in store.groupedByDate" :key="group.label">
        <h3 class="mb-3 text-sm font-semibold text-gray-500 dark:text-gray-400">
          {{ group.label }}
        </h3>
        <div class="space-y-2">
          <NotificationItem
            v-for="notification in group.notifications"
            :key="notification.id"
            :notification="notification"
            @mark-as-read="handleMarkAsRead"
            @delete="handleDelete"
            @click="handleClick"
          />
        </div>
      </div>
    </div>

    <!-- Empty state -->
    <div
      v-else
      class="flex flex-col items-center justify-center rounded-xl border border-gray-200 bg-white py-16 dark:border-gray-700 dark:bg-gray-800"
    >
      <div class="flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
        <BellSlashIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
      </div>
      <h3 class="mt-4 text-lg font-semibold text-gray-900 dark:text-white">{{ $t('notifications.empty') }}</h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
        {{ store.activeCategory ? $t('notifications.emptyCategory') : $t('notifications.emptyDescription') }}
      </p>
    </div>
  </div>
</template>
