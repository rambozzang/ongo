<template>
  <div
    class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-shadow hover:shadow-md dark:border-gray-700 dark:bg-gray-900"
    @click="$emit('select')"
  >
    <!-- Header: Video title + Status badge -->
    <div class="flex items-start justify-between gap-3">
      <div class="min-w-0 flex-1">
        <h3 class="truncate text-sm font-semibold text-gray-900 dark:text-gray-100">
          {{ result.videoTitle }}
        </h3>
        <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
          {{ $t('copyrightCheck.checkedAt') }}: {{ formattedDate }}
        </p>
      </div>
      <span
        class="inline-flex flex-shrink-0 items-center rounded-full px-2.5 py-1 text-xs font-semibold"
        :class="statusBadgeClass"
      >
        {{ statusLabel }}
      </span>
    </div>

    <!-- Monetization indicator -->
    <div class="mt-3 flex items-center gap-2">
      <div
        class="flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium"
        :class="result.monetizationEligible
          ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
          : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'"
      >
        <CurrencyDollarIcon class="h-3.5 w-3.5" />
        {{ result.monetizationEligible
          ? $t('copyrightCheck.monetizationEligible')
          : $t('copyrightCheck.monetizationIneligible') }}
      </div>
      <div
        v-if="result.issues.length > 0"
        class="flex items-center gap-1 text-xs text-gray-500 dark:text-gray-400"
      >
        <ExclamationTriangleIcon class="h-3.5 w-3.5" />
        {{ result.issues.length }} {{ $t('copyrightCheck.issues') }}
      </div>
      <div
        v-else
        class="flex items-center gap-1 text-xs text-green-600 dark:text-green-400"
      >
        <CheckCircleIcon class="h-3.5 w-3.5" />
        {{ $t('copyrightCheck.noIssues') }}
      </div>
    </div>

    <!-- Platform checks row -->
    <div class="mt-3 flex flex-wrap gap-2">
      <span
        v-for="pc in result.platformChecks"
        :key="pc.platform"
        class="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium"
        :class="platformStatusClass(pc.status)"
      >
        {{ pc.platform }}
        <CheckCircleIcon v-if="pc.status === 'PASSED'" class="h-3 w-3" />
        <ExclamationTriangleIcon v-else-if="pc.status === 'WARNING'" class="h-3 w-3" />
        <XCircleIcon v-else-if="pc.status === 'BLOCKED'" class="h-3 w-3" />
        <ClockIcon v-else class="h-3 w-3" />
      </span>
    </div>

    <!-- Expand/Collapse toggle -->
    <button
      class="mt-3 flex w-full items-center justify-center gap-1 rounded-lg py-1.5 text-xs font-medium text-gray-500 transition-colors hover:bg-gray-50 hover:text-gray-700 dark:text-gray-400 dark:hover:bg-gray-800 dark:hover:text-gray-300"
      @click.stop="expanded = !expanded"
    >
      {{ expanded ? $t('copyrightCheck.hideDetails') : $t('copyrightCheck.showDetails') }}
      <ChevronUpIcon v-if="expanded" class="h-4 w-4" />
      <ChevronDownIcon v-else class="h-4 w-4" />
    </button>

    <!-- Expanded details -->
    <div
      class="grid transition-all duration-300 ease-in-out"
      :class="expanded ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'"
    >
      <div class="overflow-hidden">
        <div class="mt-2 space-y-4">
          <!-- Issues list -->
          <div v-if="result.issues.length > 0" class="space-y-2">
            <h4 class="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
              {{ $t('copyrightCheck.issues') }}
            </h4>
            <IssueItem
              v-for="issue in result.issues"
              :key="issue.id"
              :issue="issue"
              @auto-fix="$emit('autoFix', result.id, issue.id)"
            />
          </div>

          <!-- Music detected -->
          <div v-if="result.musicDetected.length > 0">
            <h4 class="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
              {{ $t('copyrightCheck.musicDetected') }}
            </h4>
            <div class="space-y-2">
              <div
                v-for="(music, idx) in result.musicDetected"
                :key="idx"
                class="flex items-center justify-between rounded-lg border border-gray-100 bg-gray-50 px-3 py-2 dark:border-gray-700 dark:bg-gray-800/50"
              >
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                    {{ music.title }}
                  </p>
                  <p class="text-xs text-gray-500 dark:text-gray-400">
                    {{ music.artist }} &middot; {{ formatTime(music.timestamp.start) }} - {{ formatTime(music.timestamp.end) }}
                  </p>
                </div>
                <span
                  class="ml-2 flex-shrink-0 rounded-full px-2 py-0.5 text-xs font-medium"
                  :class="music.licensed
                    ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                    : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'"
                >
                  {{ music.licensed ? $t('copyrightCheck.licensed') : $t('copyrightCheck.unlicensed') }}
                </span>
              </div>
            </div>
          </div>

          <!-- Platform checks detail -->
          <div v-if="result.platformChecks.some(pc => pc.issues.length > 0)">
            <h4 class="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
              {{ $t('copyrightCheck.platformChecks') }}
            </h4>
            <div class="space-y-2">
              <div
                v-for="pc in result.platformChecks.filter(p => p.issues.length > 0)"
                :key="pc.platform"
                class="rounded-lg border border-gray-100 bg-gray-50 px-3 py-2 dark:border-gray-700 dark:bg-gray-800/50"
              >
                <div class="flex items-center gap-2">
                  <span class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ pc.platform }}</span>
                  <span
                    class="rounded-full px-2 py-0.5 text-xs font-medium"
                    :class="platformStatusClass(pc.status)"
                  >
                    {{ platformStatusLabel(pc.status) }}
                  </span>
                </div>
                <ul class="mt-1 space-y-0.5">
                  <li
                    v-for="(issue, idx) in pc.issues"
                    :key="idx"
                    class="text-xs text-gray-600 dark:text-gray-400"
                  >
                    &bull; {{ issue }}
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CheckCircleIcon,
  ExclamationTriangleIcon,
  XCircleIcon,
  ClockIcon,
  CurrencyDollarIcon,
  ChevronDownIcon,
  ChevronUpIcon,
} from '@heroicons/vue/24/outline'
import IssueItem from '@/components/copyrightcheck/IssueItem.vue'
import type { CopyrightCheckResult, CheckStatus } from '@/types/copyrightCheck'

