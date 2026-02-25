<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('agency.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('agency.description', { count: agencyStore.activeCreators.length }) }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button class="btn-secondary inline-flex items-center gap-1.5 text-sm" @click="handleExport">
          <ArrowDownTrayIcon class="h-4 w-4" />
          {{ $t('agency.exportReport') }}
        </button>
      </div>
    </div>

    <PageGuide :title="$t('agency.pageGuideTitle')" :items="($tm('agency.pageGuide') as string[])" />

    <!-- Loading -->
    <div v-if="agencyStore.loading" class="space-y-4">
      <div class="grid grid-cols-2 gap-4 desktop:grid-cols-4">
        <div v-for="i in 4" :key="i" class="h-32 animate-pulse rounded-xl bg-gray-200 dark:bg-gray-700" />
      </div>
      <div class="h-64 animate-pulse rounded-xl bg-gray-200 dark:bg-gray-700" />
    </div>

    <template v-else>
      <!-- KPI Cards -->
      <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
        <AgencyKpiCard
          :title="$t('agency.kpi.creators')"
          :value="agencyStore.kpi?.totalCreators ?? 0"
          :change="agencyStore.kpi?.creatorsChange"
          change-type="number"
          :icon="UsersIcon"
          color="indigo"
        />
        <AgencyKpiCard
          :title="$t('agency.kpi.subscribers')"
          :value="agencyStore.kpi?.totalSubscribers ?? 0"
          :change="agencyStore.kpi?.subscribersChange"
          change-type="number"
          :icon="UserGroupIcon"
          color="blue"
        />
        <AgencyKpiCard
          :title="$t('agency.kpi.views')"
          :value="agencyStore.kpi?.totalViews ?? 0"
          :change="agencyStore.kpi?.viewsChangePercent"
          change-type="percent"
          :icon="EyeIcon"
          color="green"
        />
        <AgencyKpiCard
          :title="$t('agency.kpi.revenue')"
          :value="agencyStore.kpi?.totalRevenue ?? 0"
          :change="agencyStore.kpi?.revenueChangePercent"
          change-type="percent"
          :icon="CurrencyDollarIcon"
          color="amber"
        />
      </div>

      <!-- Tabs -->
      <div class="border-b border-gray-200 dark:border-gray-700">
        <nav class="-mb-px flex space-x-4 overflow-x-auto scrollbar-hide tablet:space-x-8">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            :class="[
              activeTab === tab.id
                ? 'border-indigo-500 text-indigo-600 dark:border-indigo-400 dark:text-indigo-400'
                : 'border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 dark:hover:border-gray-600 dark:hover:text-gray-300',
              'flex items-center whitespace-nowrap border-b-2 px-1 py-4 text-sm font-medium',
            ]"
            @click="activeTab = tab.id"
          >
            <component :is="tab.icon" class="mr-2 h-5 w-5" />
            {{ tab.label }}
            <span
              v-if="tab.count !== undefined"
              :class="[
                activeTab === tab.id
                  ? 'bg-indigo-100 text-indigo-600 dark:bg-indigo-900/30 dark:text-indigo-400'
                  : 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400',
                'ml-2 rounded-full px-2.5 py-0.5 text-xs font-medium',
              ]"
            >
              {{ tab.count }}
            </span>
          </button>
        </nav>
      </div>

      <!-- Tab Content -->
      <div class="mt-6">
        <!-- Workspaces Tab -->
        <div v-if="activeTab === 'workspaces'">
          <div class="mb-4 flex items-center gap-3">
            <div class="relative flex-1">
              <MagnifyingGlassIcon class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
              <input
                v-model="searchQuery"
                type="text"
                :placeholder="$t('agency.searchCreators')"
                class="w-full rounded-lg border border-gray-300 bg-white py-2 pl-9 pr-3 text-sm text-gray-900 placeholder-gray-400 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-gray-600 dark:bg-gray-700 dark:text-gray-100 dark:placeholder-gray-500"
              />
            </div>
          </div>
          <div class="grid gap-4 sm:grid-cols-2 desktop:grid-cols-3">
            <WorkspaceCard
              v-for="creator in filteredCreators"
              :key="creator.id"
              :creator="creator"
              @view-detail="handleViewDetail"
              @create-link="handleCreateLink"
            />
          </div>
          <div v-if="filteredCreators.length === 0" class="py-12 text-center">
            <UsersIcon class="mx-auto h-12 w-12 text-gray-300 dark:text-gray-600" />
            <p class="mt-3 text-sm text-gray-500 dark:text-gray-400">{{ $t('agency.noCreatorsFound') }}</p>
          </div>
        </div>

        <!-- Performance Comparison Tab -->
        <div v-else-if="activeTab === 'comparison'">
          <CreatorComparisonChart :comparisons="agencyStore.comparisons" />
        </div>

        <!-- Batch Schedule Tab -->
        <div v-else-if="activeTab === 'schedule'" class="space-y-4">
          <div class="rounded-xl border border-gray-200 bg-white dark:border-gray-700 dark:bg-gray-800">
            <div class="border-b border-gray-100 p-4 dark:border-gray-700">
              <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">
                {{ $t('agency.batchSchedule') }}
              </h3>
              <p class="mt-0.5 text-sm text-gray-500 dark:text-gray-400">
                {{ $t('agency.batchScheduleDescription') }}
              </p>
            </div>
            <div class="divide-y divide-gray-100 dark:divide-gray-700">
              <div
                v-for="schedule in agencyStore.schedules"
                :key="schedule.id"
                class="flex items-center gap-4 p-4"
              >
                <div class="flex-shrink-0">
                  <div class="text-center">
                    <p class="text-xs text-gray-400 dark:text-gray-500">
                      {{ formatMonth(schedule.scheduledAt) }}
                    </p>
                    <p class="text-lg font-bold text-gray-900 dark:text-gray-100">
                      {{ formatDay(schedule.scheduledAt) }}
                    </p>
                    <p class="text-xs text-gray-500 dark:text-gray-400">
                      {{ formatTime(schedule.scheduledAt) }}
                    </p>
                  </div>
                </div>
                <div class="min-w-0 flex-1">
                  <div class="flex items-center gap-2">
                    <p class="truncate text-sm font-medium text-gray-900 dark:text-gray-100">
                      {{ schedule.videoTitle }}
                    </p>
                    <span
                      class="inline-flex rounded-full px-2 py-0.5 text-xs font-medium"
                      :class="scheduleStatusClass(schedule.status)"
                    >
                      {{ $t(`agency.scheduleStatus.${schedule.status}`) }}
                    </span>
                  </div>
                  <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
                    {{ schedule.creatorName }}
                  </p>
                  <div class="mt-1 flex flex-wrap gap-1">
                    <span
                      v-for="platform in schedule.platforms"
                      :key="platform"
                      class="inline-flex rounded px-1.5 py-0.5 text-[10px] font-medium"
                      :class="platformBadgeClass(platform)"
                    >
                      {{ platformShortLabel(platform) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="agencyStore.schedules.length === 0" class="p-8 text-center text-sm text-gray-400 dark:text-gray-500">
              {{ $t('agency.noSchedules') }}
            </div>
          </div>
        </div>

        <!-- Client Management Tab -->
        <div v-else-if="activeTab === 'clients'">
          <div class="grid gap-6 desktop:grid-cols-3">
            <div class="desktop:col-span-2">
              <ClientOverview
                :links="agencyStore.clientLinks"
                :creators="agencyStore.creators"
                @create="handleCreateLink"
                @revoke="handleRevokeLink"
              />
            </div>
            <div>
              <AgencyActivityFeed :activities="agencyStore.activities" />
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  UsersIcon,
  UserGroupIcon,
  EyeIcon,
  CurrencyDollarIcon,
  ArrowDownTrayIcon,
  MagnifyingGlassIcon,
  Squares2X2Icon,
  ChartBarSquareIcon,
  CalendarDaysIcon,
  UserCircleIcon,
} from '@heroicons/vue/24/outline'
import PageGuide from '@/components/common/PageGuide.vue'
import AgencyKpiCard from '@/components/agency/AgencyKpiCard.vue'
import WorkspaceCard from '@/components/agency/WorkspaceCard.vue'
import CreatorComparisonChart from '@/components/agency/CreatorComparisonChart.vue'
import AgencyActivityFeed from '@/components/agency/AgencyActivityFeed.vue'
import ClientOverview from '@/components/agency/ClientOverview.vue'
import { useAgencyStore } from '@/stores/agency'
import type { PlatformType } from '@/types/agency'

const { t } = useI18n({ useScope: 'global' })
const agencyStore = useAgencyStore()

type TabType = 'workspaces' | 'comparison' | 'schedule' | 'clients'
const activeTab = ref<TabType>('workspaces')
const searchQuery = ref('')

const tabs = computed<{ id: TabType; label: string; icon: Component; count?: number }[]>(() => [
  { id: 'workspaces', label: t('agency.tabs.workspaces'), icon: Squares2X2Icon, count: agencyStore.creators.length },
  { id: 'comparison', label: t('agency.tabs.comparison'), icon: ChartBarSquareIcon },
  { id: 'schedule', label: t('agency.tabs.schedule'), icon: CalendarDaysIcon, count: agencyStore.schedules.length },
  { id: 'clients', label: t('agency.tabs.clients'), icon: UserCircleIcon, count: agencyStore.clientLinks.length },
])

const filteredCreators = computed(() => {
  if (!searchQuery.value) return agencyStore.creators
  const q = searchQuery.value.toLowerCase()
  return agencyStore.creators.filter(
    (c) => c.name.toLowerCase().includes(q) || c.channels.some((ch) => ch.channelName.toLowerCase().includes(q)),
  )
})

function scheduleStatusClass(status: string): string {
  const map: Record<string, string> = {
    scheduled: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
    published: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300',
    failed: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300',
  }
  return map[status] ?? 'bg-gray-100 text-gray-700 dark:bg-gray-700 dark:text-gray-300'
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

function platformShortLabel(platform: PlatformType): string {
  const map: Record<PlatformType, string> = {
    YOUTUBE: 'YT',
    TIKTOK: 'TT',
    INSTAGRAM: 'IG',
    NAVER_CLIP: 'NC',
  }
  return map[platform]
}

function formatMonth(date: string): string {
  return new Date(date).toLocaleDateString('ko-KR', { month: 'short' })
}

function formatDay(date: string): string {
  return String(new Date(date).getDate())
}

function formatTime(date: string): string {
  return new Date(date).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
}

function handleViewDetail(_creatorId: number) {
  // Future: navigate to creator detail
}

async function handleCreateLink(creatorId: number) {
  await agencyStore.createClientLink(creatorId)
}

async function handleRevokeLink(linkId: number) {
  await agencyStore.revokeClientLink(linkId)
}

function handleExport() {
  // Future: export report
}

onMounted(() => {
  agencyStore.fetchDashboard()
})
</script>
