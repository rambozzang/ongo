<template>
  <!-- Mobile Layout (below 768px) -->
  <div v-if="!isTablet" class="space-y-4">
    <!-- Compact Greeting Header -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-2">
        <h1 class="text-base font-semibold text-gray-900 dark:text-gray-100">
          {{ greeting }}, {{ userName }}님 👋
        </h1>
      </div>
      <div class="text-sm text-gray-500 dark:text-gray-400">
        {{ currentDate }}
      </div>
    </div>

    <PageGuide title="대시보드" :items="[
      '상단 KPI 카드를 탭하면 해당 상세 페이지(분석, 채널, 구독)로 바로 이동합니다',
      '빠른 업로드·AI 도구·일정 확인 버튼으로 자주 쓰는 기능에 즉시 접근하세요',
      '최근 업로드 영상을 가로 스크롤하며 확인하고, 탭하면 상세 페이지로 이동합니다',
      '7일/30일 조회수 트렌드 차트로 채널 성장 추이를 한눈에 파악하세요',
      '예약 업로드 섹션에서 오늘·내일 예정된 게시 일정을 확인할 수 있습니다',
    ]" />

    <DashboardSkeleton v-if="loading" />

    <template v-else>
      <!-- Start Guide (if shown) -->
      <StartGuide />

      <!-- KPI Summary - 2x2 Grid -->
      <div class="grid grid-cols-2 gap-2">
        <div
          class="card cursor-pointer p-3 transition-all duration-200 hover:-translate-y-0.5"
          @click="$router.push('/analytics')"
        >
          <p class="text-xs text-gray-500 dark:text-gray-400">총 조회수</p>
          <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ formatCompact(kpi?.totalViews ?? 0) }}
          </p>
          <div v-if="kpi?.viewsChangePercent !== undefined" class="mt-1 flex items-center gap-1 text-xs">
            <span :class="changeColor(kpi.viewsChangePercent)">
              {{ changeIcon(kpi.viewsChangePercent) }}{{ Math.abs(kpi.viewsChangePercent) }}%
            </span>
          </div>
        </div>

        <div
          class="card cursor-pointer p-3 transition-all duration-200 hover:-translate-y-0.5"
          @click="$router.push('/channels')"
        >
          <p class="text-xs text-gray-500 dark:text-gray-400">총 구독자</p>
          <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ formatCompact(kpi?.totalSubscribers ?? 0) }}
          </p>
          <div v-if="kpi?.subscribersChange !== undefined" class="mt-1 flex items-center gap-1 text-xs">
            <span :class="changeColor(kpi.subscribersChange)">
              {{ changeIcon(kpi.subscribersChange) }}{{ Math.abs(kpi.subscribersChange) }}
            </span>
          </div>
        </div>

        <div
          class="card cursor-pointer p-3 transition-all duration-200 hover:-translate-y-0.5"
          @click="$router.push('/analytics')"
        >
          <p class="text-xs text-gray-500 dark:text-gray-400">총 좋아요</p>
          <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ formatCompact(kpi?.totalLikes ?? 0) }}
          </p>
          <div v-if="kpi?.likesChangePercent !== undefined" class="mt-1 flex items-center gap-1 text-xs">
            <span :class="changeColor(kpi.likesChangePercent)">
              {{ changeIcon(kpi.likesChangePercent) }}{{ Math.abs(kpi.likesChangePercent) }}%
            </span>
          </div>
        </div>

        <div
          class="card cursor-pointer p-3 transition-all duration-200 hover:-translate-y-0.5"
          @click="$router.push('/subscription')"
        >
          <p class="text-xs text-gray-500 dark:text-gray-400">AI 크레딧</p>
          <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ (kpi?.creditBalance ?? 0).toLocaleString() }}
          </p>
          <div class="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
            <div
              class="h-full rounded-full transition-all"
              :class="creditPercentage <= 20 ? 'bg-red-500' : 'bg-primary-500'"
              :style="{ width: `${creditPercentage}%` }"
            />
          </div>
        </div>
      </div>

      <!-- Quick Actions -->
      <div class="flex gap-2 overflow-x-auto pb-2 scrollbar-hide" style="scroll-snap-type: x mandatory">
        <router-link
          to="/upload"
          class="btn-primary btn-press inline-flex flex-shrink-0 items-center gap-1.5 text-sm"
          style="scroll-snap-align: start"
        >
          <PlusIcon class="h-4 w-4" />
          새 영상 업로드
        </router-link>
        <router-link
          to="/ai"
          class="btn-secondary btn-press inline-flex flex-shrink-0 items-center gap-1.5 text-sm"
          style="scroll-snap-align: start"
        >
          <SparklesIcon class="h-4 w-4" />
          AI 도구
        </router-link>
        <router-link
          to="/schedule"
          class="btn-secondary btn-press inline-flex flex-shrink-0 items-center gap-1.5 text-sm"
          style="scroll-snap-align: start"
        >
          <CalendarDaysIcon class="h-4 w-4" />
          일정 확인
        </router-link>
      </div>

      <!-- Recent Videos - Horizontal Scroll -->
      <div v-if="recentVideos.length > 0" class="card p-4">
        <div class="mb-3 flex items-center justify-between">
          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">최근 업로드</h3>
          <router-link to="/videos" class="text-xs text-primary-600 hover:underline">
            전체 보기 →
          </router-link>
        </div>
        <div class="flex gap-3 overflow-x-auto pb-2 scrollbar-hide" style="scroll-snap-type: x mandatory">
          <div
            v-for="video in recentVideos.slice(0, 5)"
            :key="video.id"
            class="flex-shrink-0 cursor-pointer"
            style="width: 160px; scroll-snap-align: start"
            @click="$router.push(`/videos/${video.id}`)"
          >
            <div class="aspect-video w-full overflow-hidden rounded-lg bg-gray-100 dark:bg-gray-800">
              <img
                v-if="video.thumbnailUrl"
                :src="video.thumbnailUrl"
                :alt="video.title"
                class="h-full w-full object-cover"
              />
              <div v-else class="flex h-full w-full items-center justify-center">
                <FilmIcon class="h-6 w-6 text-gray-300 dark:text-gray-600" />
              </div>
            </div>
            <p class="mt-2 line-clamp-1 text-xs font-medium text-gray-900 dark:text-gray-100">
              {{ video.title }}
            </p>
            <div class="mt-1 flex flex-wrap items-center gap-1">
              <PlatformBadge
                v-for="upload in video.uploads.slice(0, 2)"
                :key="upload.platform"
                :platform="upload.platform"
                class="scale-75 origin-left"
              />
            </div>
            <p class="mt-1 text-[11px] text-gray-500 dark:text-gray-400">
              {{ timeAgo(video.createdAt) }}
            </p>
          </div>
        </div>
      </div>

      <!-- Trend Chart - Simplified -->
      <div class="card" style="height: 280px">
        <div class="mb-3 flex items-center justify-between">
          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">조회수 트렌드</h3>
          <div class="flex rounded-lg border border-gray-200 dark:border-gray-700">
            <button
              class="px-2 py-1 text-xs transition-colors"
              :class="period === '7d' ? 'bg-primary-500 text-white' : 'text-gray-600 dark:text-gray-300'"
              @click="dashboardStore.setPeriod('7d')"
            >
              7일
            </button>
            <button
              class="px-2 py-1 text-xs transition-colors"
              :class="period === '30d' ? 'bg-primary-500 text-white' : 'text-gray-600 dark:text-gray-300'"
              @click="dashboardStore.setPeriod('30d')"
            >
              30일
            </button>
          </div>
        </div>
        <div class="h-48">
          <TrendChart
            :data="trendData"
            :period="period"
            @update:period="dashboardStore.setPeriod"
          />
        </div>
      </div>

      <!-- Upcoming Schedules - Today & Tomorrow only -->
      <div v-if="todayAndTomorrowSchedules.length > 0" class="card">
        <div class="mb-3 flex items-center justify-between">
          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">예약 업로드</h3>
          <router-link to="/schedule" class="text-xs text-primary-600 hover:underline">
            캘린더 보기 →
          </router-link>
        </div>
        <div class="divide-y divide-gray-100 dark:divide-gray-700">
          <div v-for="group in groupedSchedules" :key="group.label" class="py-3 first:pt-0 last:pb-0">
            <p class="mb-2 text-xs font-medium text-gray-500 dark:text-gray-400">{{ group.label }}</p>
            <div class="space-y-2">
              <div
                v-for="schedule in group.schedules"
                :key="schedule.id"
                class="flex items-start gap-2"
              >
                <div class="flex-shrink-0 text-xs font-medium text-primary-600 dark:text-primary-400">
                  {{ formatTime(schedule.scheduledAt) }}
                </div>
                <div class="min-w-0 flex-1">
                  <p class="line-clamp-1 text-xs font-medium text-gray-900 dark:text-gray-100">
                    {{ schedule.videoTitle }}
                  </p>
                  <div class="mt-1 flex flex-wrap gap-1">
                    <PlatformBadge
                      v-for="sp in schedule.platforms"
                      :key="sp.platform"
                      :platform="sp.platform"
                      class="scale-75 origin-left"
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="card py-8 text-center text-sm text-gray-400 dark:text-gray-500">
        예정된 일정이 없습니다
      </div>
    </template>

    <!-- Recycle Modal -->
    <RecycleModal
      v-if="recycleVideo"
      v-model="showRecycleModal"
      :video="recycleVideo"
      @confirm="handleRecycleConfirm"
    />
  </div>

  <!-- Desktop/Tablet Layout (768px+) -->
  <div v-else>
    <!-- Personalized Header -->
    <div class="mb-6">
      <div class="flex items-start justify-between">
        <div class="flex-1">
          <h1 class="text-h1 text-gray-900 dark:text-gray-100">
            {{ greeting }}, {{ userName }}님!
          </h1>
          <p class="mt-1 text-body text-gray-500 dark:text-gray-400">
            <span v-if="todayScheduleCount > 0">
              오늘 {{ todayScheduleCount }}개 영상이 예약되어 있어요
            </span>
            <span v-else>오늘 예약된 영상이 없어요</span>
            <span class="mx-2 text-gray-300 dark:text-gray-600">·</span>
            <span>AI 크레딧 {{ creditBalance }}개 남음</span>
          </p>
        </div>
        <button
          class="btn-secondary btn-press inline-flex items-center gap-1.5 text-sm"
          @click="showWidgetCustomizer = true"
        >
          <Cog6ToothIcon class="h-4 w-4" />
          커스터마이즈
        </button>
      </div>
      <div class="mt-4 flex flex-wrap gap-2">
        <router-link to="/upload" class="btn-primary btn-press inline-flex items-center gap-1.5 text-sm">
          <PlusIcon class="h-4 w-4" />
          새 영상 업로드
        </router-link>
        <router-link to="/ai" class="btn-secondary btn-press inline-flex items-center gap-1.5 text-sm">
          <SparklesIcon class="h-4 w-4" />
          AI 도구
        </router-link>
        <router-link to="/schedule" class="btn-secondary btn-press inline-flex items-center gap-1.5 text-sm">
          <CalendarDaysIcon class="h-4 w-4" />
          일정 확인
        </router-link>
      </div>
    </div>

    <PageGuide title="대시보드" :items="[
      '상단 KPI 카드(조회수·구독자·좋아요·크레딧)를 클릭하면 해당 상세 페이지로 바로 이동합니다',
      '커스터마이즈 버튼으로 대시보드 위젯 순서를 변경하고 불필요한 위젯을 숨길 수 있습니다',
      '조회수 트렌드 차트에서 YouTube·TikTok·Instagram·Naver Clip별 성과를 비교하세요',
      '재활용 추천 섹션에서 과거 인기 콘텐츠를 다시 게시할 수 있습니다',
      '예약 일정 위젯에서 다가오는 게시 스케줄을 한눈에 확인하세요',
    ]" />

    <DashboardSkeleton v-if="loading" />

    <template v-else>
      <!-- Start Guide -->
      <StartGuide />

      <!-- Recycle Suggestions -->
      <RecycleSuggestions @recycle="handleRecycleVideo" />

      <!-- Favorites Section -->
      <FavoritesSection />

      <!-- Dynamic Widgets with TransitionGroup -->
      <div class="space-y-6">
        <TransitionGroup name="widget-list">
          <template v-for="widget in visibleWidgets" :key="widget.id">
            <!-- Summary Cards Widget -->
            <div v-if="widget.id === 'summary'" class="grid grid-cols-2 gap-4 desktop:grid-cols-4">
              <SummaryCard
                title="총 조회수"
                :value="kpi?.totalViews ?? 0"
                :change="kpi?.viewsChangePercent"
                change-type="percent"
                :icon="EyeIcon"
                @click="$router.push('/analytics')"
              />
              <SummaryCard
                title="총 구독자"
                :value="kpi?.totalSubscribers ?? 0"
                :change="kpi?.subscribersChange"
                change-type="number"
                :icon="UsersIcon"
                @click="$router.push('/channels')"
              />
              <SummaryCard
                title="총 좋아요"
                :value="kpi?.totalLikes ?? 0"
                :change="kpi?.likesChangePercent"
                change-type="percent"
                :icon="HeartIcon"
                @click="$router.push('/analytics')"
              />
              <SummaryCard
                title="AI 크레딧"
                :value="kpi?.creditBalance ?? 0"
                :icon="SparklesIcon"
                format="number"
                :progress-bar="true"
                :progress-percent="creditPercentage"
                @click="$router.push('/subscription')"
              />
            </div>

            <!-- AI Weekly Digest Widget -->
            <div v-else-if="widget.id === 'weeklyDigest'" class="grid grid-cols-1 gap-4 desktop:grid-cols-2">
              <WeeklyDigestCard />
              <ContentGapCard />
            </div>

            <!-- Trend Chart Widget -->
            <div v-else-if="widget.id === 'trend'">
              <TrendChart
                :data="trendData"
                :period="period"
                @update:period="dashboardStore.setPeriod"
              />
            </div>

            <!-- Platform Pie Chart Widget -->
            <PlatformPieChart
              v-else-if="widget.id === 'platform'"
              :data="platformComparison"
            />

            <!-- Recent Videos Widget -->
            <RecentVideosList
              v-else-if="widget.id === 'videos'"
              :videos="recentVideos"
            />

            <!-- Upcoming Schedules Widget -->
            <UpcomingSchedules
              v-else-if="widget.id === 'schedules'"
              :schedules="upcomingSchedules"
            />

            <!-- Mini Widgets Grid -->
            <div v-else-if="widget.id === 'miniWidgets'" class="grid grid-cols-2 gap-4 desktop:grid-cols-4">
              <AudienceGrowthWidget
                :total-subscribers="kpi?.totalSubscribers ?? 0"
                :change="kpi?.subscribersChange"
                :trend-data="trendData"
              />
              <ContentCalendarWidget :schedules="upcomingSchedules" />
              <TopPerformingWidget :top-videos="topVideos" />
              <QuickActionsWidget />
            </div>
          </template>
        </TransitionGroup>
      </div>
    </template>

    <!-- Widget Customizer Panel -->
    <WidgetCustomizer v-model="showWidgetCustomizer" />

    <!-- Recycle Modal -->
    <RecycleModal
      v-if="recycleVideo"
      v-model="showRecycleModal"
      :video="recycleVideo"
      @confirm="handleRecycleConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useMediaQuery } from '@vueuse/core'
