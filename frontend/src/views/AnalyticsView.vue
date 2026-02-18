<template>
  <div>
    <!-- Header with period selector -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">통합 분석</h1>
      <div class="flex flex-col gap-2 mobile:flex-row">
        <div class="flex gap-2">
          <button
            v-for="p in periods"
            :key="p.value"
            class="rounded-lg px-3 py-1.5 text-sm font-medium transition-colors"
            :class="
              period === p.value
                ? 'bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-400'
                : 'text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'
            "
            @click="setPeriod(p.value)"
          >
            {{ p.label }}
          </button>
        </div>
        <div class="flex gap-2">
          <router-link
            to="/analytics/compare"
            class="inline-flex items-center gap-2 rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-1.5 text-sm font-medium text-gray-700 dark:text-gray-300 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
          >
            <ArrowsRightLeftIcon class="h-4 w-4" />
            영상 비교
          </router-link>
          <ExportDropdown
            v-if="hasExportData"
            :data="exportData"
            :columns="exportColumns"
            :filename-prefix="exportFilename"
          />
        </div>
      </div>
    </div>

    <PageGuide title="통합 분석" :items="[
      '7일·30일·90일 기간 필터로 분석 구간을 설정하고, 내보내기 버튼으로 데이터를 CSV/Excel로 다운로드하세요',
      '개요 탭에서 조회수·구독자·좋아요·예상 수익 KPI 카드와 플랫폼별 비교 차트를 확인하세요',
      '최적 게시 시간 히트맵에서 요일별·시간대별 조회수가 가장 높은 구간을 파악하여 게시 시간을 최적화하세요',
      '해시태그 분석의 태그 클라우드에서 태그를 클릭하면 해당 태그의 성과(사용 횟수·평균 조회수)를 상세 확인할 수 있습니다',
      'AI 인사이트 버튼(8크레딧)으로 현재 분석 기간의 자동 리포트를 생성하여 핵심 개선점을 확인하세요',
      '코호트 분석·리텐션 탭에서 카테고리별 누적 조회수 추이와 영상별 시청자 이탈 구간을 분석하세요',
    ]" />

    <!-- Sub-tab Navigation -->
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
      <nav class="-mb-px flex space-x-6">
        <button
          @click="analyticsSubTab = 'overview'"
          :class="[
            analyticsSubTab === 'overview'
              ? 'border-primary-500 text-primary-600 dark:border-primary-400 dark:text-primary-400'
              : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400',
            'whitespace-nowrap border-b-2 px-1 py-3 text-sm font-medium',
          ]"
        >
          개요
        </button>
        <button
          @click="analyticsSubTab = 'cohort'"
          :class="[
            analyticsSubTab === 'cohort'
              ? 'border-primary-500 text-primary-600 dark:border-primary-400 dark:text-primary-400'
              : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400',
            'whitespace-nowrap border-b-2 px-1 py-3 text-sm font-medium',
          ]"
        >
          코호트 분석
        </button>
        <button
          @click="analyticsSubTab = 'retention'"
          :class="[
            analyticsSubTab === 'retention'
              ? 'border-primary-500 text-primary-600 dark:border-primary-400 dark:text-primary-400'
              : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400',
            'whitespace-nowrap border-b-2 px-1 py-3 text-sm font-medium',
          ]"
        >
          리텐션
        </button>
      </nav>
    </div>

    <!-- Cohort Analysis Tab -->
    <div v-if="analyticsSubTab === 'cohort'" class="card">
      <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">코호트 분석</h2>
      <p class="mb-4 text-sm text-gray-500 dark:text-gray-400">
        카테고리, 태그, 플랫폼, 업로드 월별로 영상을 그룹핑하고 누적 조회수 추이를 비교합니다.
      </p>
      <CohortAnalysisChart />
    </div>

    <!-- Retention Tab -->
    <div v-if="analyticsSubTab === 'retention'">
      <div class="card mb-6">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">시청자 리텐션 커브</h2>
        <p class="mb-4 text-sm text-gray-500 dark:text-gray-400">
          영상의 시청자 리텐션을 분석합니다. 채널 평균과 비교하여 이탈 구간을 파악할 수 있습니다.
        </p>

        <!-- Video selector from top videos -->
        <div v-if="topVideos.length > 0" class="mb-4">
          <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
            분석할 영상 선택
          </label>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="video in topVideos.slice(0, 8)"
              :key="video.videoId"
              @click="retentionVideoId = video.videoId"
              :class="[
                retentionVideoId === video.videoId
                  ? 'border-indigo-500 bg-indigo-50 text-indigo-700 dark:border-indigo-400 dark:bg-indigo-900/20 dark:text-indigo-400'
                  : 'border-gray-200 bg-white text-gray-700 hover:bg-gray-50 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300',
                'rounded-lg border px-3 py-1.5 text-xs font-medium transition-colors',
              ]"
            >
              {{ video.title.length > 20 ? video.title.slice(0, 20) + '...' : video.title }}
            </button>
          </div>
        </div>

        <RetentionCurveChart :video-id="retentionVideoId" />
      </div>
    </div>

    <!-- Overview Tab Content -->
    <template v-if="analyticsSubTab === 'overview'">
    <LoadingSpinner v-if="loading" full-page />

    <!-- Empty State (when no data available) -->
    <EmptyState
      v-else-if="!kpi || (kpi.totalViews === 0 && kpi.totalSubscribers === 0 && kpi.totalLikes === 0)"
      title="분석할 데이터를 수집 중이에요"
      description="영상을 게시하면 24시간 내에 조회수, 좋아요, 댓글 등의 분석 데이터가 나타납니다."
      :icon="ChartBarIcon"
      action-label="영상 업로드하기"
      action-to="/upload"
      secondary-action-label="채널 연동하기"
      secondary-action-to="/channels"
    />

    <template v-else>
      <!-- KPI Summary Cards -->
      <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
        <SummaryCard
          title="총 조회수"
          :value="kpi?.totalViews ?? 0"
          :change="kpi?.viewsChangePercent"
          change-type="percent"
          :icon="EyeIcon"
        />
        <SummaryCard
          title="총 구독자"
          :value="kpi?.totalSubscribers ?? 0"
          :change="kpi?.subscribersChange"
          change-type="number"
          :icon="UsersIcon"
        />
        <SummaryCard
          title="총 좋아요"
          :value="kpi?.totalLikes ?? 0"
          :change="kpi?.likesChangePercent"
          change-type="percent"
          :icon="HeartIcon"
        />
        <SummaryCard
          title="예상 수익"
          :value="estimatedRevenue"
          :icon="CurrencyDollarIcon"
          format="number"
        />
      </div>

      <!-- Views Trend + Platform Comparison -->
      <div class="mb-6 grid gap-6 desktop:grid-cols-3">
        <!-- Views Trend Line Chart -->
        <div class="card desktop:col-span-2">
          <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">조회수 트렌드</h2>
          <div class="mb-3 flex flex-wrap gap-3">
            <span
              v-for="platformLegend in trendLegend"
              :key="platformLegend.key"
              class="flex items-center gap-1.5 text-xs font-medium text-gray-600 dark:text-gray-300"
            >
              <span
                class="inline-block h-2.5 w-2.5 rounded-full"
                :style="{ backgroundColor: platformLegend.color }"
              />
              {{ platformLegend.label }}
            </span>
          </div>
          <!-- Line chart placeholder - SVG visualization -->
          <div class="relative h-64 w-full overflow-hidden rounded-lg bg-gray-50 dark:bg-gray-900">
            <template v-if="trendData.length > 0">
              <svg
                class="h-full w-full"
                :viewBox="`0 0 ${trendChartWidth} ${trendChartHeight}`"
                preserveAspectRatio="none"
              >
                <!-- Grid lines -->
                <line
                  v-for="i in 5"
                  :key="'grid-' + i"
                  :x1="0"
                  :y1="(trendChartHeight / 5) * i"
                  :x2="trendChartWidth"
                  :y2="(trendChartHeight / 5) * i"
                  :stroke="themeStore.isDark ? '#374151' : '#e5e7eb'"
                  stroke-width="1"
                />
                <!-- Platform lines -->
                <polyline
                  v-for="platformLine in trendLines"
                  :key="platformLine.key"
                  :points="platformLine.points"
                  fill="none"
                  :stroke="platformLine.color"
                  stroke-width="2"
                  stroke-linejoin="round"
                  stroke-linecap="round"
                />
              </svg>
              <!-- X-axis labels -->
              <div class="absolute bottom-0 left-0 flex w-full justify-between px-1 text-[10px] text-gray-400 dark:text-gray-500">
                <span v-for="(label, idx) in trendXLabels" :key="idx">{{ label }}</span>
              </div>
            </template>
            <div v-else class="flex h-full items-center justify-center text-sm text-gray-400 dark:text-gray-500">
              데이터가 없습니다
            </div>
          </div>
        </div>

        <!-- Platform Comparison Grouped Bar Chart -->
        <div class="card">
          <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">플랫폼 비교</h2>
          <div v-if="platformComparison.length > 0" class="space-y-4">
            <!-- Metric tabs -->
            <div class="flex gap-1 rounded-lg bg-gray-100 dark:bg-gray-800 p-1">
              <button
                v-for="metric in comparisonMetrics"
                :key="metric.key"
                class="flex-1 rounded-md px-2 py-1 text-xs font-medium transition-colors"
                :class="
                  activeMetric === metric.key
                    ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm'
                    : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
                "
                @click="activeMetric = metric.key"
              >
                {{ metric.label }}
              </button>
            </div>
            <!-- Horizontal bars -->
            <div class="space-y-3">
              <div
                v-for="item in platformComparison"
                :key="item.platform"
                class="space-y-1"
              >
                <div class="flex items-center justify-between text-sm">
                  <span class="font-medium text-gray-700 dark:text-gray-300">
                    {{ platformLabel(item.platform) }}
                  </span>
                  <span class="text-gray-500 dark:text-gray-400">
                    {{ getMetricValue(item).toLocaleString() }}
                  </span>
                </div>
                <div class="h-3 w-full overflow-hidden rounded-full bg-gray-100 dark:bg-gray-800">
                  <div
                    class="h-full rounded-full transition-all duration-500"
                    :style="{
                      width: `${getBarWidth(item)}%`,
                      backgroundColor: platformColor(item.platform),
                    }"
                  />
                </div>
              </div>
            </div>
          </div>
          <div v-else class="flex h-48 items-center justify-center text-sm text-gray-400">
            데이터가 없습니다
          </div>
        </div>
      </div>

      <!-- Best Posting Time Section -->
      <div class="mb-6 grid gap-6 desktop:grid-cols-3">
        <!-- Posting Time Heatmap -->
        <div class="card desktop:col-span-2">
          <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">최적 게시 시간</h2>
          <PostingHeatmap :data="postingHeatmapData" :best-time="bestPostingTime" />
        </div>
        <!-- Best Time Recommendations -->
        <BestTimeCard :data="postingHeatmapData" />
      </div>

      <!-- Top 10 Videos Table -->
      <div class="card mb-6">
        <h2 class="mb-4 text-lg font-semibold text-gray-900">인기 영상 Top 10</h2>
        <div class="overflow-x-auto">
          <table v-if="topVideos.length > 0" class="w-full text-left text-sm">
            <thead>
              <tr class="border-b border-gray-200 dark:border-gray-700 text-xs uppercase text-gray-500 dark:text-gray-400">
                <th class="whitespace-nowrap px-3 py-3 font-medium">#</th>
                <th class="whitespace-nowrap px-3 py-3 font-medium">영상</th>
                <th class="whitespace-nowrap px-3 py-3 font-medium text-right">조회수</th>
                <th class="whitespace-nowrap px-3 py-3 font-medium text-right">좋아요</th>
                <th class="hidden whitespace-nowrap px-3 py-3 font-medium tablet:table-cell">게시일</th>
                <th class="hidden whitespace-nowrap px-3 py-3 font-medium tablet:table-cell">플랫폼</th>
                <th class="hidden whitespace-nowrap px-3 py-3 font-medium tablet:table-cell"></th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
              <tr
                v-for="(video, index) in topVideos"
                :key="video.videoId"
                class="transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
              >
                <td class="whitespace-nowrap px-3 py-3 font-semibold text-gray-400">
                  {{ index + 1 }}
                </td>
                <td class="px-3 py-3">
                  <div class="flex items-center gap-3">
                    <div class="h-10 w-16 flex-shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-800">
                      <img
                        v-if="video.thumbnailUrl"
                        :src="video.thumbnailUrl"
                        :alt="video.title"
                        class="h-full w-full object-cover"
                      />
                      <div
                        v-else
                        class="flex h-full w-full items-center justify-center text-gray-300"
                      >
                        <FilmIcon class="h-5 w-5" />
                      </div>
                    </div>
                    <div class="min-w-0">
                      <p class="truncate font-medium text-gray-900 dark:text-gray-100" :title="video.title">
                        {{ video.title }}
                      </p>
                      <!-- Mobile-only: platforms + date below title -->
                      <div class="mt-1 flex flex-wrap items-center gap-1 tablet:hidden">
                        <PlatformBadge
                          v-for="platform in video.platforms"
                          :key="platform"
                          :platform="platform"
                        />
                        <span class="ml-1 text-xs text-gray-400">
                          {{ formatDate(video.publishedAt) }}
                        </span>
                      </div>
                    </div>
                  </div>
                </td>
                <td class="whitespace-nowrap px-3 py-3 text-right font-medium text-gray-900 dark:text-gray-100">
                  {{ formatNumber(video.totalViews) }}
                </td>
                <td class="whitespace-nowrap px-3 py-3 text-right text-gray-600 dark:text-gray-300">
                  {{ formatNumber(video.totalLikes) }}
                </td>
                <td class="hidden whitespace-nowrap px-3 py-3 text-gray-500 dark:text-gray-400 tablet:table-cell">
                  {{ formatDate(video.publishedAt) }}
                </td>
                <td class="hidden px-3 py-3 tablet:table-cell">
                  <div class="flex flex-wrap gap-1">
                    <PlatformBadge
                      v-for="platform in video.platforms"
                      :key="platform"
                      :platform="platform"
                    />
                  </div>
                </td>
                <td class="hidden px-3 py-3 tablet:table-cell">
                  <button
                    @click="selectRetentionVideo(video.videoId)"
                    class="rounded-md px-2 py-1 text-xs font-medium text-indigo-600 hover:bg-indigo-50 dark:text-indigo-400 dark:hover:bg-indigo-900/20"
                  >
                    리텐션
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-else class="flex h-32 items-center justify-center text-sm text-gray-400">
            인기 영상 데이터가 없습니다
          </div>
        </div>
      </div>

      <!-- Upload Time Analysis Heatmap -->
      <div class="card mb-6">
        <h2 class="mb-4 text-lg font-semibold text-gray-900">업로드 시간 분석</h2>
        <p class="mb-3 text-xs text-gray-400 dark:text-gray-500">요일별/시간대별 평균 조회수 - 색이 진할수록 조회수가 높습니다</p>
        <div class="overflow-x-auto">
          <div class="min-w-[640px]">
            <!-- Hour labels header -->
            <div class="mb-1 flex">
              <div class="w-10 flex-shrink-0" />
              <div class="grid flex-1 grid-cols-24 gap-px">
                <div
                  v-for="hour in 24"
                  :key="'h-' + hour"
                  class="text-center text-[10px] text-gray-400 dark:text-gray-500"
                >
                  {{ hour - 1 }}
                </div>
              </div>
            </div>
            <!-- Day rows -->
            <div class="space-y-px">
              <div
                v-for="(dayLabel, dayIndex) in dayLabels"
                :key="dayIndex"
                class="flex items-center"
              >
                <div class="w-10 flex-shrink-0 pr-2 text-right text-xs font-medium text-gray-500 dark:text-gray-400">
                  {{ dayLabel }}
                </div>
                <div class="grid flex-1 grid-cols-24 gap-px">
                  <div
                    v-for="hour in 24"
                    :key="dayIndex + '-' + hour"
                    class="aspect-square rounded-sm transition-colors"
                    :style="{
                      backgroundColor: heatmapCellColor(dayIndex, hour - 1),
                    }"
                    :title="`${dayLabel} ${hour - 1}시: ${heatmapCellValue(dayIndex, hour - 1).toLocaleString()} 조회`"
                  />
                </div>
              </div>
            </div>
            <!-- Legend -->
            <div class="mt-3 flex items-center justify-end gap-1 text-[10px] text-gray-400 dark:text-gray-500">
              <span>적음</span>
              <div
                v-for="level in 5"
                :key="'legend-' + level"
                class="h-3 w-3 rounded-sm"
                :style="{ backgroundColor: heatmapLegendColor(level) }"
              />
              <span>많음</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Hashtag Analytics Section -->
      <div class="card mb-6">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">해시태그 분석</h2>
        <div class="grid gap-6 desktop:grid-cols-2">
          <div>
            <h3 class="mb-3 text-sm font-medium text-gray-700 dark:text-gray-300">태그 클라우드</h3>
            <TagCloud :tags="tagData" :selected-tag="selectedTag" @tag-click="handleTagClick" />
          </div>
          <div>
            <h3 class="mb-3 text-sm font-medium text-gray-700 dark:text-gray-300">태그 성과</h3>
            <TagPerformanceTable :tags="filteredTagData" />
          </div>
        </div>
      </div>

      <!-- AI Insight Section -->
      <div class="card">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <SparklesIcon class="h-5 w-5 text-purple-500" />
            <h2 class="text-lg font-semibold text-gray-900 dark:text-gray-100">AI 인사이트</h2>
          </div>
          <span class="text-xs text-gray-400 dark:text-gray-500">잔여 크레딧: {{ kpi?.creditBalance ?? balance }}</span>
        </div>

        <div class="mt-4">
          <!-- Generate button -->
          <button
            v-if="!aiReport && !aiLoading"
            class="btn-primary inline-flex items-center gap-2"
            :disabled="aiLoading"
            @click="handleGenerateInsight"
          >
            <SparklesIcon class="h-4 w-4" />
            AI 인사이트 생성 (8 크레딧)
          </button>

          <!-- Loading state -->
          <div v-if="aiLoading" class="flex items-center gap-3 rounded-lg bg-purple-50 dark:bg-purple-900/20 p-4">
            <div class="h-5 w-5 animate-spin rounded-full border-2 border-purple-300 border-t-purple-600" />
            <span class="text-sm text-purple-700 dark:text-purple-400">AI가 분석 리포트를 생성하고 있습니다...</span>
          </div>

          <!-- AI error -->
          <div v-if="aiError" class="mt-3 rounded-lg bg-red-50 dark:bg-red-900/20 p-4">
            <p class="text-sm text-red-700 dark:text-red-400">{{ aiError }}</p>
            <button
              class="mt-2 text-sm font-medium text-red-600 underline hover:text-red-800"
              @click="handleGenerateInsight"
            >
              다시 시도
            </button>
          </div>

          <!-- AI report result -->
          <div v-if="aiReport" class="mt-3 space-y-3">
            <div class="rounded-lg border border-purple-200 dark:border-purple-800 bg-purple-50/50 dark:bg-purple-900/20 p-4">
              <div class="prose prose-sm max-w-none text-gray-700 dark:text-gray-300" v-html="sanitizedMarkdown" />
            </div>
            <div class="flex items-center justify-between text-xs text-gray-400 dark:text-gray-500">
              <span>사용 크레딧: {{ aiReport.creditsUsed }} / 잔여: {{ aiReport.creditsRemaining }}</span>
              <button
                class="font-medium text-purple-600 hover:text-purple-800"
                @click="handleGenerateInsight"
              >
                다시 생성
              </button>
            </div>
          </div>
        </div>
      </div>
    </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { storeToRefs } from 'pinia'
