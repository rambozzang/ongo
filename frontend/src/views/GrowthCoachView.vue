<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ $t('growthCoach.title') }}
          </h1>
          <span
            v-if="session"
            class="inline-flex items-center gap-1 rounded-full bg-green-100 dark:bg-green-900/30 px-2.5 py-0.5 text-xs font-semibold text-green-700 dark:text-green-400"
          >
            <ArrowTrendingUpIcon class="h-3.5 w-3.5" />
            {{ $t('growthCoach.overallGrowthRate') }}: +{{ session.overallGrowthRate }}%
          </span>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('growthCoach.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <span class="text-xs text-gray-400 dark:text-gray-500">
          {{ $t('growthCoach.creditsRemaining') }}: {{ creditBalance }}
        </span>
        <button
          class="btn-primary inline-flex items-center gap-2"
          :disabled="generatingReport"
          @click="handleGenerateReport"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ generatingReport ? $t('growthCoach.generatingReport') : $t('growthCoach.generateReport') }}
        </button>
      </div>
    </div>

    <PageGuide
      :title="$t('growthCoach.pageGuideTitle')"
      :items="($tm('growthCoach.pageGuide') as string[])"
    />

    <!-- Tabs -->
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
      <nav :class="isTablet ? '-mb-px flex gap-6' : '-mb-px flex'">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          :class="[
            isTablet
              ? 'inline-flex items-center gap-2 border-b-2 px-1 py-3 text-sm font-medium transition-colors'
              : 'flex-1 border-b-2 px-1 py-3 text-center text-xs font-medium transition-colors',
            activeTab === tab.key
              ? 'border-primary-500 text-primary-600 dark:text-primary-400'
              : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 dark:hover:border-gray-600 dark:hover:text-gray-300',
          ]"
          @click="activeTab = tab.key"
        >
          <component :is="tab.icon" :class="isTablet ? 'h-5 w-5' : 'mx-auto mb-1 h-5 w-5'" />
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-12">
      <div class="h-8 w-8 animate-spin rounded-full border-2 border-primary-300 border-t-primary-600" />
    </div>

    <!-- Tab: Overview -->
    <div v-else-if="activeTab === 'overview'" class="space-y-6">
      <template v-if="currentReport">
        <!-- Overall Score + Growth Summary -->
        <div class="grid gap-4 tablet:grid-cols-2 desktop:grid-cols-4">
          <!-- Score Circle -->
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm flex flex-col items-center justify-center">
            <div class="relative mb-2">
              <svg class="h-24 w-24" viewBox="0 0 96 96">
                <circle
                  cx="48" cy="48" r="40"
                  fill="none"
                  class="stroke-gray-200 dark:stroke-gray-700"
                  stroke-width="6"
                />
                <circle
                  cx="48" cy="48" r="40"
                  fill="none"
                  :class="overviewScoreStrokeClass"
                  stroke-width="6"
                  stroke-linecap="round"
                  :stroke-dasharray="overviewCircumference"
                  :stroke-dashoffset="overviewScoreOffset"
                  transform="rotate(-90 48 48)"
                  class="transition-all duration-700"
                />
              </svg>
              <span
                class="absolute inset-0 flex items-center justify-center text-2xl font-bold"
                :class="overviewScoreTextClass"
              >
                {{ currentReport.overallScore }}
              </span>
            </div>
            <p class="text-sm font-medium text-gray-700 dark:text-gray-300">
              {{ $t('growthCoach.overview.overallScore') }}
            </p>
            <p class="text-xs text-gray-400 dark:text-gray-500">{{ $t('growthCoach.overview.scoreOutOf') }}</p>
          </div>

          <!-- Subscriber Growth -->
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
            <div class="flex items-center gap-2 mb-2">
              <UsersIcon class="h-5 w-5 text-blue-500 dark:text-blue-400" />
              <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('growthCoach.overview.subscriberGrowth') }}
              </h4>
            </div>
            <p
              class="text-2xl font-bold"
              :class="currentReport.subscriberGrowth >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
            >
              {{ currentReport.subscriberGrowth > 0 ? '+' : '' }}{{ formatNumber(currentReport.subscriberGrowth) }}
            </p>
            <p class="text-xs text-gray-400 dark:text-gray-500">
              {{ $t('growthCoach.overview.subscribers') }}
            </p>
          </div>

          <!-- Views Growth -->
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
            <div class="flex items-center gap-2 mb-2">
              <EyeIcon class="h-5 w-5 text-purple-500 dark:text-purple-400" />
              <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('growthCoach.overview.viewsGrowth') }}
              </h4>
            </div>
            <p
              class="text-2xl font-bold"
              :class="currentReport.viewsGrowth >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
            >
              {{ currentReport.viewsGrowth > 0 ? '+' : '' }}{{ formatNumber(currentReport.viewsGrowth) }}
            </p>
            <p class="text-xs text-gray-400 dark:text-gray-500">
              {{ $t('growthCoach.overview.views') }}
            </p>
          </div>

          <!-- Engagement Change -->
          <div class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm">
            <div class="flex items-center gap-2 mb-2">
              <HeartIcon class="h-5 w-5 text-pink-500 dark:text-pink-400" />
              <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">
                {{ $t('growthCoach.overview.engagementChange') }}
              </h4>
            </div>
            <p
              class="text-2xl font-bold"
              :class="currentReport.engagementChange >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'"
            >
              {{ currentReport.engagementChange > 0 ? '+' : '' }}{{ currentReport.engagementChange.toFixed(1) }}%
            </p>
          </div>
        </div>

        <!-- Top Insights -->
        <div v-if="highImpactInsights.length > 0">
          <h3 class="mb-3 text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('growthCoach.overview.topInsights') }}
          </h3>
          <div class="grid gap-3 tablet:grid-cols-2 desktop:grid-cols-3">
            <InsightCard
              v-for="insight in highImpactInsights.slice(0, 3)"
              :key="insight.id"
              :insight="insight"
            />
          </div>
        </div>

        <!-- Quick Action Items -->
        <div v-if="currentReport.actionItems.length > 0">
          <h3 class="mb-3 text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('growthCoach.overview.quickActions') }}
          </h3>
          <div class="space-y-2">
            <div
              v-for="action in currentReport.actionItems.filter(a => a.status !== 'DONE' && a.status !== 'SKIPPED').slice(0, 3)"
              :key="action.id"
              class="flex items-center gap-3 rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-3 shadow-sm"
            >
              <button
                class="flex-shrink-0"
                @click="handleUpdateAction(action.id, 'DONE')"
              >
                <div class="h-5 w-5 rounded-full border-2 border-gray-300 dark:border-gray-600 hover:border-green-400 dark:hover:border-green-500 transition-colors" />
              </button>
              <div class="min-w-0 flex-1">
                <p class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ action.title }}</p>
                <p class="text-xs text-gray-500 dark:text-gray-400">{{ action.estimatedImpact }}</p>
              </div>
              <span
                class="flex-shrink-0 rounded-full px-2 py-0.5 text-xs font-medium"
                :class="priorityBadgeClass(action.priority)"
              >
                {{ $t(`growthCoach.actions.priority${action.priority.charAt(0) + action.priority.slice(1).toLowerCase()}`) }}
              </span>
            </div>
          </div>
        </div>
      </template>

      <!-- Empty state -->
      <div v-else class="py-12 text-center">
        <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
          <ChartBarIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
        </div>
        <h3 class="mb-2 text-lg font-medium text-gray-900 dark:text-gray-100">
          {{ $t('growthCoach.overview.noReport') }}
        </h3>
        <p class="mb-6 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('growthCoach.overview.noReportDesc') }}
        </p>
        <button
          class="btn-primary inline-flex items-center gap-2"
          :disabled="generatingReport"
          @click="handleGenerateReport"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ $t('growthCoach.generateReport') }}
        </button>
      </div>
    </div>

    <!-- Tab: Goals -->
    <div v-else-if="activeTab === 'goals'">
      <template v-if="goals.length > 0">
        <div class="grid gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <GoalProgressCard
            v-for="goal in goals"
            :key="goal.id"
            :goal="goal"
          />
        </div>
      </template>

      <!-- Empty state -->
      <div v-else class="py-12 text-center">
        <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
          <FlagIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
        </div>
        <h3 class="mb-2 text-lg font-medium text-gray-900 dark:text-gray-100">
          {{ $t('growthCoach.goals.noGoals') }}
        </h3>
        <p class="text-sm text-gray-500 dark:text-gray-400">
          {{ $t('growthCoach.goals.noGoalsDesc') }}
        </p>
      </div>
    </div>

    <!-- Tab: Reports -->
    <div v-else-if="activeTab === 'reports'" class="space-y-6">
      <!-- Current Report -->
      <template v-if="currentReport">
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('growthCoach.reports.currentReport') }}
        </h3>
        <WeeklyReportCard
          :report="currentReport"
          @update-action="handleUpdateAction"
        />
      </template>

      <!-- Past Reports -->
      <div v-if="session && session.pastReports.length > 0">
        <button
          class="mb-4 flex items-center gap-2 text-sm font-medium text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200 transition-colors"
          @click="showPastReports = !showPastReports"
        >
          <ChevronDownIcon
            class="h-4 w-4 transition-transform"
            :class="{ 'rotate-180': showPastReports }"
          />
          {{ showPastReports
            ? $t('growthCoach.reports.hidePastReports')
            : $t('growthCoach.reports.showPastReports', { count: session.pastReports.length })
          }}
        </button>

        <div v-if="showPastReports" class="space-y-4">
          <WeeklyReportCard
            v-for="report in session.pastReports"
            :key="report.id"
            :report="report"
            @update-action="handleUpdateAction"
          />
        </div>
      </div>

      <!-- Empty state -->
      <div v-if="!currentReport" class="py-12 text-center">
        <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
          <DocumentTextIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
        </div>
        <h3 class="mb-2 text-lg font-medium text-gray-900 dark:text-gray-100">
          {{ $t('growthCoach.reports.noReport') }}
        </h3>
        <p class="mb-6 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('growthCoach.reports.noReportDesc') }}
        </p>
        <button
          class="btn-primary inline-flex items-center gap-2"
          :disabled="generatingReport"
          @click="handleGenerateReport"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ $t('growthCoach.generateReport') }}
        </button>
      </div>
    </div>

    <!-- Tab: Insights -->
    <div v-else-if="activeTab === 'insights'">
      <!-- Impact Filter -->
      <div class="mb-4 flex gap-2">
        <button
          v-for="filter in insightFilters"
          :key="filter.key"
          class="rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
          :class="
            insightFilter === filter.key
              ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
              : 'text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700'
          "
          @click="insightFilter = filter.key"
        >
          {{ filter.label }}
        </button>
      </div>

      <template v-if="filteredInsights.length > 0">
        <div class="grid gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <InsightCard
            v-for="insight in filteredInsights"
            :key="insight.id"
            :insight="insight"
          />
        </div>
      </template>

      <!-- Empty state -->
      <div v-else class="py-12 text-center">
        <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
          <LightBulbIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
        </div>
        <h3 class="mb-2 text-lg font-medium text-gray-900 dark:text-gray-100">
          {{ $t('growthCoach.insights.noInsights') }}
        </h3>
        <p class="text-sm text-gray-500 dark:text-gray-400">
          {{ $t('growthCoach.insights.noInsightsDesc') }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useMediaQuery } from '@vueuse/core'
