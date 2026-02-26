<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  UsersIcon,
  UserGroupIcon,
  ClockIcon,
  GlobeAltIcon,
} from '@heroicons/vue/24/outline'
import { useFanInsightsStore } from '@/stores/fanInsights'
import DemographicChart from '@/components/faninsights/DemographicChart.vue'
import BehaviorCard from '@/components/faninsights/BehaviorCard.vue'
import FanSegmentCard from '@/components/faninsights/FanSegmentCard.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useFanInsightsStore()
const { demographics, behaviors, segments, summary, isLoading } = storeToRefs(store)

type TabType = 'demographics' | 'behaviors' | 'segments'

const activeTab = ref<TabType>('demographics')

const tabs: { value: TabType; label: string }[] = [
  { value: 'demographics', label: '인구통계' },
  { value: 'behaviors', label: '행동 분석' },
  { value: 'segments', label: '팬 세그먼트' },
]

const handleTabChange = (tab: TabType) => {
  activeTab.value = tab
}

onMounted(() => {
  store.fetchDemographics()
  store.fetchBehaviors()
  store.fetchSegments()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            팬 인사이트
          </h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          팬 인구통계, 행동 패턴, 세그먼트를 분석하여 타겟 전략을 최적화하세요
        </p>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <UsersIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">전체 팬</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalFans.toLocaleString() }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <UserGroupIcon class="h-5 w-5 text-blue-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">주요 연령대</p>
        </div>
        <p class="mt-1 text-lg font-bold text-gray-900 dark:text-gray-100 truncate">
          {{ summary.topAgeGroup || '-' }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <GlobeAltIcon class="h-5 w-5 text-green-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">주요 국가</p>
        </div>
        <p class="mt-1 text-lg font-bold text-gray-900 dark:text-gray-100 truncate">
          {{ summary.topCountry || '-' }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ClockIcon class="h-5 w-5 text-purple-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">피크 활동 시간</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.peakActiveHour }}:00
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <template v-else>
      <!-- Tabs -->
      <div class="mb-6 flex gap-2 border-b border-gray-200 dark:border-gray-700">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          @click="handleTabChange(tab.value)"
          :class="[
            'px-4 py-2.5 text-sm font-medium transition-colors border-b-2 -mb-px',
            activeTab === tab.value
              ? 'border-blue-600 text-blue-600 dark:border-blue-400 dark:text-blue-400'
              : 'border-transparent text-gray-500 hover:text-gray-700 dark:text-gray-400 dark:hover:text-gray-300',
          ]"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- Demographics Tab -->
      <div v-if="activeTab === 'demographics'">
        <DemographicChart
          v-if="demographics.length > 0"
          :demographics="demographics"
        />
        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-10 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <UsersIcon class="mx-auto mb-2 h-10 w-10 text-gray-400 dark:text-gray-600" />
          <p class="text-sm text-gray-500 dark:text-gray-400">인구통계 데이터가 없습니다</p>
        </div>
      </div>

      <!-- Behaviors Tab -->
      <div v-if="activeTab === 'behaviors'">
        <div v-if="behaviors.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
          <BehaviorCard
            v-for="b in behaviors"
            :key="b.id"
            :behavior="b"
          />
        </div>
        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-10 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <ClockIcon class="mx-auto mb-2 h-10 w-10 text-gray-400 dark:text-gray-600" />
          <p class="text-sm text-gray-500 dark:text-gray-400">행동 분석 데이터가 없습니다</p>
        </div>
      </div>

      <!-- Segments Tab -->
      <div v-if="activeTab === 'segments'">
        <div v-if="segments.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <FanSegmentCard
            v-for="s in segments"
            :key="s.id"
            :segment="s"
          />
        </div>
        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-10 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <UserGroupIcon class="mx-auto mb-2 h-10 w-10 text-gray-400 dark:text-gray-600" />
          <p class="text-sm text-gray-500 dark:text-gray-400">팬 세그먼트 데이터가 없습니다</p>
        </div>
      </div>
    </template>
  </div>
</template>
