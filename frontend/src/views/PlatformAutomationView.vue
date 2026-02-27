<script setup lang="ts">
import { onMounted } from 'vue'
import {
  CogIcon,
  BoltIcon,
  PlayCircleIcon,
  CheckCircleIcon,
} from '@heroicons/vue/24/outline'
import { usePlatformAutomationStore } from '@/stores/platformAutomation'
import { useLocale } from '@/composables/useLocale'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import AutomationRuleCard from '@/components/platformautomation/AutomationRuleCard.vue'
import AutomationLogRow from '@/components/platformautomation/AutomationLogRow.vue'

const { t } = useLocale()
const store = usePlatformAutomationStore()

onMounted(() => {
  store.fetchRules()
  store.fetchLogs()
  store.fetchSummary()
})
</script>

<template>
  <div class="relative">
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">
          {{ $t('platformAutomation.title') }}
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('platformAutomation.description') }}
        </p>
      </div>
    </div>

    <!-- Loading -->
    <LoadingSpinner v-if="store.loading" :full-page="true" size="lg" />

    <template v-else>
      <!-- Summary Cards -->
      <div v-if="store.summary" class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-4">
        <!-- Total Rules -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400">
              <CogIcon class="h-5 w-5" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('platformAutomation.totalRules') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary.totalRules }}{{ $t('platformAutomation.countUnit') }}
              </p>
            </div>
          </div>
        </div>

        <!-- Active Rules -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-green-100 text-green-600 dark:bg-green-900/30 dark:text-green-400">
              <BoltIcon class="h-5 w-5" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('platformAutomation.activeRules') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary.activeRules }}{{ $t('platformAutomation.countUnit') }}
              </p>
            </div>
          </div>
        </div>

        <!-- Total Executions -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-purple-100 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400">
              <PlayCircleIcon class="h-5 w-5" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('platformAutomation.totalExecutions') }}</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary.totalExecutions.toLocaleString('ko-KR') }}{{ $t('platformAutomation.timesUnit') }}
              </p>
            </div>
          </div>
        </div>

        <!-- Success Rate -->
        <div class="rounded-xl border border-gray-200 bg-white p-4 shadow-sm dark:border-gray-700 dark:bg-gray-900">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-100 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400">
              <CheckCircleIcon class="h-5 w-5" />
            </div>
            <div>
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">{{ $t('platformAutomation.successRate') }}</p>
              <p class="text-xl font-bold text-green-600 dark:text-green-400">
                {{ store.summary.successRate.toFixed(1) }}%
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Automation Rule Cards Grid -->
      <section class="mb-8">
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('platformAutomation.automationRules') }}
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ store.rules.length }})</span>
        </h2>

        <div v-if="store.rules.length > 0" class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
          <AutomationRuleCard
            v-for="rule in store.rules"
            :key="rule.id"
            :rule="rule"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-12 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <CogIcon class="mx-auto mb-3 h-12 w-12 text-gray-400 dark:text-gray-600" />
          <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {{ $t('platformAutomation.noRules') }}
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            {{ $t('platformAutomation.noRulesDesc') }}
          </p>
        </div>
      </section>

      <!-- Execution Logs -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          {{ $t('platformAutomation.executionLogs') }}
          <span class="ml-1 text-sm font-normal text-gray-500 dark:text-gray-400">({{ store.logs.length }})</span>
        </h2>

        <div v-if="store.logs.length > 0" class="space-y-2">
          <AutomationLogRow
            v-for="log in store.logs"
            :key="log.id"
            :log="log"
          />
        </div>

        <div
          v-else
          class="rounded-xl border border-gray-200 bg-white py-12 text-center shadow-sm dark:border-gray-700 dark:bg-gray-900"
        >
          <p class="text-sm text-gray-500 dark:text-gray-400">
            {{ $t('platformAutomation.noLogs') }}
          </p>
        </div>
      </section>
    </template>
  </div>
</template>
