<template>
  <div class="page-frame space-y-5 pb-6">
    <!-- Compact Greeting Header -->
    <div class="flex items-center justify-between pt-2">
      <div class="flex items-center gap-2">
        <h1 class="text-title font-bold tracking-tight text-gray-900 dark:text-gray-100">
          {{ greeting }}, {{ userName }}님 👋
        </h1>
      </div>
      <div class="text-body text-gray-500 dark:text-gray-400">
        {{ currentDate }}
      </div>
    </div>

    <PageGuide :title="$t('dashboard.pageGuideTitle')" :items="($tm('dashboard.pageGuideMobile') as string[])" />

    <DashboardSkeleton v-if="loading" />

    <template v-else>
      <!-- Onboarding Banner -->
      <OnboardingBanner />

      <!-- Start Guide (if shown) -->
      <StartGuide />

      <!-- KPI Summary - 2x2 Grid -->
      <div class="grid grid-cols-2 gap-2">
        <div
          class="card cursor-pointer p-3 transition-all duration-200 hover:-translate-y-0.5"
          role="button"
          tabindex="0"
          @click="$router.push('/analytics')"
          @keydown.enter="$router.push('/analytics')"
        >
          <p class="text-body-xs text-gray-500 dark:text-gray-400">{{ $t('dashboard.totalViews') }}</p>
          <p class="mt-1 text-h1 font-bold text-gray-900 dark:text-gray-100">
            <!--
              `?? 0` 을 하지 않는다. 조회수를 수집하는 플랫폼이 없으면 서버가 `null` 을
              주는데(Tumblr 는 노트 총합을 `views` 에 넣어 제외된다), 0 으로 채우면
              실제로 0 회였던 경우와 구분되지 않는다.
            -->
            {{ kpi?.totalViews == null ? $t('analyticsView.notMeasured') : formatCompact(kpi.totalViews) }}
          </p>
          <!--
            `!== undefined` 만 검사하면 서버가 주는 `null`(비교 불가)이 통과해
            `Math.abs(null) === 0` → "↑0%" 라는 없는 사실이 뜬다.
          -->
          <div v-if="hasChange(kpi?.viewsChangePercent)" class="mt-1 flex items-center gap-1 text-body-xs">
            <span :class="changeColor(kpi!.viewsChangePercent!)">
              {{ changeIcon(kpi!.viewsChangePercent!) }}{{ Math.abs(kpi!.viewsChangePercent!) }}%
            </span>
          </div>
        </div>

        <div
          class="card cursor-pointer p-3 transition-all duration-200 hover:-translate-y-0.5"
          role="button"
          tabindex="0"
          @click="$router.push('/channels')"
          @keydown.enter="$router.push('/channels')"
        >
          <p class="text-body-xs text-gray-500 dark:text-gray-400">{{ $t('dashboard.totalSubscribers') }}</p>
          <p class="mt-1 text-h1 font-bold text-gray-900 dark:text-gray-100">
            <!--
              `?? 0` 을 하지 않는다. 구독 증가를 수집하는 플랫폼이 없으면 서버가 `null` 을
              주는데, 0 으로 채우면 실제로 0명이 늘어난 경우와 구분되지 않는다.
            -->
            {{ kpi?.totalSubscribers == null ? $t('analyticsView.notMeasured') : formatCompact(kpi.totalSubscribers) }}
          </p>
          <!-- `!== undefined` 만 검사하면 `null` 이 통과해 `Math.abs(null) === 0` 이 된다. -->
          <div v-if="kpi?.subscribersChange != null" class="mt-1 flex items-center gap-1 text-body-xs">
            <span :class="changeColor(kpi.subscribersChange)">
              {{ changeIcon(kpi.subscribersChange) }}{{ Math.abs(kpi.subscribersChange) }}
            </span>
          </div>
        </div>

        <div
          class="card cursor-pointer p-3 transition-all duration-200 hover:-translate-y-0.5"
          role="button"
          tabindex="0"
          @click="$router.push('/analytics')"
          @keydown.enter="$router.push('/analytics')"
        >
          <p class="text-body-xs text-gray-500 dark:text-gray-400">{{ $t('dashboard.totalLikes') }}</p>
          <p class="mt-1 text-h1 font-bold text-gray-900 dark:text-gray-100">
            <!-- Pinterest 는 저장 수를 `likes` 에 넣어 제외된다. `?? 0` 은 그 미수집을 감춘다. -->
            {{ kpi?.totalLikes == null ? $t('analyticsView.notMeasured') : formatCompact(kpi.totalLikes) }}
          </p>
          <div v-if="hasChange(kpi?.likesChangePercent)" class="mt-1 flex items-center gap-1 text-body-xs">
            <span :class="changeColor(kpi!.likesChangePercent!)">
              {{ changeIcon(kpi!.likesChangePercent!) }}{{ Math.abs(kpi!.likesChangePercent!) }}%
            </span>
          </div>
        </div>

        <div
          class="card cursor-pointer p-3 transition-all duration-200 hover:-translate-y-0.5"
          role="button"
          tabindex="0"
          @click="$router.push('/subscription')"
          @keydown.enter="$router.push('/subscription')"
        >
          <p class="text-body-xs text-gray-500 dark:text-gray-400">{{ $t('dashboard.aiCredits') }}</p>
          <p class="mt-1 text-h1 font-bold text-gray-900 dark:text-gray-100">
            {{ (kpi?.creditBalance ?? 0).toLocaleString() }}
          </p>
          <div class="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
            <div
              class="h-full rounded-full transition-all"
              :class="creditPercentage <= 20 ? 'bg-error' : 'bg-primary-500'"
              :style="{ width: `${creditPercentage}%` }"
            />
          </div>
        </div>
      </div>

      <!-- Quick Actions -->
      <div class="flex gap-2 overflow-x-auto pb-2 scrollbar-hide" style="scroll-snap-type: x mandatory">
        <router-link
          to="/upload"
          class="btn-primary btn-press inline-flex flex-shrink-0 items-center gap-1.5 text-body"
          style="scroll-snap-align: start"
        >
          <PlusIcon class="h-4 w-4" />
          {{ $t('dashboard.newUpload') }}
        </router-link>
        <router-link
          to="/ai"
          class="btn-secondary btn-press inline-flex flex-shrink-0 items-center gap-1.5 text-body"
          style="scroll-snap-align: start"
        >
          <SparklesIcon class="h-4 w-4" />
          {{ $t('dashboard.aiTools') }}
        </router-link>
        <router-link
          to="/schedule"
          class="btn-secondary btn-press inline-flex flex-shrink-0 items-center gap-1.5 text-body"
          style="scroll-snap-align: start"
        >
          <CalendarDaysIcon class="h-4 w-4" />
          {{ $t('dashboard.checkSchedule') }}
        </router-link>
      </div>

      <!-- Recent Videos - Horizontal Scroll -->
      <div v-if="recentVideos.length > 0" class="card p-4">
        <div class="mb-3 flex items-center justify-between">
          <h3 class="text-body font-semibold text-gray-900 dark:text-gray-100">{{ $t('dashboard.recentUploads') }}</h3>
          <router-link to="/videos" class="text-body-xs text-primary-600 hover:underline">
            {{ $t('dashboard.viewAll') }}
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
            <p class="mt-2 line-clamp-1 text-caption text-gray-900 dark:text-gray-100">
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
          <h3 class="text-body font-semibold text-gray-900 dark:text-gray-100">{{ $t('dashboard.viewsTrend') }}</h3>
          <div class="flex rounded-lg border border-gray-200 dark:border-gray-700">
            <button
              class="px-2 py-1 text-body-xs transition-colors"
              :class="period === '7d' ? 'bg-primary-500 text-white' : 'text-gray-600 dark:text-gray-300'"
              @click="emit('setPeriod', '7d')"
            >
              {{ $t('dashboard.days7') }}
            </button>
            <button
              class="px-2 py-1 text-body-xs transition-colors"
              :class="period === '30d' ? 'bg-primary-500 text-white' : 'text-gray-600 dark:text-gray-300'"
              @click="emit('setPeriod', '30d')"
            >
              {{ $t('dashboard.days30') }}
            </button>
          </div>
        </div>
        <div class="h-48">
          <TrendChart
            :data="trendData"
            :period="period"
            @update:period="(p) => emit('setPeriod', p)"
          />
        </div>
      </div>

      <!-- Upcoming Schedules - Today & Tomorrow only -->
      <div v-if="todayAndTomorrowSchedules.length > 0" class="card">
        <div class="mb-3 flex items-center justify-between">
          <h3 class="text-body font-semibold text-gray-900 dark:text-gray-100">{{ $t('dashboard.scheduledUploads') }}</h3>
          <router-link to="/schedule" class="text-body-xs text-primary-600 hover:underline">
            {{ $t('dashboard.viewCalendar') }}
          </router-link>
        </div>
        <div class="divide-y divide-gray-100 dark:divide-gray-700">
          <div v-for="group in groupedSchedules" :key="group.label" class="py-3 first:pt-0 last:pb-0">
            <p class="mb-2 text-caption text-gray-500 dark:text-gray-400">{{ group.label }}</p>
            <div class="space-y-2">
              <div
                v-for="schedule in group.schedules"
                :key="schedule.id"
                class="flex items-start gap-2"
              >
                <div class="flex-shrink-0 text-caption text-primary-600 dark:text-primary-400">
                  {{ formatTime(schedule.scheduledAt) }}
                </div>
                <div class="min-w-0 flex-1">
                  <p class="line-clamp-1 text-caption text-gray-900 dark:text-gray-100">
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
      <EmptyState
        v-else
        variant="compact"
        :title="$t('dashboard.noScheduleTitle')"
        :description="$t('dashboard.noScheduleDescription')"
        :icon="CalendarDaysIcon"
        :action-label="$t('dashboard.createSchedule')"
        action-to="/schedule"
        :secondary-action-label="$t('dashboard.uploadVideo')"
        secondary-action-to="/upload"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { PlusIcon, SparklesIcon, CalendarDaysIcon, FilmIcon } from '@heroicons/vue/24/outline'
