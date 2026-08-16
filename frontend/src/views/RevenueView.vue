<template>
  <div class="relative min-h-full space-y-5 py-5 text-content">
    <!-- Header -->
    <PageHeader :title="$t('revenue.title')" :description="$t('revenue.description')">
      <template #actions>
        <button
          class="btn-secondary flex items-center gap-1.5 text-body"
          @click="showAlertModal = true"
        >
          <BellIcon class="h-4 w-4" />
          {{ $t('revenue.alerts.title') }}
        </button>
        <button
          v-for="option in periodOptions"
          :key="option.value"
          class="rounded-lg border border-line-control px-3 py-1.5 text-body font-semibold transition-colors"
          :class="
            selectedPeriod === option.value
              ? 'bg-accent-dim text-accent'
              : 'bg-surface-input text-content-secondary hover:bg-surface-raised hover:text-content'
          "
          @click="selectedPeriod = option.value"
        >
          {{ option.label }}
        </button>
      </template>
    </PageHeader>

    <!-- 알림 설정 모달 -->
    <RevenueAlertModal v-model="showAlertModal" />

    <PageGuide :title="$t('revenue.pageGuideTitle')" :items="($tm('revenue.pageGuide') as string[])" />

    <div
      v-if="revenueStore.loadError"
      class="flex flex-wrap items-center gap-2 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2.5 text-body text-error-strong"
      role="alert"
    >
      <span class="min-w-0 flex-1">{{ $t('revenue.loadFailed') }}</span>
      <button
        type="button"
        class="shrink-0 rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold hover:bg-error-strong hover:text-surface-base"
        :disabled="revenueStore.loading"
        @click="revenueStore.fetchRevenue"
      >
        {{ $t('action.retry') }}
      </button>
    </div>

    <!-- Sub-tab Navigation -->
    <OTabs v-model="activeTab" :tabs="revenueTabs" class="mb-0" />

    <!-- Loading State -->
    <div v-if="revenueStore.loading" class="flex items-center justify-center py-12">
      <div class="text-gray-500 dark:text-gray-400">{{ $t('revenue.loading') }}</div>
    </div>

    <template v-else>
      <!-- 개요 탭 -->
      <template v-if="activeTab === 'overview'">
        <!-- AI 인사이트 섹션 -->
        <SectionCard :title="$t('revenue.insights.title')" :meta="$t('revenue.insights.description')">
          <div class="mb-4 flex items-center justify-between gap-4">
            <div>
              <h2 class="flex items-center gap-2 text-title font-semibold text-content">
                <SparklesIcon class="h-5 w-5 text-accent" />
                {{ $t('revenue.insights.title') }}
              </h2>
              <p class="mt-0.5 text-body text-content-tertiary">
                {{ $t('revenue.insights.description') }}
              </p>
            </div>
            <button
              class="btn-primary flex shrink-0 items-center gap-2 text-body"
              :disabled="revenueStore.generateInsightLoading"
              @click="handleGenerateInsight"
            >
              <SparklesIcon class="h-4 w-4" />
              <span>{{ revenueStore.generateInsightLoading ? $t('revenue.insights.generating') : $t('revenue.insights.generate') }}</span>
              <span class="rounded-full bg-primary-500/30 px-1.5 py-0.5 text-body-xs">
                {{ $t('revenue.insights.generateHint') }}
              </span>
            </button>
          </div>

          <!-- 로딩 -->
          <div v-if="revenueStore.insightsLoading" class="flex items-center justify-center py-8">
            <div class="text-body text-gray-500 dark:text-gray-400">{{ $t('revenue.loading') }}</div>
          </div>

          <div v-else-if="revenueStore.insightsError" class="rounded-lg border border-error-subtle bg-error-subtle p-4 text-body text-error-strong" role="alert">
            <p>{{ revenueStore.insightsError }}</p>
            <button type="button" class="mt-3 rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" @click="revenueStore.fetchInsights()">다시 시도</button>
          </div>

          <!-- 인사이트 목록 -->
          <div v-else-if="revenueStore.revenueInsights.length > 0" class="space-y-3">
            <RevenueInsightCard
              v-for="insight in revenueStore.revenueInsights"
              :key="insight.id"
              :insight="insight"
            />
          </div>

          <!-- 빈 상태 -->
          <div v-else class="flex flex-col items-center justify-center py-10 text-center">
            <SparklesIcon class="mb-3 h-10 w-10 text-content-quaternary" />
            <p class="text-body font-medium text-content-secondary">{{ $t('revenue.insights.empty') }}</p>
            <p class="mt-1 text-body-xs text-content-tertiary">{{ $t('revenue.insights.emptyHint') }}</p>
          </div>
        </SectionCard>

        <!-- Summary Cards -->
        <div class="grid gap-2.5 tablet:grid-cols-2 desktop:grid-cols-4">
          <!-- Total Revenue -->
          <KpiCard
            :label="$t('revenue.totalRevenue')"
            :value="formatCurrency(revenueStore.summary.totalRevenue)"
            :note="periodOptions.find((option) => option.value === selectedPeriod)?.label"
          />
          <!-- Monthly Growth -->
          <KpiCard
            :label="$t('revenue.monthlyGrowth')"
            :value="`${revenueStore.summary.monthlyGrowth >= 0 ? '+' : ''}${revenueStore.summary.monthlyGrowth.toFixed(1)}%`"
            :delta-variant="revenueStore.summary.monthlyGrowth >= 0 ? 'success' : 'error'"
            :note="periodOptions.find((option) => option.value === selectedPeriod)?.label"
          />
          <KpiCard
            :label="$t('revenue.avgRpm')"
            :value="`₩${revenueStore.summary.averageRPM.toLocaleString('ko-KR')}`"
          />
          <KpiCard
            :label="$t('revenue.topPlatform')"
            :value="PLATFORM_CONFIG[revenueStore.summary.topPlatform]?.label ?? revenueStore.summary.topPlatform"
            :note="formatCurrency(revenueStore.summary.topPlatformRevenue)"
          />
        </div>

        <!-- Revenue Trend Chart -->
        <SectionCard :title="$t('revenue.revenueTrend')" :meta="periodOptions.find((option) => option.value === selectedPeriod)?.label">
          <div class="h-[400px]">
            <RevenueChart :data="filteredData" :period="selectedPeriod" />
          </div>
        </SectionCard>

        <!-- Platform Breakdown & Bar Chart -->
        <div class="page-grid page-grid--split">
          <!-- Platform Breakdown Doughnut -->
          <div class="card">
            <h2 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('revenue.platformBreakdown') }}
            </h2>
            <div class="max-w-md">
              <RevenuePlatformBreakdown :data="revenueStore.platformBreakdown" />
            </div>
          </div>

          <!-- Platform Comparison Bar Chart -->
          <div class="card">
            <h2 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('revenue.platformComparison') }}
            </h2>
            <div class="h-[300px]">
              <Bar :data="platformBarData" :options="barChartOptions" />
            </div>
          </div>
        </div>

        <!-- Monthly Revenue Table -->
        <div class="card">
          <h2 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('revenue.monthlyRevenueTable') }}
          </h2>
          <RevenueTable :data="filteredData" />
        </div>
      </template>

      <!-- CPM·RPM 탭 -->
      <template v-if="activeTab === 'cpmRpm'">
        <div v-if="revenueStore.cpmRpmLoading" class="flex items-center justify-center py-12">
          <div class="text-gray-500 dark:text-gray-400">{{ $t('revenue.loading') }}</div>
        </div>
        <div v-else-if="revenueStore.cpmRpmError" class="rounded-lg border border-error-subtle bg-error-subtle p-4 text-body text-error-strong" role="alert">
          <p>{{ revenueStore.cpmRpmError }}</p>
          <button type="button" class="mt-3 rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" @click="revenueStore.fetchCpmRpm()">다시 시도</button>
        </div>
        <template v-else-if="revenueStore.cpmRpmData">
          <div class="card">
            <h2 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">{{ $t('revenue.cpmRpmTitle') }}</h2>
            <p class="mb-4 text-body text-gray-500 dark:text-gray-400">
              {{ $t('revenue.cpmRpmDesc') }}
            </p>
            <div v-if="revenueStore.cpmRpmData.platforms.length > 0" class="overflow-x-auto">
              <table class="w-full text-body">
                <thead>
                  <tr class="border-b border-gray-200 dark:border-gray-700 text-body-xs uppercase text-gray-500 dark:text-gray-400">
                    <th class="px-4 py-3 text-left font-medium">{{ $t('revenue.thPlatform') }}</th>
                    <th class="px-4 py-3 text-right font-medium">{{ $t('revenue.thCpm') }}</th>
                    <th class="px-4 py-3 text-right font-medium">{{ $t('revenue.thRpm') }}</th>
                    <th class="px-4 py-3 text-right font-medium">{{ $t('revenue.thImpressions') }}</th>
                    <th class="px-4 py-3 text-right font-medium">{{ $t('revenue.thViews') }}</th>
                    <th class="px-4 py-3 text-right font-medium">{{ $t('revenue.thRevenue') }}</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
                  <tr
                    v-for="item in revenueStore.cpmRpmData.platforms"
                    :key="item.platform"
                    class="hover:bg-gray-50 dark:hover:bg-gray-700/50"
                  >
                    <td class="px-4 py-3 font-medium text-gray-900 dark:text-gray-100">
                      <div class="flex items-center gap-2">
                        <span
                          class="h-2 w-2 rounded-full"
                          :style="{ backgroundColor: getPlatformColor(item.platform) }"
                        />
                        {{ PLATFORM_CONFIG[item.platform as keyof typeof PLATFORM_CONFIG]?.label ?? item.platform }}
                      </div>
                    </td>
                    <td class="px-4 py-3 text-right text-gray-700 dark:text-gray-300">
                      ₩{{ item.cpm.toLocaleString('ko-KR', { minimumFractionDigits: 2 }) }}
                    </td>
                    <td class="px-4 py-3 text-right text-gray-700 dark:text-gray-300">
                      ₩{{ item.rpm.toLocaleString('ko-KR', { minimumFractionDigits: 2 }) }}
                    </td>
                    <td class="px-4 py-3 text-right text-gray-700 dark:text-gray-300">
                      {{ item.impressions.toLocaleString() }}
                    </td>
                    <td class="px-4 py-3 text-right text-gray-700 dark:text-gray-300">
                      {{ item.views.toLocaleString() }}
                    </td>
                    <td class="px-4 py-3 text-right font-medium text-gray-900 dark:text-gray-100">
                      {{ formatCurrency(item.revenueMicro / 1_000_000) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="flex items-center justify-center py-12 text-body text-gray-400 dark:text-gray-500">
              {{ $t('revenue.cpmRpmEmpty') }}
            </div>
          </div>
        </template>
      </template>

      <!-- 브랜드딜 탭 -->
      <template v-if="activeTab === 'brandDeals'">
        <div v-if="revenueStore.brandDealLoading" class="flex items-center justify-center py-12">
          <div class="text-gray-500 dark:text-gray-400">{{ $t('revenue.loading') }}</div>
        </div>
        <div v-else-if="revenueStore.brandDealError" class="rounded-lg border border-error-subtle bg-error-subtle p-4 text-body text-error-strong" role="alert">
          <p>{{ revenueStore.brandDealError }}</p>
          <button type="button" class="mt-3 rounded-md border border-error-strong px-2 py-1 text-body-xs font-semibold" @click="revenueStore.fetchBrandDealRevenue()">다시 시도</button>
        </div>
        <template v-else-if="revenueStore.brandDealData">
          <!-- 브랜드딜 요약 -->
          <div class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
            <div class="card border-t-4 border-warning">
              <p class="text-body text-gray-600 dark:text-gray-400">{{ $t('revenue.brandDealTotalRevenue') }}</p>
              <p class="mt-2 text-h1 font-bold text-gray-900 dark:text-gray-100">
                {{ formatCurrency(revenueStore.brandDealData.totalRevenueKrw) }}
              </p>
            </div>
            <div class="card border-t-4 border-warning">
              <p class="text-body text-gray-600 dark:text-gray-400">{{ $t('revenue.brandDealCount') }}</p>
              <p class="mt-2 text-h1 font-bold text-gray-900 dark:text-gray-100">
                {{ revenueStore.brandDealData.deals.length }}건
              </p>
            </div>
          </div>

          <!-- 브랜드딜 목록 -->
          <div class="card">
            <h2 class="mb-4 text-title font-semibold text-gray-900 dark:text-gray-100">{{ $t('revenue.brandDealList') }}</h2>
            <div v-if="revenueStore.brandDealData.deals.length > 0" class="overflow-x-auto">
              <table class="w-full text-body">
                <thead>
                  <tr class="border-b border-gray-200 dark:border-gray-700 text-body-xs uppercase text-gray-500 dark:text-gray-400">
                    <th class="px-4 py-3 text-left font-medium">{{ $t('revenue.thBrand') }}</th>
                    <th class="px-4 py-3 text-right font-medium">{{ $t('revenue.thAmount') }}</th>
                    <th class="px-4 py-3 text-center font-medium">{{ $t('revenue.thStatus') }}</th>
                    <th class="px-4 py-3 text-left font-medium">{{ $t('revenue.thPlatform') }}</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
                  <tr
                    v-for="deal in revenueStore.brandDealData.deals"
                    :key="deal.id"
                    class="hover:bg-gray-50 dark:hover:bg-gray-700/50"
                  >
                    <td class="px-4 py-3 font-medium text-gray-900 dark:text-gray-100">
                      {{ deal.brandName }}
                    </td>
                    <td class="px-4 py-3 text-right text-gray-700 dark:text-gray-300">
                      {{ formatCurrency(deal.dealValueKrw) }}
                    </td>
                    <td class="px-4 py-3 text-center">
                      <span
                        class="inline-flex items-center rounded-full px-2 py-0.5 text-caption"
                        :class="dealStatusClass(deal.status)"
                      >
                        {{ dealStatusLabel(deal.status) }}
                      </span>
                    </td>
                    <td class="px-4 py-3 text-gray-700 dark:text-gray-300">
                      {{ deal.platform ? (PLATFORM_CONFIG[deal.platform as keyof typeof PLATFORM_CONFIG]?.label ?? deal.platform) : '-' }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="flex items-center justify-center py-12 text-body text-gray-400 dark:text-gray-500">
              {{ $t('revenue.brandDealEmpty') }}
            </div>
          </div>
        </template>
      </template>

    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRevenueStore } from '@/stores/revenue'
import {
  SparklesIcon,
  BellIcon,
} from '@heroicons/vue/24/outline'
import { PLATFORM_CONFIG } from '@/types/channel'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import OTabs from '@/components/ui/OTabs.vue'
import RevenueChart from '@/components/revenue/RevenueChart.vue'
import RevenuePlatformBreakdown from '@/components/revenue/RevenuePlatformBreakdown.vue'
import RevenueTable from '@/components/revenue/RevenueTable.vue'
import RevenueInsightCard from '@/components/revenue/RevenueInsightCard.vue'
import RevenueAlertModal from '@/components/revenue/RevenueAlertModal.vue'
import KpiCard from '@/components/redesign/KpiCard.vue'
import SectionCard from '@/components/redesign/SectionCard.vue'
import { Bar } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend,
  type TooltipItem,
} from 'chart.js'

ChartJS.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend)

