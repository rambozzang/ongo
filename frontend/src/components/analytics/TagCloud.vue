<template>
  <div class="relative flex min-h-[300px] flex-wrap items-center justify-center gap-3 rounded-lg bg-gray-50 p-6 dark:bg-gray-900">
    <button
      v-for="tag in sortedTags"
      :key="tag.tag"
      class="tag-button relative inline-flex items-center rounded-full px-4 py-2 font-medium transition-all duration-200 hover:scale-110 focus:outline-none focus:ring-2 focus:ring-primary-500"
      :style="{
        fontSize: `${getTagSize(tag)}px`,
        color: getTagColor(tag),
      }"
      :class="{
        'opacity-50': selectedTag && selectedTag !== tag.tag,
      }"
      @click="handleTagClick(tag.tag)"
    >
      #{{ tag.tag }}
      <div
        class="tooltip pointer-events-none absolute left-1/2 top-full z-10 mt-2 hidden w-48 -translate-x-1/2 rounded-lg border border-gray-200 bg-white p-3 text-xs text-gray-700 shadow-lg group-hover:block dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
      >
        <div class="space-y-1">
          <div class="font-semibold text-gray-900 dark:text-gray-100">#{{ tag.tag }}</div>
          <div class="flex justify-between">
            <span class="text-gray-500 dark:text-gray-400">영상 수:</span>
            <span class="font-medium">{{ tag.videoCount }}개</span>
          </div>
          <div class="flex justify-between">
            <span class="text-gray-500 dark:text-gray-400">평균 조회수:</span>
            <span class="font-medium">{{ formatNumber(tag.avgViews) }}</span>
          </div>
          <div class="flex justify-between">
            <span class="text-gray-500 dark:text-gray-400">참여율:</span>
            <span class="font-medium">{{ tag.avgEngagement.toFixed(1) }}%</span>
          </div>
        </div>
      </div>
    </button>
    <div v-if="sortedTags.length === 0" class="text-sm text-gray-400 dark:text-gray-500">
      태그 데이터가 없습니다
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TagPerformance } from '@/types/analytics'

interface Props {
  tags: TagPerformance[]
  selectedTag?: string | null
}

interface Emits {
  (e: 'tagClick', tag: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const sortedTags = computed(() => {
  return [...props.tags].sort((a, b) => b.videoCount - a.videoCount)
})

const maxVideoCount = computed(() => {
  if (props.tags.length === 0) return 1
  return Math.max(...props.tags.map((t) => t.videoCount), 1)
})

const minVideoCount = computed(() => {
  if (props.tags.length === 0) return 1
  return Math.min(...props.tags.map((t) => t.videoCount), 1)
})

const performancePercentiles = computed(() => {
  const sorted = [...props.tags].sort((a, b) => a.avgEngagement - b.avgEngagement)
  const count = sorted.length
  if (count === 0) return { top25: 0, bottom25: 0 }

  const top25Index = Math.floor(count * 0.75)
  const bottom25Index = Math.floor(count * 0.25)

  return {
    top25: sorted[top25Index]?.avgEngagement ?? 0,
    bottom25: sorted[bottom25Index]?.avgEngagement ?? 0,
  }
})

function getTagSize(tag: TagPerformance): number {
  const minSize = 12
  const maxSize = 32
  const range = maxSize - minSize

  if (maxVideoCount.value === minVideoCount.value) return (minSize + maxSize) / 2

  const normalized = (tag.videoCount - minVideoCount.value) / (maxVideoCount.value - minVideoCount.value)
  return minSize + normalized * range
}

function getTagColor(tag: TagPerformance): string {
  const percentiles = performancePercentiles.value

  if (tag.avgEngagement >= percentiles.top25) {
    return '#16a34a'
  } else if (tag.avgEngagement <= percentiles.bottom25) {
    return '#9ca3af'
  } else {
    return '#7c3aed'
  }
}

function handleTagClick(tag: string) {
  emit('tagClick', tag)
}

function formatNumber(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}K`
  return n.toLocaleString()
}
</script>

<style scoped>
.tag-button:hover .tooltip {
  display: block;
}
</style>
