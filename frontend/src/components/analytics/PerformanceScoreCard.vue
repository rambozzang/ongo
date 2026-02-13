<template>
  <div class="card">
    <div class="mb-4 flex items-center justify-between">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">AI 성과 점수</h3>
      <span
        v-if="score"
        class="rounded-full px-2.5 py-0.5 text-xs font-semibold"
        :class="percentileBadgeClass"
      >
        Top {{ score.percentileRank.toFixed(0) }}%
      </span>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex h-48 items-center justify-center">
      <div class="h-8 w-8 animate-spin rounded-full border-2 border-gray-300 border-t-primary-500" />
    </div>

    <template v-else-if="score">
      <div class="grid gap-6 desktop:grid-cols-2">
        <!-- Overall Score Circle -->
        <div class="flex flex-col items-center justify-center">
          <div class="relative">
            <svg class="h-40 w-40 -rotate-90 transform">
              <circle
                cx="80" cy="80" r="70"
                stroke="currentColor" :stroke-width="10" fill="none"
                class="text-gray-200 dark:text-gray-700"
              />
              <circle
                cx="80" cy="80" r="70"
                stroke="currentColor" :stroke-width="10" fill="none"
                :stroke-dasharray="circumference"
                :stroke-dashoffset="scoreOffset"
                :class="scoreColorClass"
                class="transition-all duration-1000 ease-out"
                stroke-linecap="round"
              />
            </svg>
            <div class="absolute inset-0 flex flex-col items-center justify-center">
              <span class="text-4xl font-bold text-gray-900 dark:text-gray-100">
                {{ Math.round(score.overallScore) }}
              </span>
              <div class="flex items-center gap-1">
                <svg
                  v-if="score.trend === 'up'"
                  class="h-3.5 w-3.5 text-green-500" viewBox="0 0 20 20" fill="currentColor"
                >
                  <path fill-rule="evenodd" d="M10 17a.75.75 0 01-.75-.75V5.612L5.29 9.77a.75.75 0 01-1.08-1.04l5.25-5.5a.75.75 0 011.08 0l5.25 5.5a.75.75 0 11-1.08 1.04l-3.96-4.158V16.25A.75.75 0 0110 17z" clip-rule="evenodd" />
                </svg>
                <svg
                  v-else-if="score.trend === 'down'"
                  class="h-3.5 w-3.5 text-red-500" viewBox="0 0 20 20" fill="currentColor"
                >
                  <path fill-rule="evenodd" d="M10 3a.75.75 0 01.75.75v10.638l3.96-4.158a.75.75 0 111.08 1.04l-5.25 5.5a.75.75 0 01-1.08 0l-5.25-5.5a.75.75 0 111.08-1.04l3.96 4.158V3.75A.75.75 0 0110 3z" clip-rule="evenodd" />
                </svg>
                <span class="text-xs text-gray-500 dark:text-gray-400">
                  {{ trendLabel }}
                </span>
              </div>
            </div>
          </div>
          <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">
            7일 예상 조회수: {{ formatNumber(score.prediction7d) }}
          </p>
        </div>

        <!-- Breakdown Bars -->
        <div class="space-y-3">
          <div v-for="(item, key) in breakdownItems" :key="key">
            <div class="mb-1 flex items-center justify-between">
              <span class="text-xs font-medium text-gray-600 dark:text-gray-400">{{ item.label }}</span>
              <span class="text-xs font-semibold text-gray-900 dark:text-gray-100">
                {{ Math.round(item.value) }}
              </span>
            </div>
            <div class="h-2 w-full rounded-full bg-gray-200 dark:bg-gray-700">
              <div
                class="h-full rounded-full transition-all duration-700"
                :class="item.colorClass"
                :style="{ width: `${item.value}%` }"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- Anomaly Alert -->
      <div
        v-if="score.isAnomaly"
        class="mt-4 flex items-start gap-3 rounded-lg border border-yellow-200 bg-yellow-50 p-3 dark:border-yellow-900/30 dark:bg-yellow-900/10"
      >
        <svg class="mt-0.5 h-5 w-5 flex-shrink-0 text-yellow-500" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd" d="M8.485 2.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.17 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 2.495zM10 5a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 0110 5zm0 9a1 1 0 100-2 1 1 0 000 2z" clip-rule="evenodd" />
        </svg>
        <div>
          <p class="text-sm font-medium text-yellow-800 dark:text-yellow-200">이상 감지됨</p>
          <p class="mt-0.5 text-xs text-yellow-700 dark:text-yellow-300">
            {{ score.anomalyDescription }}
          </p>
        </div>
      </div>
    </template>

    <!-- Empty State -->
    <div v-else class="flex h-48 flex-col items-center justify-center text-center">
      <svg class="mb-2 h-10 w-10 text-gray-300 dark:text-gray-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
      </svg>
      <p class="text-sm text-gray-400 dark:text-gray-500">성과 데이터가 없습니다</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { analyticsApi } from '@/api/analytics'
import type { PerformanceScoreResponse } from '@/types/analytics'

const props = defineProps<{
  videoId: number
}>()

const score = ref<PerformanceScoreResponse | null>(null)
const loading = ref(false)

const circumference = 2 * Math.PI * 70

const scoreOffset = computed(() => {
  if (!score.value) return circumference
  return circumference * (1 - score.value.overallScore / 100)
})

const scoreColorClass = computed(() => {
  if (!score.value) return 'text-gray-400'
  const s = score.value.overallScore
  if (s <= 30) return 'text-red-500'
  if (s <= 60) return 'text-yellow-500'
  if (s <= 80) return 'text-green-500'
  return 'text-primary-500'
})

const percentileBadgeClass = computed(() => {
  if (!score.value) return ''
  const p = score.value.percentileRank
  if (p >= 80) return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
  if (p >= 50) return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
  return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
})

const trendLabel = computed(() => {
  if (!score.value) return ''
  switch (score.value.trend) {
    case 'up': return '상승세'
    case 'down': return '하락세'
    default: return '안정'
  }
})

const breakdownItems = computed(() => {
  if (!score.value) return []
  const b = score.value.breakdown
  return [
    { label: '조회수 속도 (30%)', value: b.viewVelocity ?? 0, colorClass: 'bg-blue-500' },
    { label: '참여율 (25%)', value: b.engagement ?? 0, colorClass: 'bg-green-500' },
    { label: '시청 시간 (20%)', value: b.watchTime ?? 0, colorClass: 'bg-purple-500' },
    { label: '구독자 전환 (15%)', value: b.conversion ?? 0, colorClass: 'bg-orange-500' },
    { label: '공유율 (10%)', value: b.share ?? 0, colorClass: 'bg-pink-500' },
  ]
})

function formatNumber(n: number): string {
  if (n >= 10_000) return `${(n / 10_000).toFixed(1)}만`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}천`
  return n.toLocaleString('ko-KR')
}

async function fetchScore() {
  loading.value = true
  try {
    score.value = await analyticsApi.performanceScore(props.videoId)
  } catch {
    score.value = null
  } finally {
    loading.value = false
  }
}

onMounted(fetchScore)
watch(() => props.videoId, fetchScore)
</script>
