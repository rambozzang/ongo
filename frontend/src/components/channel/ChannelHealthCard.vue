<template>
  <div
    class="card relative overflow-hidden"
    :class="{
      'border-l-4': true,
      'border-l-green-500': healthStatus === 'healthy',
      'border-l-yellow-500': healthStatus === 'warning',
      'border-l-red-500': healthStatus === 'error',
    }"
  >
    <!-- Health Indicator Dot -->
    <div class="absolute top-4 right-4">
      <div
        class="h-3 w-3 rounded-full"
        :class="{
          'bg-green-500': healthStatus === 'healthy',
          'bg-yellow-500': healthStatus === 'warning',
          'bg-red-500': healthStatus === 'error',
          'animate-pulse': healthStatus === 'error',
        }"
      />
    </div>

    <!-- Platform Icon and Name -->
    <div class="mb-4 flex items-start gap-4">
      <div
        class="flex h-16 w-16 flex-shrink-0 items-center justify-center rounded-full text-2xl font-bold text-white shadow-md"
        :style="{ backgroundColor: platformConfig.color }"
      >
        {{ platformInitial }}
      </div>
      <div class="flex-1 min-w-0">
        <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100 truncate">
          {{ channel.channelName }}
        </h3>
        <PlatformBadge :platform="channel.platform" class="mt-1" />
        <div class="mt-2">
          <span
            class="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium"
            :class="{
              'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400': statusConfig.type === 'success',
              'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400': statusConfig.type === 'warning',
              'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400': statusConfig.type === 'error',
            }"
          >
            {{ statusConfig.label }}
          </span>
        </div>
      </div>
    </div>

    <!-- Token Expiration Countdown -->
    <div v-if="tokenExpiresAt" class="mb-4 rounded-lg bg-gray-50 dark:bg-gray-700/50 p-3">
      <div class="mb-2 flex items-center justify-between text-sm">
        <span class="text-gray-600 dark:text-gray-400">토큰 만료</span>
        <span
          class="font-medium"
          :class="{
            'text-green-600 dark:text-green-400': daysUntilExpiry > 7,
            'text-yellow-600 dark:text-yellow-400': daysUntilExpiry <= 7 && daysUntilExpiry > 0,
            'text-red-600 dark:text-red-400': daysUntilExpiry <= 0,
          }"
        >
          {{ expiryText }}
        </span>
      </div>
      <!-- Progress Bar -->
      <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-600">
        <div
          class="h-full transition-all duration-300"
          :class="{
            'bg-green-500': daysUntilExpiry > 7,
            'bg-yellow-500': daysUntilExpiry <= 7 && daysUntilExpiry > 0,
            'bg-red-500': daysUntilExpiry <= 0,
          }"
          :style="{ width: `${Math.max(0, Math.min(100, expiryProgress))}%` }"
        />
      </div>
    </div>

    <!-- Channel Info Grid -->
    <div class="mb-4 grid grid-cols-2 gap-3">
      <div class="rounded-lg bg-gray-50 dark:bg-gray-700/50 p-3">
        <div class="text-xs text-gray-500 dark:text-gray-400 mb-1">구독자/팔로워</div>
        <div class="text-lg font-bold text-gray-900 dark:text-gray-100">
          {{ formattedSubscriberCount }}
        </div>
      </div>
      <div class="rounded-lg bg-gray-50 dark:bg-gray-700/50 p-3">
        <div class="text-xs text-gray-500 dark:text-gray-400 mb-1">마지막 동기화</div>
        <div class="text-sm font-medium text-gray-900 dark:text-gray-100">
          {{ lastSyncText }}
        </div>
      </div>
    </div>

    <!-- Action Buttons -->
    <div class="flex items-center gap-2 border-t border-gray-100 dark:border-gray-700 pt-4">
      <button
        class="btn-secondary inline-flex flex-1 items-center justify-center gap-1.5 text-sm"
        @click="$emit('sync')"
      >
        <ArrowPathIcon class="h-4 w-4" />
        동기화
      </button>
      <button
        v-if="channel.tokenStatus === 'EXPIRED' || channel.tokenStatus === 'EXPIRING_SOON'"
        class="btn-primary inline-flex flex-1 items-center justify-center gap-1.5 text-sm"
        @click="$emit('reconnect')"
      >
        <LinkIcon class="h-4 w-4" />
        재연결
      </button>
      <button
        class="btn-danger inline-flex flex-1 items-center justify-center gap-1.5 text-sm"
        @click="$emit('disconnect')"
      >
        <XMarkIcon class="h-4 w-4" />
        연결 해제
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ArrowPathIcon, LinkIcon, XMarkIcon } from '@heroicons/vue/24/outline'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import type { Channel, Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'

const props = defineProps<{
  channel: Channel
  tokenExpiresAt?: string | null
}>()

defineEmits<{
  sync: []
  reconnect: []
  disconnect: []
}>()

// Platform Configuration
const platformConfig = computed(() => PLATFORM_CONFIG[props.channel.platform])

const platformInitial = computed(() => {
  const initials: Record<Platform, string> = {
    YOUTUBE: 'Y',
    TIKTOK: 'T',
    INSTAGRAM: 'I',
    NAVER_CLIP: 'N',
  }
  return initials[props.channel.platform]
})

// Status Configuration
const statusConfig = computed(() => {
  switch (props.channel.tokenStatus) {
    case 'ACTIVE':
      return { label: '연결됨', type: 'success' }
    case 'EXPIRING_SOON':
      return { label: '만료 예정', type: 'warning' }
    case 'EXPIRED':
      return { label: '만료됨', type: 'error' }
    default:
      return { label: '오류', type: 'error' }
  }
})

// Health Status (for left border color and dot)
const healthStatus = computed<'healthy' | 'warning' | 'error'>(() => {
  if (props.channel.tokenStatus === 'EXPIRED') return 'error'
  if (props.channel.tokenStatus === 'EXPIRING_SOON') return 'warning'
  return 'healthy'
})

// Token Expiration
const daysUntilExpiry = computed(() => {
  if (!props.tokenExpiresAt) return 999
  const now = Date.now()
  const expiryTime = new Date(props.tokenExpiresAt).getTime()
  const diffMs = expiryTime - now
  return Math.ceil(diffMs / (1000 * 60 * 60 * 24))
})

const expiryText = computed(() => {
  const days = daysUntilExpiry.value
  if (days <= 0) return '만료됨'
  if (days === 1) return '내일 만료'
  if (days <= 7) return `${days}일 남음`
  if (days <= 30) return `${days}일 남음`
  return `${Math.floor(days / 30)}개월 남음`
})

const expiryProgress = computed(() => {
  if (!props.tokenExpiresAt) return 100
  const totalDays = 90 // Assume 90 days total validity
  const days = daysUntilExpiry.value
  return (days / totalDays) * 100
})

// Subscriber Count Formatting
const formattedSubscriberCount = computed(() => {
  const count = props.channel.subscriberCount
  if (count >= 100_000_000) {
    const value = count / 100_000_000
    return value % 1 === 0 ? `${value}억` : `${value.toFixed(1)}억`
  }
  if (count >= 10_000) {
    const value = count / 10_000
    return value % 1 === 0 ? `${value}만` : `${value.toFixed(1)}만`
  }
  if (count >= 1_000) {
    const value = count / 1_000
    return value % 1 === 0 ? `${value}K` : `${value.toFixed(1)}K`
  }
  return count.toLocaleString()
})

// Last Sync Time Formatting
const lastSyncText = computed(() => {
  if (!props.channel.lastSyncedAt) return '없음'

  const now = Date.now()
  const syncTime = new Date(props.channel.lastSyncedAt).getTime()
  const diffMs = now - syncTime

  const minutes = Math.floor(diffMs / 60_000)
  const hours = Math.floor(diffMs / 3_600_000)
  const days = Math.floor(diffMs / 86_400_000)

  if (minutes < 1) return '방금 전'
  if (minutes < 60) return `${minutes}분 전`
  if (hours < 24) return `${hours}시간 전`
  if (days < 7) return `${days}일 전`

  const date = new Date(props.channel.lastSyncedAt)
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${m}.${d}`
})
</script>
