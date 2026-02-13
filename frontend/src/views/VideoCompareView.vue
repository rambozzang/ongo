<template>
  <div>
    <!-- Header -->
    <div class="mb-6">
      <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">영상 비교 분석</h1>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
        두 영상의 성과를 비교하여 인사이트를 얻으세요
      </p>
    </div>

    <!-- Video Selectors -->
    <div class="mb-6 grid gap-4 desktop:grid-cols-2">
      <!-- Video A Selector -->
      <div class="card">
        <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
          비교 영상 A
        </label>
        <div class="relative">
          <button
            type="button"
            class="flex w-full items-center justify-between rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-4 py-2.5 text-left text-sm transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
            @click="showVideoADropdown = !showVideoADropdown"
          >
            <span v-if="selectedVideoA" class="text-gray-900 dark:text-gray-100">
              {{ selectedVideoA.title }}
            </span>
            <span v-else class="text-gray-400">영상을 선택하세요</span>
            <ChevronDownIcon class="h-5 w-5 text-gray-400" />
          </button>

          <!-- Dropdown -->
          <div
            v-if="showVideoADropdown"
            v-click-outside="() => (showVideoADropdown = false)"
            class="absolute z-10 mt-1 max-h-80 w-full overflow-y-auto rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-lg"
          >
            <div class="p-2">
              <input
                v-model="searchVideoA"
                type="text"
                placeholder="영상 검색..."
                class="w-full rounded-md border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              />
            </div>
            <div class="divide-y divide-gray-100 dark:divide-gray-700">
              <button
                v-for="video in filteredVideosA"
                :key="video.videoId"
                type="button"
                class="flex w-full items-center gap-3 p-3 text-left transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
                @click="selectVideoA(video)"
              >
                <div class="h-12 w-20 flex-shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-900">
                  <img
                    v-if="video.thumbnailUrl"
                    :src="video.thumbnailUrl"
                    :alt="video.title"
                    class="h-full w-full object-cover"
                  />
                  <div v-else class="flex h-full w-full items-center justify-center">
                    <FilmIcon class="h-6 w-6 text-gray-400" />
                  </div>
                </div>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                    {{ video.title }}
                  </p>
                  <p class="text-xs text-gray-500 dark:text-gray-400">
                    {{ formatDate(video.publishedAt) }}
                  </p>
                  <div class="mt-1 flex flex-wrap gap-1">
                    <PlatformBadge
                      v-for="platform in video.platforms"
                      :key="platform"
                      :platform="platform"
                    />
                  </div>
                </div>
              </button>
            </div>
          </div>
        </div>

        <!-- Selected Video A Card -->
        <div v-if="selectedVideoA" class="mt-4">
          <VideoComparisonCard :video="selectedVideoA" color="primary" />
        </div>
      </div>

      <!-- Video B Selector -->
      <div class="card">
        <label class="mb-2 block text-sm font-medium text-gray-700 dark:text-gray-300">
          비교 영상 B
        </label>
        <div class="relative">
          <button
            type="button"
            class="flex w-full items-center justify-between rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-4 py-2.5 text-left text-sm transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
            @click="showVideoBDropdown = !showVideoBDropdown"
          >
            <span v-if="selectedVideoB" class="text-gray-900 dark:text-gray-100">
              {{ selectedVideoB.title }}
            </span>
            <span v-else class="text-gray-400">영상을 선택하세요</span>
            <ChevronDownIcon class="h-5 w-5 text-gray-400" />
          </button>

          <!-- Dropdown -->
          <div
            v-if="showVideoBDropdown"
            v-click-outside="() => (showVideoBDropdown = false)"
            class="absolute z-10 mt-1 max-h-80 w-full overflow-y-auto rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-lg"
          >
            <div class="p-2">
              <input
                v-model="searchVideoB"
                type="text"
                placeholder="영상 검색..."
                class="w-full rounded-md border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:border-primary-500 focus:outline-none focus:ring-1 focus:ring-primary-500"
              />
            </div>
            <div class="divide-y divide-gray-100 dark:divide-gray-700">
              <button
                v-for="video in filteredVideosB"
                :key="video.videoId"
                type="button"
                class="flex w-full items-center gap-3 p-3 text-left transition-colors hover:bg-gray-50 dark:hover:bg-gray-700"
                @click="selectVideoB(video)"
              >
                <div class="h-12 w-20 flex-shrink-0 overflow-hidden rounded bg-gray-100 dark:bg-gray-900">
                  <img
                    v-if="video.thumbnailUrl"
                    :src="video.thumbnailUrl"
                    :alt="video.title"
                    class="h-full w-full object-cover"
                  />
                  <div v-else class="flex h-full w-full items-center justify-center">
                    <FilmIcon class="h-6 w-6 text-gray-400" />
                  </div>
                </div>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                    {{ video.title }}
                  </p>
                  <p class="text-xs text-gray-500 dark:text-gray-400">
                    {{ formatDate(video.publishedAt) }}
                  </p>
                  <div class="mt-1 flex flex-wrap gap-1">
                    <PlatformBadge
                      v-for="platform in video.platforms"
                      :key="platform"
                      :platform="platform"
                    />
                  </div>
                </div>
              </button>
            </div>
          </div>
        </div>

        <!-- Selected Video B Card -->
        <div v-if="selectedVideoB" class="mt-4">
          <VideoComparisonCard :video="selectedVideoB" color="amber" />
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <EmptyState
      v-if="!selectedVideoA || !selectedVideoB"
      title="두 영상을 선택해주세요"
      description="비교할 두 영상을 선택하면 상세한 성과 비교 분석을 확인할 수 있습니다."
      :icon="ArrowsRightLeftIcon"
    />

    <!-- Comparison Results -->
    <template v-else>
      <!-- Visual Comparison Chart -->
      <div class="card mb-6">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">주요 지표 비교</h2>
        <div class="h-80">
          <Bar :data="chartData" :options="chartOptions" />
        </div>
      </div>

      <!-- Performance Difference Cards -->
      <div class="mb-6 grid gap-4 tablet:grid-cols-2 desktop:grid-cols-4">
        <MetricDifferenceCard
          title="조회수"
          :value-a="selectedVideoA.totalViews"
          :value-b="selectedVideoB.totalViews"
          :icon="EyeIcon"
        />
        <MetricDifferenceCard
          title="좋아요"
          :value-a="selectedVideoA.totalLikes"
          :value-b="selectedVideoB.totalLikes"
          :icon="HeartIcon"
        />
        <MetricDifferenceCard
          title="댓글"
          :value-a="mockCommentsA"
          :value-b="mockCommentsB"
          :icon="ChatBubbleLeftIcon"
        />
        <MetricDifferenceCard
          title="참여율"
          :value-a="engagementRateA"
          :value-b="engagementRateB"
          :icon="ChartBarIcon"
          is-percentage
        />
      </div>

      <!-- Detailed Insights -->
      <div class="card">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">상세 분석</h2>
        <div class="space-y-3">
          <InsightItem
            v-for="insight in comparisonInsights"
            :key="insight.text"
            :text="insight.text"
            :is-positive="insight.isPositive"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
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
import {
  ChevronDownIcon,
  FilmIcon,
  EyeIcon,
  HeartIcon,
  ChatBubbleLeftIcon,
  ChartBarIcon,
  ArrowsRightLeftIcon,
} from '@heroicons/vue/24/outline'
import { useAnalyticsStore } from '@/stores/analytics'
import { useThemeStore } from '@/stores/theme'
import { storeToRefs } from 'pinia'
import type { TopVideo } from '@/types/analytics'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import VideoComparisonCard from '@/components/analytics/VideoComparisonCard.vue'
import MetricDifferenceCard from '@/components/analytics/MetricDifferenceCard.vue'
import InsightItem from '@/components/analytics/InsightItem.vue'

