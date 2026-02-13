<template>
  <div class="inline-flex items-center gap-3 rounded-lg bg-gray-50 dark:bg-gray-700/50 px-4 py-2">
    <!-- Last Sync Time -->
    <div class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
      <ClockIcon class="h-4 w-4" />
      <span>{{ lastSyncText }}</span>
    </div>

    <!-- Sync Button -->
    <button
      class="inline-flex items-center gap-1.5 rounded-md bg-white dark:bg-gray-600 px-3 py-1.5 text-sm font-medium text-gray-700 dark:text-gray-200 shadow-sm hover:bg-gray-50 dark:hover:bg-gray-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
      :disabled="syncing"
      @click="handleSync"
    >
      <ArrowPathIcon
        class="h-4 w-4"
        :class="{ 'animate-spin': syncing }"
      />
      {{ syncing ? '동기화 중...' : '동기화' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ClockIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import { useNotification } from '@/composables/useNotification'

const props = defineProps<{
  lastSyncedAt: string | null
}>()

const emit = defineEmits<{
  sync: []
}>()

const notification = useNotification()
const syncing = ref(false)
const localLastSyncedAt = ref(props.lastSyncedAt)

const lastSyncText = computed(() => {
  if (!localLastSyncedAt.value) return '동기화 기록 없음'

  const now = Date.now()
  const syncTime = new Date(localLastSyncedAt.value).getTime()
  const diffMs = now - syncTime

  const minutes = Math.floor(diffMs / 60_000)
  const hours = Math.floor(diffMs / 3_600_000)
  const days = Math.floor(diffMs / 86_400_000)

  if (minutes < 1) return '방금 동기화'
  if (minutes < 60) return `${minutes}분 전 동기화`
  if (hours < 24) return `${hours}시간 전 동기화`
  return `${days}일 전 동기화`
})

async function handleSync() {
  syncing.value = true

  try {
    // Emit sync event — parent handles the actual API call
    emit('sync')

    // Optimistically update last synced time
    localLastSyncedAt.value = new Date().toISOString()
  } catch {
    notification.error('동기화에 실패했습니다. 다시 시도해주세요.')
  } finally {
    syncing.value = false
  }
}
</script>
