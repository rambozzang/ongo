<template>
  <div
    class="flex items-start gap-3 rounded-lg border border-gray-100 bg-gray-50 p-3 dark:border-gray-700 dark:bg-gray-800/50"
  >
    <!-- Severity Icon -->
    <div class="mt-0.5 flex-shrink-0">
      <InformationCircleIcon
        v-if="issue.severity === 'INFO'"
        class="h-5 w-5 text-blue-500 dark:text-blue-400"
      />
      <ExclamationTriangleIcon
        v-else-if="issue.severity === 'WARNING'"
        class="h-5 w-5 text-yellow-500 dark:text-yellow-400"
      />
      <StopCircleIcon
        v-else
        class="h-5 w-5 text-red-500 dark:text-red-400"
      />
    </div>

    <!-- Content -->
    <div class="min-w-0 flex-1">
      <div class="flex flex-wrap items-center gap-2">
        <!-- Issue Type Badge -->
        <span
          class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
          :class="issueTypeBadgeClass"
        >
          {{ issueTypeLabel }}
        </span>
        <!-- Severity Badge -->
        <span
          class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
          :class="severityBadgeClass"
        >
          {{ severityLabel }}
        </span>
      </div>

      <!-- Title -->
      <h4 class="mt-1.5 text-sm font-medium text-gray-900 dark:text-gray-100">
        {{ issue.title }}
      </h4>

      <!-- Description -->
      <p class="mt-0.5 text-xs text-gray-600 dark:text-gray-400">
        {{ issue.description }}
      </p>

      <!-- Timestamp range -->
      <div
        v-if="issue.timestamp"
        class="mt-1.5 flex items-center gap-1 text-xs text-gray-500 dark:text-gray-500"
      >
        <ClockIcon class="h-3.5 w-3.5" />
        <span>{{ $t('copyrightCheck.timestamp') }}: {{ formatTime(issue.timestamp.start) }} - {{ formatTime(issue.timestamp.end) }}</span>
      </div>

      <!-- Suggestion -->
      <div class="mt-2 flex items-start gap-1.5 rounded-md bg-blue-50 p-2 dark:bg-blue-900/20">
        <LightBulbIcon class="mt-0.5 h-3.5 w-3.5 flex-shrink-0 text-blue-500 dark:text-blue-400" />
        <p class="text-xs text-blue-700 dark:text-blue-300">
          <span class="font-medium">{{ $t('copyrightCheck.suggestion') }}:</span> {{ issue.suggestion }}
        </p>
      </div>

      <!-- Auto-fix Button -->
      <button
        v-if="issue.autoFixAvailable"
        class="btn-primary mt-2 inline-flex items-center gap-1.5 px-3 py-1.5 text-xs"
        @click="$emit('autoFix')"
      >
        <WrenchScrewdriverIcon class="h-3.5 w-3.5" />
        {{ $t('copyrightCheck.autoFix') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  InformationCircleIcon,
  ExclamationTriangleIcon,
  StopCircleIcon,
  ClockIcon,
  LightBulbIcon,
  WrenchScrewdriverIcon,
} from '@heroicons/vue/24/outline'
import type { CopyrightIssue } from '@/types/copyrightCheck'

const { t } = useI18n({ useScope: 'global' })

const props = defineProps<{
  issue: CopyrightIssue
}>()

defineEmits<{
  autoFix: []
}>()

const issueTypeLabel = computed(() => {
  const map: Record<string, string> = {
    MUSIC: t('copyrightCheck.issueTypeMusic'),
    IMAGE: t('copyrightCheck.issueTypeImage'),
    BRAND: t('copyrightCheck.issueTypeBrand'),
    CONTENT_POLICY: t('copyrightCheck.issueTypeContentPolicy'),
    MONETIZATION: t('copyrightCheck.issueTypeMonetization'),
  }
  return map[props.issue.type] ?? props.issue.type
})

const issueTypeBadgeClass = computed(() => {
  const map: Record<string, string> = {
    MUSIC: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
    IMAGE: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-400',
    BRAND: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-400',
    CONTENT_POLICY: 'bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-400',
    MONETIZATION: 'bg-cyan-100 text-cyan-800 dark:bg-cyan-900/30 dark:text-cyan-400',
  }
  return map[props.issue.type] ?? 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300'
})

const severityLabel = computed(() => {
  const map: Record<string, string> = {
    INFO: t('copyrightCheck.severityInfo'),
    WARNING: t('copyrightCheck.severityWarning'),
    CRITICAL: t('copyrightCheck.severityCritical'),
  }
  return map[props.issue.severity] ?? props.issue.severity
})

const severityBadgeClass = computed(() => {
  const map: Record<string, string> = {
    INFO: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
    WARNING: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
    CRITICAL: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
  }
  return map[props.issue.severity] ?? 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300'
})

function formatTime(seconds: number): string {
  const min = Math.floor(seconds / 60)
  const sec = Math.floor(seconds % 60)
  return `${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}`
}
</script>
