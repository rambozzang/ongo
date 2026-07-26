<template>
  <!-- FAQ 클러스터 -->
  <div class="card">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300">
        {{ $t('commentsView.faqTitle') }}
      </h3>
      <button
        class="btn-secondary inline-flex items-center gap-1.5 text-xs"
        :disabled="loading"
        @click="emit('analyze')"
      >
        <SparklesIcon class="h-3.5 w-3.5" />
        {{ loading ? $t('commentsView.faqAnalyzing') : $t('commentsView.faqAiAnalyze') }}
      </button>
    </div>
    <div v-if="loading" class="flex items-center justify-center py-8">
      <div class="h-6 w-6 animate-spin rounded-full border-4 border-primary-200 border-t-primary-600" />
    </div>
    <div v-else-if="data && data.clusters.length > 0" class="space-y-3">
      <div
        v-for="(cluster, idx) in data.clusters"
        :key="idx"
        class="rounded-lg border border-gray-200 dark:border-gray-700 p-3"
      >
        <div class="flex items-center justify-between mb-2">
          <h4 class="text-sm font-medium text-gray-900 dark:text-gray-100">
            {{ cluster.topic }}
          </h4>
          <span class="text-xs text-gray-500 dark:text-gray-400">
            {{ $t('commentsView.faqCount', { count: cluster.questionCount }) }}
          </span>
        </div>
        <ul class="mb-2 space-y-1">
          <li
            v-for="(q, qIdx) in cluster.sampleQuestions"
            :key="qIdx"
            class="text-xs text-gray-600 dark:text-gray-400 pl-3 relative before:absolute before:left-0 before:top-1.5 before:h-1 before:w-1 before:rounded-full before:bg-gray-400"
          >
            {{ q }}
          </li>
        </ul>
        <div class="rounded-md bg-primary-50 dark:bg-primary-900/20 p-2">
          <p class="text-xs text-primary-700 dark:text-primary-300">
            <span class="font-medium">{{ $t('commentsView.faqSuggestedReply') }}</span> {{ cluster.suggestedReply }}
          </p>
        </div>
      </div>
    </div>
    <div v-else class="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
      {{ $t('commentsView.faqEmpty') }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { SparklesIcon } from '@heroicons/vue/24/outline'
import type { FaqClusterResponse } from '@/types/comment'

defineProps<{
  data: FaqClusterResponse | null
  loading: boolean
}>()

const emit = defineEmits<{
  analyze: []
}>()
</script>