import {
  EyeIcon,
  UsersIcon,
  HeartIcon,
  CurrencyDollarIcon,
  SparklesIcon,
  FilmIcon,
  ChartBarIcon,
  ArrowsRightLeftIcon,
} from '@heroicons/vue/24/outline'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import SummaryCard from '@/components/dashboard/SummaryCard.vue'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import ExportDropdown from '@/components/analytics/ExportDropdown.vue'
import PostingHeatmap from '@/components/analytics/PostingHeatmap.vue'
import BestTimeCard from '@/components/analytics/BestTimeCard.vue'
import TagCloud from '@/components/analytics/TagCloud.vue'
import TagPerformanceTable from '@/components/analytics/TagPerformanceTable.vue'
import CohortAnalysisChart from '@/components/analytics/CohortAnalysisChart.vue'
import RetentionCurveChart from '@/components/analytics/RetentionCurveChart.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import { analyticsApi } from '@/api/analytics'
import { useAnalyticsStore } from '@/stores/analytics'
import { useAiStore } from '@/stores/ai'
import { useCredit } from '@/composables/useCredit'
import { PLATFORM_CONFIG } from '@/types/channel'
import type { Platform } from '@/types/channel'
import type { PlatformComparison, TrendDataPoint } from '@/types/analytics'
import type { GenerateReportResponse } from '@/types/ai'
import type { ColumnDefinition } from '@/utils/export'
import { formatDateOnlyForExport } from '@/utils/export'
import type { TagPerformance } from '@/types/analytics'

