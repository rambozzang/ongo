<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900">
    <!-- Loading -->
    <div v-if="agencyStore.portalLoading" class="flex min-h-screen items-center justify-center">
      <div class="text-center">
        <div class="mx-auto h-8 w-8 animate-spin rounded-full border-4 border-indigo-200 border-t-indigo-600" />
        <p class="mt-3 text-sm text-gray-500 dark:text-gray-400">{{ $t('clientPortal.loading') }}</p>
      </div>
    </div>

    <!-- Portal Content -->
    <template v-else-if="portal">
      <!-- Header -->
      <div class="border-b border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800">
        <div class="mx-auto max-w-5xl px-4 py-6 sm:px-6">
          <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div class="flex items-center gap-4">
              <div
                class="flex h-14 w-14 items-center justify-center rounded-full bg-indigo-600 text-xl font-bold text-white"
              >
                {{ portal.creator.name.charAt(0) }}
              </div>
              <div>
                <h1 class="text-xl font-bold text-gray-900 dark:text-gray-100">
                  {{ portal.creator.name }}
                </h1>
                <p v-if="portal.creator.bio" class="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
                  {{ portal.creator.bio }}
                </p>
              </div>
            </div>
            <button
              class="btn-secondary inline-flex items-center gap-1.5 text-sm"
              @click="handleDownloadPdf"
            >
              <ArrowDownTrayIcon class="h-4 w-4" />
              {{ $t('clientPortal.downloadReport') }}
            </button>
          </div>

          <!-- Channel badges -->
          <div class="mt-4 flex flex-wrap gap-2">
            <span
              v-for="channel in portal.channels"
              :key="channel.id"
              class="inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm font-medium"
              :class="platformBadgeClass(channel.platform)"
            >
              {{ platformLabel(channel.platform) }}
              <span class="opacity-70">{{ formatCompact(channel.subscriberCount) }}</span>
            </span>
          </div>
        </div>
      </div>

      <div class="mx-auto max-w-5xl px-4 py-6 sm:px-6">
        <!-- KPI Cards -->
        <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
          <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('clientPortal.totalViews') }}</p>
            <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
              {{ formatCompact(portal.kpi.totalViews) }}
            </p>
            <div class="mt-1 flex items-center gap-1 text-xs">
              <span :class="portal.kpi.viewsChangePercent >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ portal.kpi.viewsChangePercent >= 0 ? '↑' : '↓' }} {{ Math.abs(portal.kpi.viewsChangePercent) }}%
              </span>
            </div>
          </div>
          <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('clientPortal.subscribers') }}</p>
            <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
              {{ formatCompact(portal.kpi.totalSubscribers) }}
            </p>
            <div class="mt-1 flex items-center gap-1 text-xs">
              <span :class="portal.kpi.subscribersChange >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ portal.kpi.subscribersChange >= 0 ? '↑' : '↓' }} {{ formatCompact(Math.abs(portal.kpi.subscribersChange)) }}
              </span>
            </div>
          </div>
          <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('clientPortal.engagementRate') }}</p>
            <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
              {{ portal.kpi.engagementRate.toFixed(1) }}%
            </p>
            <div class="mt-1 flex items-center gap-1 text-xs">
              <span :class="portal.kpi.engagementChangePercent >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ portal.kpi.engagementChangePercent >= 0 ? '↑' : '↓' }} {{ Math.abs(portal.kpi.engagementChangePercent) }}%
              </span>
            </div>
          </div>
          <div class="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-gray-800">
            <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('clientPortal.estimatedROI') }}</p>
            <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
              {{ portal.kpi.estimatedROI }}%
            </p>
            <div class="mt-1 flex items-center gap-1 text-xs">
              <span :class="portal.kpi.roiChangePercent >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ portal.kpi.roiChangePercent >= 0 ? '↑' : '↓' }} {{ Math.abs(portal.kpi.roiChangePercent) }}%
              </span>
            </div>
          </div>
        </div>

        <!-- Performance Chart -->
        <div class="mb-6 rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-800">
          <h2 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('clientPortal.recentPerformance') }}
          </h2>
          <div class="relative h-48">
            <div class="absolute left-0 top-0 flex h-full flex-col justify-between pr-2">
              <span class="text-[10px] text-gray-400">{{ formatCompact(performanceMax) }}</span>
              <span class="text-[10px] text-gray-400">{{ formatCompact(performanceMax / 2) }}</span>
              <span class="text-[10px] text-gray-400">0</span>
            </div>
            <div class="ml-10 flex h-full items-end gap-1">
              <div
                v-for="(point, idx) in portal.recentPerformance"
                :key="idx"
                class="flex flex-1 flex-col items-center"
              >
                <div
                  class="w-full rounded-t bg-indigo-500 transition-all duration-300 hover:bg-indigo-600 dark:bg-indigo-400 dark:hover:bg-indigo-300"
                  :style="{ height: `${Math.max((point.views / performanceMax) * 100, 2)}%` }"
                  :title="`${point.date}: ${point.views.toLocaleString()}`"
                />
                <span v-if="idx % 2 === 0" class="mt-1 text-[9px] text-gray-400">
                  {{ point.date.split('-')[2] }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <div class="grid gap-6 desktop:grid-cols-2">
          <!-- Campaigns -->
          <div class="rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-800">
            <h2 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('clientPortal.campaigns') }}
            </h2>
            <div class="space-y-4">
              <div
                v-for="campaign in portal.campaigns"
                :key="campaign.id"
                class="rounded-lg border border-gray-100 p-4 dark:border-gray-700"
              >
                <div class="flex items-center justify-between">
                  <h3 class="text-sm font-medium text-gray-900 dark:text-gray-100">{{ campaign.name }}</h3>
                  <span
                    class="inline-flex rounded-full px-2 py-0.5 text-xs font-medium"
                    :class="campaignStatusClass(campaign.status)"
                  >
                    {{ $t(`clientPortal.campaignStatus.${campaign.status}`) }}
                  </span>
                </div>
                <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
                  {{ formatDateShort(campaign.startDate) }} ~ {{ formatDateShort(campaign.endDate) }}
                </p>
                <!-- Progress bar -->
                <div class="mt-3">
                  <div class="mb-1 flex items-center justify-between text-xs">
                    <span class="text-gray-500 dark:text-gray-400">{{ $t('clientPortal.deliverables') }}</span>
                    <span class="font-medium text-gray-900 dark:text-gray-100">
                      {{ campaign.completedDeliverables }}/{{ campaign.deliverables }}
                    </span>
                  </div>
                  <div class="h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
                    <div
                      class="h-full rounded-full bg-indigo-500 transition-all"
                      :style="{ width: `${campaign.deliverables > 0 ? (campaign.completedDeliverables / campaign.deliverables) * 100 : 0}%` }"
                    />
                  </div>
                </div>
                <div class="mt-3 grid grid-cols-2 gap-4">
                  <div>
                    <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('clientPortal.totalViewsCampaign') }}</p>
                    <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
                      {{ formatCompact(campaign.totalViews) }}
                    </p>
                  </div>
                  <div>
                    <p class="text-xs text-gray-500 dark:text-gray-400">{{ $t('clientPortal.engagement') }}</p>
                    <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">
                      {{ formatCompact(campaign.totalEngagement) }}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Upcoming Content Calendar -->
          <div class="rounded-xl border border-gray-200 bg-white p-5 dark:border-gray-700 dark:bg-gray-800">
            <h2 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('clientPortal.upcomingContent') }}
            </h2>
            <div v-if="portal.upcomingContent.length === 0" class="py-8 text-center text-sm text-gray-400 dark:text-gray-500">
              {{ $t('clientPortal.noUpcomingContent') }}
            </div>
            <div v-else class="space-y-3">
              <div
                v-for="content in portal.upcomingContent"
                :key="content.id"
                class="flex items-center gap-3 rounded-lg border border-gray-100 p-3 dark:border-gray-700"
              >
                <div class="flex-shrink-0 text-center">
                  <p class="text-lg font-bold text-gray-900 dark:text-gray-100">
                    {{ new Date(content.scheduledAt).getDate() }}
                  </p>
                  <p class="text-[10px] text-gray-400">
                    {{ formatMonth(content.scheduledAt) }}
                  </p>
                </div>
                <div class="min-w-0 flex-1">
                  <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                    {{ content.title }}
                  </p>
                  <div class="mt-1 flex items-center gap-2">
                    <span
                      class="inline-flex rounded px-1.5 py-0.5 text-[10px] font-medium"
                      :class="platformBadgeClass(content.platform)"
                    >
                      {{ platformLabel(content.platform) }}
                    </span>
                    <span class="text-xs text-gray-400 dark:text-gray-500">
                      {{ formatTime(content.scheduledAt) }}
                    </span>
                  </div>
                </div>
                <span
                  class="inline-flex rounded-full px-2 py-0.5 text-xs font-medium"
                  :class="content.status === 'published'
                    ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
                    : 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'"
                >
                  {{ $t(`clientPortal.contentStatus.${content.status}`) }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="mt-8 border-t border-gray-200 pt-6 text-center dark:border-gray-700">
          <p class="text-xs text-gray-400 dark:text-gray-500">
            {{ $t('clientPortal.poweredBy') }}
          </p>
        </div>
      </div>
    </template>

    <!-- Error / Not Found -->
    <div v-else class="flex min-h-screen items-center justify-center">
      <div class="text-center">
        <ExclamationTriangleIcon class="mx-auto h-12 w-12 text-gray-300 dark:text-gray-600" />
        <h2 class="mt-3 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('clientPortal.notFound') }}
        </h2>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('clientPortal.notFoundDescription') }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowDownTrayIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import { useAgencyStore } from '@/stores/agency'