// Register Chart.js components
ChartJS.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend)

// Stores
const analyticsStore = useAnalyticsStore()
const themeStore = useThemeStore()
const { topVideos } = storeToRefs(analyticsStore)

// Video selection state
const selectedVideoA = ref<TopVideo | null>(null)
const selectedVideoB = ref<TopVideo | null>(null)
const showVideoADropdown = ref(false)
const showVideoBDropdown = ref(false)
const searchVideoA = ref('')
const searchVideoB = ref('')

// Click outside directive
interface ClickOutsideElement extends HTMLElement {
  clickOutsideEvent?: (event: Event) => void
}

const vClickOutside = {
  mounted(el: ClickOutsideElement, binding: { value: () => void }) {
    el.clickOutsideEvent = (event: Event) => {
      if (!(el === event.target || el.contains(event.target as Node))) {
        binding.value()
      }
    }
    document.addEventListener('click', el.clickOutsideEvent)
  },
  unmounted(el: ClickOutsideElement) {
    if (el.clickOutsideEvent) {
      document.removeEventListener('click', el.clickOutsideEvent)
    }
  },
}

// Filtered videos for dropdowns
const filteredVideosA = computed(() => {
  if (!searchVideoA.value) return topVideos.value
  return topVideos.value.filter((v) =>
    v.title.toLowerCase().includes(searchVideoA.value.toLowerCase())
  )
})

