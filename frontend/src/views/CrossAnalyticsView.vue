<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import {
  EyeIcon,
  HeartIcon,
  TrophyIcon,
  DocumentDuplicateIcon,
  LightBulbIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import SummaryCard from '@/components/dashboard/SummaryCard.vue'
import PlatformShareChart from '@/components/crossanalytics/PlatformShareChart.vue'
import ContentCompareTable from '@/components/crossanalytics/ContentCompareTable.vue'
import AudienceOverlapChart from '@/components/crossanalytics/AudienceOverlapChart.vue'
import { useCrossAnalyticsStore } from '@/stores/crossAnalytics'
import { useNotification } from '@/composables/useNotification'

const { t } = useI18n({ useScope: 'global' })
const store = useCrossAnalyticsStore()
const notification = useNotification()

const {
  report,
  selectedPeriod,
  loading,
  activeTab,
  bestPlatform,
  totalViewsAllPlatforms,
} = storeToRefs(store)

// --- Period Selector ---
const periods = computed(() => [
  { value: '7d', label: t('crossAnalytics.period.7d') },
  { value: '30d', label: t('crossAnalytics.period.30d') },
  { value: '90d', label: t('crossAnalytics.period.90d') },
])

function handlePeriodChange(period: string) {
  store.fetchReport(period)
}

// --- Tabs ---
const tabs = computed(() => [
  { key: 'overview' as const, label: t('crossAnalytics.tabs.overview') },
  { key: 'contents' as const, label: t('crossAnalytics.tabs.contents') },
  { key: 'audience' as const, label: t('crossAnalytics.tabs.audience') },
])

// --- Computed summary values ---
const totalLikesAll = computed(() => {
  if (!report.value) return 0
  return report.value.platformSummaries.reduce((sum, p) => sum + p.totalLikes, 0)
})

const contentCount = computed(() => {
  if (!report.value) return 0
  return report.value.contents.length
})

const bestPlatformLabel = computed(() => {
  if (!bestPlatform.value) return '-'
  return t(`crossAnalytics.platform.${bestPlatform.value.platform}`)
})

// --- Lifecycle ---
onMounted(async () => {
  try {
    await store.fetchReport()
  } catch {
    notification.error(t('crossAnalytics.fetchError'))
  }
})
</script>

<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('crossAnalytics.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('crossAnalytics.description') }}
        </p>
      </div>
      <div class="flex gap-1">
        <button
          v-for="p in periods"
          :key="p.value"
          class="rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
          :class="
            selectedPeriod === p.value
              ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
              : 'text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700'
          "
          @click="handlePeriodChange(p.value)"
        >
          {{ p.label }}
        </button>
      </div>
    </div>

    <PageGuide
      :title="$t('crossAnalytics.pageGuideTitle')"
      :items="($tm('crossAnalytics.pageGuide') as string[])"
    />

    <!-- Tabs -->
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
      <nav class="-mb-px flex space-x-6">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="[
            activeTab === tab.key
              ? 'border-primary-500 text-primary-600 dark:border-primary-400 dark:text-primary-400'
              : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 dark:hover:border-gray-600 dark:hover:text-gray-300',
            'whitespace-nowrap border-b-2 px-1 py-3 text-sm font-medium transition-colors',
          ]"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- Loading Skeleton -->
    <div v-if="loading" class="space-y-6">
      <div class="grid grid-cols-2 gap-4 desktop:grid-cols-4">
        <div
          v-for="i in 4"
          :key="i"
          class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm"
        >
          <div class="h-4 w-24 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
          <div class="mt-3 h-8 w-20 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
          <div class="mt-2 h-3 w-16 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
        </div>
      </div>
      <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
        <div class="h-5 w-48 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
        <div class="mt-4 space-y-3">
          <div v-for="j in 4" :key="j" class="space-y-1.5">
            <div class="flex justify-between">
              <div class="h-4 w-20 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
              <div class="h-4 w-12 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
            </div>
            <div class="h-3 w-full animate-pulse rounded-full bg-gray-200 dark:bg-gray-700" />
          </div>
        </div>
      </div>
    </div>

    <!-- No Report State -->
    <div
      v-else-if="!report"
      class="flex flex-col items-center justify-center py-16 text-center"
    >
      <DocumentDuplicateIcon class="mb-4 h-12 w-12 text-gray-300 dark:text-gray-600" />
      <h3 class="mb-2 text-lg font-medium text-gray-900 dark:text-gray-100">
        {{ $t('crossAnalytics.noReport') }}
      </h3>
      <p class="text-sm text-gray-500 dark:text-gray-400">
        {{ $t('crossAnalytics.noReportDesc') }}
      </p>
    </div>

    <!-- Content loaded -->
    <template v-else>
      <!-- Overview Tab -->
      <div v-if="activeTab === 'overview'" class="space-y-6">
        <!-- Summary Cards -->
        <div class="grid grid-cols-2 gap-4 desktop:grid-cols-4">
          <SummaryCard
            :title="$t('crossAnalytics.overview.totalViews')"
            :value="totalViewsAllPlatforms"
            :icon="EyeIcon"
            color="blue"
          />
          <SummaryCard
            :title="$t('crossAnalytics.overview.totalLikes')"
            :value="totalLikesAll"
            :icon="HeartIcon"
            color="rose"
          />
          <SummaryCard
            :title="$t('crossAnalytics.overview.bestPlatform')"
            :value="bestPlatform?.totalViews ?? 0"
            :icon="TrophyIcon"
            color="purple"
          >
            <p class="mt-1 text-xs font-medium text-purple-600 dark:text-purple-400">
              {{ bestPlatformLabel }}
            </p>
          </SummaryCard>
          <SummaryCard
            :title="$t('crossAnalytics.overview.contentCount')"
            :value="contentCount"
            :icon="DocumentDuplicateIcon"
            color="green"
            format="number"
          >
            <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">
              {{ $t('crossAnalytics.items') }}
            </p>
          </SummaryCard>
        </div>

        <!-- Platform Share Chart -->
        <PlatformShareChart
          :summaries="report.platformSummaries"
          :total-views="totalViewsAllPlatforms"
        />

        <!-- Recommendations -->
        <div
          v-if="report.recommendations.length > 0"
          class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm"
        >
          <div class="mb-3 flex items-center gap-2">
            <LightBulbIcon class="h-5 w-5 text-yellow-500" />
            <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('crossAnalytics.overview.recommendations') }}
            </h3>
          </div>
          <ul class="space-y-2">
            <li
              v-for="(rec, idx) in report.recommendations"
              :key="idx"
              class="flex items-start gap-2 text-sm text-gray-600 dark:text-gray-400"
            >
              <span class="mt-1.5 h-1.5 w-1.5 flex-shrink-0 rounded-full bg-primary-400" />
              <span>{{ rec }}</span>
            </li>
          </ul>
        </div>
      </div>

      <!-- Contents Compare Tab -->
      <div v-else-if="activeTab === 'contents'" class="space-y-6">
        <ContentCompareTable :contents="report.contents" />
      </div>

      <!-- Audience Tab -->
      <div v-else-if="activeTab === 'audience'" class="space-y-6">
        <AudienceOverlapChart :overlaps="report.audienceOverlap" />
      </div>
    </template>
  </div>
</template>
