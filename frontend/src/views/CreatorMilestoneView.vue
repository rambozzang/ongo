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

const store = useCreatorMilestoneStore()
const { milestones, summary, isLoading } = storeToRefs(store)

const activeTab = ref<'ALL' | 'IN_PROGRESS' | 'ACHIEVED' | 'PENDING'>('ALL')

const tabs = [
  { key: 'ALL' as const, label: '전체' },
  { key: 'IN_PROGRESS' as const, label: '진행중' },
  { key: 'ACHIEVED' as const, label: '달성' },
  { key: 'PENDING' as const, label: '대기' },
]

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
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            크리에이터 마일스톤
          </h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          채널 성장 목표를 설정하고 달성 현황을 추적하세요
        </p>
      </div>
    </div>

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
      class="mb-6 rounded-xl border border-blue-200 bg-gradient-to-r from-blue-50 to-indigo-50 p-5 shadow-sm dark:border-blue-800 dark:from-blue-900/20 dark:to-indigo-900/20"
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
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
      <nav class="-mb-px flex gap-8">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          @click="handleTabChange(tab.key)"
          :class="[
            'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
            activeTab === tab.key
              ? 'border-blue-600 text-blue-600 dark:text-blue-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
          ]"
        >
          {{ tab.label }}
          <span
            :class="[
              'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
              activeTab === tab.key
                ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
            ]"
          >
            {{ tab.key === 'ALL' ? milestones.length : milestones.filter((m) => m.status === tab.key).length }}
          </span>
        </button>
      </nav>
    </div>

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
