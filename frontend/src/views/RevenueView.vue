<template>
  <div class="relative">
    <!-- Header -->
    <PageHeader :title="$t('revenue.title')" :description="$t('revenue.description')">
      <template #actions>
        <button
          class="btn-secondary flex items-center gap-1.5 text-sm"
          @click="showAlertModal = true"
        >
          <BellIcon class="h-4 w-4" />
          {{ $t('revenue.alerts.title') }}
        </button>
        <button
          v-for="option in periodOptions"
          :key="option.value"
          class="rounded-lg px-4 py-2 text-sm font-medium transition-colors"
          :class="
            selectedPeriod === option.value
              ? 'bg-primary-600 text-white'
              : 'bg-white text-gray-700 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700'
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
        <div class="card">
          <div class="mb-4 flex items-center justify-between gap-4">
            <div>
              <h2 class="flex items-center gap-2 text-lg font-semibold text-gray-900 dark:text-gray-100">
                <SparklesIcon class="h-5 w-5 text-primary-600" />
                {{ $t('revenue.insights.title') }}
              </h2>
              <p class="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
                {{ $t('revenue.insights.description') }}
              </p>
            </div>
            <button
              class="btn-primary flex shrink-0 items-center gap-2 text-sm"
              :disabled="revenueStore.generateInsightLoading"
              @click="handleGenerateInsight"
            >
              <SparklesIcon class="h-4 w-4" />
              <span>{{ revenueStore.generateInsightLoading ? $t('revenue.insights.generating') : $t('revenue.insights.generate') }}</span>
              <span class="rounded-full bg-primary-500/30 px-1.5 py-0.5 text-xs">
                {{ $t('revenue.insights.generateHint') }}
              </span>
            </button>
          </div>

          <!-- 로딩 -->
          <div v-if="revenueStore.insightsLoading" class="flex items-center justify-center py-8">
            <div class="text-sm text-gray-500 dark:text-gray-400">{{ $t('revenue.loading') }}</div>
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
            <SparklesIcon class="mb-3 h-10 w-10 text-gray-300 dark:text-gray-600" />
            <p class="text-sm font-medium text-gray-500 dark:text-gray-400">{{ $t('revenue.insights.empty') }}</p>
            <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">{{ $t('revenue.insights.emptyHint') }}</p>
          </div>
        </div>

        <!-- Summary Cards -->
        <div class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-4">
          <!-- Total Revenue -->
          <div class="card border-t-4 border-primary-600">
            <div class="flex items-start justify-between">
              <div>
                <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('revenue.totalRevenue') }}</p>
                <p class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
                  {{ formatCurrency(revenueStore.summary.totalRevenue) }}
                </p>
              </div>
              <BanknotesIcon class="h-8 w-8 text-primary-600" />
            </div>
          </div>

          <!-- Monthly Growth -->
          <div
            class="card border-t-4"
            :class="
              revenueStore.summary.monthlyGrowth >= 0
                ? 'border-green-600'
                : 'border-red-600'
            "
          >
            <div class="flex items-start justify-between">
              <div>
                <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('revenue.monthlyGrowth') }}</p>
                <p class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
                  <span
                    :class="
                      revenueStore.summary.monthlyGrowth >= 0
                        ? 'text-green-600 dark:text-green-400'
                        : 'text-red-600 dark:text-red-400'
                    "
                  >
                    {{ revenueStore.summary.monthlyGrowth >= 0 ? '+' : '' }}{{
                      revenueStore.summary.monthlyGrowth.toFixed(1)
                    }}%
                  </span>
                </p>
              </div>
              <ArrowTrendingUpIcon
                v-if="revenueStore.summary.monthlyGrowth >= 0"
                class="h-8 w-8 text-green-600"
              />
              <ArrowTrendingDownIcon v-else class="h-8 w-8 text-red-600" />
            </div>
          </div>

          <!-- Average RPM -->
          <div class="card border-t-4 border-blue-600">
            <div class="flex items-start justify-between">
              <div>
                <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('revenue.avgRpm') }}</p>
                <p class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
                  ₩{{ revenueStore.summary.averageRPM.toLocaleString('ko-KR') }}
                </p>
              </div>
              <ChartBarIcon class="h-8 w-8 text-blue-600" />
            </div>
          </div>

          <!-- Top Platform -->
          <div class="card border-t-4 border-purple-600">
            <div class="flex items-start justify-between">
              <div>
                <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('revenue.topPlatform') }}</p>
                <div class="mt-2 flex items-center gap-2">
                  <span
                    class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
                    :style="{
                      backgroundColor: `${getPlatformColor(revenueStore.summary.topPlatform)}20`,
                      color: getPlatformColor(revenueStore.summary.topPlatform),
                    }"
                  >
                    {{ PLATFORM_CONFIG[revenueStore.summary.topPlatform].label }}
                  </span>
                </div>
                <p class="mt-1 text-lg font-semibold text-gray-900 dark:text-gray-100">
                  {{ formatCurrency(revenueStore.summary.topPlatformRevenue) }}
                </p>
              </div>
              <TrophyIcon class="h-8 w-8 text-purple-600" />
            </div>
          </div>
        </div>

        <!-- Revenue Trend Chart -->
        <div class="card">
          <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('revenue.revenueTrend') }}
          </h2>
          <div class="h-[400px]">
            <RevenueChart :data="filteredData" :period="selectedPeriod" />
          </div>
        </div>

        <!-- Platform Breakdown & Bar Chart -->
        <div class="grid grid-cols-1 gap-6 desktop:grid-cols-2">
          <!-- Platform Breakdown Doughnut -->
          <div class="card">
            <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('revenue.platformBreakdown') }}
            </h2>
            <div class="max-w-md">
              <RevenuePlatformBreakdown :data="revenueStore.platformBreakdown" />
            </div>
          </div>

          <!-- Platform Comparison Bar Chart -->
          <div class="card">
            <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('revenue.platformComparison') }}
            </h2>
            <div class="h-[300px]">
              <Bar :data="platformBarData" :options="barChartOptions" />
            </div>
          </div>
        </div>

        <!-- Monthly Revenue Table -->
        <div class="card">
          <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
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
        <template v-else-if="revenueStore.cpmRpmData">
          <div class="card">
            <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ $t('revenue.cpmRpmTitle') }}</h2>
            <p class="mb-4 text-sm text-gray-500 dark:text-gray-400">
              {{ $t('revenue.cpmRpmDesc') }}
            </p>
            <div v-if="revenueStore.cpmRpmData.platforms.length > 0" class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="border-b border-gray-200 dark:border-gray-700 text-xs uppercase text-gray-500 dark:text-gray-400">
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
            <div v-else class="flex items-center justify-center py-12 text-sm text-gray-400 dark:text-gray-500">
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
        <template v-else-if="revenueStore.brandDealData">
          <!-- 브랜드딜 요약 -->
          <div class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
            <div class="card border-t-4 border-orange-500">
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('revenue.brandDealTotalRevenue') }}</p>
              <p class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
                {{ formatCurrency(revenueStore.brandDealData.totalRevenueKrw) }}
              </p>
            </div>
            <div class="card border-t-4 border-orange-500">
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('revenue.brandDealCount') }}</p>
              <p class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
                {{ revenueStore.brandDealData.deals.length }}건
              </p>
            </div>
          </div>

          <!-- 브랜드딜 목록 -->
          <div class="card">
            <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">{{ $t('revenue.brandDealList') }}</h2>
            <div v-if="revenueStore.brandDealData.deals.length > 0" class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="border-b border-gray-200 dark:border-gray-700 text-xs uppercase text-gray-500 dark:text-gray-400">
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
                        class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
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
            <div v-else class="flex items-center justify-center py-12 text-sm text-gray-400 dark:text-gray-500">
              {{ $t('revenue.brandDealEmpty') }}
            </div>
          </div>
        </template>
      </template>

      <!-- AI 리포트 탭 (플레이스홀더) -->
      <template v-if="activeTab === 'aiReport'">
        <div class="card">
          <div class="flex flex-col items-center justify-center py-12 text-gray-400 dark:text-gray-500">
            <SparklesIcon class="h-12 w-12 mb-3" />
            <p class="text-sm font-medium">{{ $t('revenue.aiReportTitle') }}</p>
            <p class="text-xs mt-1">{{ $t('revenue.aiReportDesc') }}</p>
            <p class="text-xs mt-1 text-gray-400">{{ $t('revenue.comingSoon') }}</p>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRevenueStore } from '@/stores/revenue'