import DashboardSkeleton from '@/components/dashboard/DashboardSkeleton.vue'
import StartGuide from '@/components/dashboard/StartGuide.vue'
import TrendChart from '@/components/dashboard/TrendChart.vue'
import PlatformBadge from '@/components/common/PlatformBadge.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import OnboardingBanner from '@/components/common/OnboardingBanner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import type { DashboardKpi, TrendDataPoint } from '@/types/analytics'
import type { Video } from '@/types/video'
import type { Schedule } from '@/types/schedule'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/ko'

dayjs.extend(relativeTime)
dayjs.locale('ko')

interface Props {
  loading: boolean
  kpi: DashboardKpi | null
  greeting: string
  userName: string
  currentDate: string
  creditPercentage: number
  recentVideos: Video[]
  trendData: TrendDataPoint[]
  period: '7d' | '30d'
  todayAndTomorrowSchedules: Schedule[]
  groupedSchedules: { label: string; schedules: Schedule[] }[]
}

defineProps<Props>()

const emit = defineEmits<{
  setPeriod: [period: '7d' | '30d']
}>()

function formatCompact(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`
  return value.toLocaleString()
}

/**
 * 증감을 표시할 수 있는가.
 *
 * 서버는 이전 기간 데이터가 없으면 `null` 을 준다(비교 불가). `null` 과 `undefined` 를
 * 함께 걸러야 `Math.abs(null) === 0` 으로 인한 "↑0%" 오표시를 막는다.
 */
function hasChange(change: number | null | undefined): boolean {
  return typeof change === 'number' && Number.isFinite(change)
}

function changeIcon(change: number): string {
  return change >= 0 ? '↑' : '↓'
}

function changeColor(change: number): string {
  return change >= 0 ? 'text-success-strong' : 'text-error-strong'
}

function timeAgo(date: string): string {
  return dayjs(date).fromNow()
}

function formatTime(date: string): string {
  return dayjs(date).format('HH:mm')
}
</script>

<style scoped>
.scrollbar-hide {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
.scrollbar-hide::-webkit-scrollbar {
  display: none;
}
</style>
