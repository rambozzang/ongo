<script setup lang="ts">
import {
  InformationCircleIcon,
  ExclamationTriangleIcon,
  ExclamationCircleIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'
import type { RightsAlert } from '@/types/contentRights'

interface Props {
  alerts: RightsAlert[]
}

interface Emits {
  (e: 'dismiss', alertId: number): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

function getSeverityConfig(severity: string) {
  const configs: Record<string, { icon: typeof InformationCircleIcon; bg: string; border: string; text: string; iconColor: string }> = {
    INFO: {
      icon: InformationCircleIcon,
      bg: 'bg-blue-50 dark:bg-blue-900/20',
      border: 'border-blue-200 dark:border-blue-800',
      text: 'text-blue-800 dark:text-blue-300',
      iconColor: 'text-blue-500 dark:text-blue-400',
    },
    WARNING: {
      icon: ExclamationTriangleIcon,
      bg: 'bg-yellow-50 dark:bg-yellow-900/20',
      border: 'border-yellow-200 dark:border-yellow-800',
      text: 'text-yellow-800 dark:text-yellow-300',
      iconColor: 'text-yellow-500 dark:text-yellow-400',
    },
    CRITICAL: {
      icon: ExclamationCircleIcon,
      bg: 'bg-red-50 dark:bg-red-900/20',
      border: 'border-red-200 dark:border-red-800',
      text: 'text-red-800 dark:text-red-300',
      iconColor: 'text-red-500 dark:text-red-400',
    },
  }
  return configs[severity] ?? configs.INFO
}

function getAssetTypeLabel(type: string): string {
  const map: Record<string, string> = {
    MUSIC: '음악', IMAGE: '이미지', FONT: '폰트',
    VIDEO_CLIP: '비디오 클립', SOUND_EFFECT: '효과음', OTHER: '기타',
  }
  return map[type] ?? type
}
</script>

<template>
  <div v-if="alerts.length > 0" class="mb-6 space-y-2">
    <TransitionGroup
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0 -translate-y-2"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 -translate-y-2"
    >
      <div
        v-for="alert in alerts"
        :key="alert.id"
        :class="[
          'flex items-start gap-3 rounded-lg border p-4',
          getSeverityConfig(alert.severity).bg,
          getSeverityConfig(alert.severity).border,
        ]"
      >
        <!-- Icon -->
        <component
          :is="getSeverityConfig(alert.severity).icon"
          :class="['h-5 w-5 shrink-0 mt-0.5', getSeverityConfig(alert.severity).iconColor]"
        />

        <!-- Content -->
        <div class="flex-1 min-w-0">
          <p :class="['text-sm font-medium', getSeverityConfig(alert.severity).text]">
            {{ alert.message }}
          </p>
          <div class="mt-1 flex items-center gap-3 text-xs">
            <span :class="getSeverityConfig(alert.severity).text" class="opacity-75">
              {{ alert.assetName }}
            </span>
            <span :class="getSeverityConfig(alert.severity).text" class="opacity-75">
              {{ getAssetTypeLabel(alert.assetType) }}
            </span>
            <span
              v-if="alert.daysUntilExpiry !== 0"
              :class="getSeverityConfig(alert.severity).text"
              class="opacity-75"
            >
              {{ alert.daysUntilExpiry > 0 ? `${alert.daysUntilExpiry}일 후 만료` : `${Math.abs(alert.daysUntilExpiry)}일 전 만료됨` }}
            </span>
          </div>
        </div>

        <!-- Dismiss -->
        <button
          class="shrink-0 rounded-lg p-1 transition-colors hover:bg-black/5 dark:hover:bg-white/10"
          :title="'알림 닫기'"
          @click="emit('dismiss', alert.id)"
        >
          <XMarkIcon :class="['h-4 w-4', getSeverityConfig(alert.severity).iconColor]" />
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>
