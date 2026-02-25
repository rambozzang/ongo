<script setup lang="ts">
import { computed } from 'vue'
import { XMarkIcon, CheckCircleIcon, TrophyIcon } from '@heroicons/vue/24/outline'
import type { AbTest, AbTestVariant } from '@/types/abtest'
import VariantCompare from './VariantCompare.vue'

interface Props {
  test: AbTest | null
}

interface Emits {
  (e: 'close'): void
  (e: 'apply-winner', testId: number): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const winner = computed(() => {
  if (!props.test) return null
  return props.test.variants.find((v: AbTestVariant) => v.isWinner)
})

const loser = computed(() => {
  if (!props.test) return null
  return props.test.variants.find((v: AbTestVariant) => !v.isWinner)
})

const improvement = computed(() => {
  if (!winner.value || !loser.value || loser.value.ctr === 0) return 0
  return ((winner.value.ctr - loser.value.ctr) / loser.value.ctr) * 100
})

const isSignificant = computed(() => {
  return Math.abs(improvement.value) >= 10
})
</script>

<template>
  <div
    v-if="test"
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
    role="dialog"
    aria-modal="true"
    aria-labelledby="abtest-results-panel-title"
  >
    <div
      class="bg-white dark:bg-gray-800 rounded-xl shadow-2xl max-w-5xl w-full max-h-[90vh] overflow-hidden flex flex-col"
      @keydown.escape="emit('close')"
    >
      <!-- Header -->
      <div class="flex items-center justify-between p-6 border-b border-gray-200 dark:border-gray-700">
        <div>
          <h2 id="abtest-results-panel-title" class="text-2xl font-bold text-gray-900 dark:text-white">테스트 결과</h2>
          <p class="text-sm text-gray-600 dark:text-gray-400 mt-1">{{ test.videoTitle }}</p>
        </div>
        <button
          @click="emit('close')"
          class="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
          aria-label="모달 닫기"
        >
          <XMarkIcon class="w-6 h-6 text-gray-500 dark:text-gray-400" />
        </button>
      </div>

