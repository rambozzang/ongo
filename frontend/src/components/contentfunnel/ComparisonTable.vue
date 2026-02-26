<template>
  <div class="rounded-xl border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-900">
    <div class="border-b border-gray-200 p-4 dark:border-gray-700">
      <h3 class="text-base font-semibold text-gray-900 dark:text-gray-100">퍼널 비교</h3>
      <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
        두 영상의 각 단계별 수치를 비교합니다
      </p>
    </div>

    <div class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-gray-200 text-xs uppercase text-gray-500 dark:border-gray-700 dark:text-gray-400">
            <th class="px-4 py-3 text-left font-medium">단계</th>
            <th class="px-4 py-3 text-right font-medium">
              <span class="block truncate max-w-[150px]" :title="comparison.videoA.videoTitle">
                {{ comparison.videoA.videoTitle }}
              </span>
            </th>
            <th class="px-4 py-3 text-right font-medium">
              <span class="block truncate max-w-[150px]" :title="comparison.videoB.videoTitle">
                {{ comparison.videoB.videoTitle }}
              </span>
            </th>
            <th class="px-4 py-3 text-center font-medium">우세</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-100 dark:divide-gray-700">
          <tr
            v-for="(stageWinner, i) in comparison.stageWinners"
            :key="stageWinner.stage"
            class="hover:bg-gray-50 dark:hover:bg-gray-700/50"
          >
            <td class="whitespace-nowrap px-4 py-3 font-medium text-gray-900 dark:text-gray-100">
              {{ stageWinner.stage }}
            </td>
            <td class="whitespace-nowrap px-4 py-3 text-right" :class="cellClassA(stageWinner.winner)">
              <span class="font-semibold">{{ formatCount(comparison.videoA.stages[i]?.count ?? 0) }}</span>
              <span class="ml-1 text-xs text-gray-400 dark:text-gray-500">
                ({{ (comparison.videoA.stages[i]?.rate ?? 0).toFixed(1) }}%)
              </span>
            </td>
            <td class="whitespace-nowrap px-4 py-3 text-right" :class="cellClassB(stageWinner.winner)">
              <span class="font-semibold">{{ formatCount(comparison.videoB.stages[i]?.count ?? 0) }}</span>
              <span class="ml-1 text-xs text-gray-400 dark:text-gray-500">
                ({{ (comparison.videoB.stages[i]?.rate ?? 0).toFixed(1) }}%)
              </span>
            </td>
            <td class="whitespace-nowrap px-4 py-3 text-center">
              <span
                class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
                :class="winnerBadgeClass(stageWinner.winner)"
              >
                {{ winnerLabel(stageWinner.winner) }}
              </span>
            </td>
          </tr>
          <!-- Overall row -->
          <tr class="bg-gray-50 dark:bg-gray-800/50">
            <td class="whitespace-nowrap px-4 py-3 font-bold text-gray-900 dark:text-gray-100">
              전체 전환율
            </td>
            <td class="whitespace-nowrap px-4 py-3 text-right font-bold" :class="comparison.videoA.overallConversion >= comparison.videoB.overallConversion ? 'text-green-600 dark:text-green-400' : 'text-gray-500 dark:text-gray-400'">
              {{ comparison.videoA.overallConversion.toFixed(2) }}%
            </td>
            <td class="whitespace-nowrap px-4 py-3 text-right font-bold" :class="comparison.videoB.overallConversion >= comparison.videoA.overallConversion ? 'text-green-600 dark:text-green-400' : 'text-gray-500 dark:text-gray-400'">
              {{ comparison.videoB.overallConversion.toFixed(2) }}%
            </td>
            <td class="whitespace-nowrap px-4 py-3 text-center">
              <span
                class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-bold"
                :class="overallWinnerClass"
              >
                {{ overallWinnerLabel }}
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { FunnelComparison } from '@/types/contentFunnel'

const props = defineProps<{
  comparison: FunnelComparison
}>()

function formatCount(value: number): string {
  if (value >= 10000) {
    return `${(value / 10000).toFixed(1)}만`
  }
  return value.toLocaleString('ko-KR')
}

function cellClassA(winner: 'A' | 'B' | 'TIE'): string {
  return winner === 'A' ? 'text-green-600 dark:text-green-400' : 'text-gray-500 dark:text-gray-400'
}

function cellClassB(winner: 'A' | 'B' | 'TIE'): string {
  return winner === 'B' ? 'text-green-600 dark:text-green-400' : 'text-gray-500 dark:text-gray-400'
}

function winnerBadgeClass(winner: 'A' | 'B' | 'TIE'): string {
  if (winner === 'A') return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
  if (winner === 'B') return 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400'
  return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
}

function winnerLabel(winner: 'A' | 'B' | 'TIE'): string {
  if (winner === 'A') return 'A'
  if (winner === 'B') return 'B'
  return '동률'
}

const overallWinnerClass = computed(() => {
  const a = props.comparison.videoA.overallConversion
  const b = props.comparison.videoB.overallConversion
  if (a > b) return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
  if (b > a) return 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400'
  return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
})

const overallWinnerLabel = computed(() => {
  const a = props.comparison.videoA.overallConversion
  const b = props.comparison.videoB.overallConversion
  if (a > b) return 'A 승리'
  if (b > a) return 'B 승리'
  return '동률'
})
</script>
