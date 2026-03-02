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
import { useLocale } from '@/composables/useLocale'
import { useBrandSafetyStore } from '@/stores/brandSafety'
import SafetyCheckCard from '@/components/brandsafety/SafetyCheckCard.vue'
import SafetyRuleToggle from '@/components/brandsafety/SafetyRuleToggle.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import OTabs from '@/components/ui/OTabs.vue'

const { t } = useLocale()
const store = useBrandSafetyStore()
const { checks, rules, summary, isLoading } = storeToRefs(store)

const activeTab = ref<'checks' | 'rules'>('checks')
const statusFilter = ref<string>('ALL')

const safetyTabs = computed(() => [
  { key: 'checks', label: t('brandSafety.checksTab'), count: checks.value.length },
  { key: 'rules', label: t('brandSafety.rulesTab'), count: rules.value.length },
])

const statusFilters = [
  { value: 'ALL', label: t('brandSafety.filterAll') },
  { value: 'SAFE', label: t('brandSafety.filterSafe') },
  { value: 'WARNING', label: t('brandSafety.filterWarning') },
  { value: 'VIOLATION', label: t('brandSafety.filterViolation') },
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
    <PageHeader :title="$t('brandSafety.title')" :description="$t('brandSafety.description')" />

    <!-- Summary Stats -->
    <div class="mb-6 grid grid-cols-2 gap-4 desktop:grid-cols-5">
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ShieldCheckIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('brandSafety.totalChecks') }}</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ summary.totalChecks.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <CheckBadgeIcon class="h-5 w-5 text-green-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('brandSafety.safe') }}</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-green-600 dark:text-green-400">
          {{ summary.safeCount.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ExclamationTriangleIcon class="h-5 w-5 text-yellow-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('brandSafety.warning') }}</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-yellow-600 dark:text-yellow-400">
          {{ summary.warningCount.toLocaleString('ko-KR') }}
        </p>
      </div>

      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <XCircleIcon class="h-5 w-5 text-red-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('brandSafety.violation') }}</p>
        </div>
        <p class="mt-1 text-2xl font-bold text-red-600 dark:text-red-400">
          {{ summary.violationCount.toLocaleString('ko-KR') }}
        </p>
      </div>

      <!-- Average Score Circle -->
      <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
        <div class="flex items-center gap-2">
          <ShieldExclamationIcon class="h-5 w-5 text-primary-500" />
          <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('brandSafety.avgScore') }}</p>
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
    <OTabs v-model="activeTab" :tabs="safetyTabs" class="mb-6" />

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
          {{ $t('brandSafety.noChecksTitle') }}
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('brandSafety.noChecksDesc') }}
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
          {{ $t('brandSafety.noRulesTitle') }}
        </h3>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('brandSafety.noRulesDesc') }}
        </p>
      </div>
    </div>
  </div>
</template>
