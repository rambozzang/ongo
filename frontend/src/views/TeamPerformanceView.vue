<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { storeToRefs } from 'pinia'
import {
  UsersIcon,
  ChartBarIcon,
  CheckCircleIcon,
  DocumentTextIcon,
  TrophyIcon,
} from '@heroicons/vue/24/outline'
import { useTeamPerformanceStore } from '@/stores/teamPerformance'
import MemberCard from '@/components/teamperformance/MemberCard.vue'
import ActivityFeed from '@/components/teamperformance/ActivityFeed.vue'
import PerformanceRanking from '@/components/teamperformance/PerformanceRanking.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useTeamPerformanceStore()
const { members, activities, summary, isLoading } = storeToRefs(store)

const topPerformer = computed(() => {
  if (members.value.length === 0) return null
  return [...members.value].sort((a, b) => b.completionRate - a.completionRate)[0]
})

onMounted(() => {
  store.fetchMembers()
  store.fetchActivities()
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
            팀 성과 대시보드
          </h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          팀원별 성과를 추적하고 활동 현황을 한눈에 확인하세요
        </p>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-4">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <UsersIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 멤버</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalMembers }}명
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ChartBarIcon class="h-5 w-5 text-green-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 완료율</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.avgCompletionRate.toFixed(1) }}%
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <CheckCircleIcon class="h-5 w-5 text-blue-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 완료 작업</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalTasksCompleted.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <DocumentTextIcon class="h-5 w-5 text-yellow-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 콘텐츠 수</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalContentProduced.toLocaleString('ko-KR') }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <template v-else>
      <!-- 탑 퍼포머 하이라이트 -->
      <div
        v-if="topPerformer"
        class="mb-8 rounded-xl border border-yellow-200 bg-gradient-to-r from-yellow-50 to-orange-50 p-5 shadow-sm dark:border-yellow-800 dark:from-yellow-900/10 dark:to-orange-900/10"
      >
        <div class="flex items-center gap-4">
          <div class="flex h-12 w-12 items-center justify-center rounded-full bg-yellow-100 dark:bg-yellow-900/30">
            <TrophyIcon class="h-6 w-6 text-yellow-600 dark:text-yellow-400" />
          </div>
          <div class="flex-1">
            <p class="text-xs font-medium text-yellow-700 dark:text-yellow-400">
              이달의 탑 퍼포머
            </p>
            <p class="text-lg font-bold text-gray-900 dark:text-gray-100">
              {{ topPerformer.name }}
            </p>
            <p class="text-sm text-gray-600 dark:text-gray-400">
              {{ topPerformer.role }} &middot; 완료율 {{ topPerformer.completionRate.toFixed(1) }}% &middot; {{ topPerformer.contentProduced }}건 제작
            </p>
          </div>
          <div class="hidden text-right tablet:block">
            <p class="text-2xl font-bold text-yellow-600 dark:text-yellow-400">
              {{ topPerformer.streak }}일
            </p>
            <p class="text-xs text-gray-500 dark:text-gray-400">연속 달성</p>
          </div>
        </div>
      </div>

      <!-- 팀원 카드 그리드 + 성과 랭킹 -->
      <div class="mb-8 grid grid-cols-1 gap-6 desktop:grid-cols-3">
        <!-- 팀원 카드 그리드 (2/3) -->
        <div class="desktop:col-span-2">
          <h2 class="mb-4 flex items-center gap-2 text-lg font-bold text-gray-900 dark:text-gray-100">
            <UsersIcon class="h-5 w-5 text-primary-500" />
            팀원 현황
          </h2>

          <div v-if="members.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
            <MemberCard
              v-for="member in members"
              :key="member.id"
              :member="member"
            />
          </div>

          <div
            v-else
            class="rounded-xl border border-gray-200 bg-white py-12 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
          >
            <UsersIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
            <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
              팀원 데이터가 없습니다
            </h3>
            <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
              팀원을 추가하면 성과를 추적할 수 있습니다
            </p>
          </div>
        </div>

        <!-- 성과 랭킹 (1/3) -->
        <div>
          <PerformanceRanking :members="members" />
        </div>
      </div>

      <!-- 최근 활동 피드 -->
      <div class="mb-8">
        <ActivityFeed :activities="activities" />
      </div>
    </template>
  </div>
</template>
