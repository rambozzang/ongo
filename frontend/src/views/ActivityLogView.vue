<script setup lang="ts">
import { onMounted, computed, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import {
  ArrowDownTrayIcon,
  ArrowUpTrayIcon,
  CheckCircleIcon,
  SparklesIcon,
  ClockIcon,
  TrashIcon,
  LinkIcon,
} from '@heroicons/vue/24/outline'
import ActivityLogFilter from '@/components/activitylog/ActivityLogFilter.vue'
import ActivityLogTimeline from '@/components/activitylog/ActivityLogTimeline.vue'
import { useActivityLogsStore } from '@/stores/activityLogs'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import KpiCard from '@/components/redesign/KpiCard.vue'
import SectionCard from '@/components/redesign/SectionCard.vue'
import type { ActivityLog, ActivityAction, ActivityDateRange, ActivityDateCustomRange } from '@/types/activitylog'

const router = useRouter()
const activityLogsStore = useActivityLogsStore()

const {
  isLoading, filter, customDateRange, groupedByDate, actionCounts, uniqueUsers,
  page, pageSize, totalCount, totalPages, hasNextPage, hasPrevPage,
} = storeToRefs(activityLogsStore)

// Summary cards showing today's activity counts
interface SummaryCardData {
  label: string
  key: ActivityAction
  icon: Component
  colorClass: string
}

const summaryCards: SummaryCardData[] = [
  { label: 'activityLog.summary.upload', key: 'upload', icon: ArrowUpTrayIcon, colorClass: 'text-info-strong bg-info-subtle' },
  { label: 'activityLog.summary.publish', key: 'publish', icon: CheckCircleIcon, colorClass: 'text-success-strong bg-success-subtle' },
  { label: 'activityLog.summary.schedule', key: 'schedule', icon: ClockIcon, colorClass: 'text-info-strong bg-info-subtle' },
  { label: 'activityLog.summary.aiGenerate', key: 'ai_generate', icon: SparklesIcon, colorClass: 'text-primary-600 dark:text-primary-400 bg-primary-100 dark:bg-primary-900/30' },
  { label: 'activityLog.summary.delete', key: 'delete', icon: TrashIcon, colorClass: 'text-error-strong bg-error-subtle' },
  { label: 'activityLog.summary.channelConnect', key: 'channel_connect', icon: LinkIcon, colorClass: 'text-success-strong bg-success-subtle' },
]

const selectedAction = computed(() => filter.value.action ?? null)
const selectedDateRange = computed(() => filter.value.dateRange ?? null)
const selectedUserId = computed(() => filter.value.userId ?? null)
const searchQuery = computed(() => filter.value.searchQuery ?? '')

function handleActionUpdate(action: ActivityAction | null) {
  activityLogsStore.filterByAction(action)
}

function handleDateRangeUpdate(range: ActivityDateRange | null, custom?: ActivityDateCustomRange) {
  activityLogsStore.filterByDate(range, custom)
}

function handleUserUpdate(userId: string | null) {
  activityLogsStore.filterByUser(userId)
}

function handleSearchUpdate(query: string) {
  activityLogsStore.setSearchQuery(query)
}

function handleResetFilters() {
  activityLogsStore.resetFilters()
}

function handleExport() {
  activityLogsStore.exportLogs()
}

function handleEntityClick(log: ActivityLog) {
  switch (log.entityType) {
    case 'video':
      if (log.entityId) {
        router.push(`/videos/${log.entityId}`)
      }
      break
    case 'schedule':
      router.push('/schedule')
      break
    case 'channel':
      router.push('/channels')
      break
    default:
      break
  }
}

onMounted(() => {
  activityLogsStore.fetchLogs()
})
</script>

<template>
  <div class="relative min-h-full space-y-5 py-5 text-content">
    <!-- Header -->
    <PageHeader :title="$t('activityLog.title')" :description="$t('activityLog.description')">
      <template #actions>
        <button
          class="inline-flex items-center gap-2 rounded-lg border border-gray-300 bg-white px-4 py-2 text-body font-medium text-gray-700 transition-colors hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
          @click="handleExport"
        >
          <ArrowDownTrayIcon class="h-4 w-4" />
          {{ $t('activityLog.exportCsv') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('activityLog.pageGuideTitle')" :items="($tm('activityLog.pageGuide') as string[])" />

    <!-- Today's activity summary cards -->
    <div class="grid gap-2.5 tablet:grid-cols-3 desktop:grid-cols-6">
      <KpiCard
        v-for="card in summaryCards"
        :key="card.key"
        :label="`${$t('activityLog.today')} ${$t(card.label)}`"
        :value="String(actionCounts[card.key] ?? 0)"
      />
    </div>

    <!-- Filters -->
    <SectionCard :title="$t('activityLog.title')">
      <ActivityLogFilter
        :selected-action="selectedAction"
        :selected-date-range="selectedDateRange"
        :selected-user-id="selectedUserId"
        :search-query="searchQuery"
        :users="uniqueUsers"
        :custom-date-range="customDateRange"
        @update:action="handleActionUpdate"
        @update:date-range="handleDateRangeUpdate"
        @update:user-id="handleUserUpdate"
        @update:search-query="handleSearchUpdate"
        @reset="handleResetFilters"
      />
    </SectionCard>

    <!-- Timeline -->
    <SectionCard :title="$t('activityLog.today')">
      <ActivityLogTimeline
        :grouped-logs="groupedByDate"
        :is-loading="isLoading"
        @click-entity="handleEntityClick"
      />
    </SectionCard>

    <!-- Pagination -->
    <div
      v-if="totalPages > 1"
      class="mt-4 flex items-center justify-between"
    >
      <p class="text-body text-gray-600 dark:text-gray-400">
        {{ page * pageSize + 1 }}–{{ Math.min((page + 1) * pageSize, totalCount) }} / {{ totalCount }}
      </p>
      <div class="flex gap-2">
        <button
          class="btn-secondary text-body"
          :disabled="!hasPrevPage"
          @click="activityLogsStore.prevPage()"
        >
          {{ $t('activityLog.pagination.previous') }}
        </button>
        <button
          class="btn-secondary text-body"
          :disabled="!hasNextPage"
          @click="activityLogsStore.nextPage()"
        >
          {{ $t('activityLog.pagination.next') }}
        </button>
      </div>
    </div>
  </div>
</template>
