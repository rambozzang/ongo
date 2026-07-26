<template>
  <!-- 위기 감지 배너 -->
  <div
    v-if="status?.isInCrisis && !dismissed"
    class="rounded-xl border border-red-300 bg-red-50 p-4 dark:border-red-800 dark:bg-red-900/20"
  >
    <div class="flex items-start justify-between gap-3">
      <div class="flex items-start gap-3">
        <ExclamationTriangleIcon class="h-5 w-5 shrink-0 text-red-600 dark:text-red-400 mt-0.5" />
        <div>
          <h4 class="text-sm font-semibold text-red-800 dark:text-red-300">
            {{ $t('commentsView.crisis.title') }}
          </h4>
          <p class="mt-0.5 text-xs text-red-700 dark:text-red-400">
            {{ $t('commentsView.crisis.description', { changePercent: status.changePercent, count: status.currentNegativeCount }) }}
          </p>
          <!-- 주요 키워드 태그 -->
          <div v-if="status.topKeywords.length > 0" class="mt-2 flex flex-wrap gap-1.5">
            <span class="text-xs font-medium text-red-700 dark:text-red-400">
              {{ $t('commentsView.crisis.keywords') }}:
            </span>
            <span
              v-for="kw in status.topKeywords"
              :key="kw"
              class="rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700 dark:bg-red-900/40 dark:text-red-300"
            >
              {{ kw }}
            </span>
          </div>
        </div>
      </div>
      <button
        class="text-xs text-red-600 hover:text-red-800 dark:text-red-400 dark:hover:text-red-200 shrink-0"
        @click="dismissed = true"
      >
        {{ $t('commentsView.crisis.dismiss') }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import type { CrisisDetectionResult } from '@/types/comment'

defineProps<{
  status: CrisisDetectionResult | null
}>()

const dismissed = ref(false)
</script>
