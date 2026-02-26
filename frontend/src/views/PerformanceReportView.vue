<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  DocumentChartBarIcon,
  CalendarIcon,
  EyeIcon,
  ArrowTrendingUpIcon,
  XMarkIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import ReportCard from '@/components/performancereport/ReportCard.vue'
import ReportSectionItem from '@/components/performancereport/ReportSectionItem.vue'
import { usePerformanceReportStore } from '@/stores/performanceReport'
import type { PerformanceReport, ReportSection, PerformanceReportSummary } from '@/types/performanceReport'

const store = usePerformanceReportStore()

const activeStatus = ref<string>('all')
const selectedReportId = ref<number | null>(null)

onMounted(async () => {
  await Promise.all([store.fetchReports(), store.fetchSummary()])
})

/* ---- Status filter tabs ---- */
const statusTabs = computed(() => [
  { key: 'all', label: '전체', count: store.reports.length },
  { key: 'COMPLETED', label: '완료', count: store.reports.filter(r => r.status === 'COMPLETED').length },
  { key: 'GENERATING', label: '생성중', count: store.reports.filter(r => r.status === 'GENERATING').length },
  { key: 'SCHEDULED', label: '예약됨', count: store.reports.filter(r => r.status === 'SCHEDULED').length },
  { key: 'DRAFT', label: '초안', count: store.reports.filter(r => r.status === 'DRAFT').length },
])

/* ---- Filtered reports ---- */
const filteredReports = computed(() => {
  if (activeStatus.value === 'all') return store.reports
  return store.reports.filter(r => r.status === activeStatus.value)
})

/* ---- Selected report ---- */
const selectedReport = computed(() =>
  store.reports.find(r => r.id === selectedReportId.value) ?? null,
)

function handleSelect(id: number) {
  selectedReportId.value = id
  store.fetchSections(id)
}

/* ---- Summary stats ---- */
function formatNumber(num: number): string {
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return num.toLocaleString('ko-KR')
}

const summaryCards = computed(() => {
  const s = store.summary
  if (!s) return []
  return [
    { label: '총 보고서', value: s.totalReports.toLocaleString('ko-KR'), icon: DocumentChartBarIcon, color: 'text-blue-600 dark:text-blue-400 bg-blue-100 dark:bg-blue-900/30' },
    { label: '예약된 보고서', value: `${s.scheduledReports}개`, icon: CalendarIcon, color: 'text-purple-600 dark:text-purple-400 bg-purple-100 dark:bg-purple-900/30' },
    { label: '기간당 평균 조회수', value: formatNumber(s.avgViewsPerPeriod), icon: EyeIcon, color: 'text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-900/30' },
    { label: '성장률', value: `${s.growthRate > 0 ? '+' : ''}${s.growthRate}%`, icon: ArrowTrendingUpIcon, color: 'text-orange-600 dark:text-orange-400 bg-orange-100 dark:bg-orange-900/30' },
  ]
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          콘텐츠 성과 보고서
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          기간별 콘텐츠 성과를 분석하고 인사이트를 확인하세요
        </p>
      </div>
    </div>

    <PageGuide
      title="성과 보고서 가이드"
      :items="[
        'AI가 자동으로 생성하는 기간별 성과 보고서를 확인할 수 있습니다.',
        '상태 탭으로 완료, 생성중, 예약된 보고서를 필터링하세요.',
        '보고서 카드를 클릭하면 상세 섹션별 분석 내용을 볼 수 있습니다.',
        '완료된 보고서는 PDF로 다운로드할 수 있습니다.',
      ]"
    />

    <!-- Loading -->
    <LoadingSpinner v-if="store.loading" :full-page="true" size="lg" />

    <div v-else class="space-y-6 mt-6">
      <!-- Summary cards -->
      <div
        v-if="store.summary"
        class="grid grid-cols-2 tablet:grid-cols-4 gap-4"
      >
        <div
          v-for="card in summaryCards"
          :key="card.label"
          class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-4 shadow-sm"
        >
          <div class="flex items-center gap-3">
            <div
              :class="['flex-shrink-0 w-10 h-10 rounded-lg flex items-center justify-center', card.color]"
            >
              <component :is="card.icon" class="w-5 h-5" />
            </div>
            <div>
              <p class="text-xs text-gray-500 dark:text-gray-400">{{ card.label }}</p>
              <p class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ card.value }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- Status filter tabs -->
      <div class="border-b border-gray-200 dark:border-gray-700">
        <nav class="-mb-px flex gap-6 overflow-x-auto">
          <button
            v-for="tab in statusTabs"
            :key="tab.key"
            @click="activeStatus = tab.key"
            :class="[
              'py-3 px-1 border-b-2 font-medium text-sm whitespace-nowrap transition-colors',
              activeStatus === tab.key
                ? 'border-blue-600 text-blue-600 dark:border-blue-400 dark:text-blue-400'
                : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
            ]"
          >
            {{ tab.label }}
            <span
              :class="[
                'ml-1.5 px-1.5 py-0.5 rounded-full text-xs',
                activeStatus === tab.key
                  ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                  : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
              ]"
            >
              {{ tab.count }}
            </span>
          </button>
        </nav>
      </div>

      <!-- Report cards list -->
      <div v-if="filteredReports.length > 0" class="grid grid-cols-1 tablet:grid-cols-2 desktop:grid-cols-3 gap-6">
        <ReportCard
          v-for="report in filteredReports"
          :key="report.id"
          :report="report"
          @select="handleSelect"
        />
      </div>

      <div
        v-else
        class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 p-12 text-center shadow-sm"
      >
        <DocumentChartBarIcon class="w-16 h-16 text-gray-400 dark:text-gray-500 mx-auto mb-4" />
        <h3 class="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
          보고서가 없습니다
        </h3>
        <p class="text-sm text-gray-600 dark:text-gray-400">
          해당 상태의 보고서가 아직 생성되지 않았습니다.
        </p>
      </div>

      <!-- Selected report - sections -->
      <section v-if="selectedReport && store.sections.length > 0">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ selectedReport.title }} - 상세 분석
          </h2>
          <button
            @click="selectedReportId = null"
            class="p-1.5 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
          >
            <XMarkIcon class="w-5 h-5" />
          </button>
        </div>

        <div class="space-y-3">
          <ReportSectionItem
            v-for="section in store.sections"
            :key="section.id"
            :section="section"
          />
        </div>
      </section>
    </div>
  </div>
</template>