import type { PlatformType } from '@/types/agency'

const route = useRoute()
const agencyStore = useAgencyStore()

const portal = computed(() => agencyStore.portalData)

const performanceMax = computed(() => {
  if (!portal.value) return 1
  return Math.max(...portal.value.recentPerformance.map((p) => p.views), 1)
})

function formatCompact(value: number): string {
  if (value >= 1_000_000_000) return `${(value / 1_000_000_000).toFixed(1)}B`
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`
  return value.toLocaleString()
}

function platformBadgeClass(platform: PlatformType): string {
  const map: Record<PlatformType, string> = {
    YOUTUBE: 'bg-red-50 text-red-700 dark:bg-red-900/20 dark:text-red-300',
    TIKTOK: 'bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200',
    INSTAGRAM: 'bg-pink-50 text-pink-700 dark:bg-pink-900/20 dark:text-pink-300',
    NAVER_CLIP: 'bg-green-50 text-green-700 dark:bg-green-900/20 dark:text-green-300',
  }
  return map[platform]
}

function platformLabel(platform: PlatformType): string {
  const map: Record<PlatformType, string> = {
    YOUTUBE: 'YouTube',
    TIKTOK: 'TikTok',
    INSTAGRAM: 'Instagram',
    NAVER_CLIP: 'Naver Clip',
  }
  return map[platform]
}

function campaignStatusClass(status: string): string {
  const map: Record<string, string> = {
    active: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
    completed: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
    upcoming: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
  }
  return map[status] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
}

function formatDateShort(date: string): string {
  return new Date(date).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
}

function formatMonth(date: string): string {
  return new Date(date).toLocaleDateString('ko-KR', { month: 'short' })
}

function formatTime(date: string): string {
  return new Date(date).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
}

function handleDownloadPdf() {
  // Future: PDF report download
}

onMounted(() => {
  const token = route.params.token as string
  if (token) {
    agencyStore.fetchPortalData(token)
  }
})
</script>