// ----- Analytics Sub-tab -----
type AnalyticsSubTab = 'overview' | 'cohort' | 'retention'
const analyticsSubTab = ref<AnalyticsSubTab>('overview')
const retentionVideoId = ref<number | undefined>(undefined)

function selectRetentionVideo(videoId: number) {
  retentionVideoId.value = videoId
  analyticsSubTab.value = 'retention'
}

// ----- Stores & Composables -----
const themeStore = useThemeStore()
const analyticsStore = useAnalyticsStore()
const aiStore = useAiStore()
const { balance, checkAndUse } = useCredit()

const {
  kpi,
  trendData,
  platformComparison,
  heatmapData,
  postingHeatmapData,
  topVideos,
  loading,
  period,
} = storeToRefs(analyticsStore)
const { setPeriod } = analyticsStore

// ----- Period Selector -----
const periods = [
  { value: '7d' as const, label: '7일' },
  { value: '30d' as const, label: '30일' },
  { value: '90d' as const, label: '90일' },
]

// ----- KPI Estimated Revenue -----
// CPM-based rough estimation: totalViews / 1000 * average CPM (KRW)
const estimatedRevenue = computed(() => {
  if (!kpi.value) return 0
  return Math.round(kpi.value.totalViews / 1000 * 1.5)
})