const filteredVideosB = computed(() => {
  if (!searchVideoB.value) return topVideos.value
  return topVideos.value.filter((v) =>
    v.title.toLowerCase().includes(searchVideoB.value.toLowerCase())
  )
})

// Select video handlers
function selectVideoA(video: TopVideo) {
  selectedVideoA.value = video
  showVideoADropdown.value = false
  searchVideoA.value = ''
}

function selectVideoB(video: TopVideo) {
  selectedVideoB.value = video
  showVideoBDropdown.value = false
  searchVideoB.value = ''
}

// Mock data for comments (since not in TopVideo type)
const mockCommentsA = computed(() => {
  if (!selectedVideoA.value) return 0
  return Math.floor(selectedVideoA.value.totalViews * 0.02)
})

const mockCommentsB = computed(() => {
  if (!selectedVideoB.value) return 0
  return Math.floor(selectedVideoB.value.totalViews * 0.02)
})

// Engagement rate calculation
const engagementRateA = computed(() => {
  if (!selectedVideoA.value || selectedVideoA.value.totalViews === 0) return 0
  return Number(((selectedVideoA.value.totalLikes / selectedVideoA.value.totalViews) * 100).toFixed(2))
})

const engagementRateB = computed(() => {
  if (!selectedVideoB.value || selectedVideoB.value.totalViews === 0) return 0
  return Number(((selectedVideoB.value.totalLikes / selectedVideoB.value.totalViews) * 100).toFixed(2))
})

// Chart data
const chartData = computed(() => {
  if (!selectedVideoA.value || !selectedVideoB.value) {
    return { labels: [], datasets: [] }
  }

  return {
    labels: ['조회수', '좋아요', '댓글', '공유'],
    datasets: [
      {
        label: selectedVideoA.value.title.substring(0, 30) + '...',
        data: [
          selectedVideoA.value.totalViews,
          selectedVideoA.value.totalLikes,
          mockCommentsA.value,
          Math.floor(selectedVideoA.value.totalViews * 0.01),
        ],
        backgroundColor: 'rgba(99, 102, 241, 0.8)',
        borderColor: 'rgb(99, 102, 241)',
        borderWidth: 1,
      },
      {
        label: selectedVideoB.value.title.substring(0, 30) + '...',
        data: [
          selectedVideoB.value.totalViews,
          selectedVideoB.value.totalLikes,
          mockCommentsB.value,
          Math.floor(selectedVideoB.value.totalViews * 0.01),
        ],
        backgroundColor: 'rgba(245, 158, 11, 0.8)',
        borderColor: 'rgb(245, 158, 11)',
        borderWidth: 1,
      },
    ],
  }
})