const { t } = useI18n()
const revenueStore = useRevenueStore()

// 알림 모달
const showAlertModal = ref(false)

// AI 인사이트 생성
async function handleGenerateInsight() {
  try {
    await revenueStore.generateInsight()
  } catch (e) {
    console.error(t('revenue.insights.generateFailed'), e)
  }
}

// ----- 탭 -----
type RevenueTab = 'overview' | 'cpmRpm' | 'brandDeals'
const activeTab = ref<RevenueTab>('overview')

const revenueTabs: { key: RevenueTab; label: string }[] = [
  { key: 'overview', label: t('revenue.tabOverview') },
  { key: 'cpmRpm', label: t('revenue.tabCpmRpm') },
  { key: 'brandDeals', label: t('revenue.tabBrandDeals') },
]

watch(activeTab, (tab) => {
  if (tab === 'cpmRpm') {
    revenueStore.fetchCpmRpm()
  } else if (tab === 'brandDeals') {
    revenueStore.fetchBrandDealRevenue()
  }
})

// ----- 기간 필터 -----
const periodOptions = [
  { value: '1', label: t('revenue.periodThisMonth') },
  { value: '3', label: t('revenue.period3Months') },
  { value: '6', label: t('revenue.period6Months') },
  { value: '12', label: t('revenue.period1Year') },
]

