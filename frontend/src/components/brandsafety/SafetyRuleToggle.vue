<script setup lang="ts">
import type { BrandSafetyRule } from '@/types/brandSafety'

const props = defineProps<{
  rule: BrandSafetyRule
}>()

const emit = defineEmits<{
  toggle: [id: number, isEnabled: boolean]
}>()

const severityConfig: Record<string, { label: string; bg: string; text: string }> = {
  LOW: { label: '낮음', bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-300' },
  MEDIUM: { label: '중간', bg: 'bg-yellow-100 dark:bg-yellow-900/30', text: 'text-yellow-700 dark:text-yellow-300' },
  HIGH: { label: '높음', bg: 'bg-orange-100 dark:bg-orange-900/30', text: 'text-orange-700 dark:text-orange-300' },
  CRITICAL: { label: '심각', bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-300' },
}

const getSeverityStyle = (severity: string) => severityConfig[severity] ?? severityConfig.LOW

const handleToggle = () => {
  emit('toggle', props.rule.id, !props.rule.isEnabled)
}
</script>

<template>
  <div class="flex items-center justify-between rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <div class="min-w-0 flex-1">
      <!-- Name + Category + Severity -->
      <div class="flex flex-wrap items-center gap-2">
        <span class="text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ rule.name }}
        </span>
        <span class="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-400">
          {{ rule.category }}
        </span>
        <span
          :class="[getSeverityStyle(rule.severity).bg, getSeverityStyle(rule.severity).text]"
          class="rounded-full px-2 py-0.5 text-xs font-medium"
        >
          {{ getSeverityStyle(rule.severity).label }}
        </span>
      </div>

      <!-- Description -->
      <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
        {{ rule.description }}
      </p>
    </div>

    <!-- Toggle Switch -->
    <button
      type="button"
      role="switch"
      :aria-checked="rule.isEnabled"
      :class="rule.isEnabled ? 'bg-primary-600' : 'bg-gray-300 dark:bg-gray-600'"
      class="relative ml-4 inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
      @click="handleToggle"
    >
      <span
        :class="rule.isEnabled ? 'translate-x-5' : 'translate-x-0'"
        class="pointer-events-none inline-block h-5 w-5 translate-y-0.5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out"
      />
    </button>
  </div>
</template>