import {
  BanknotesIcon,
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  ChartBarIcon,
  TrophyIcon,
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
type RevenueTab = 'overview' | 'cpmRpm' | 'brandDeals' | 'aiReport'
const activeTab = ref<RevenueTab>('overview')

const revenueTabs: { key: RevenueTab; label: string }[] = [
  { key: 'overview', label: t('revenue.tabOverview') },
  { key: 'cpmRpm', label: t('revenue.tabCpmRpm') },
  { key: 'brandDeals', label: t('revenue.tabBrandDeals') },
  { key: 'aiReport', label: t('revenue.tabAiReport') },
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
  const labels = ['YouTube', 'TikTok', 'Instagram', 'Naver Clip']
  const data = revenueStore.platformBreakdown.map(p => p.revenue)
  const colors = [
    PLATFORM_CONFIG.YOUTUBE.color,
    '#000000',
    PLATFORM_CONFIG.INSTAGRAM.color,
    PLATFORM_CONFIG.NAVER_CLIP.color,
  ]

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
    NEGOTIATING: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
    CONFIRMED: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
    IN_PROGRESS: 'bg-primary-100 text-primary-800 dark:bg-primary-900/30 dark:text-primary-400',
    COMPLETED: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    CANCELLED: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
  }
  return classes[status] ?? 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-400'
}

onMounted(() => {
  revenueStore.fetchRevenue()
  revenueStore.fetchInsights()
})
</script>
