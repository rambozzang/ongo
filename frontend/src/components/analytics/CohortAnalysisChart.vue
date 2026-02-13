<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { Line } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler,
} from 'chart.js'
import { ArrowPathIcon } from '@heroicons/vue/24/outline'
import { analyticsApi } from '@/api/analytics'
import type { CohortAnalysisResponse, CohortGroupData } from '@/types/analytics'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler)

const loading = ref(true)
const cohortData = ref<CohortAnalysisResponse | null>(null)
const groupBy = ref('CATEGORY')
const dateFrom = ref('')
const dateTo = ref('')

const groupByOptions = [
  { value: 'CATEGORY', label: '카테고리' },
  { value: 'TAG', label: '태그' },
  { value: 'PLATFORM', label: '플랫폼' },
  { value: 'UPLOAD_MONTH', label: '업로드 월' },
]

const COHORT_COLORS = [
  '#6366F1', // indigo
  '#EC4899', // pink
  '#10B981', // emerald
  '#F59E0B', // amber
  '#3B82F6', // blue
  '#EF4444', // red
  '#8B5CF6', // violet
  '#14B8A6', // teal
  '#F97316', // orange
  '#06B6D4', // cyan
]

async function fetchCohort() {
  loading.value = true
  try {
    cohortData.value = await analyticsApi.cohortAnalysis(
      groupBy.value,
      dateFrom.value || undefined,
      dateTo.value || undefined,
    )
  } catch {
    cohortData.value = null
  } finally {
    loading.value = false
  }
}

watch(groupBy, () => fetchCohort())

onMounted(() => fetchCohort())

const chartData = computed(() => {
  if (!cohortData.value || cohortData.value.cohorts.length === 0) {
    return { labels: [], datasets: [] }
  }

  // Collect all unique days across cohorts
  const allDays = new Set<number>()
  for (const cohort of cohortData.value.cohorts) {
    for (const point of cohort.cumulativeViewCurve) {
      allDays.add(point.day)
    }
  }
  const sortedDays = Array.from(allDays).sort((a, b) => a - b)
  const labels = sortedDays.map((d) => `Day ${d}`)

  const datasets = cohortData.value.cohorts.slice(0, 10).map((cohort, index) => {
    const color = COHORT_COLORS[index % COHORT_COLORS.length]
    const dataMap = new Map(cohort.cumulativeViewCurve.map((p) => [p.day, p.normalizedPercent]))

    return {
      label: `${cohort.name} (${cohort.videoCount}편)`,
      data: sortedDays.map((day) => dataMap.get(day) ?? null),
      borderColor: color,
      backgroundColor: color + '20',
      fill: false,
      tension: 0.3,
      pointRadius: 4,
      pointHoverRadius: 6,
      borderWidth: 2,
    }
  })

  return { labels, datasets }
})

const chartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  interaction: {
    mode: 'index' as const,
    intersect: false,
  },
  plugins: {
    legend: {
      position: 'bottom' as const,
      labels: {
        usePointStyle: true,
        padding: 16,
        font: { size: 11 },
      },
    },
    tooltip: {
      callbacks: {
        label: (ctx: { dataset: { label?: string }; parsed: { y: number | null } }) => {
          const label = ctx.dataset.label || ''
          const val = ctx.parsed.y
          return val != null ? `${label}: ${val.toFixed(1)}%` : label
        },
      },
    },
  },
  scales: {
    y: {
      beginAtZero: true,
      max: 100,
      title: {
        display: true,
        text: '누적 조회수 (%)',
        font: { size: 12 },
      },
      ticks: {
        callback: (value: number | string) => `${value}%`,
      },
    },
    x: {
      title: {
        display: true,
        text: '게시 후 경과 일수',
        font: { size: 12 },
      },
    },
  },
}))

const topCohorts = computed<CohortGroupData[]>(() => {
  if (!cohortData.value) return []
  return cohortData.value.cohorts.slice(0, 5)
})

function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`
  return n.toLocaleString()
}
</script>

<template>
  <div>
    <!-- Controls -->
    <div class="mb-4 flex flex-wrap items-center gap-3">
      <div class="flex gap-1 rounded-lg bg-gray-100 p-1 dark:bg-gray-800">
        <button
          v-for="opt in groupByOptions"
          :key="opt.value"
          @click="groupBy = opt.value"
          :class="[
            groupBy === opt.value
              ? 'bg-white text-gray-900 shadow-sm dark:bg-gray-700 dark:text-gray-100'
              : 'text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300',
            'rounded-md px-3 py-1.5 text-xs font-medium transition-colors',
          ]"
        >
          {{ opt.label }}
        </button>
      </div>
      <div class="flex items-center gap-2">
        <input
          v-model="dateFrom"
          type="date"
          class="rounded-md border border-gray-300 bg-white px-2 py-1 text-xs dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300"
          @change="fetchCohort"
        />
        <span class="text-xs text-gray-400">~</span>
        <input
          v-model="dateTo"
          type="date"
          class="rounded-md border border-gray-300 bg-white px-2 py-1 text-xs dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300"
          @change="fetchCohort"
        />
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex items-center justify-center py-16">
      <ArrowPathIcon class="h-6 w-6 animate-spin text-gray-400" />
    </div>

    <!-- Chart -->
    <template v-else-if="cohortData && cohortData.cohorts.length > 0">
      <div class="relative h-80 w-full">
        <Line :data="chartData" :options="chartOptions" />
      </div>

      <!-- Summary Table -->
      <div class="mt-6 overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead>
            <tr
              class="border-b border-gray-200 text-xs uppercase text-gray-500 dark:border-gray-700 dark:text-gray-400"
            >
              <th class="px-3 py-2 font-medium">코호트</th>
              <th class="px-3 py-2 font-medium text-right">영상 수</th>
              <th class="px-3 py-2 font-medium text-right">평균 조회수</th>
              <th class="px-3 py-2 font-medium text-right">Day 7 도달률</th>
              <th class="px-3 py-2 font-medium text-right">Day 30 도달률</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
            <tr
              v-for="(cohort, idx) in topCohorts"
              :key="cohort.name"
              class="transition-colors hover:bg-gray-50 dark:hover:bg-gray-800/30"
            >
              <td class="px-3 py-2">
                <div class="flex items-center gap-2">
                  <span
                    class="inline-block h-3 w-3 rounded-full"
                    :style="{ backgroundColor: COHORT_COLORS[idx % COHORT_COLORS.length] }"
                  />
                  <span class="font-medium text-gray-900 dark:text-gray-100">{{
                    cohort.name
                  }}</span>
                </div>
              </td>
              <td class="px-3 py-2 text-right text-gray-600 dark:text-gray-300">
                {{ cohort.videoCount }}
              </td>
              <td class="px-3 py-2 text-right text-gray-600 dark:text-gray-300">
                {{ formatNumber(cohort.avgViews) }}
              </td>
              <td class="px-3 py-2 text-right text-gray-600 dark:text-gray-300">
                {{
                  (
                    cohort.cumulativeViewCurve.find((p) => p.day === 7)?.normalizedPercent ?? 0
                  ).toFixed(1)
                }}%
              </td>
              <td class="px-3 py-2 text-right text-gray-600 dark:text-gray-300">
                {{
                  (
                    cohort.cumulativeViewCurve.find((p) => p.day === 30)?.normalizedPercent ?? 0
                  ).toFixed(1)
                }}%
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!-- Empty State -->
    <div v-else class="py-16 text-center">
      <p class="text-sm text-gray-400 dark:text-gray-500">
        코호트 분석을 위한 데이터가 충분하지 않습니다.
      </p>
    </div>
  </div>
</template>
