<script setup lang="ts">
import { computed } from 'vue'

interface TagItem {
  tag: string
  count: number
}

interface Props {
  tags: TagItem[]
}

const props = defineProps<Props>()

const maxCount = computed(() => {
  if (props.tags.length === 0) return 1
  return Math.max(...props.tags.map((t) => t.count))
})

const minCount = computed(() => {
  if (props.tags.length === 0) return 0
  return Math.min(...props.tags.map((t) => t.count))
})

const tagItems = computed(() => {
  return props.tags.map((t) => {
    const range = maxCount.value - minCount.value || 1
    const normalized = (t.count - minCount.value) / range

    // 크기: 0.75rem ~ 1.5rem
    const fontSize = 0.75 + normalized * 0.75

    // 색상 단계: 5단계
    let colorClass: string
    if (normalized >= 0.8) {
      colorClass = 'text-blue-700 dark:text-blue-300 bg-blue-100 dark:bg-blue-900/40'
    } else if (normalized >= 0.6) {
      colorClass = 'text-indigo-700 dark:text-indigo-300 bg-indigo-100 dark:bg-indigo-900/40'
    } else if (normalized >= 0.4) {
      colorClass = 'text-purple-700 dark:text-purple-300 bg-purple-100 dark:bg-purple-900/40'
    } else if (normalized >= 0.2) {
      colorClass = 'text-violet-600 dark:text-violet-300 bg-violet-100 dark:bg-violet-900/40'
    } else {
      colorClass = 'text-gray-600 dark:text-gray-400 bg-gray-100 dark:bg-gray-700'
    }

    return {
      tag: t.tag,
      count: t.count,
      fontSize,
      colorClass,
    }
  })
})
</script>

<template>
  <div class="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
    <h3 class="mb-4 text-sm font-semibold text-gray-900 dark:text-gray-100">
      태그 클라우드
    </h3>
    <div v-if="tagItems.length > 0" class="flex flex-wrap items-center justify-center gap-2">
      <span
        v-for="item in tagItems"
        :key="item.tag"
        :class="['inline-flex items-center rounded-full px-3 py-1 font-medium transition-transform hover:scale-110 cursor-default', item.colorClass]"
        :style="{ fontSize: `${item.fontSize}rem` }"
        :title="`${item.tag}: ${item.count}회`"
      >
        #{{ item.tag }}
      </span>
    </div>
    <div v-else class="py-6 text-center">
      <p class="text-sm text-gray-500 dark:text-gray-400">태그 데이터가 없습니다</p>
    </div>
  </div>
</template>