      <!-- Content -->
      <div class="flex-1 overflow-y-auto p-6 space-y-6">
        <!-- Summary -->
        <div class="bg-gradient-to-r from-blue-50 to-purple-50 dark:from-blue-900/20 dark:to-purple-900/20 rounded-lg p-6">
          <div class="flex items-center gap-3 mb-4">
            <TrophyIcon class="w-8 h-8 text-yellow-500" />
            <div>
              <h3 class="text-lg font-semibold text-gray-900 dark:text-white">우승 변형</h3>
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ winner?.label }}</p>
            </div>
          </div>
          <div class="grid grid-cols-3 gap-4">
            <div>
              <div class="text-sm text-gray-600 dark:text-gray-400 mb-1">CTR 개선율</div>
              <div class="text-3xl font-bold text-green-600 dark:text-green-400">
                +{{ improvement.toFixed(1) }}%
              </div>
            </div>
            <div>
              <div class="text-sm text-gray-600 dark:text-gray-400 mb-1">통계적 유의성</div>
              <div class="flex items-center gap-2">
                <CheckCircleIcon
                  :class="[
                    'w-6 h-6',
                    isSignificant ? 'text-green-500' : 'text-yellow-500'
                  ]"
                />
                <span class="text-lg font-semibold text-gray-900 dark:text-white">
                  {{ isSignificant ? '유의함' : '제한적' }}
                </span>
              </div>
            </div>
            <div>
              <div class="text-sm text-gray-600 dark:text-gray-400 mb-1">신뢰 수준</div>
              <div class="text-3xl font-bold text-gray-900 dark:text-white">{{ test.confidenceLevel }}%</div>
            </div>
          </div>
        </div>

        <!-- Bar chart comparison -->
        <div class="bg-gray-50 dark:bg-gray-700/50 rounded-lg p-6">
          <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">성과 비교</h3>
          <div class="space-y-6">
            <!-- CTR Comparison -->
            <div>
              <div class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">클릭률 (CTR)</div>
              <div class="space-y-2">
                <div v-for="variant in test.variants" :key="variant.id">
                  <div class="flex items-center justify-between mb-1">
                    <span class="text-sm text-gray-600 dark:text-gray-400">{{ variant.label }}</span>
                    <span class="text-sm font-semibold text-gray-900 dark:text-white">{{ variant.ctr.toFixed(2) }}%</span>
                  </div>
                  <div class="relative h-8 bg-gray-200 dark:bg-gray-700 rounded-lg overflow-hidden">
                    <div
                      :class="[
                        'h-full flex items-center justify-end px-3 transition-all',
                        variant.isWinner ? 'bg-gradient-to-r from-green-500 to-green-600' : 'bg-gradient-to-r from-blue-400 to-blue-500'
                      ]"
                      :style="{ width: `${(variant.ctr / Math.max(...test.variants.map((v: AbTestVariant) => v.ctr))) * 100}%` }"
                    >
                      <TrophyIcon v-if="variant.isWinner" class="w-5 h-5 text-white" />
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Watch Time Comparison -->
            <div>
              <div class="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">평균 시청 시간</div>
              <div class="space-y-2">
                <div v-for="variant in test.variants" :key="variant.id">
                  <div class="flex items-center justify-between mb-1">
                    <span class="text-sm text-gray-600 dark:text-gray-400">{{ variant.label }}</span>
                    <span class="text-sm font-semibold text-gray-900 dark:text-white">{{ variant.avgWatchTime }}초</span>
                  </div>
                  <div class="relative h-8 bg-gray-200 dark:bg-gray-700 rounded-lg overflow-hidden">
                    <div
                      :class="[
                        'h-full flex items-center justify-end px-3 transition-all',
                        variant.isWinner ? 'bg-gradient-to-r from-purple-500 to-purple-600' : 'bg-gradient-to-r from-indigo-400 to-indigo-500'
                      ]"
                      :style="{ width: `${(variant.avgWatchTime / Math.max(...test.variants.map((v: AbTestVariant) => v.avgWatchTime))) * 100}%` }"
                    >
                      <TrophyIcon v-if="variant.isWinner" class="w-5 h-5 text-white" />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Metrics table -->
        <div class="bg-white dark:bg-gray-800 rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden">
          <div class="overflow-x-auto">
            <table class="w-full">
              <thead class="bg-gray-50 dark:bg-gray-700/50">
                <tr>
                  <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">변형</th>
                  <th class="px-4 py-3 text-right text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">노출 수</th>
                  <th class="px-4 py-3 text-right text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">클릭 수</th>
                  <th class="px-4 py-3 text-right text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">CTR</th>
                  <th class="px-4 py-3 text-right text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">시청 시간</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-200 dark:divide-gray-700">
                <tr v-for="variant in test.variants" :key="variant.id" :class="variant.isWinner ? 'bg-green-50 dark:bg-green-900/10' : ''">
                  <td class="px-4 py-3 text-sm font-medium text-gray-900 dark:text-white">
                    <div class="flex items-center gap-2">
                      {{ variant.label }}
                      <TrophyIcon v-if="variant.isWinner" class="w-4 h-4 text-yellow-500" />
                    </div>
                  </td>
                  <td class="px-4 py-3 text-sm text-right text-gray-700 dark:text-gray-300">{{ variant.impressions.toLocaleString() }}</td>
                  <td class="px-4 py-3 text-sm text-right text-gray-700 dark:text-gray-300">{{ variant.clicks.toLocaleString() }}</td>
                  <td class="px-4 py-3 text-sm text-right font-semibold text-gray-900 dark:text-white">{{ variant.ctr.toFixed(2) }}%</td>
                  <td class="px-4 py-3 text-sm text-right text-gray-700 dark:text-gray-300">{{ variant.avgWatchTime }}초</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Detailed comparison -->
        <div>
          <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">변형 상세 비교</h3>
          <VariantCompare :variants="test.variants" :type="test.type" />
        </div>
      </div>

      <!-- Footer -->
      <div class="flex items-center justify-end gap-3 p-6 border-t border-gray-200 dark:border-gray-700">
        <button
          @click="emit('close')"
          class="px-4 py-2 text-sm font-medium text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors"
        >
          닫기
        </button>
        <button
          @click="emit('apply-winner', test.id)"
          class="px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 dark:bg-blue-600 dark:hover:bg-blue-700 rounded-lg transition-colors"
        >
          우승 변형 적용
        </button>
      </div>
    </div>
  </div>
</template>