// ----- Trend Chart -----
const trendChartWidth = 800
const trendChartHeight = 240

const trendLegend = [
  { key: 'youtube', label: 'YouTube', color: '#FF0000' },
  { key: 'tiktok', label: 'TikTok', color: '#000000' },
  { key: 'instagram', label: 'Instagram', color: '#E1306C' },
  { key: 'naverClip', label: 'Naver Clip', color: '#03C75A' },
]

// platformViews 맵에서 플랫폼별 값을 가져오는 헬퍼
function getPlatformViews(d: TrendDataPoint, platformKey: string): number {
  const keyMap: Record<string, string> = {
    youtube: 'YOUTUBE',
    tiktok: 'TIKTOK',
    instagram: 'INSTAGRAM',
    naverClip: 'NAVER_CLIP',
  }
  return d.platformViews?.[keyMap[platformKey] ?? platformKey] ?? 0
}

const trendMaxValue = computed(() => {
  if (trendData.value.length === 0) return 1
  return Math.max(
    ...trendData.value.flatMap((d) =>
      trendLegend.map((l) => getPlatformViews(d, l.key))
    ),
    1,
  )
})

const trendLines = computed(() => {
  const data = trendData.value
  if (data.length === 0) return []

  const maxVal = trendMaxValue.value
  const padding = 10

  return trendLegend.map((legend) => {
    const points = data
      .map((d, i) => {
        const x = padding + (i / Math.max(data.length - 1, 1)) * (trendChartWidth - padding * 2)
        const val = getPlatformViews(d, legend.key)
        const y = trendChartHeight - padding - (val / maxVal) * (trendChartHeight - padding * 2)
        return `${x},${y}`
      })
      .join(' ')

    return {
      key: legend.key,
      color: legend.color,
      points,
    }
  })
})

