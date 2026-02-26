<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useMediaQuery } from '@vueuse/core'
import {
  ChartBarIcon,
  ChatBubbleLeftRightIcon,
  BellAlertIcon,
  FaceSmileIcon,
  MinusCircleIcon,
  FaceFrownIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import SentimentChart from '@/components/sociallistening/SentimentChart.vue'
import MentionCard from '@/components/sociallistening/MentionCard.vue'
import AlertManager from '@/components/sociallistening/AlertManager.vue'
import { useSocialListeningStore } from '@/stores/socialListening'
import type { SentimentType } from '@/types/socialListening'

const { t } = useI18n({ useScope: 'global' })
const store = useSocialListeningStore()
const isTablet = useMediaQuery('(min-width: 768px)')

const { report, loading, selectedPeriod, sentimentRatio } = storeToRefs(store)

// --- Tabs ---
type TabKey = 'overview' | 'mentions' | 'alerts'
const activeTab = ref<TabKey>('overview')

const tabs = computed(() => [
  { key: 'overview' as const, label: t('socialListening.tabs.overview'), icon: ChartBarIcon },
  { key: 'mentions' as const, label: t('socialListening.tabs.mentions'), icon: ChatBubbleLeftRightIcon },
  { key: 'alerts' as const, label: t('socialListening.tabs.alerts'), icon: BellAlertIcon },
])

// --- Period selector ---
type PeriodOption = { value: string; label: string }
const periods = computed<PeriodOption[]>(() => [
  { value: '7d', label: t('socialListening.period.7d') },
  { value: '14d', label: t('socialListening.period.14d') },
  { value: '30d', label: t('socialListening.period.30d') },
  { value: '90d', label: t('socialListening.period.90d') },
])

function handlePeriodChange(period: string) {
  store.fetchReport(period)
}

// --- Sentiment helpers ---
function sentimentColor(sentiment: SentimentType): string {
  if (sentiment === 'POSITIVE') return 'text-green-600 dark:text-green-400'
  if (sentiment === 'NEGATIVE') return 'text-red-600 dark:text-red-400'
  return 'text-gray-500 dark:text-gray-400'
}

function sentimentBadge(sentiment: SentimentType): string {
  if (sentiment === 'POSITIVE') return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400'
  if (sentiment === 'NEGATIVE') return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400'
  return 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-300'
}

// --- Alert handlers ---
function handleAddAlert(keyword: string) {
  store.addAlert(keyword)
}

function handleToggleAlert(id: number, enabled: boolean) {
  store.toggleAlert(id, enabled)
}

function handleDeleteAlert(id: number) {
  store.deleteAlert(id)
}

// --- Lifecycle ---
onMounted(() => {
  store.fetchReport()
})
</script>

<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('socialListening.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('socialListening.description') }}
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
      :title="$t('socialListening.pageGuideTitle')"
      :items="($tm('socialListening.pageGuide') as string[])"
    />

    <!-- Loading -->
    <LoadingSpinner v-if="loading" size="lg" :full-page="true" />

    <template v-else-if="report">
      <!-- Stats Row -->
      <div class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-4">
        <!-- Total mentions -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
            {{ $t('socialListening.totalMentions') }}
          </p>
          <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-white">
            {{ report.totalMentions.toLocaleString() }}
          </p>
        </div>

        <!-- Positive -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <FaceSmileIcon class="h-4 w-4 text-green-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
              {{ $t('socialListening.positive') }}
            </p>
          </div>
          <div class="mt-1 flex items-baseline gap-2">
            <span class="text-2xl font-bold text-green-600 dark:text-green-400">
              {{ report.sentimentBreakdown.positive }}
            </span>
            <span class="text-sm text-gray-400 dark:text-gray-500">{{ sentimentRatio.positive }}%</span>
          </div>
          <div class="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
            <div
              class="h-full rounded-full bg-green-500 transition-all duration-500"
              :style="{ width: sentimentRatio.positive + '%' }"
            />
          </div>
        </div>

        <!-- Neutral -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <MinusCircleIcon class="h-4 w-4 text-gray-400" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
              {{ $t('socialListening.neutral') }}
            </p>
          </div>
          <div class="mt-1 flex items-baseline gap-2">
            <span class="text-2xl font-bold text-gray-600 dark:text-gray-300">
              {{ report.sentimentBreakdown.neutral }}
            </span>
            <span class="text-sm text-gray-400 dark:text-gray-500">{{ sentimentRatio.neutral }}%</span>
          </div>
          <div class="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
            <div
              class="h-full rounded-full bg-gray-400 transition-all duration-500"
              :style="{ width: sentimentRatio.neutral + '%' }"
            />
          </div>
        </div>

        <!-- Negative -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-2">
            <FaceFrownIcon class="h-4 w-4 text-red-500" />
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">
              {{ $t('socialListening.negative') }}
            </p>
          </div>
          <div class="mt-1 flex items-baseline gap-2">
            <span class="text-2xl font-bold text-red-600 dark:text-red-400">
              {{ report.sentimentBreakdown.negative }}
            </span>
            <span class="text-sm text-gray-400 dark:text-gray-500">{{ sentimentRatio.negative }}%</span>
          </div>
          <div class="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
            <div
              class="h-full rounded-full bg-red-500 transition-all duration-500"
              :style="{ width: sentimentRatio.negative + '%' }"
            />
          </div>
        </div>
      </div>

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

      <!-- Overview Tab -->
      <div v-if="activeTab === 'overview'" class="space-y-6">
        <!-- Sentiment Trend Chart -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <h3 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('socialListening.sentimentTrend') }}
          </h3>
          <SentimentChart
            v-if="report.sentimentTrends.length > 0"
            :trends="report.sentimentTrends"
          />
          <p
            v-else
            class="py-8 text-center text-sm text-gray-400 dark:text-gray-500"
          >
            {{ $t('socialListening.noTrendData') }}
          </p>
        </div>

        <!-- Top Keywords -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <h3 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('socialListening.topKeywords') }}
          </h3>
          <div v-if="report.topKeywords.length > 0" class="space-y-3">
            <div
              v-for="(kw, idx) in report.topKeywords"
              :key="idx"
              class="flex items-center justify-between"
            >
              <div class="flex items-center gap-3">
                <span class="flex h-6 w-6 items-center justify-center rounded-full bg-gray-100 text-xs font-bold text-gray-500 dark:bg-gray-700 dark:text-gray-400">
                  {{ idx + 1 }}
                </span>
                <span class="text-sm font-medium text-gray-900 dark:text-gray-100">
                  {{ kw.keyword }}
                </span>
                <span
                  :class="['inline-block rounded-full px-2 py-0.5 text-xs font-medium', sentimentBadge(kw.sentiment)]"
                >
                  {{ kw.sentiment === 'POSITIVE' ? $t('socialListening.positive') : kw.sentiment === 'NEGATIVE' ? $t('socialListening.negative') : $t('socialListening.neutral') }}
                </span>
              </div>
              <div class="flex items-center gap-3">
                <div class="hidden w-24 tablet:block">
                  <div class="h-1.5 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
                    <div
                      class="h-full rounded-full transition-all duration-500"
                      :class="kw.sentiment === 'POSITIVE' ? 'bg-green-500' : kw.sentiment === 'NEGATIVE' ? 'bg-red-500' : 'bg-gray-400'"
                      :style="{ width: Math.min((kw.count / (report.topKeywords[0]?.count || 1)) * 100, 100) + '%' }"
                    />
                  </div>
                </div>
                <span :class="['text-sm font-semibold tabular-nums', sentimentColor(kw.sentiment)]">
                  {{ kw.count }}
                </span>
              </div>
            </div>
          </div>
          <p
            v-else
            class="py-6 text-center text-sm text-gray-400 dark:text-gray-500"
          >
            {{ $t('socialListening.noKeywords') }}
          </p>
        </div>
      </div>

      <!-- Mentions Tab -->
      <div v-else-if="activeTab === 'mentions'" class="space-y-4">
        <div v-if="report.topMentions.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <MentionCard
            v-for="mention in report.topMentions"
            :key="mention.id"
            :mention="mention"
          />
        </div>

        <!-- Empty state -->
        <div v-else class="py-12 text-center">
          <div class="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-gray-100 dark:bg-gray-700">
            <ChatBubbleLeftRightIcon class="h-8 w-8 text-gray-400 dark:text-gray-500" />
          </div>
          <h3 class="mb-2 text-lg font-medium text-gray-900 dark:text-gray-100">
            {{ $t('socialListening.noMentions') }}
          </h3>
          <p class="text-sm text-gray-500 dark:text-gray-400">
            {{ $t('socialListening.noMentionsDesc') }}
          </p>
        </div>
      </div>

      <!-- Alerts Tab -->
      <div v-else-if="activeTab === 'alerts'">
        <AlertManager
          :alerts="report.alerts"
          @add="handleAddAlert"
          @toggle="handleToggleAlert"
          @delete="handleDeleteAlert"
        />
      </div>
    </template>

    <!-- No report empty state -->
    <div v-else class="py-12 text-center">
      <p class="text-sm text-gray-500 dark:text-gray-400">
        {{ $t('socialListening.noData') }}
      </p>
    </div>
  </div>
</template>
