<template>
  <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm tablet:p-6">
    <!-- Header: Score Badge + Week Range -->
    <div class="mb-4 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div class="flex items-center gap-4">
        <!-- Overall Score Circle -->
        <div class="relative flex-shrink-0">
          <svg class="h-16 w-16" viewBox="0 0 64 64">
            <circle
              cx="32" cy="32" r="28"
              fill="none"
              class="stroke-gray-200 dark:stroke-gray-700"
              stroke-width="5"
            />
            <circle
              cx="32" cy="32" r="28"
              fill="none"
              :class="scoreStrokeClass"
              stroke-width="5"
              stroke-linecap="round"
              :stroke-dasharray="circumference"
              :stroke-dashoffset="scoreOffset"
              transform="rotate(-90 32 32)"
              class="transition-all duration-700"
            />
          </svg>
          <span
            class="absolute inset-0 flex items-center justify-center text-sm font-bold"
            :class="scoreTextClass"
          >
            {{ report.overallScore }}
          </span>
        </div>

        <div>
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('growthCoach.reports.overallScore') }}
          </h3>
          <p class="text-sm text-gray-500 dark:text-gray-400">
            {{ $t('growthCoach.reports.weekRange', { start: report.weekStart, end: report.weekEnd }) }}
          </p>
        </div>
      </div>

      <span class="text-xs text-gray-400 dark:text-gray-500">
        {{ $t('growthCoach.reports.generatedAt') }}: {{ formatDate(report.generatedAt) }}
      </span>
    </div>

    <!-- Summary -->
    <div class="mb-4 rounded-lg bg-gray-50 dark:bg-gray-800/50 p-3">
      <h4 class="mb-1 text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('growthCoach.reports.summary') }}
      </h4>
      <p class="text-sm text-gray-600 dark:text-gray-400 leading-relaxed">{{ report.summary }}</p>
    </div>

    <!-- Highlights & Concerns -->
    <div class="mb-4 grid gap-4 tablet:grid-cols-2">
      <!-- Highlights -->
      <div>
        <h4 class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ $t('growthCoach.reports.highlights') }}
        </h4>
        <ul class="space-y-1.5">
          <li
            v-for="(item, idx) in report.highlights"
            :key="idx"
            class="flex items-start gap-2 text-sm text-gray-600 dark:text-gray-400"
          >
            <CheckCircleIcon class="mt-0.5 h-4 w-4 flex-shrink-0 text-green-500 dark:text-green-400" />
            <span>{{ item }}</span>
          </li>
        </ul>
      </div>

      <!-- Concerns -->
      <div>
        <h4 class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">
          {{ $t('growthCoach.reports.concerns') }}
        </h4>
        <ul class="space-y-1.5">
          <li
            v-for="(item, idx) in report.concerns"
            :key="idx"
            class="flex items-start gap-2 text-sm text-gray-600 dark:text-gray-400"
          >
            <ExclamationTriangleIcon class="mt-0.5 h-4 w-4 flex-shrink-0 text-orange-500 dark:text-orange-400" />
            <span>{{ item }}</span>
          </li>
        </ul>
      </div>
    </div>

    <!-- Growth Metrics Row -->
    <div class="mb-4">
      <h4 class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('growthCoach.reports.growthMetrics') }}
      </h4>
      <div class="grid grid-cols-3 gap-3">
        <div class="rounded-lg border border-gray-200 dark:border-gray-700 p-3 text-center">
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('growthCoach.reports.subscriberGrowth') }}</p>
          <p class="mt-1 text-lg font-bold" :class="metricColor(report.subscriberGrowth)">
            {{ report.subscriberGrowth > 0 ? '+' : '' }}{{ formatNumber(report.subscriberGrowth) }}
          </p>
        </div>
        <div class="rounded-lg border border-gray-200 dark:border-gray-700 p-3 text-center">
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('growthCoach.reports.viewsGrowth') }}</p>
          <p class="mt-1 text-lg font-bold" :class="metricColor(report.viewsGrowth)">
            {{ report.viewsGrowth > 0 ? '+' : '' }}{{ formatNumber(report.viewsGrowth) }}
          </p>
        </div>
        <div class="rounded-lg border border-gray-200 dark:border-gray-700 p-3 text-center">
          <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('growthCoach.reports.engagementChange') }}</p>
          <p class="mt-1 text-lg font-bold" :class="metricColor(report.engagementChange)">
            {{ report.engagementChange > 0 ? '+' : '' }}{{ report.engagementChange.toFixed(1) }}%
          </p>
        </div>
      </div>
    </div>

    <!-- Top Content Table -->
    <div v-if="report.topContent.length > 0" class="mb-4">
      <h4 class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('growthCoach.reports.topContent') }}
      </h4>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-200 dark:border-gray-700">
              <th class="pb-2 text-left text-xs font-medium text-gray-500 dark:text-gray-400">
                {{ $t('growthCoach.reports.contentTitle') }}
              </th>
              <th class="pb-2 text-right text-xs font-medium text-gray-500 dark:text-gray-400">
                {{ $t('growthCoach.reports.contentViews') }}
              </th>
              <th class="pb-2 text-right text-xs font-medium text-gray-500 dark:text-gray-400">
                {{ $t('growthCoach.reports.contentPlatform') }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(content, idx) in report.topContent"
              :key="idx"
              class="border-b border-gray-100 dark:border-gray-800 last:border-0"
            >
              <td class="py-2 text-gray-900 dark:text-gray-100">{{ content.title }}</td>
              <td class="py-2 text-right text-gray-600 dark:text-gray-400">{{ formatNumber(content.views) }}</td>
              <td class="py-2 text-right">
                <span class="rounded-full bg-gray-100 dark:bg-gray-800 px-2 py-0.5 text-xs font-medium text-gray-600 dark:text-gray-400 capitalize">
                  {{ content.platform }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Action Items Checklist -->
    <div v-if="report.actionItems.length > 0">
      <h4 class="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">
        {{ $t('growthCoach.reports.actionItems') }}
      </h4>
      <ul class="space-y-2">
        <li
          v-for="action in report.actionItems"
          :key="action.id"
          class="flex items-start gap-3 rounded-lg border border-gray-100 dark:border-gray-800 p-3"
        >
          <!-- Status toggle -->
          <button
            class="mt-0.5 flex-shrink-0"
            @click="toggleAction(action)"
          >
            <CheckCircleIcon
              v-if="action.status === 'DONE'"
              class="h-5 w-5 text-green-500 dark:text-green-400"
            />
            <div
              v-else
              class="h-5 w-5 rounded-full border-2 border-gray-300 dark:border-gray-600 hover:border-green-400 dark:hover:border-green-500 transition-colors"
            />
          </button>

          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <span
                class="text-sm font-medium"
                :class="action.status === 'DONE'
                  ? 'text-gray-400 dark:text-gray-500 line-through'
                  : 'text-gray-900 dark:text-gray-100'"
              >
                {{ action.title }}
              </span>
              <span
                class="rounded-full px-2 py-0.5 text-xs font-medium"
                :class="priorityClass(action.priority)"
              >
                {{ $t(`growthCoach.actions.priority${action.priority.charAt(0) + action.priority.slice(1).toLowerCase()}`) }}
              </span>
            </div>
            <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">{{ action.description }}</p>
            <div class="mt-1 flex items-center gap-3 text-xs text-gray-400 dark:text-gray-500">
              <span>{{ $t('growthCoach.actions.impact') }}: {{ action.estimatedImpact }}</span>
              <span v-if="action.dueDate">{{ $t('growthCoach.actions.dueDate') }}: {{ action.dueDate }}</span>
            </div>
          </div>

          <!-- Action buttons for non-done items -->
          <div v-if="action.status !== 'DONE' && action.status !== 'SKIPPED'" class="flex gap-1">
            <button
              class="rounded px-2 py-1 text-xs text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800 transition-colors"
              @click="emit('updateAction', action.id, 'SKIPPED')"
            >
              {{ $t('growthCoach.actions.markSkipped') }}
            </button>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  CheckCircleIcon,
  ExclamationTriangleIcon,
} from '@heroicons/vue/24/outline'
import type { WeeklyReport, ActionItem, ActionStatus } from '@/types/growthCoach'

const props = defineProps<{
  report: WeeklyReport
}>()

const emit = defineEmits<{
  updateAction: [actionId: number, status: ActionStatus]
}>()


const circumference = 2 * Math.PI * 28

const scoreOffset = computed(() => {
  return circumference - (props.report.overallScore / 100) * circumference
})

const scoreStrokeClass = computed(() => {
  if (props.report.overallScore >= 80) return 'stroke-green-500 dark:stroke-green-400'
  if (props.report.overallScore >= 60) return 'stroke-yellow-500 dark:stroke-yellow-400'
  return 'stroke-red-500 dark:stroke-red-400'
})

const scoreTextClass = computed(() => {
  if (props.report.overallScore >= 80) return 'text-green-600 dark:text-green-400'
  if (props.report.overallScore >= 60) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
})

function metricColor(value: number): string {
  if (value > 0) return 'text-green-600 dark:text-green-400'
  if (value < 0) return 'text-red-600 dark:text-red-400'
  return 'text-gray-600 dark:text-gray-400'
}

function priorityClass(priority: string): string {
  switch (priority) {
    case 'HIGH': return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
    case 'MEDIUM': return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
    case 'LOW': return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
    default: return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
  }
}

function toggleAction(action: ActionItem) {
  const newStatus: ActionStatus = action.status === 'DONE' ? 'TODO' : 'DONE'
  emit('updateAction', action.id, newStatus)
}

function formatNumber(num: number): string {
  if (Math.abs(num) >= 1_000_000) return `${(num / 1_000_000).toFixed(1)}M`
  if (Math.abs(num) >= 1_000) return `${(num / 1_000).toFixed(1)}K`
  return num.toLocaleString()
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleDateString()
}
</script>