const { t } = useI18n({ useScope: 'global' })

const props = defineProps<{
  result: CopyrightCheckResult
}>()

defineEmits<{
  autoFix: [resultId: number, issueId: string]
  select: []
}>()

const expanded = ref(false)

const statusLabel = computed(() => {
  const map: Record<string, string> = {
    PASSED: t('copyrightCheck.statusPassed'),
    WARNING: t('copyrightCheck.statusWarning'),
    BLOCKED: t('copyrightCheck.statusBlocked'),
    PENDING: t('copyrightCheck.statusPending'),
    CHECKING: t('copyrightCheck.statusChecking'),
  }
  return map[props.result.status] ?? props.result.status
})

const statusBadgeClass = computed(() => {
  const map: Record<string, string> = {
    PASSED: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    WARNING: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
    BLOCKED: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
    PENDING: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300',
    CHECKING: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
  }
  return map[props.result.status] ?? 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300'
})

const formattedDate = computed(() => {
  const date = new Date(props.result.checkedAt)
  return date.toLocaleString()
})

function platformStatusClass(status: CheckStatus): string {
  const map: Record<string, string> = {
    PASSED: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    WARNING: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
    BLOCKED: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
    PENDING: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300',
    CHECKING: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
  }
  return map[status] ?? 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300'
}

function platformStatusLabel(status: CheckStatus): string {
  const map: Record<string, string> = {
    PASSED: t('copyrightCheck.statusPassed'),
    WARNING: t('copyrightCheck.statusWarning'),
    BLOCKED: t('copyrightCheck.statusBlocked'),
    PENDING: t('copyrightCheck.statusPending'),
    CHECKING: t('copyrightCheck.statusChecking'),
  }
  return map[status] ?? status
}

function formatTime(seconds: number): string {
  const min = Math.floor(seconds / 60)
  const sec = Math.floor(seconds % 60)
  return `${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}`
}
</script>
