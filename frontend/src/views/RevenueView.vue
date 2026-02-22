<template>
  <div class="space-y-6">
    <!-- Header -->
    <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">수익 분석</h1>
      <div class="flex gap-2">
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
      </div>
    </div>

    <PageGuide title="수익 분석" :items="[
      '주간/월간/연간 기간 필터를 전환하여 원하는 구간의 수익을 분석하세요',
      '상단 요약 카드에서 총 수익(원화), 월간 성장률(초록=증가/빨간=감소), 평균 RPM을 확인하세요',
      'CPM·RPM 탭에서 플랫폼별 1000회 노출/조회당 수익을 비교하세요',
      '브랜드딜 탭에서 브랜드 협업 수익을 확인하세요',
    ]" />

    <!-- Sub-tab Navigation -->
    <div class="border-b border-gray-200 dark:border-gray-700">
      <nav class="-mb-px flex space-x-6">
        <button
          v-for="tab in revenueTabs"
          :key="tab.key"
          @click="activeTab = tab.key"
          :class="[
            activeTab === tab.key
              ? 'border-primary-500 text-primary-600 dark:border-primary-400 dark:text-primary-400'
              : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400',
            'whitespace-nowrap border-b-2 px-1 py-3 text-sm font-medium',
          ]"
        >
          {{ tab.label }}
        </button>
      </nav>
    </div>

    <!-- Loading State -->
    <div v-if="revenueStore.loading" class="flex items-center justify-center py-12">
      <div class="text-gray-500 dark:text-gray-400">로딩 중...</div>
    </div>

    <template v-else>
      <!-- 개요 탭 -->
      <template v-if="activeTab === 'overview'">
        <!-- Summary Cards -->
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <!-- Total Revenue -->
          <div class="card border-t-4 border-primary-600">
            <div class="flex items-start justify-between">
              <div>
                <p class="text-sm text-gray-600 dark:text-gray-400">총 수익</p>
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
                <p class="text-sm text-gray-600 dark:text-gray-400">월간 성장률</p>
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
                <p class="text-sm text-gray-600 dark:text-gray-400">평균 RPM</p>
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
                <p class="text-sm text-gray-600 dark:text-gray-400">최고 수익 플랫폼</p>
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
            수익 추이
          </h2>
          <div class="h-[400px]">
            <RevenueChart :data="filteredData" :period="selectedPeriod" />
          </div>
        </div>

        <!-- Platform Breakdown & Bar Chart -->
        <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
          <!-- Platform Breakdown Doughnut -->
          <div class="card">
            <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
              플랫폼별 수익 비율
            </h2>
            <div class="mx-auto max-w-md">
              <RevenuePlatformBreakdown :data="revenueStore.platformBreakdown" />
            </div>
          </div>

          <!-- Platform Comparison Bar Chart -->
          <div class="card">
            <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
              플랫폼 비교
            </h2>
            <div class="h-[300px]">
              <Bar :data="platformBarData" :options="barChartOptions" />
            </div>
          </div>
        </div>

        <!-- Monthly Revenue Table -->
        <div class="card">
          <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
            월별 수익 내역
          </h2>
          <RevenueTable :data="filteredData" />
        </div>
      </template>

      <!-- CPM·RPM 탭 -->
      <template v-if="activeTab === 'cpmRpm'">
        <div v-if="revenueStore.cpmRpmLoading" class="flex items-center justify-center py-12">
          <div class="text-gray-500 dark:text-gray-400">로딩 중...</div>
        </div>
        <template v-else-if="revenueStore.cpmRpmData">
          <div class="card">
            <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">플랫폼별 CPM / RPM</h2>
            <p class="mb-4 text-sm text-gray-500 dark:text-gray-400">
              CPM = 1,000회 노출당 수익 · RPM = 1,000회 조회당 수익
            </p>
            <div v-if="revenueStore.cpmRpmData.platforms.length > 0" class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="border-b border-gray-200 dark:border-gray-700 text-xs uppercase text-gray-500 dark:text-gray-400">
                    <th class="px-4 py-3 text-left font-medium">플랫폼</th>
                    <th class="px-4 py-3 text-right font-medium">CPM (₩)</th>
                    <th class="px-4 py-3 text-right font-medium">RPM (₩)</th>
                    <th class="px-4 py-3 text-right font-medium">노출수</th>
                    <th class="px-4 py-3 text-right font-medium">조회수</th>
                    <th class="px-4 py-3 text-right font-medium">수익</th>
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
              CPM/RPM 데이터가 없습니다
            </div>
          </div>
        </template>
      </template>

      <!-- 브랜드딜 탭 -->
      <template v-if="activeTab === 'brandDeals'">
        <div v-if="revenueStore.brandDealLoading" class="flex items-center justify-center py-12">
          <div class="text-gray-500 dark:text-gray-400">로딩 중...</div>
        </div>
        <template v-else-if="revenueStore.brandDealData">
          <!-- 브랜드딜 요약 -->
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div class="card border-t-4 border-orange-500">
              <p class="text-sm text-gray-600 dark:text-gray-400">브랜드딜 총 수익</p>
              <p class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
                {{ formatCurrency(revenueStore.brandDealData.totalRevenueKrw) }}
              </p>
            </div>
            <div class="card border-t-4 border-orange-500">
              <p class="text-sm text-gray-600 dark:text-gray-400">브랜드딜 건수</p>
              <p class="mt-2 text-2xl font-bold text-gray-900 dark:text-gray-100">
                {{ revenueStore.brandDealData.deals.length }}건
              </p>
            </div>
          </div>

          <!-- 브랜드딜 목록 -->
          <div class="card">
            <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">브랜드딜 내역</h2>
            <div v-if="revenueStore.brandDealData.deals.length > 0" class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="border-b border-gray-200 dark:border-gray-700 text-xs uppercase text-gray-500 dark:text-gray-400">
                    <th class="px-4 py-3 text-left font-medium">브랜드</th>
                    <th class="px-4 py-3 text-right font-medium">금액</th>
                    <th class="px-4 py-3 text-center font-medium">상태</th>
                    <th class="px-4 py-3 text-left font-medium">플랫폼</th>
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
              브랜드딜 데이터가 없습니다
            </div>
          </div>
        </template>
      </template>

      <!-- AI 리포트 탭 (플레이스홀더) -->
      <template v-if="activeTab === 'aiReport'">
        <div class="card">
          <div class="flex flex-col items-center justify-center py-12 text-gray-400 dark:text-gray-500">
            <SparklesIcon class="h-12 w-12 mb-3" />
            <p class="text-sm font-medium">AI 수익 리포트</p>
            <p class="text-xs mt-1">AI가 수익 데이터를 분석하여 맞춤 리포트를 생성합니다</p>
            <p class="text-xs mt-1 text-gray-400">준비 중입니다</p>
          </div>
        </div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRevenueStore } from '@/stores/revenue'
