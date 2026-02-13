<template>
  <div class="rounded-lg border border-gray-200 bg-white p-6 dark:border-gray-700 dark:bg-gray-800">
    <h4 class="mb-4 text-base font-semibold text-gray-900 dark:text-white">통계적 유의성</h4>

    <!-- Loading -->
    <div v-if="loading" class="flex h-40 items-center justify-center">
      <div class="h-8 w-8 animate-spin rounded-full border-2 border-gray-300 border-t-blue-500" />
    </div>

    <template v-else-if="stats">
      <div class="grid gap-6 lg:grid-cols-2">
        <!-- Confidence Gauge -->
        <div class="flex flex-col items-center">
          <div class="relative">
            <svg class="h-32 w-32 -rotate-90 transform">
              <circle cx="64" cy="64" r="56" stroke="currentColor" :stroke-width="8" fill="none"
                class="text-gray-200 dark:text-gray-700" />
              <circle cx="64" cy="64" r="56" stroke="currentColor" :stroke-width="8" fill="none"
                :stroke-dasharray="gaugeCircumference"
                :stroke-dashoffset="gaugeCircumference * (1 - stats.confidence / 100)"
                :class="confidenceColorClass"
                class="transition-all duration-1000 ease-out" stroke-linecap="round" />
            </svg>
            <div class="absolute inset-0 flex flex-col items-center justify-center">
              <span class="text-2xl font-bold text-gray-900 dark:text-white">
                {{ stats.confidence.toFixed(1) }}%
              </span>
              <span class="text-xs text-gray-500 dark:text-gray-400">신뢰도</span>
            </div>
          </div>

          <!-- Winner Badge -->
          <div v-if="stats.isSignificant && winner" class="mt-3 flex items-center gap-2 rounded-full bg-green-100 px-3 py-1.5 dark:bg-green-900/30">
            <svg class="h-4 w-4 text-green-600 dark:text-green-400" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.857-9.809a.75.75 0 00-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 10-1.06 1.061l2.5 2.5a.75.75 0 001.137-.089l4-5.5z" clip-rule="evenodd" />
            </svg>
            <span class="text-sm font-medium text-green-700 dark:text-green-400">
              우승: {{ winner.name }}
            </span>
          </div>
        </div>

        <!-- Stats Details -->
        <div class="space-y-4">
          <!-- Sample Size Progress -->
          <div>
            <div class="mb-1 flex items-center justify-between text-sm">
              <span class="text-gray-600 dark:text-gray-400">표본 크기</span>
              <span class="font-medium text-gray-900 dark:text-white">
                {{ stats.currentSampleSize.toLocaleString() }} / {{ stats.sampleSizeRequired.toLocaleString() }}
              </span>
            </div>
            <div class="h-2.5 w-full rounded-full bg-gray-200 dark:bg-gray-700">
              <div
                class="h-full rounded-full bg-blue-500 transition-all duration-500"
                :style="{ width: `${Math.min(stats.sampleProgress, 100)}%` }"
              />
            </div>
            <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
              {{ stats.sampleProgress.toFixed(1) }}% 달성
            </p>
          </div>

          <!-- P-Value -->
          <div class="flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2 dark:bg-gray-900/50">
            <span class="text-sm text-gray-600 dark:text-gray-400">P-값</span>
            <span class="text-sm font-mono font-medium" :class="pValueColorClass">
              {{ stats.pValue.toFixed(4) }}
            </span>
          </div>

          <!-- Significance Status -->
          <div class="flex items-center gap-2">
            <span class="h-2.5 w-2.5 rounded-full" :class="stats.isSignificant ? 'bg-green-500' : 'bg-gray-400'" />
            <span class="text-sm" :class="stats.isSignificant ? 'text-green-600 dark:text-green-400 font-medium' : 'text-gray-500 dark:text-gray-400'">
              {{ stats.isSignificant ? '통계적으로 유의미합니다 (p < 0.05)' : '아직 유의미하지 않습니다' }}
            </span>
          </div>
        </div>
      </div>

      <!-- Variant Comparison Table -->
      <div v-if="stats.variants.length > 0" class="mt-6 overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-200 dark:border-gray-700">
              <th class="pb-2 text-left font-medium text-gray-500 dark:text-gray-400">변형</th>
              <th class="pb-2 text-right font-medium text-gray-500 dark:text-gray-400">노출</th>
              <th class="pb-2 text-right font-medium text-gray-500 dark:text-gray-400">전환</th>
              <th class="pb-2 text-right font-medium text-gray-500 dark:text-gray-400">전환율</th>
              <th class="pb-2 text-right font-medium text-gray-500 dark:text-gray-400">신뢰 구간</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="variant in stats.variants"
              :key="variant.variantId"
              class="border-b border-gray-100 dark:border-gray-700/50"
              :class="variant.isWinner ? 'bg-green-50/50 dark:bg-green-900/10' : ''"
            >
              <td class="py-2.5 text-left">
                <div class="flex items-center gap-2">
                  <span class="font-medium text-gray-900 dark:text-white">{{ variant.name }}</span>
                  <span v-if="variant.isWinner" class="rounded bg-green-100 px-1.5 py-0.5 text-xs font-semibold text-green-700 dark:bg-green-900/30 dark:text-green-400">
                    우승
                  </span>
                </div>
              </td>
              <td class="py-2.5 text-right text-gray-700 dark:text-gray-300">
                {{ variant.impressions.toLocaleString() }}
              </td>
              <td class="py-2.5 text-right text-gray-700 dark:text-gray-300">
                {{ variant.conversions.toLocaleString() }}
              </td>
              <td class="py-2.5 text-right font-medium text-gray-900 dark:text-white">
                {{ variant.conversionRate.toFixed(2) }}%
              </td>
              <td class="py-2.5 text-right font-mono text-xs text-gray-500 dark:text-gray-400">
                [{{ variant.confidenceInterval[0].toFixed(2) }}%, {{ variant.confidenceInterval[1].toFixed(2) }}%]
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <!-- Empty -->
    <div v-else class="flex h-40 flex-col items-center justify-center text-center">
      <p class="text-sm text-gray-400 dark:text-gray-500">통계 데이터를 불러올 수 없습니다</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { abtestApi } from '@/api/abtest'
import type { ABTestStatisticsResponse } from '@/types/abtest'

const props = defineProps<{
  testId: number
}>()

const stats = ref<ABTestStatisticsResponse | null>(null)
const loading = ref(false)

const gaugeCircumference = 2 * Math.PI * 56

const confidenceColorClass = computed(() => {
  if (!stats.value) return 'text-gray-400'
  const c = stats.value.confidence
  if (c < 80) return 'text-red-500'
  if (c < 95) return 'text-yellow-500'
  return 'text-green-500'
})

const pValueColorClass = computed(() => {
  if (!stats.value) return 'text-gray-500'
  return stats.value.pValue < 0.05
    ? 'text-green-600 dark:text-green-400'
    : 'text-gray-700 dark:text-gray-300'
})

const winner = computed(() => {
  if (!stats.value) return null
  return stats.value.variants.find(v => v.isWinner) ?? null
})

async function fetchStatistics() {
  loading.value = true
  try {
    stats.value = await abtestApi.statistics(props.testId)
  } catch {
    stats.value = null
  } finally {
    loading.value = false
  }
}

onMounted(fetchStatistics)
watch(() => props.testId, fetchStatistics)
</script>