const trendXLabels = computed(() => {
  const data = trendData.value
  if (data.length === 0) return []
  // Show up to 7 evenly spaced date labels
  const maxLabels = 7
  const step = Math.max(1, Math.floor(data.length / maxLabels))
  const labels: string[] = []
  for (let i = 0; i < data.length; i += step) {
    const dateStr = data[i].date
    const date = new Date(dateStr)
    labels.push(`${date.getMonth() + 1}/${date.getDate()}`)
  }
  return labels
})

// ----- Platform Comparison -----
type ComparisonMetricKey = 'views' | 'likes' | 'comments' | 'shares'

const comparisonMetrics: { key: ComparisonMetricKey; label: string }[] = [
  { key: 'views', label: '조회수' },
  { key: 'likes', label: '좋아요' },
  { key: 'comments', label: '댓글' },
  { key: 'shares', label: '공유' },
]

const activeMetric = ref<ComparisonMetricKey>('views')

function getMetricValue(item: PlatformComparison): number {
  return item[activeMetric.value]
}

const maxMetricValue = computed(() => {
  if (platformComparison.value.length === 0) return 1
  return Math.max(...platformComparison.value.map((i) => getMetricValue(i)), 1)
})

function getBarWidth(item: PlatformComparison): number {
  return (getMetricValue(item) / maxMetricValue.value) * 100
}

