<template>
  <div class="card overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between border-b border-gray-100 p-4 dark:border-gray-700">
      <div class="flex items-center gap-2">
        <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-success-subtle">
          <LightBulbIcon class="h-4 w-4 text-success-strong" />
        </div>
        <div>
          <h3 class="text-body font-semibold text-gray-900 dark:text-gray-100">콘텐츠 갭 분석</h3>
          <p class="text-body-xs text-gray-500 dark:text-gray-400">10 크레딧</p>
        </div>
      </div>
    </div>

    <!-- 로딩 → 에러 → (초기 안내 | 결과) -->
    <AsyncState
      :loading="loading"
      :error="error"
      skeleton="list"
      :skeleton-count="2"
      class="p-4"
      @retry="analyze"
    >
      <!-- Initial State (Not Analyzed) -->
      <div v-if="!result" class="text-center">
        <LightBulbIcon class="mx-auto h-8 w-8 text-gray-300 dark:text-gray-600" />
        <p class="mt-2 text-body text-gray-500 dark:text-gray-400">콘텐츠 기회를 발견하세요</p>
        <button
          class="btn-primary btn-press mt-3 inline-flex items-center gap-1.5 text-body"
          @click="analyze"
        >
          <SparklesIcon class="h-4 w-4" />
          분석 시작
        </button>
      </div>

      <!-- Results -->
      <div v-else class="space-y-3">
        <!-- Opportunities -->
        <div v-if="result.opportunities.length > 0">
          <p class="mb-2 text-caption text-success-strong">기회 발견</p>
          <div class="space-y-2">
            <div
              v-for="(opp, idx) in result.opportunities.slice(0, 3)"
              :key="idx"
              class="rounded-lg border border-success bg-success-subtle p-2.5"
            >
              <div class="flex items-start justify-between">
                <p class="text-caption text-gray-900 dark:text-gray-100">{{ opp.topic }}</p>
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
          <p class="mb-2 text-caption text-error-strong">과포화 주제</p>
          <div class="space-y-2">
            <div
              v-for="(topic, idx) in result.oversaturated.slice(0, 2)"
              :key="idx"
              class="rounded-lg border border-error bg-error-subtle p-2.5"
            >
              <p class="text-caption text-gray-900 dark:text-gray-100">{{ topic.topic }}</p>
              <p class="mt-1 text-[11px] text-gray-600 dark:text-gray-400">{{ topic.recommendation }}</p>
            </div>
          </div>
        </div>

        <!-- Re-analyze button -->
        <button
          class="w-full rounded-lg border border-gray-200 py-2 text-body-xs text-gray-500 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-400 dark:hover:bg-gray-800"
          @click="analyze"
        >
          다시 분석
        </button>
      </div>
    </AsyncState>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { LightBulbIcon, SparklesIcon } from '@heroicons/vue/24/outline'
import { aiApi } from '@/api/ai'
import type { ContentGapResponse } from '@/types/ai'
import AsyncState from '@/components/common/AsyncState.vue'

const result = ref<ContentGapResponse | null>(null)
const loading = ref(false)
const error = ref('')

function demandBadge(demand: string) {
  switch (demand) {
    case 'HIGH':
      return 'bg-success-subtle text-success-strong'
    case 'MEDIUM':
      return 'bg-warning-subtle text-warning-strong'
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
