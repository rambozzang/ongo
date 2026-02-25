<template>
  <div
    class="flex items-start gap-3 rounded-lg border px-4 py-3 transition-colors"
    :class="[
      alert.read
        ? 'border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-900'
        : 'border-blue-200 bg-blue-50/50 dark:border-blue-800 dark:bg-blue-950/30',
    ]"
  >
    <!-- Unread indicator -->
    <div class="mt-1.5 flex-shrink-0">
      <span
        v-if="!alert.read"
        class="inline-block h-2 w-2 rounded-full bg-blue-500"
      />
      <span v-else class="inline-block h-2 w-2" />
    </div>

    <!-- Alert type badge -->
    <span
      class="mt-0.5 inline-flex flex-shrink-0 items-center rounded-full px-2 py-0.5 text-xs font-medium"
      :class="badgeClass"
    >
      {{ $t(`liveDashboard.alertType.${alert.type}`) }}
    </span>

    <!-- Content -->
    <div class="min-w-0 flex-1">
      <p class="text-sm font-medium text-gray-900 dark:text-gray-100">
        {{ alert.title }}
      </p>
      <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
        {{ alert.description }}
      </p>
      <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
        {{ timeAgo }}
      </p>
    </div>

    <!-- Mark as read button -->
    <button
      v-if="!alert.read"
      class="flex-shrink-0 rounded-lg p-1.5 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600 dark:hover:bg-gray-800 dark:hover:text-gray-300"
      :title="$t('liveDashboard.markAsRead')"
      @click="$emit('markRead', alert.id)"
    >
      <CheckIcon class="h-4 w-4" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckIcon } from '@heroicons/vue/24/outline'
import type { LiveAlert } from '@/types/liveDashboard'

const props = defineProps<{
  alert: LiveAlert
}>()

defineEmits<{
  markRead: [alertId: number]
}>()

const { t } = useI18n({ useScope: 'global' })

const badgeClass = computed(() => {
  switch (props.alert.type) {
    case 'SPIKE':
      return 'bg-orange-100 text-orange-700 dark:bg-orange-900/40 dark:text-orange-400'
    case 'DROP':
      return 'bg-red-100 text-red-700 dark:bg-red-900/40 dark:text-red-400'
    case 'MILESTONE':
      return 'bg-blue-100 text-blue-700 dark:bg-blue-900/40 dark:text-blue-400'
    case 'VIRAL':
      return 'bg-purple-100 text-purple-700 dark:bg-purple-900/40 dark:text-purple-400'
    default:
      return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
  }
})

const timeAgo = computed(() => {
  const now = Date.now()
  const created = new Date(props.alert.createdAt).getTime()
  const diffMs = now - created
  const diffMin = Math.floor(diffMs / 60000)
  const diffHour = Math.floor(diffMs / 3600000)
  const diffDay = Math.floor(diffMs / 86400000)

  if (diffMin < 1) return t('liveDashboard.timeAgo.justNow')
  if (diffMin < 60) return t('liveDashboard.timeAgo.minutes', { n: diffMin })
  if (diffHour < 24) return t('liveDashboard.timeAgo.hours', { n: diffHour })
  return t('liveDashboard.timeAgo.days', { n: diffDay })
})
</script>
