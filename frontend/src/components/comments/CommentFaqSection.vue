<template>
  <!-- FAQ 클러스터 -->
  <div class="card">
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-sm font-semibold text-gray-700 dark:text-gray-300">
        {{ $t('commentsView.faqTitle') }}
      </h3>
      <button
        class="btn-secondary inline-flex items-center gap-1.5 text-xs"
        data-testid="faq-analyze-button"
        :disabled="loading || faqCreditBlocked"
        @click="emit('analyze')"
      >
        <SparklesIcon class="h-3.5 w-3.5" />
        {{ loading ? $t('commentsView.faqAnalyzing') : $t('commentsView.faqAiAnalyze') }}
      </button>
    </div>
    <div
      v-if="faqCreditBlocked"
      class="mt-4 flex flex-col gap-2 rounded-lg border border-warning bg-warning-subtle px-4 py-3"
      role="alert"
    >
      <p class="text-body text-warning-strong">{{ $t('commentsView.faqCreditBlocked') }}</p>
      <button
        type="button"
        data-testid="faq-credit-cta"
        class="btn-primary inline-flex w-full items-center justify-center gap-2"
        @click="emit('credit')"
      >
        {{ $t('commentsView.faqChargeCredits') }}
      </button>
    </div>
    <AsyncState
      :loading="loading"
      :empty="!data || data.clusters.length === 0"
      skeleton="list"
      :skeleton-count="2"
      :empty-icon="QuestionMarkCircleIcon"
      :empty-title="$t('commentsView.faqEmpty')"
      empty-variant="compact"
    >
      <div class="space-y-3">
        <div
          v-for="(cluster, idx) in data?.clusters ?? []"
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
    </AsyncState>
  </div>
</template>

<script setup lang="ts">
import { SparklesIcon, QuestionMarkCircleIcon } from '@heroicons/vue/24/outline'
import type { FaqClusterResponse } from '@/types/comment'
import AsyncState from '@/components/common/AsyncState.vue'

defineProps<{
  data: FaqClusterResponse | null
  loading: boolean
  faqCreditBlocked: boolean
}>()

const emit = defineEmits<{
  analyze: []
  credit: []
}>()
</script>
