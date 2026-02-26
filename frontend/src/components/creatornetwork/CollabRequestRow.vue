<template>
  <div class="flex flex-col gap-3 rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900 tablet:flex-row tablet:items-center">
    <!-- Sender / Receiver -->
    <div class="flex min-w-0 flex-1 items-center gap-3">
      <!-- Sender avatar -->
      <div
        class="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full bg-gradient-to-br text-sm font-bold text-white"
        :class="avatarGradient(request.senderId)"
      >
        {{ request.senderName.charAt(0) }}
      </div>
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-1 text-sm">
          <span class="font-semibold text-gray-900 dark:text-gray-100">{{ request.senderName }}</span>
          <svg class="h-3.5 w-3.5 flex-shrink-0 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M13 7l5 5m0 0l-5 5m5-5H6" />
          </svg>
          <span class="font-semibold text-gray-900 dark:text-gray-100">{{ request.receiverName }}</span>
        </div>
        <p class="mt-0.5 truncate text-xs text-gray-500 dark:text-gray-400">
          {{ request.message }}
        </p>
      </div>
    </div>

    <!-- Collab type badge -->
    <div class="flex flex-shrink-0 items-center gap-2">
      <span
        class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
        :class="collabTypeBadgeClass"
      >
        {{ collabTypeLabel }}
      </span>
    </div>

    <!-- Status badge -->
    <div class="flex flex-shrink-0 items-center gap-2">
      <span
        class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
        :class="statusBadgeClass"
      >
        {{ statusLabel }}
      </span>
    </div>

    <!-- Actions for pending received requests -->
    <div v-if="request.status === 'PENDING' && isReceived" class="flex flex-shrink-0 items-center gap-2">
      <button
        class="rounded-lg bg-primary-600 px-3 py-1.5 text-xs font-medium text-white transition-colors hover:bg-primary-700"
        @click="$emit('respond', request.id, true)"
      >
        {{ $t('creatorNetwork.accept') }}
      </button>
      <button
        class="rounded-lg border border-gray-300 px-3 py-1.5 text-xs font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:text-gray-300 dark:hover:bg-gray-800"
        @click="$emit('respond', request.id, false)"
      >
        {{ $t('creatorNetwork.decline') }}
      </button>
    </div>

    <!-- Timestamp -->
    <span class="flex-shrink-0 text-xs text-gray-400 dark:text-gray-500">
      {{ formatDate(request.createdAt) }}
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { CollabRequest } from '@/types/creatorNetwork'

const { t } = useI18n({ useScope: 'global' })

const props = defineProps<{
  request: CollabRequest
  isReceived?: boolean
}>()

defineEmits<{
  respond: [id: number, accept: boolean]
}>()

function avatarGradient(id: number): string {
  const gradients = [
    'from-blue-500 to-cyan-400',
    'from-purple-500 to-pink-400',
    'from-emerald-500 to-teal-400',
    'from-orange-500 to-amber-400',
    'from-red-500 to-rose-400',
    'from-indigo-500 to-blue-400',
  ]
  return gradients[id % gradients.length]
}

const collabTypeBadgeClass = computed(() => {
  const map: Record<string, string> = {
    COLLAB_VIDEO: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    GUEST_APPEARANCE: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
    CROSS_PROMOTION: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400',
    LIVE_TOGETHER: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-400',
    CHALLENGE: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400',
  }
  return map[props.request.collabType] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const collabTypeLabel = computed(() => {
  const labels: Record<string, string> = {
    COLLAB_VIDEO: t('creatorNetwork.typeCollabVideo'),
    GUEST_APPEARANCE: t('creatorNetwork.typeGuestAppearance'),
    CROSS_PROMOTION: t('creatorNetwork.typeCrossPromotion'),
    LIVE_TOGETHER: t('creatorNetwork.typeLiveTogether'),
    CHALLENGE: t('creatorNetwork.typeChallenge'),
  }
  return labels[props.request.collabType] ?? props.request.collabType
})

const statusBadgeClass = computed(() => {
  const map: Record<string, string> = {
    PENDING: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
    ACCEPTED: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    DECLINED: 'bg-red-100 text-red-600 dark:bg-red-900/30 dark:text-red-400',
    EXPIRED: 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400',
  }
  return map[props.request.status] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
})

const statusLabel = computed(() => {
  const labels: Record<string, string> = {
    PENDING: t('creatorNetwork.statusPending'),
    ACCEPTED: t('creatorNetwork.statusAccepted'),
    DECLINED: t('creatorNetwork.statusDeclined'),
    EXPIRED: t('creatorNetwork.statusExpired'),
  }
  return labels[props.request.status] ?? props.request.status
})

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
}
</script>