// Chart options
const chartOptions = computed(() => {
  const textColor = themeStore.isDark ? '#e5e7eb' : '#374151'
  const gridColor = themeStore.isDark ? '#374151' : '#e5e7eb'

  return {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'top' as const,
        labels: {
          color: textColor,
          font: {
            size: 12,
          },
        },
      },
      tooltip: {
        backgroundColor: themeStore.isDark ? '#1f2937' : '#ffffff',
        titleColor: textColor,
        bodyColor: textColor,
        borderColor: gridColor,
        borderWidth: 1,
        callbacks: {
          label: (context: any) => {
            const label = context.dataset.label || ''
            const value = context.parsed.y.toLocaleString()
            return `${label}: ${value}`
          },
        },
      },
    },
    scales: {
      x: {
        ticks: {
          color: textColor,
        },
        grid: {
          color: gridColor,
        },
      },
      y: {
        ticks: {
          color: textColor,
          callback: (value: string | number) => {
            const numValue = typeof value === 'string' ? parseFloat(value) : value
            if (numValue >= 1000000) return `${(numValue / 1000000).toFixed(1)}M`
            if (numValue >= 1000) return `${(numValue / 1000).toFixed(0)}K`
            return numValue.toLocaleString()
          },
        },
        grid: {
          color: gridColor,
        },
      },
    },
  }
})

// Comparison insights
const comparisonInsights = computed(() => {
  if (!selectedVideoA.value || !selectedVideoB.value) return []

  const insights = []
  const viewsDiff = ((selectedVideoA.value.totalViews - selectedVideoB.value.totalViews) / selectedVideoB.value.totalViews) * 100
  const likesDiff = ((selectedVideoA.value.totalLikes - selectedVideoB.value.totalLikes) / selectedVideoB.value.totalLikes) * 100
  const engagementDiff = engagementRateA.value - engagementRateB.value

  // Views comparison
  if (Math.abs(viewsDiff) > 5) {
    insights.push({
      text: `영상 A는 영상 B보다 조회수가 ${Math.abs(viewsDiff).toFixed(1)}% ${viewsDiff > 0 ? '더 높습니다' : '더 낮습니다'}`,
      isPositive: viewsDiff > 0,
    })
  } else {
    insights.push({
      text: '두 영상의 조회수는 비슷한 수준입니다',
      isPositive: null,
    })
  }

  // Likes comparison
  if (Math.abs(likesDiff) > 5) {
    insights.push({
      text: `영상 A는 영상 B보다 좋아요가 ${Math.abs(likesDiff).toFixed(1)}% ${likesDiff > 0 ? '더 많습니다' : '더 적습니다'}`,
      isPositive: likesDiff > 0,
    })
  }

  // Engagement rate comparison
  if (Math.abs(engagementDiff) > 0.1) {
    insights.push({
      text: `영상 A의 참여율(${engagementRateA.value}%)이 영상 B(${engagementRateB.value}%)보다 ${engagementDiff > 0 ? '높습니다' : '낮습니다'}`,
      isPositive: engagementDiff > 0,
    })
  }

  // Platform comparison
  const platformsA = selectedVideoA.value.platforms
  const platformsB = selectedVideoB.value.platforms
  if (platformsA.length > platformsB.length) {
    insights.push({
      text: `영상 A는 ${platformsA.length}개 플랫폼에 게시되어 영상 B(${platformsB.length}개)보다 더 많은 채널에 노출되었습니다`,
      isPositive: true,
    })
  } else if (platformsA.length < platformsB.length) {
    insights.push({
      text: `영상 B는 ${platformsB.length}개 플랫폼에 게시되어 영상 A(${platformsA.length}개)보다 더 많은 채널에 노출되었습니다`,
      isPositive: false,
    })
  }

  return insights
})

// Date formatting
function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}.${month}.${day}`
}

// Lifecycle
onMounted(() => {
  if (topVideos.value.length === 0) {
    analyticsStore.fetchAnalytics()
  }
})
</script>