function platformLabel(platform: Platform): string {
  return PLATFORM_CONFIG[platform]?.label ?? platform
}

function platformColor(platform: Platform): string {
  return PLATFORM_CONFIG[platform]?.color ?? '#6b7280'
}

// ----- Top Videos Table -----
function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`
  return n.toLocaleString()
}

function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}.${month}.${day}`
}

// ----- Heatmap -----
const dayLabels = ['월', '화', '수', '목', '금', '토', '일']

const heatmapLookup = computed(() => {
  const map = new Map<string, number>()
  for (const d of heatmapData.value) {
    map.set(`${d.dayOfWeek}-${d.hour}`, d.value)
  }
  return map
})

const heatmapMax = computed(() => {
  if (heatmapData.value.length === 0) return 1
  return Math.max(...heatmapData.value.map((d) => d.value), 1)
})

function heatmapCellValue(dayIndex: number, hour: number): number {
  return heatmapLookup.value.get(`${dayIndex}-${hour}`) ?? 0
}

function heatmapCellColor(dayIndex: number, hour: number): string {
  const value = heatmapCellValue(dayIndex, hour)
  if (value === 0) return themeStore.isDark ? '#1f2937' : '#f3f4f6' // gray-100 / gray-800
  const intensity = value / heatmapMax.value
  // Map intensity to green shades (matching Naver primary or a neutral green-blue)
  const alpha = Math.max(0.1, Math.min(1, intensity))
  // Use a purple-blue gradient for visual distinction
  return `rgba(124, 58, 237, ${alpha})`
}

