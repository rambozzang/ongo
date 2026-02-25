<template>
  <div class="rounded-lg border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-800">
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-3">
        <!-- 플랫폼 아이콘 -->
        <div class="flex h-12 w-12 items-center justify-center rounded-lg" :class="config.iconBg">
          <span class="text-lg font-bold" :style="{ color: config.color }">
            {{ platformInitial }}
          </span>
        </div>
        <div>
          <h3 class="font-semibold text-gray-900 dark:text-gray-100">{{ config.label }}</h3>
          <p v-if="connection.storeName" class="text-xs text-gray-500 dark:text-gray-400">
            {{ connection.storeName }}
          </p>
        </div>
      </div>

      <!-- 상태 배지 -->
      <span
        class="inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium"
        :class="statusBadgeClass"
      >
        <span class="mr-1.5 h-1.5 w-1.5 rounded-full" :class="statusDotClass" />
        {{ statusLabel }}
      </span>
    </div>

    <!-- 연결 정보 -->
    <div v-if="connection.status === 'CONNECTED' && connection.connectedAt" class="mt-3 text-xs text-gray-400 dark:text-gray-500">
      {{ $t('commerce.connectedSince') }} {{ formatDate(connection.connectedAt) }}
    </div>

    <!-- 액션 -->
    <div class="mt-4 flex justify-end">
      <button
        v-if="connection.status === 'DISCONNECTED'"
        class="btn-primary text-xs"
        @click="$emit('connect', connection.platform)"
      >
        {{ $t('commerce.connect') }}
      </button>
      <button
        v-else-if="connection.status === 'CONNECTED'"
        class="text-xs text-red-500 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300"
        @click="$emit('disconnect', connection.platform)"
      >
        {{ $t('commerce.disconnect') }}
      </button>
      <button
        v-else
        class="btn-primary text-xs"
        @click="$emit('connect', connection.platform)"
      >
        {{ $t('commerce.reconnect') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CommercePlatformConnection } from '@/types/commerce'
import { COMMERCE_PLATFORM_CONFIG } from './commerceConstants'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  connection: CommercePlatformConnection
}>()

defineEmits<{
  connect: [platform: string]
  disconnect: [platform: string]
}>()

const config = computed(() => COMMERCE_PLATFORM_CONFIG[props.connection.platform])

const platformInitial = computed(() => {
  const map = { COUPANG: 'C', NAVER_SMARTSTORE: 'N', TIKTOK_SHOP: 'T' }
  return map[props.connection.platform] ?? '?'
})

const statusLabel = computed(() => {
  const map: Record<string, string> = {
    CONNECTED: t('commerce.statusConnected'),
    DISCONNECTED: t('commerce.statusDisconnected'),
    EXPIRED: t('commerce.statusExpired'),
  }
  return map[props.connection.status] ?? props.connection.status
})

const statusBadgeClass = computed(() => {
  const map: Record<string, string> = {
    CONNECTED: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    DISCONNECTED: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400',
    EXPIRED: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
  }
  return map[props.connection.status] ?? ''
})

const statusDotClass = computed(() => {
  const map: Record<string, string> = {
    CONNECTED: 'bg-green-500',
    DISCONNECTED: 'bg-gray-400',
    EXPIRED: 'bg-yellow-500',
  }
  return map[props.connection.status] ?? 'bg-gray-400'
})

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('ko-KR')
}
</script>
