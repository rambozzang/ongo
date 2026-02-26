<script setup lang="ts">
import {
  CheckCircleIcon,
  ExclamationTriangleIcon,
  XCircleIcon,
} from '@heroicons/vue/24/outline'
import type { SafetyCheckItem } from '@/types/brandSafety'

defineProps<{
  item: SafetyCheckItem
}>()

const statusIcons: Record<string, { icon: typeof CheckCircleIcon; color: string }> = {
  PASS: { icon: CheckCircleIcon, color: 'text-green-500' },
  WARN: { icon: ExclamationTriangleIcon, color: 'text-yellow-500' },
  FAIL: { icon: XCircleIcon, color: 'text-red-500' },
}

const severityConfig: Record<string, { label: string; bg: string; text: string }> = {
  LOW: { label: '낮음', bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300' },
  MEDIUM: { label: '중간', bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300' },
  HIGH: { label: '높음', bg: 'bg-orange-100 dark:bg-orange-900/30', text: 'text-orange-700 dark:text-orange-300' },
  CRITICAL: { label: '심각', bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
}

const getStatusIcon = (status: string) => statusIcons[status] ?? statusIcons.PASS
const getSeverityStyle = (severity: string) => severityConfig[severity] ?? severityConfig.LOW
</script>

<template>
  <div class="rounded-lg bg-gray-50 p-2.5 dark:bg-gray-800/50">
    <div class="flex items-start gap-2">
      <!-- Status Icon -->
      <component
        :is="getStatusIcon(item.status).icon"
        class="mt-0.5 h-4 w-4 shrink-0"
        :class="getStatusIcon(item.status).color"
      />

      <div class="min-w-0 flex-1">
        <!-- Category + Name + Severity -->
        <div class="flex flex-wrap items-center gap-1.5">
          <span class="text-xs text-gray-500 dark:text-gray-400">[{{ item.category }}]</span>
          <span class="text-xs font-semibold text-gray-900 dark:text-gray-100">
            {{ item.name }}
          </span>
          <span
            :class="[getSeverityStyle(item.severity).bg, getSeverityStyle(item.severity).text]"
            class="rounded-full px-1.5 py-0.5 text-[10px] font-medium"
          >
            {{ getSeverityStyle(item.severity).label }}
          </span>
        </div>

        <!-- Description -->
        <p class="mt-1 text-xs text-gray-600 dark:text-gray-400">
          {{ item.description }}
        </p>

        <!-- Recommendation -->
        <p
          v-if="item.recommendation"
          class="mt-1 text-xs font-medium text-blue-600 dark:text-blue-400"
        >
          {{ item.recommendation }}
        </p>
      </div>
    </div>
  </div>
</template>
