<template>
  <div class="bg-white rounded-lg border border-gray-200 p-6">
    <h3 class="text-lg font-semibold text-gray-900 mb-4">키워드 트렌드</h3>
    <div v-if="trends.length === 0" class="text-center py-8 text-gray-400">
      트렌드 데이터가 없습니다.
    </div>
    <div v-else class="space-y-3">
      <div
        v-for="(trend, index) in trends"
        :key="trend.id"
        class="flex items-center gap-4"
      >
        <span class="w-8 text-right text-sm font-bold" :class="index < 3 ? 'text-indigo-600' : 'text-gray-400'">
          {{ index + 1 }}
        </span>
        <div class="flex-1">
          <div class="flex items-center justify-between mb-1">
            <span class="text-sm font-medium text-gray-900">{{ trend.keyword }}</span>
            <span class="text-xs text-gray-500">{{ formatScore(trend.score) }}</span>
          </div>
          <div class="w-full bg-gray-100 rounded-full h-2">
            <div
              class="h-2 rounded-full transition-all"
              :class="index < 3 ? 'bg-indigo-500' : 'bg-gray-300'"
              :style="{ width: barWidth(trend.score) + '%' }"
            />
          </div>
        </div>
        <span class="text-xs px-2 py-0.5 rounded-full bg-gray-100 text-gray-500">
          {{ trend.source }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { Trend } from '@/types/trend'

const props = defineProps<{
  trends: Trend[]
}>()

const maxScore = () => {
  if (props.trends.length === 0) return 1
  return Math.max(...props.trends.map((t) => t.score), 1)
}

function barWidth(score: number): number {
  return Math.min((score / maxScore()) * 100, 100)
}

function formatScore(score: number): string {
  if (score >= 1000000) return (score / 1000000).toFixed(1) + 'M'
  if (score >= 1000) return (score / 1000).toFixed(1) + 'K'
  return score.toFixed(0)
}
</script>