import {
  EyeIcon,
  UsersIcon,
  HeartIcon,
  SparklesIcon,
  PlusIcon,
  CalendarDaysIcon,
  FilmIcon,
  Cog6ToothIcon,
} from '@heroicons/vue/24/outline'
import DashboardSkeleton from '@/components/dashboard/DashboardSkeleton.vue'
import StartGuide from '@/components/dashboard/StartGuide.vue'
import SummaryCard from '@/components/dashboard/SummaryCard.vue'
import TrendChart from '@/components/dashboard/TrendChart.vue'
import PlatformPieChart from '@/components/dashboard/PlatformPieChart.vue'
import RecentVideosList from '@/components/dashboard/RecentVideosList.vue'
import UpcomingSchedules from '@/components/dashboard/UpcomingSchedules.vue'
import WidgetCustomizer from '@/components/dashboard/WidgetCustomizer.vue'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import RecycleSuggestions from '@/components/video/RecycleSuggestions.vue'
import RecycleModal from '@/components/video/RecycleModal.vue'
import FavoritesSection from '@/components/video/FavoritesSection.vue'
import AudienceGrowthWidget from '@/components/dashboard/AudienceGrowthWidget.vue'
import ContentCalendarWidget from '@/components/dashboard/ContentCalendarWidget.vue'
import TopPerformingWidget from '@/components/dashboard/TopPerformingWidget.vue'
import QuickActionsWidget from '@/components/dashboard/QuickActionsWidget.vue'
import WeeklyDigestCard from '@/components/dashboard/WeeklyDigestCard.vue'
import ContentGapCard from '@/components/dashboard/ContentGapCard.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import { useDashboardStore } from '@/stores/dashboard'
import { useAuthStore } from '@/stores/auth'
import { useCreditStore } from '@/stores/credit'
import { useWidgetSettingsStore } from '@/stores/widgetSettings'
import type { Video } from '@/types/video'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/ko'

