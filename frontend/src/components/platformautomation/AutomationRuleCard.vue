<script setup lang="ts">
import {
  PlayIcon,
  ArrowPathIcon,
  ArchiveBoxIcon,
  ArrowUturnRightIcon,
  TagIcon,
  CogIcon,
} from '@heroicons/vue/24/outline'
import type { AutomationRule } from '@/types/platformAutomation'
import TriggerTypeBadge from './TriggerTypeBadge.vue'

interface Props {
  rule: AutomationRule
}

interface Emits {
  (e: 'toggle', id: number): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const actionConfig: Record<string, { icon: typeof PlayIcon; label: string }> = {
  PUBLISH: { icon: PlayIcon, label: '게시' },
  NOTIFY: { icon: ArrowPathIcon, label: '알림' },
  REPLY: { icon: ArrowUturnRightIcon, label: '답글' },
  TAG: { icon: TagIcon, label: '태그' },
  ARCHIVE: { icon: ArchiveBoxIcon, label: '보관' },
}

const platformConfig: Record<string, { label: string; class: string }> = {
  YOUTUBE: {
    label: 'YouTube',
    class: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
  },
  TIKTOK: {
    label: 'TikTok',
    class: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
  },
  INSTAGRAM: {
    label: 'Instagram',
    class: 'bg-pink-100 text-pink-700 dark:bg-pink-900/30 dark:text-pink-300',
  },
  NAVERCLIP: {
    label: 'Naver Clip',
    class: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
  },
}

function formatDate(iso: string | null): string {
  if (!iso) return '-'
  const d = new Date(iso)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  return `${month}/${day} ${hours}:${minutes}`
}

function getActionInfo(actionType: string) {
  return actionConfig[actionType] ?? { icon: CogIcon, label: actionType }
}

function getPlatformInfo(platform: string) {
  return platformConfig[platform] ?? { label: platform, class: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300' }
}
</script>

<template>
  <div
    class="relative rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition-all hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
  >
    <!-- Header -->
    <div class="mb-3 flex items-start justify-between">
      <div class="flex-1 min-w-0">
        <h3 class="truncate text-base font-semibold text-gray-900 dark:text-gray-100">
          {{ rule.name }}
        </h3>
      </div>
      <!-- Active Toggle -->
      <label class="relative ml-3 inline-flex cursor-pointer items-center">
        <input
          type="checkbox"
          :checked="rule.isActive"
          class="peer sr-only"
          @change="emit('toggle', rule.id)"
        />
        <div
          class="h-5 w-9 rounded-full bg-gray-200 after:absolute after:left-[2px] after:top-[2px] after:h-4 after:w-4 after:rounded-full after:border after:border-gray-300 after:bg-white after:transition-all after:content-[''] peer-checked:bg-primary-600 peer-checked:after:translate-x-full peer-checked:after:border-white peer-focus:outline-none peer-focus:ring-2 peer-focus:ring-primary-300 dark:bg-gray-700 dark:peer-focus:ring-primary-800"
        />
      </label>
    </div>

    <!-- Badges: Platform + Trigger + Action -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <span
        :class="getPlatformInfo(rule.platform).class"
        class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
      >
        {{ getPlatformInfo(rule.platform).label }}
      </span>
      <TriggerTypeBadge :trigger-type="rule.triggerType" />
      <span class="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-gray-700 dark:bg-gray-800 dark:text-gray-300">
        <component :is="getActionInfo(rule.actionType).icon" class="h-3.5 w-3.5" />
        {{ getActionInfo(rule.actionType).label }}
      </span>
    </div>

    <!-- Condition -->
    <p class="mb-4 text-sm text-gray-600 dark:text-gray-400">
      {{ rule.condition }}
    </p>

    <!-- Footer Stats -->
    <div class="flex items-center justify-between border-t border-gray-100 pt-3 text-xs text-gray-500 dark:border-gray-700 dark:text-gray-400">
      <span>
        실행 <span class="font-semibold text-gray-900 dark:text-gray-100">{{ rule.executionCount }}</span>회
      </span>
      <span>
        마지막 실행: {{ formatDate(rule.lastExecuted) }}
      </span>
    </div>

    <!-- Inactive overlay -->
    <div
      v-if="!rule.isActive"
      class="absolute inset-0 flex items-center justify-center rounded-xl bg-gray-900/10 dark:bg-gray-900/30"
    >
      <span class="rounded-full bg-gray-800/80 px-3 py-1 text-xs font-medium text-white">
        비활성
      </span>
    </div>
  </div>
</template>
