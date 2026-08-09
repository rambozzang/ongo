<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useNotificationCenterStore } from '@/stores/notificationCenter'
import NotificationItem from '@/components/notifications/NotificationItem.vue'
import NotificationFilter from '@/components/notifications/NotificationFilter.vue'
import NotificationSettings from '@/components/notifications/NotificationSettings.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import type { Notification, NotificationCategory } from '@/types/notification'
import { settingsApi, type NotificationSettings as NotificationSettingsForm } from '@/api/settings'
import {
  Cog6ToothIcon,
  CheckIcon,
  BellSlashIcon,
} from '@heroicons/vue/24/outline'

useI18n({ useScope: 'global' })

const router = useRouter()
const store = useNotificationCenterStore()

const showSettings = ref(false)
const notificationSettings = reactive<NotificationSettingsForm>({
  commentFrequency: 'realtime',
})
const settingsLoading = ref(false)
const settingsSaving = ref(false)
const settingsError = ref<string | null>(null)
const settingsSaved = ref(false)

onMounted(() => {
  store.fetchNotifications().catch(() => undefined)
})

async function loadNotificationSettings() {
  settingsLoading.value = true
  settingsError.value = null
  try {
    const result = await settingsApi.getSettings()
    notificationSettings.commentFrequency = result.notificationComment.toLowerCase() === 'realtime' ? 'realtime' : 'none'
  } catch (error) {
    settingsError.value = error instanceof Error ? error.message : '알림 설정을 불러오지 못했습니다'
  } finally {
    settingsLoading.value = false
  }
}

function handleSettingsToggle() {
  showSettings.value = !showSettings.value
  if (showSettings.value && !settingsLoading.value) {
    loadNotificationSettings()
  }
}

function handleUpdateSettings(next: NotificationSettingsForm) {
  Object.assign(notificationSettings, next)
  settingsSaved.value = false
  settingsError.value = null
}

async function saveNotificationSettings() {
  settingsSaving.value = true
  settingsSaved.value = false
  settingsError.value = null
  try {
    const result = await settingsApi.updateNotifications({ ...notificationSettings })
    notificationSettings.commentFrequency = result.notificationComment.toLowerCase() === 'realtime' ? 'realtime' : 'none'
    settingsSaved.value = true
  } catch (error) {
    settingsError.value = error instanceof Error ? error.message : '알림 설정 저장에 실패했습니다'
  } finally {
    settingsSaving.value = false
  }
}

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

</script>

<template>
  <div class="min-h-full py-5 text-content tablet:py-6">
    <!-- Header -->
    <PageHeader
      :title="$t('notifications.title')"
      :description="store.unreadCount > 0 ? $t('notifications.unreadCount', { count: store.unreadCount }) : $t('notifications.allRead')"
    >
      <template #actions>
        <button
          v-if="store.unreadCount > 0"
          class="btn-secondary inline-flex items-center gap-1.5"
          @click="store.markAllAsRead()"
        >
          <CheckIcon class="h-4 w-4" />
          {{ $t('notifications.markAllRead') }}
        </button>
        <button
          class="btn-secondary inline-flex items-center gap-1.5"
          :class="showSettings ? 'border-accent text-accent' : ''"
          @click="handleSettingsToggle"
        >
          <Cog6ToothIcon class="h-4 w-4" />
          {{ $t('notifications.settings') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('notifications.pageGuideTitle')" :items="($tm('notifications.pageGuide') as string[])" />

    <div v-if="store.loadError" class="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-error-subtle bg-error-subtle px-4 py-3 text-body-sm text-error-strong" role="alert">
      <span class="min-w-0 flex-1">{{ store.loadError }}</span>
      <button type="button" class="btn-secondary shrink-0" @click="store.fetchNotifications().catch(() => undefined)">
        {{ $t('action.retry') }}
      </button>
    </div>

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
        <div v-if="settingsLoading" class="card mb-4 h-56 animate-pulse" aria-label="알림 설정 불러오는 중" />
        <NotificationSettings
          v-else
          :settings="notificationSettings"
          :saving="settingsSaving"
          :error="settingsError"
          :saved="settingsSaved"
          @update="handleUpdateSettings"
          @save="saveNotificationSettings"
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
        <h3 class="mb-3 text-overline uppercase text-content-tertiary">
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
      class="card py-16 text-center"
    >
      <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-surface-raised">
        <BellSlashIcon class="h-8 w-8 text-content-quaternary" />
      </div>
      <h3 class="mt-4 text-h3 text-content">{{ $t('notifications.empty') }}</h3>
      <p class="mt-1 text-body-sm text-content-secondary">
        {{ store.activeCategory ? $t('notifications.emptyCategory') : $t('notifications.emptyDescription') }}
      </p>
    </div>

    <!-- Pagination -->
    <div
      v-if="store.totalPages > 1"
      class="mt-4 flex items-center justify-between"
    >
      <p class="text-body-sm text-content-secondary">
        {{ store.page * store.pageSize + 1 }}–{{ Math.min((store.page + 1) * store.pageSize, store.totalCount) }} / {{ store.totalCount }}
      </p>
      <div class="flex gap-2">
        <button
          class="btn-secondary text-sm"
          :disabled="!store.hasPrevPage"
          @click="store.prevPage()"
        >
          {{ $t('notifications.pagination.previous') }}
        </button>
        <button
          class="btn-secondary text-sm"
          :disabled="!store.hasNextPage"
          @click="store.nextPage()"
        >
          {{ $t('notifications.pagination.next') }}
        </button>
      </div>
    </div>
  </div>
</template>
