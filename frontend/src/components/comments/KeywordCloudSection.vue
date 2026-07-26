<template>
  <!-- CSS 기반 키워드 클라우드 섹션 -->
  <div class="card">
    <div class="flex items-center justify-between mb-4">
      <div>
        <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300">
          {{ $t('commentsView.keywordCloud.title') }}
        </h3>
        <p class="mt-0.5 text-xs text-gray-500 dark:text-gray-400">
          {{ $t('commentsView.keywordCloud.description') }}
        </p>
      </div>
      <div class="flex items-center gap-2">
        <!-- 기간 선택 -->
        <div class="flex gap-1">
          <button
            v-for="d in dayOptions"
            :key="d.value"
            class="rounded px-2 py-1 text-xs font-medium transition-colors"
            :class="selectedDays === d.value
              ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
              : 'text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'"
            @click="handleDayChange(d.value)"
          >
            {{ $t(d.labelKey) }}
          </button>
        </div>
        <button
          class="btn-secondary inline-flex items-center gap-1.5 text-xs"
          :disabled="loading"
          @click="emit('load', selectedDays)"
        >
          <ArrowPathIcon class="h-3.5 w-3.5" :class="{ 'animate-spin': loading }" />
          {{ loading ? $t('commentsView.keywordCloud.loading') : $t('commentsView.keywordCloud.load') }}
        </button>
      </div>
    </div>

    <!-- 로딩 → 빈 상태 → 키워드 클라우드 -->
    <AsyncState
      :loading="loading"
      :empty="keywords.length === 0"
      skeleton="list"
      :skeleton-count="2"
      :empty-icon="HashtagIcon"
      :empty-title="$t('commentsView.keywordCloud.empty')"
      empty-variant="compact"
    >
      <div class="flex flex-wrap items-center justify-center gap-2 py-4">
        <button
          v-for="item in keywords"
          :key="item.keyword"
          class="rounded-full px-3 py-1 font-medium transition-all hover:scale-110 hover:shadow-md"
          :style="getKeywordStyle(item)"
          :title="$t('commentsView.keywordCloud.filterHint')"
          @click="emit('filter', item.keyword)"
        >
          {{ item.keyword }}
          <span class="ml-1 text-xs opacity-70">({{ item.count }})</span>
        </button>
      </div>
    </AsyncState>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ArrowPathIcon, HashtagIcon } from '@heroicons/vue/24/outline'
import type { KeywordCloudItem } from '@/types/comment'
import AsyncState from '@/components/common/AsyncState.vue'

const props = defineProps<{
  keywords: KeywordCloudItem[]
  loading: boolean
}>()

const emit = defineEmits<{
  load: [days: number]
  filter: [keyword: string]
}>()

const selectedDays = ref(30)

const dayOptions = [
  { value: 7, labelKey: 'commentsView.keywordCloud.days7' },
  { value: 30, labelKey: 'commentsView.keywordCloud.days30' },
  { value: 90, labelKey: 'commentsView.keywordCloud.days90' },
]

const handleDayChange = (days: number) => {
  selectedDays.value = days
  emit('load', days)
}

// 빈도에 따라 font-size 및 색상 계산
const getKeywordStyle = (item: KeywordCloudItem) => {
  const maxCount = Math.max(...props.keywords.map((k) => k.count), 1)
  const ratio = item.count / maxCount
  const fontSize = Math.round(12 + ratio * 20) // 12px ~ 32px

  // 감정별 배경색
  const sentimentColors: Record<string, { bg: string; text: string }> = {
    positive: { bg: 'rgb(220,252,231)', text: 'rgb(21,128,61)' },
    negative: { bg: 'rgb(254,226,226)', text: 'rgb(185,28,28)' },
    neutral:  { bg: 'rgb(243,244,246)', text: 'rgb(55,65,81)' },
  }
  const color = sentimentColors[item.sentiment] ?? sentimentColors.neutral

  return {
    fontSize: `${fontSize}px`,
    backgroundColor: color.bg,
    color: color.text,
    opacity: 0.6 + ratio * 0.4,
  }
}
</script>
