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
      '수익 트렌드 차트에서 기간별 수익 변화 추이를 시각적으로 파악하세요',
      '플랫폼별 수익 내역에서 YouTube·TikTok·Instagram·Naver Clip 각각의 수입을 비교하세요',
    ]" />

    <!-- Loading State -->
    <div v-if="revenueStore.loading" class="flex items-center justify-center py-12">
      <div class="text-gray-500 dark:text-gray-400">로딩 중...</div>
    </div>

    <template v-else>
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
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRevenueStore } from '@/stores/revenue'
import {
  BanknotesIcon,
  ArrowTrendingUpIcon,
  ArrowTrendingDownIcon,
  ChartBarIcon,
  TrophyIcon,
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
} from 'chart.js'

ChartJS.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend)

const revenueStore = useRevenueStore()

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
        label: (context: any) => {
          const value = context.parsed.y
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
        callback: (value: any) => `₩${(value / 1000000).toFixed(1)}M`,
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

onMounted(() => {
  revenueStore.fetchRevenue()
})
</script>