dayjs.extend(relativeTime)
dayjs.locale('ko')

const dashboardStore = useDashboardStore()
const authStore = useAuthStore()
const creditStore = useCreditStore()
const widgetSettingsStore = useWidgetSettingsStore()

const { kpi, trendData, platformComparison, recentVideos, upcomingSchedules, topVideos, loading, period } =
  storeToRefs(dashboardStore)

// Media query for responsive layout
const isTablet = useMediaQuery('(min-width: 768px)')

const userName = computed(() => authStore.user?.nickname || authStore.user?.name || '크리에이터')
const creditBalance = computed(() => creditStore.totalBalance)
const todayScheduleCount = computed(() => {
  const today = dayjs().startOf('day')
  return upcomingSchedules.value.filter(s => dayjs(s.scheduledAt).isSame(today, 'day')).length
})

// Widget Customizer
const showWidgetCustomizer = ref(false)

// Get visible widgets
const visibleWidgets = computed(() => widgetSettingsStore.getVisibleWidgets())

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour >= 6 && hour < 12) return '좋은 아침이에요'
  if (hour >= 12 && hour < 18) return '좋은 오후예요'
  if (hour >= 18 && hour < 22) return '좋은 저녁이에요'
  return '밤늦게까지 수고하세요'
})

const currentDate = computed(() => {
  const now = new Date()
  return `${now.getMonth() + 1}/${now.getDate()}`
})

