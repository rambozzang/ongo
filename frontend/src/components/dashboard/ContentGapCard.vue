<template>
  <div class="card overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between border-b border-gray-100 p-4 dark:border-gray-700">
      <div class="flex items-center gap-2">
        <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-green-100 dark:bg-green-900/30">
          <LightBulbIcon class="h-4 w-4 text-green-600 dark:text-green-400" />
        </div>
        <div>
          <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">콘텐츠 갭 분석</h3>
          <p class="text-xs text-gray-500 dark:text-gray-400">10 크레딧</p>
        </div>
      </div>
    </div>

    <!-- Initial State (Not Analyzed) -->
    <div v-if="!result && !loading" class="p-4 text-center">
      <LightBulbIcon class="mx-auto h-8 w-8 text-gray-300 dark:text-gray-600" />
      <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">콘텐츠 기회를 발견하세요</p>
      <button
        class="btn-primary btn-press mt-3 inline-flex items-center gap-1.5 text-sm"
        @click="analyze"
      >
        <SparklesIcon class="h-4 w-4" />
        분석 시작
      </button>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="space-y-3 p-4">
      <div class="flex items-center gap-2">
        <div class="h-4 w-4 animate-spin rounded-full border-2 border-primary-500 border-t-transparent" />
        <span class="text-sm text-gray-500 dark:text-gray-400">AI가 분석 중입니다...</span>
      </div>
      <div class="h-4 w-3/4 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
      <div class="h-4 w-1/2 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
    </div>

    <!-- Results -->
    <div v-if="result && !loading" class="space-y-3 p-4">
      <!-- Opportunities -->
      <div v-if="result.opportunities.length > 0">
        <p class="mb-2 text-xs font-medium text-green-600 dark:text-green-400">기회 발견</p>
        <div class="space-y-2">
          <div
            v-for="(opp, idx) in result.opportunities.slice(0, 3)"
            :key="idx"
            class="rounded-lg border border-green-200 bg-green-50 p-2.5 dark:border-green-800 dark:bg-green-900/20"
          >
            <div class="flex items-start justify-between">
              <p class="text-xs font-medium text-gray-900 dark:text-gray-100">{{ opp.topic }}</p>
              <span
                class="flex-shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-medium"
                :class="demandBadge(opp.estimatedDemand)"
              >
                {{ opp.estimatedDemand }}
              </span>
            </div>
            <p class="mt-1 text-[11px] text-gray-600 dark:text-gray-400">{{ opp.suggestedAngle }}</p>
          </div>
        </div>
      </div>

      <!-- Oversaturated -->
      <div v-if="result.oversaturated.length > 0">
        <p class="mb-2 text-xs font-medium text-red-600 dark:text-red-400">과포화 주제</p>
        <div class="space-y-2">
          <div
            v-for="(topic, idx) in result.oversaturated.slice(0, 2)"
            :key="idx"
            class="rounded-lg border border-red-200 bg-red-50 p-2.5 dark:border-red-800 dark:bg-red-900/20"
          >
            <p class="text-xs font-medium text-gray-900 dark:text-gray-100">{{ topic.topic }}</p>
            <p class="mt-1 text-[11px] text-gray-600 dark:text-gray-400">{{ topic.recommendation }}</p>
          </div>
        </div>
      </div>

      <!-- Re-analyze button -->
      <button
        class="w-full rounded-lg border border-gray-200 py-2 text-xs text-gray-500 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-400 dark:hover:bg-gray-800"
        @click="analyze"
      >
        다시 분석
      </button>
    </div>

    <!-- Error State -->
    <div v-if="error" class="p-4 text-center">
      <p class="text-sm text-red-500">{{ error }}</p>
      <button
        class="mt-2 text-xs text-primary-600 hover:underline dark:text-primary-400"
        @click="analyze"
      >
        다시 시도
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { LightBulbIcon, SparklesIcon } from '@heroicons/vue/24/outline'
import { aiApi } from '@/api/ai'
import type { ContentGapResponse } from '@/types/ai'

const result = ref<ContentGapResponse | null>(null)
const loading = ref(false)
const error = ref('')

function demandBadge(demand: string) {
  switch (demand) {
    case 'HIGH':
      return 'bg-green-100 text-green-700 dark:bg-green-900/50 dark:text-green-300'
    case 'MEDIUM':
      return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/50 dark:text-yellow-300'
    case 'LOW':
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
  }
}

async function analyze() {
  loading.value = true
  error.value = ''
  try {
    result.value = await aiApi.contentGapAnalysis({ includeCompetitors: true })
  } catch (e: unknown) {
    error.value = e instanceof Error ? e.message : '분석에 실패했습니다'
  } finally {
    loading.value = false
  }
}
</script>