function heatmapLegendColor(level: number): string {
  const alpha = level / 5
  return `rgba(124, 58, 237, ${alpha})`
}

// ----- Best Posting Time -----
const bestPostingTime = computed(() => {
  if (postingHeatmapData.value.length === 0) return null

  // Find the slot with the highest value
  const sorted = [...postingHeatmapData.value].sort((a, b) => b.value - a.value)
  if (sorted.length === 0) return null

  const best = sorted[0]
  return {
    dayOfWeek: best.dayOfWeek,
    hour: best.hour,
  }
})

// ----- AI Insight -----
const aiReport = ref<GenerateReportResponse | null>(null)
const aiLoading = ref(false)
const aiError = ref<string | null>(null)

async function handleGenerateInsight() {
  aiError.value = null

  // Credit check
  const canUse = await checkAndUse(8, 'AI 인사이트')
  if (!canUse) return

  aiLoading.value = true
  try {
    // generateReport accepts '7d' | '30d'; map current period
    const reportPeriod: '7d' | '30d' =
      period.value === '7d' ? '7d' : '30d'
    const result = await aiStore.generateReport(reportPeriod)
    aiReport.value = result ?? null
  } catch (e) {
    aiError.value = e instanceof Error ? e.message : 'AI 인사이트 생성에 실패했습니다. 다시 시도해주세요.'
  } finally {
    aiLoading.value = false
  }
}

