<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  FlagIcon,
  TrophyIcon,
  ArrowTrendingUpIcon,
  ChartBarIcon,
  StarIcon,
} from '@heroicons/vue/24/outline'
import { useCreatorMilestoneStore } from '@/stores/creatorMilestone'
import MilestoneCard from '@/components/creatormilestone/MilestoneCard.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import OTabs from '@/components/ui/OTabs.vue'

const store = useCreatorMilestoneStore()
const { milestones, summary, isLoading } = storeToRefs(store)

const activeTab = ref<'ALL' | 'IN_PROGRESS' | 'ACHIEVED' | 'PENDING'>('ALL')

const tabItems = computed(() => [
  { key: 'ALL', label: '전체', count: milestones.value.length },
  { key: 'IN_PROGRESS', label: '진행중', count: milestones.value.filter((m) => m.status === 'IN_PROGRESS').length },
  { key: 'ACHIEVED', label: '달성', count: milestones.value.filter((m) => m.status === 'ACHIEVED').length },
  { key: 'PENDING', label: '대기', count: milestones.value.filter((m) => m.status === 'PENDING').length },
])

const filteredMilestones = computed(() => {
  if (activeTab.value === 'ALL') return milestones.value
  return milestones.value.filter((m) => m.status === activeTab.value)
})

const nextMilestone = computed(() => {
  const inProgress = milestones.value
    .filter((m) => m.status === 'IN_PROGRESS')
    .sort((a, b) => b.progress - a.progress)
  return inProgress[0] || null
})

const handleTabChange = (tab: typeof activeTab.value) => {
  activeTab.value = tab
  if (tab === 'ALL') {
    store.fetchMilestones()
  } else {
    store.fetchMilestones(tab)
  }
}

onMounted(() => {
  store.fetchMilestones()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <PageHeader title="크리에이터 마일스톤" description="채널 성장 목표를 설정하고 달성 현황을 추적하세요" />

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <FlagIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 마일스톤</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalMilestones.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <TrophyIcon class="h-5 w-5 text-yellow-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">달성</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.achievedCount.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ArrowTrendingUpIcon class="h-5 w-5 text-blue-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">진행중</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.inProgressCount.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ChartBarIcon class="h-5 w-5 text-green-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">달성률</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.achievementRate.toFixed(1) }}%
        </p>
      </div>
    </div>

    <!-- Next Milestone Highlight -->
    <div
      v-if="nextMilestone"
      class="mb-6 rounded-xl border border-blue-200 bg-gradient-to-r from-blue-50 to-primary-50 p-5 shadow-sm dark:border-blue-800 dark:from-blue-900/20 dark:to-primary-900/20"
    >
      <div class="flex items-center gap-2 mb-2">
        <StarIcon class="h-5 w-5 text-blue-600 dark:text-blue-400" />
        <h2 class="text-sm font-semibold text-blue-800 dark:text-blue-300">다음 마일스톤</h2>
      </div>
      <div class="flex flex-col gap-3 tablet:flex-row tablet:items-center tablet:justify-between">
        <div>
          <h3 class="text-lg font-bold text-gray-900 dark:text-gray-100">
            {{ nextMilestone.title }}
          </h3>
          <p class="text-sm text-gray-600 dark:text-gray-400">
            {{ nextMilestone.description }}
          </p>
        </div>
        <div class="flex items-center gap-4">
          <div class="text-right">
            <p class="text-sm text-gray-500 dark:text-gray-400">진행률</p>
            <p class="text-xl font-bold text-blue-600 dark:text-blue-400">
              {{ nextMilestone.progress.toFixed(1) }}%
            </p>
          </div>
          <div class="h-3 w-32 overflow-hidden rounded-full bg-blue-200 dark:bg-blue-800">
            <div
              class="h-full rounded-full bg-blue-600 transition-all duration-500 dark:bg-blue-400"
              :style="{ width: `${Math.min(nextMilestone.progress, 100)}%` }"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- Tabs -->
    <OTabs v-model="activeTab" :tabs="tabItems" class="mb-6" @update:model-value="handleTabChange($event as typeof activeTab)" />

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Milestone Cards Grid -->
    <div v-else-if="filteredMilestones.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
      <MilestoneCard
        v-for="m in filteredMilestones"
        :key="m.id"
        :milestone="m"
      />
    </div>

    <!-- Empty State -->
    <div
      v-else
      class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
    >
      <FlagIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
        마일스톤이 없습니다
      </h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
        채널 성장 목표를 설정하고 진행 상황을 추적하세요
      </p>
    </div>
  </div>
</template>