const selectedPeriod = ref('12')

const filteredData = computed(() => {
  const months = parseInt(selectedPeriod.value)
  return revenueStore.monthlyRevenue.slice(-months)
})

const platformBarData = computed(() => {
  const breakdown = revenueStore.platformBreakdown
  const labels = breakdown.map(item =>
    PLATFORM_CONFIG[item.platform as keyof typeof PLATFORM_CONFIG]?.label ?? item.platform,
  )
  const data = breakdown.map(item => item.revenue)
  const colors = breakdown.map(item => getPlatformColor(item.platform))

  return {
    labels,
    datasets: [
      {
        label: t('revenue.chart.totalRevenue'),
        data,
        backgroundColor: colors,
      },
    ],
  }
})

const barChartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      display: false,
    },
    tooltip: {
      callbacks: {
        label: (context: TooltipItem<'bar'>) => {
          const value = context.parsed.y ?? 0
          return `수익: ₩${value.toLocaleString('ko-KR')}`
        },
      },
    },
  },
  scales: {
    x: {
      grid: {
        display: false,
      },
      ticks: {
        color: document.documentElement.classList.contains('dark') ? '#9CA3AF' : '#6B7280',
      },
    },
    y: {
      beginAtZero: true,
      grid: {
        color: document.documentElement.classList.contains('dark') ? '#374151' : '#E5E7EB',
      },
      ticks: {
        color: document.documentElement.classList.contains('dark') ? '#9CA3AF' : '#6B7280',
        callback: (value: string | number) => `₩${(Number(value) / 1000000).toFixed(1)}M`,
      },
    },
  },
}))