// Simple markdown to HTML renderer (handles headers, bold, lists, paragraphs)
const renderedMarkdown = computed(() => {
  if (!aiReport.value?.reportMarkdown) return ''
  return aiReport.value.reportMarkdown
    // Escape HTML first
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    // Headers
    .replace(/^### (.+)$/gm, '<h3 class="mt-3 mb-1 text-sm font-bold text-gray-800">$1</h3>')
    .replace(/^## (.+)$/gm, '<h2 class="mt-4 mb-1 text-base font-bold text-gray-900">$1</h2>')
    .replace(/^# (.+)$/gm, '<h1 class="mt-4 mb-2 text-lg font-bold text-gray-900">$1</h1>')
    // Bold
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    // Italic
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    // Unordered list items
    .replace(/^[*-] (.+)$/gm, '<li class="ml-4 list-disc">$1</li>')
    // Ordered list items
    .replace(/^\d+\. (.+)$/gm, '<li class="ml-4 list-decimal">$1</li>')
    // Line breaks for remaining lines
    .replace(/\n\n/g, '</p><p class="mt-2">')
    .replace(/\n/g, '<br />')
})

// 허용된 HTML 태그만 남기고 나머지 제거 (XSS 방지)
const ALLOWED_TAGS = new Set(['h1', 'h2', 'h3', 'p', 'strong', 'em', 'li', 'br', 'ul', 'ol'])
const ALLOWED_ATTRS = new Set(['class'])

const sanitizedMarkdown = computed(() => {
  const html = renderedMarkdown.value
  if (!html) return ''

  const doc = new DOMParser().parseFromString(html, 'text/html')

  function sanitizeNode(node: Node): void {
    const children = Array.from(node.childNodes)
    for (const child of children) {
      if (child.nodeType === Node.ELEMENT_NODE) {
        const el = child as Element
        const tagName = el.tagName.toLowerCase()
        if (!ALLOWED_TAGS.has(tagName)) {
          // 허용되지 않은 태그는 텍스트 콘텐츠로 대체
          const text = document.createTextNode(el.textContent || '')
          node.replaceChild(text, child)
        } else {
          // 허용되지 않은 속성 제거
          for (const attr of Array.from(el.attributes)) {
            if (!ALLOWED_ATTRS.has(attr.name)) {
              el.removeAttribute(attr.name)
            }
          }
          sanitizeNode(child)
        }
      }
    }
  }

  sanitizeNode(doc.body)
  return doc.body.innerHTML
})

// ----- Hashtag Analytics -----
const selectedTag = ref<string | null>(null)

const tagData = ref<TagPerformance[]>([])
const tagDataLoading = ref(false)

const filteredTagData = computed(() => {
  if (!selectedTag.value) {
    return tagData.value
  }
  return tagData.value.filter((t) => t.tag === selectedTag.value)
})

function handleTagClick(tag: string) {
  if (selectedTag.value === tag) {
    selectedTag.value = null
  } else {
    selectedTag.value = tag
  }
}

// ----- Export Data Preparation -----
interface ExportDataRow {
  date: string
  youtube: number
  tiktok: number
  instagram: number
  naverClip: number
  total: number
}

const hasExportData = computed(() => {
  return trendData.value.length > 0
})

const exportData = computed<ExportDataRow[]>(() => {
  return trendData.value.map((d) => ({
    date: d.date,
    youtube: getPlatformViews(d, 'youtube'),
    tiktok: getPlatformViews(d, 'tiktok'),
    instagram: getPlatformViews(d, 'instagram'),
    naverClip: getPlatformViews(d, 'naverClip'),
    total: d.totalViews,
  }))
})

const exportColumns = computed<ColumnDefinition<ExportDataRow>[]>(() => [
  { key: 'date', header: '날짜', formatter: (val) => formatDateOnlyForExport(val) },
  { key: 'youtube', header: 'YouTube 조회수' },
  { key: 'tiktok', header: 'TikTok 조회수' },
  { key: 'instagram', header: 'Instagram 조회수' },
  { key: 'naverClip', header: 'Naver Clip 조회수' },
  { key: 'total', header: '총 조회수' },
])

const exportFilename = computed(() => {
  const today = new Date()
  const dateStr = formatDateOnlyForExport(today.toISOString())
  const periodLabel = periods.find((p) => p.value === period.value)?.label ?? period.value
  return `onGo_analytics_${periodLabel}_${dateStr}`
})

// ----- Lifecycle -----
onMounted(() => {
  analyticsStore.fetchAnalytics()
  loadTagPerformance()
})

async function loadTagPerformance() {
  tagDataLoading.value = true
  try {
    tagData.value = await analyticsApi.tagPerformance(period.value)
  } catch {
    // silently ignore — tag data will be empty
  } finally {
    tagDataLoading.value = false
  }
}
</script>

<style scoped>
.grid-cols-24 {
  grid-template-columns: repeat(24, minmax(0, 1fr));
}
</style>