const creditPercentage = computed(() => {
  if (!kpi.value || kpi.value.creditTotal === 0) return 0
  return Math.round((kpi.value.creditBalance / kpi.value.creditTotal) * 100)
})

// Mobile-specific helper functions
function formatCompact(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`
  return value.toLocaleString()
}

function changeIcon(change: number): string {
  return change >= 0 ? '↑' : '↓'
}

function changeColor(change: number): string {
  return change >= 0 ? 'text-green-600' : 'text-red-600'
}

function timeAgo(date: string): string {
  return dayjs(date).fromNow()
}

function formatTime(date: string): string {
  return dayjs(date).format('HH:mm')
}

// Filter schedules for today and tomorrow only (mobile)
const todayAndTomorrowSchedules = computed(() => {
  const now = dayjs()
  const tomorrow = now.add(1, 'day')

  return upcomingSchedules.value.filter((schedule) => {
    const scheduleDate = dayjs(schedule.scheduledAt)
    return scheduleDate.isBefore(tomorrow.endOf('day'))
  })
})

// Group schedules by today/tomorrow
const groupedSchedules = computed(() => {
  const now = dayjs()
  const today = now.startOf('day')
  const tomorrow = today.add(1, 'day')

  const groups = [
    {
      label: '오늘',
      schedules: todayAndTomorrowSchedules.value.filter((s) => {
        const scheduleDate = dayjs(s.scheduledAt)
        return scheduleDate.isSame(today, 'day')
      }),
    },
    {
      label: '내일',
      schedules: todayAndTomorrowSchedules.value.filter((s) => {
        const scheduleDate = dayjs(s.scheduledAt)
        return scheduleDate.isSame(tomorrow, 'day')
      }),
    },
  ]

  return groups.filter(g => g.schedules.length > 0)
})

// Recycle Modal
const showRecycleModal = ref(false)
const recycleVideo = ref<Video | null>(null)

function handleRecycleVideo(video: Video) {
  recycleVideo.value = video
  showRecycleModal.value = true
}

async function handleRecycleConfirm() {
  await dashboardStore.fetchDashboard()
}

onMounted(() => {
  dashboardStore.fetchDashboard()
  creditStore.fetchBalance()
})
</script>