import {
  BanknotesIcon,
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  ChartBarIcon,
  TrophyIcon,
  SparklesIcon,
} from '@heroicons/vue/24/outline'
import { PLATFORM_CONFIG } from '@/types/channel'
import PageGuide from '@/components/common/PageGuide.vue'
import RevenueChart from '@/components/revenue/RevenueChart.vue'
import RevenuePlatformBreakdown from '@/components/revenue/RevenuePlatformBreakdown.vue'
import RevenueTable from '@/components/revenue/RevenueTable.vue'
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

const revenueStore = useRevenueStore()

// ----- 탭 -----
type RevenueTab = 'overview' | 'cpmRpm' | 'brandDeals' | 'aiReport'
const activeTab = ref<RevenueTab>('overview')

const revenueTabs: { key: RevenueTab; label: string }[] = [
  { key: 'overview', label: '개요' },
  { key: 'cpmRpm', label: 'CPM·RPM' },
  { key: 'brandDeals', label: '브랜드딜' },
  { key: 'aiReport', label: 'AI 리포트' },
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
  { value: '1', label: '이번 달' },
  { value: '3', label: '최근 3개월' },
  { value: '6', label: '최근 6개월' },
  { value: '12', label: '1년' },
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
        label: '총 수익',
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
    NEGOTIATING: '협상 중',
    CONFIRMED: '확정',
    IN_PROGRESS: '진행 중',
    COMPLETED: '완료',
    CANCELLED: '취소',
  }
  return labels[status] ?? status
}

function dealStatusClass(status: string): string {
  const classes: Record<string, string> = {
    NEGOTIATING: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
    CONFIRMED: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
    IN_PROGRESS: 'bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-400',
    COMPLETED: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    CANCELLED: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
  }
  return classes[status] ?? 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-400'
}

onMounted(() => {
  revenueStore.fetchRevenue()
})
</script>
