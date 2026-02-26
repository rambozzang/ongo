<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import {
  ShieldCheckIcon,
  ShieldExclamationIcon,
  ExclamationTriangleIcon,
  XCircleIcon,
  CheckBadgeIcon,
  Cog6ToothIcon,
} from '@heroicons/vue/24/outline'
import { useBrandSafetyStore } from '@/stores/brandSafety'
import SafetyCheckCard from '@/components/brandsafety/SafetyCheckCard.vue'
import SafetyRuleToggle from '@/components/brandsafety/SafetyRuleToggle.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const store = useBrandSafetyStore()
const { checks, rules, summary, isLoading } = storeToRefs(store)

const activeTab = ref<'checks' | 'rules'>('checks')
const statusFilter = ref<string>('ALL')

const statusFilters = [
  { value: 'ALL', label: '전체' },
  { value: 'SAFE', label: '안전' },
  { value: 'WARNING', label: '경고' },
  { value: 'VIOLATION', label: '위반' },
]

const filteredChecks = computed(() => {
  if (statusFilter.value === 'ALL') return checks.value
  return checks.value.filter((c) => c.status === statusFilter.value)
})

const scoreColor = computed(() => {
  const score = summary.value.avgScore
  if (score >= 80) return 'text-green-600 dark:text-green-400'
  if (score >= 60) return 'text-yellow-600 dark:text-yellow-400'
  return 'text-red-600 dark:text-red-400'
})

const scoreBorderColor = computed(() => {
  const score = summary.value.avgScore
  if (score >= 80) return 'border-green-500'
  if (score >= 60) return 'border-yellow-500'
  return 'border-red-500'
})

const handleToggleRule = (id: number, isEnabled: boolean) => {
  store.toggleRule(id, isEnabled)
}

const handleFilterChange = (status: string) => {
  statusFilter.value = status
  if (status === 'ALL') {
    store.fetchChecks()
  } else {
    store.fetchChecks(status)
  }
}

onMounted(() => {
  store.fetchChecks()
  store.fetchRules()
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
            브랜드 안전성 점검
          </h1>
        </div>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          콘텐츠의 브랜드 안전성을 AI로 점검하고 위반 사항을 사전에 방지하세요
        </p>
      </div>
    </div>

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-5">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ShieldCheckIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 점검수</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalChecks.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <CheckBadgeIcon class="h-5 w-5 text-green-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">안전</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-green-600 dark:text-green-400">
          {{ summary.safeCount.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ExclamationTriangleIcon class="h-5 w-5 text-yellow-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">경고</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-yellow-600 dark:text-yellow-400">
          {{ summary.warningCount.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <XCircleIcon class="h-5 w-5 text-red-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">위반</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-red-600 dark:text-red-400">
          {{ summary.violationCount.toLocaleString('ko-KR') }}
        </p>
      </div>

      <!-- Average Score Circle -->
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ShieldExclamationIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">평균 점수</p>
        </div>
        <div class="mt-1 flex items-center gap-3">
          <div
            class="flex h-12 w-12 items-center justify-center rounded-full border-4"
            :class="scoreBorderColor"
          >
            <span class="text-sm font-bold" :class="scoreColor">
              {{ summary.avgScore.toFixed(0) }}
            </span>
          </div>
          <span class="text-xs text-gray-500 dark:text-gray-400">/ 100</span>
        </div>
      </div>
    </div>

    <!-- Tabs -->
    <div class="mb-6 border-b border-gray-200 dark:border-gray-700">
      <nav class="-mb-px flex gap-8">
        <button
          @click="activeTab = 'checks'"
          :class="[
            'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
            activeTab === 'checks'
              ? 'border-blue-600 text-blue-600 dark:text-blue-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
          ]"
        >
          <ShieldCheckIcon class="mr-1.5 inline h-4 w-4" />
          점검 결과
          <span
            :class="[
              'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
              activeTab === 'checks'
                ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
            ]"
          >
            {{ checks.length }}
          </span>
        </button>

        <button
          @click="activeTab = 'rules'"
          :class="[
            'py-4 px-1 border-b-2 font-medium text-sm transition-colors',
            activeTab === 'rules'
              ? 'border-blue-600 text-blue-600 dark:text-blue-400'
              : 'border-transparent text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 hover:border-gray-300 dark:hover:border-gray-600',
          ]"
        >
          <Cog6ToothIcon class="mr-1.5 inline h-4 w-4" />
          규칙 관리
          <span
            :class="[
              'ml-2 px-2 py-0.5 rounded-full text-xs font-semibold',
              activeTab === 'rules'
                ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-400',
            ]"
          >
            {{ rules.length }}
          </span>
        </button>
      </nav>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="isLoading" :full-page="true" size="lg" />

    <!-- Checks Tab -->
    <div v-else-if="activeTab === 'checks'">
      <!-- Status Filter -->
      <div class="mb-4 flex flex-wrap gap-2">
        <button
          v-for="filter in statusFilters"
          :key="filter.value"
          :class="[
            'rounded-lg px-3 py-1.5 text-sm font-medium transition-colors',
            statusFilter === filter.value
              ? 'bg-primary-600 text-white'
              : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-400 dark:hover:bg-gray-700',
          ]"
          @click="handleFilterChange(filter.value)"
        >
          {{ filter.label }}
        </button>
      </div>

      <!-- Check Cards Grid -->
      <div v-if="filteredChecks.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
        <SafetyCheckCard
          v-for="c in filteredChecks"
          :key="c.id"
          :check="c"
        />
      </div>

      <!-- Empty State -->
      <div
        v-else
        class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
      >
        <ShieldCheckIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          점검 결과가 없습니다
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          콘텐츠를 업로드하면 브랜드 안전성이 자동으로 점검됩니다
        </p>
      </div>
    </div>

    <!-- Rules Tab -->
    <div v-else-if="activeTab === 'rules'">
      <div v-if="rules.length > 0" class="space-y-3">
        <SafetyRuleToggle
          v-for="r in rules"
          :key="r.id"
          :rule="r"
          @toggle="handleToggleRule"
        />
      </div>

      <!-- Empty State -->
      <div
        v-else
        class="rounded-xl border border-gray-200 bg-white py-16 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
      >
        <Cog6ToothIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
        <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
          등록된 규칙이 없습니다
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          브랜드 안전성 규칙을 설정하여 콘텐츠를 보호하세요
        </p>
      </div>
    </div>
  </div>
</template>
