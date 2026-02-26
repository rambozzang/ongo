<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import {
  BanknotesIcon,
  FunnelIcon,
} from '@heroicons/vue/24/outline'
import { useRevenueGoalStore } from '@/stores/revenueGoal'
import RevenueGoalCard from '@/components/revenuegoal/RevenueGoalCard.vue'
import GoalProgressRing from '@/components/revenuegoal/GoalProgressRing.vue'
import GoalMilestoneList from '@/components/revenuegoal/GoalMilestoneList.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

useI18n({ useScope: 'global' })
const store = useRevenueGoalStore()

const { goals, summary, loading } = storeToRefs(store)

// ─── Filters ──────────────────────────────────────
const statusFilter = ref<string>('')
const selectedGoalId = ref<number | null>(null)

const filteredGoals = computed(() => {
  return goals.value.filter((goal) => {
    if (statusFilter.value && goal.status !== statusFilter.value) return false
    return true
  })
})

const selectedGoalMilestones = computed(() => {
  if (!selectedGoalId.value) return []
  return store.getMilestonesForGoal(selectedGoalId.value)
})

const selectedGoal = computed(() => {
  if (!selectedGoalId.value) return null
  return goals.value.find((g) => g.id === selectedGoalId.value) ?? null
})

const formatAmount = (value: number): string => {
  if (value >= 100000000) return `${(value / 100000000).toFixed(1)}억원`
  if (value >= 10000) return `${(value / 10000).toFixed(0)}만원`
  return `${value.toLocaleString('ko-KR')}원`
}

const selectGoal = (goalId: number) => {
  if (selectedGoalId.value === goalId) {
    selectedGoalId.value = null
  } else {
    selectedGoalId.value = goalId
    store.fetchMilestones(goalId)
  }
}

onMounted(() => {
  store.fetchGoals()
  store.fetchSummary()
  store.fetchMilestones()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <div class="flex items-center gap-3">
          <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {{ $t('revenueGoal.title') }}
          </h1>
          <span
            v-if="store.activeGoalCount > 0"
            class="rounded-full bg-primary-600 px-2.5 py-0.5 text-xs font-semibold text-white"
          >
            {{ store.activeGoalCount }} {{ $t('revenueGoal.active') }}
          </span>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('revenueGoal.description') }}
        </p>
      </div>
    </div>

    <PageGuide
      :title="$t('revenueGoal.pageGuideTitle')"
      :items="($tm('revenueGoal.pageGuide') as string[])"
    />

    <!-- Summary Stats -->
    <div v-if="summary" class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-5">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('revenueGoal.totalGoals') }}</p>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalGoals }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('revenueGoal.activeGoals') }}</p>
        <p class="mt-1 text-2xl font-bold text-green-600 dark:text-green-400">
          {{ summary.activeGoals }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('revenueGoal.avgProgress') }}</p>
        <p class="mt-1 text-2xl font-bold text-primary-600 dark:text-primary-400">
          {{ summary.avgProgress.toFixed(1) }}%
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('revenueGoal.totalTarget') }}</p>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ formatAmount(summary.totalTarget) }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('revenueGoal.totalCurrent') }}</p>
        <p class="mt-1 text-2xl font-bold text-primary-600 dark:text-primary-400">
          {{ formatAmount(summary.totalCurrent) }}
        </p>
      </div>
    </div>

    <!-- Filter -->
    <div class="mb-4 flex items-center gap-2">
      <FunnelIcon class="h-4 w-4 text-gray-400 dark:text-gray-500" />
      <select
        v-model="statusFilter"
        class="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 dark:border-gray-600 dark:bg-gray-800 dark:text-gray-300"
      >
        <option value="">{{ $t('revenueGoal.allStatuses') }}</option>
        <option value="ACTIVE">{{ $t('revenueGoal.statusActive') }}</option>
        <option value="COMPLETED">{{ $t('revenueGoal.statusCompleted') }}</option>
        <option value="EXPIRED">{{ $t('revenueGoal.statusExpired') }}</option>
      </select>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="loading" :full-page="true" size="lg" />

    <template v-else>
      <div class="grid grid-cols-1 gap-6 desktop:grid-cols-3">
        <!-- Goal Cards (2 columns) -->
        <div class="desktop:col-span-2">
          <div v-if="filteredGoals.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
            <div
              v-for="goal in filteredGoals"
              :key="goal.id"
              class="cursor-pointer transition-transform hover:scale-[1.01]"
              :class="{ 'ring-2 ring-primary-500 rounded-xl': selectedGoalId === goal.id }"
              @click="selectGoal(goal.id)"
            >
              <RevenueGoalCard :goal="goal" />
            </div>
          </div>

          <!-- Empty state -->
          <div
            v-else
            class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 py-16 text-center shadow-sm"
          >
            <BanknotesIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
            <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('revenueGoal.emptyGoals') }}
            </h3>
            <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
              {{ $t('revenueGoal.emptyGoalsHint') }}
            </p>
          </div>
        </div>

        <!-- Milestone Panel (1 column) -->
        <div class="desktop:col-span-1">
          <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
            <h2 class="mb-4 text-base font-semibold text-gray-900 dark:text-gray-100">
              {{ $t('revenueGoal.milestones') }}
            </h2>

            <template v-if="selectedGoal">
              <!-- Selected goal info -->
              <div class="mb-4 flex items-center gap-3 rounded-lg bg-gray-50 p-3 dark:bg-gray-800/50">
                <GoalProgressRing :progress="selectedGoal.progress" :size="48" :stroke-width="4" />
                <div class="min-w-0 flex-1">
                  <p class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ selectedGoal.name }}</p>
                  <p class="text-xs text-gray-500 dark:text-gray-400">
                    {{ formatAmount(selectedGoal.currentAmount) }} / {{ formatAmount(selectedGoal.targetAmount) }}
                  </p>
                </div>
              </div>

              <GoalMilestoneList :milestones="selectedGoalMilestones" />
            </template>

            <div v-else class="flex items-center justify-center py-8 text-sm text-gray-400 dark:text-gray-500">
              {{ $t('revenueGoal.selectGoalForMilestones') }}
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