function getPlatformColor(platform: string): string {
  const platformKey = platform as keyof typeof PLATFORM_CONFIG
  return PLATFORM_CONFIG[platformKey]?.color || '#6B7280'
}

function formatCurrency(value: number): string {
  if (value >= 100000000) {
    return `₩${(value / 100000000).toFixed(1)}억`
  } else if (value >= 10000) {
    return `₩${(value / 10000).toFixed(0)}만`
  }
  return `₩${value.toLocaleString('ko-KR')}`
}

function dealStatusLabel(status: string): string {
  const labels: Record<string, string> = {
    NEGOTIATING: t('revenue.statusNegotiating'),
    CONFIRMED: t('revenue.statusConfirmed'),
    IN_PROGRESS: t('revenue.statusInProgress'),
    COMPLETED: t('revenue.statusCompleted'),
    CANCELLED: t('revenue.statusCancelled'),
  }
  return labels[status] ?? status
}

function dealStatusClass(status: string): string {
  const classes: Record<string, string> = {
    NEGOTIATING: 'bg-warning-subtle text-warning-strong',
    CONFIRMED: 'bg-info-subtle text-info-strong',
    IN_PROGRESS: 'bg-primary-100 text-primary-800 dark:bg-primary-900/30 dark:text-primary-400',
    COMPLETED: 'bg-success-subtle text-success-strong',
    CANCELLED: 'bg-error-subtle text-error-strong',
  }
  return classes[status] ?? 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-400'
}

onMounted(() => {
  revenueStore.fetchRevenue()
  revenueStore.fetchInsights()
})
</script>
