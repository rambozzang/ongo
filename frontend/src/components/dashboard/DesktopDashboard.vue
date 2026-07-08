<template>
  <div class="px-6 pb-8 pt-4">
    <!-- Personalized Header -->
    <div class="mb-8">
      <div class="flex items-start justify-between">
        <div class="flex-1">
          <h1 class="text-3xl font-bold tracking-tight text-gray-900 dark:text-gray-100">
            {{ greeting }}, {{ userName }}님!
          </h1>
          <p class="mt-2 text-base font-medium text-gray-500 dark:text-gray-400">
            <span v-if="todayScheduleCount > 0">
              {{ $t('dashboard.scheduledToday', { count: todayScheduleCount }) }}
            </span>
            <span v-else>{{ $t('dashboard.noScheduledToday') }}</span>
            <span class="mx-2 text-gray-300 dark:text-gray-600">·</span>
            <span>{{ $t('dashboard.creditsRemaining', { count: creditBalance }) }}</span>
          </p>
        </div>
        <button
          class="btn-secondary btn-press inline-flex items-center gap-1.5 text-sm"
          @click="emit('openCustomizer')"
        >
          <Cog6ToothIcon class="h-4 w-4" />
          {{ $t('dashboard.customize') }}
        </button>
      </div>
      <div class="mt-4 flex flex-wrap gap-2">
        <router-link to="/upload" class="btn-primary btn-press inline-flex items-center gap-1.5 text-sm">
          <PlusIcon class="h-4 w-4" />
          {{ $t('dashboard.newUpload') }}
        </router-link>
        <router-link to="/ai" class="btn-secondary btn-press inline-flex items-center gap-1.5 text-sm">
          <SparklesIcon class="h-4 w-4" />
          {{ $t('dashboard.aiTools') }}
        </router-link>
        <router-link to="/schedule" class="btn-secondary btn-press inline-flex items-center gap-1.5 text-sm">
          <CalendarDaysIcon class="h-4 w-4" />
          {{ $t('dashboard.checkSchedule') }}
        </router-link>
      </div>
    </div>

    <PageGuide :title="$t('dashboard.pageGuideTitle')" :items="($tm('dashboard.pageGuideDesktop') as string[])" />

    <DashboardSkeleton v-if="loading" />

    <template v-else>
      <!-- Onboarding Banner -->
      <OnboardingBanner />

      <!-- Start Guide -->
      <StartGuide />

      <!-- Recycle Suggestions -->
      <RecycleSuggestions @recycle="(video) => emit('recycle', video)" />

      <!-- Favorites Section -->
      <FavoritesSection />

      <!-- Dynamic Widgets with TransitionGroup -->
      <div class="space-y-6">
        <TransitionGroup name="widget-list">
          <template v-for="widget in visibleWidgets" :key="widget.id">
            <!-- Summary Cards Widget -->
            <div v-if="widget.id === 'summary'" class="grid grid-cols-2 gap-4 desktop:grid-cols-4">
              <SummaryCard
                :title="$t('dashboard.totalViews')"
                :value="kpi?.totalViews ?? 0"
                :change="kpi?.viewsChangePercent"
                change-type="percent"
                :icon="EyeIcon"
                color="blue"
                @click="$router.push('/analytics')"
              />
              <SummaryCard
                :title="$t('dashboard.totalSubscribers')"
                :value="kpi?.totalSubscribers ?? 0"
                :change="kpi?.subscribersChange"
                change-type="number"
                :icon="UsersIcon"
                color="green"
                @click="$router.push('/channels')"
              />
              <SummaryCard
                :title="$t('dashboard.totalLikes')"
                :value="kpi?.totalLikes ?? 0"
                :change="kpi?.likesChangePercent"
                change-type="percent"
                :icon="HeartIcon"
                color="rose"
                @click="$router.push('/analytics')"
              />
              <SummaryCard
                :title="$t('dashboard.aiCredits')"
                :value="kpi?.creditBalance ?? 0"
                :icon="SparklesIcon"
                color="purple"
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
                @update:period="(p) => emit('setPeriod', p)"
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
    <WidgetCustomizer
      :model-value="showWidgetCustomizer"
      @update:model-value="(v) => emit('update:showWidgetCustomizer', v)"
    />
  </div>
</template>

<script setup lang="ts">
import {
  EyeIcon,
  UsersIcon,
  HeartIcon,
  SparklesIcon,
  PlusIcon,
  CalendarDaysIcon,
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
import RecycleSuggestions from '@/components/video/RecycleSuggestions.vue'
import FavoritesSection from '@/components/video/FavoritesSection.vue'
import AudienceGrowthWidget from '@/components/dashboard/AudienceGrowthWidget.vue'
import ContentCalendarWidget from '@/components/dashboard/ContentCalendarWidget.vue'
import TopPerformingWidget from '@/components/dashboard/TopPerformingWidget.vue'
import QuickActionsWidget from '@/components/dashboard/QuickActionsWidget.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import OnboardingBanner from '@/components/common/OnboardingBanner.vue'
import type { DashboardKpi, TrendDataPoint, PlatformComparison, TopVideo } from '@/types/analytics'
import type { Video } from '@/types/video'
import type { Schedule } from '@/types/schedule'
import type { WidgetConfig } from '@/stores/widgetSettings'

interface Props {
  loading: boolean
  kpi: DashboardKpi | null
  greeting: string
  userName: string
  todayScheduleCount: number
  creditBalance: number
  creditPercentage: number
  trendData: TrendDataPoint[]
  period: '7d' | '30d'
  platformComparison: PlatformComparison[]
  recentVideos: Video[]
  upcomingSchedules: Schedule[]
  topVideos: TopVideo[]
  visibleWidgets: WidgetConfig[]
  showWidgetCustomizer: boolean
}

defineProps<Props>()

const emit = defineEmits<{
  setPeriod: [period: '7d' | '30d']
  openCustomizer: []
  recycle: [video: Video]
  'update:showWidgetCustomizer': [value: boolean]
}>()
</script>
