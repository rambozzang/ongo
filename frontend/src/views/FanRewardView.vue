<script setup lang="ts">
import { onMounted } from 'vue'
import {
  GiftIcon,
  SparklesIcon,
  CurrencyDollarIcon,
  HandThumbUpIcon,
} from '@heroicons/vue/24/outline'
import { useFanRewardStore } from '@/stores/fanReward'
import type { FanReward, FanActivity } from '@/types/fanReward'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import RewardCard from '@/components/fanreward/RewardCard.vue'
import FanActivityRow from '@/components/fanreward/FanActivityRow.vue'

const store = useFanRewardStore()

onMounted(() => {
  store.fetchRewards()
  store.fetchActivities()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          팬 리워드
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          팬 활동에 대한 보상을 관리하고 리워드를 배포합니다
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="store.loading" :full-page="true" size="lg" />

    <template v-else>
      <!-- Summary Cards -->
      <div v-if="store.summary" class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-4">
        <!-- Total Rewards -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400">
              <GiftIcon class="h-5 w-5" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 리워드</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary.totalRewards }}개
              </p>
            </div>
          </div>
        </div>

        <!-- Active Rewards -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400">
              <SparklesIcon class="h-5 w-5" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">활성 리워드</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary.activeRewards }}개
              </p>
            </div>
          </div>
        </div>

        <!-- Distributed Points -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400">
              <CurrencyDollarIcon class="h-5 w-5" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">배포 포인트</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary.totalPointsDistributed.toLocaleString('ko-KR') }}P
              </p>
            </div>
          </div>
        </div>

        <!-- Total Claims -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-orange-100 text-orange-600 dark:bg-orange-900/30 dark:text-orange-400">
              <HandThumbUpIcon class="h-5 w-5" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 클레임</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary.totalClaims.toLocaleString('ko-KR') }}건
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Reward Cards Grid -->
      <section class="mb-8">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          리워드 목록
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ store.rewards.length }})</span>
        </h2>

        <div v-if="store.rewards.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <RewardCard
            v-for="reward in store.rewards"
            :key="reward.id"
            :reward="reward"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-12 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <GiftIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            등록된 리워드가 없습니다
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            새 리워드를 추가하여 팬에게 보상하세요
          </p>
        </div>
      </section>

      <!-- Recent Fan Activities -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          최근 팬 활동
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ store.activities.length }})</span>
        </h2>

        <div v-if="store.activities.length > 0" class="space-y-2">
          <FanActivityRow
            v-for="activity in store.activities"
            :key="activity.id"
            :activity="activity"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-12 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <p class="text-sm text-gray-500 dark:text-gray-400">
            최근 팬 활동이 없습니다
          </p>
        </div>
      </section>
    </template>
  </div>
</template>
