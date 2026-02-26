<script setup lang="ts">
import { onMounted, computed } from 'vue'
import {
  CogIcon,
  BoltIcon,
  PlayCircleIcon,
  CheckCircleIcon,
} from '@heroicons/vue/24/outline'
import { usePlatformAutomationStore } from '@/stores/platformAutomation'
import type { AutomationRule, AutomationLog } from '@/types/platformAutomation'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import AutomationRuleCard from '@/components/platformautomation/AutomationRuleCard.vue'
import AutomationLogRow from '@/components/platformautomation/AutomationLogRow.vue'

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
          플랫폼 자동화
        </h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          플랫폼별 자동화 규칙을 관리하고 실행 로그를 확인합니다
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
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 규칙</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary.totalRules }}개
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
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">활성 규칙</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary.activeRules }}개
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
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">총 실행</p>
              <p class="text-xl font-bold text-gray-900 dark:text-gray-100">
                {{ store.summary.totalExecutions.toLocaleString('ko-KR') }}회
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
              <p class="text-xs font-medium text-gray-500 dark:text-gray-400">성공률</p>
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
          자동화 규칙
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
            등록된 자동화 규칙이 없습니다
          </h3>
          <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
            새 규칙을 추가하여 플랫폼 작업을 자동화하세요
          </p>
        </div>
      </section>

      <!-- Execution Logs -->
      <section>
        <h2 class="mb-4 text-lg font-semibold text-gray-900 dark:text-gray-100">
          실행 로그
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
            실행 로그가 없습니다
          </p>
        </div>
      </section>
    </template>
  </div>
</template>