import {
  SparklesIcon,
  ArrowTrendingUpIcon,
  UsersIcon,
  EyeIcon,
  HeartIcon,
  ChartBarIcon,
  FlagIcon,
  DocumentTextIcon,
  LightBulbIcon,
  ChevronDownIcon,
  ClipboardDocumentListIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import GoalProgressCard from '@/components/growthcoach/GoalProgressCard.vue'
import WeeklyReportCard from '@/components/growthcoach/WeeklyReportCard.vue'
import InsightCard from '@/components/growthcoach/InsightCard.vue'
import { useGrowthCoachStore } from '@/stores/growthCoach'
import { useCredit } from '@/composables/useCredit'
import { useNotification } from '@/composables/useNotification'
import type { ActionStatus } from '@/types/growthCoach'

const { t } = useI18n({ useScope: 'global' })
const store = useGrowthCoachStore()
const { balance: creditBalance, checkAndUse } = useCredit()
const notification = useNotification()
const isTablet = useMediaQuery('(min-width: 768px)')

const {
  session,
  loading,
  generatingReport,
  activeTab,
  goals,
  currentReport,
  insights,
  highImpactInsights,
} = storeToRefs(store)

const showPastReports = ref(false)
const insightFilter = ref<'ALL' | 'HIGH' | 'MEDIUM' | 'LOW'>('ALL')

// Tabs
const tabs = computed(() => [
  { key: 'overview' as const, label: t('growthCoach.tabs.overview'), icon: ChartBarIcon },
  { key: 'goals' as const, label: t('growthCoach.tabs.goals'), icon: FlagIcon },
  { key: 'reports' as const, label: t('growthCoach.tabs.reports'), icon: ClipboardDocumentListIcon },
  { key: 'insights' as const, label: t('growthCoach.tabs.insights'), icon: LightBulbIcon },
])

// Insight filters
const insightFilters = computed(() => [
  { key: 'ALL' as const, label: t('growthCoach.insights.filterAll') },
  { key: 'HIGH' as const, label: t('growthCoach.insights.filterHigh') },
  { key: 'MEDIUM' as const, label: t('growthCoach.insights.filterMedium') },
  { key: 'LOW' as const, label: t('growthCoach.insights.filterLow') },
])

const filteredInsights = computed(() => {
  if (insightFilter.value === 'ALL') return insights.value
  return insights.value.filter((i) => i.impact === insightFilter.value)
})

// Overview score circle
const overviewCircumference = 2 * Math.PI * 40

const overviewScoreOffset = computed(() => {
  const score = currentReport.value?.overallScore ?? 0
  return overviewCircumference - (score / 100) * overviewCircumference
})

const overviewScoreStrokeClass = computed(() => {
  const score = currentReport.value?.overallScore ?? 0
  if (score >= 80) return 'stroke-green-500 dark:stroke-green-400'
  if (score >= 60) return 'stroke-yellow-500 dark:stroke-yellow-400'
  return 'stroke-red-500 dark:stroke-red-400'
})

const overviewScoreTextClass = computed(() => {
  const score = currentReport.value?.overallScore ?? 0
  if (score >= 80) return 'text-green-600 dark:text-green-400'
  if (score >= 60) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
})

function priorityBadgeClass(priority: string): string {
  switch (priority) {
    case 'HIGH': return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
    case 'MEDIUM': return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400'
    case 'LOW': return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
    default: return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400'
  }
}

function formatNumber(num: number): string {
  if (Math.abs(num) >= 1_000_000) return `${(num / 1_000_000).toFixed(1)}M`
  if (Math.abs(num) >= 1_000) return `${(num / 1_000).toFixed(1)}K`
  return num.toLocaleString()
}

async function handleGenerateReport() {
  const canUse = await checkAndUse(10, t('growthCoach.title'))
  if (!canUse) return

  try {
    await store.generateReport()
  } catch {
    notification.error(t('growthCoach.generateError'))
  }
}

async function handleUpdateAction(actionId: number, status: ActionStatus) {
  if (!currentReport.value) return
  await store.updateActionStatus(currentReport.value.id, actionId, status)
}

onMounted(() => {
  store.fetchSession()
})
</script>
