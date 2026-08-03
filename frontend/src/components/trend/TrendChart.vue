<template>
  <div class="overflow-hidden rounded-[11px] border border-line bg-surface-card">
    <div class="border-b border-line-row px-[15px] py-3">
      <h3 class="text-[13px] font-bold text-content">키워드 트렌드</h3>
    </div>
    <div v-if="trends.length === 0" class="px-4 py-12 text-center text-body text-content-tertiary">
      트렌드 데이터가 없습니다.
    </div>
    <div v-else class="space-y-3 p-4">
      <div
        v-for="(trend, index) in trends"
        :key="trend.id"
        class="flex items-center gap-4"
      >
        <span class="w-8 text-right font-mono text-body font-bold" :class="index < 3 ? 'text-accent' : 'text-content-tertiary'">
          {{ index + 1 }}
        </span>
        <div class="flex-1">
          <div class="flex items-center justify-between mb-1">
            <span class="text-body font-medium text-content">{{ trend.keyword }}</span>
            <span class="font-mono text-body-xs text-content-tertiary">{{ formatScore(trend.score) }}</span>
          </div>
          <div class="h-2 w-full overflow-hidden rounded-full bg-surface-muted">
            <div
              class="h-2 rounded-full transition-all"
              :class="index < 3 ? 'bg-accent' : 'bg-muted-strong'"
              :style="{ width: barWidth(trend.score) + '%' }"
            />
          </div>
        </div>
        <span class="rounded-full bg-muted-subtle px-2 py-0.5 text-body-xs text-muted-strong">
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
