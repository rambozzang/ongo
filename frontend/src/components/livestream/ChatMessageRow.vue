<script setup lang="ts">
import { ShieldCheckIcon } from '@heroicons/vue/24/solid'
import type { StreamChat } from '@/types/liveStream'

defineProps<{
  chat: StreamChat
}>()

const formatTime = (iso: string) => {
  try {
    const date = new Date(iso)
    return date.toLocaleTimeString('ko-KR', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    })
  } catch {
    return iso
  }
}
</script>

<template>
  <div
    class="flex items-start gap-3 rounded-lg px-3 py-2 transition-colors"
    :class="chat.isHighlighted
      ? 'bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800'
      : 'hover:bg-gray-50 dark:hover:bg-gray-800/50'"
  >
    <!-- Username + Moderator badge -->
    <div class="flex shrink-0 items-center gap-1.5">
      <span
        class="text-sm font-semibold"
        :class="chat.isModerator
          ? 'text-green-600 dark:text-green-400'
          : 'text-gray-900 dark:text-gray-100'"
      >
        {{ chat.username }}
      </span>
      <ShieldCheckIcon
        v-if="chat.isModerator"
        class="h-4 w-4 text-green-500"
        :title="'모더레이터'"
      />
    </div>

    <!-- Message -->
    <p class="flex-1 text-sm text-gray-700 dark:text-gray-300">
      {{ chat.message }}
    </p>

    <!-- Timestamp -->
    <span class="shrink-0 text-xs text-gray-400 dark:text-gray-500">
      {{ formatTime(chat.timestamp) }}
    </span>
  </div>
</template>
