<template>
  <!-- 위기 감지 배너 -->
  <div
    v-if="status?.isInCrisis && !dismissed"
    class="rounded-xl border border-error bg-error-subtle p-4"
  >
    <div class="flex items-start justify-between gap-3">
      <div class="flex items-start gap-3">
        <ExclamationTriangleIcon class="h-5 w-5 shrink-0 text-error-strong mt-0.5" />
        <div>
          <h4 class="text-body font-semibold text-error-strong">
            {{ $t('commentsView.crisis.title') }}
          </h4>
          <p class="mt-0.5 text-body-xs text-error-strong">
            {{ $t('commentsView.crisis.description', { changePercent: status.changePercent, count: status.currentNegativeCount }) }}
          </p>
          <!-- 주요 키워드 태그 -->
          <div v-if="status.topKeywords.length > 0" class="mt-2 flex flex-wrap gap-1.5">
            <span class="text-body-xs font-medium text-error-strong">
              {{ $t('commentsView.crisis.keywords') }}:
            </span>
            <span
              v-for="kw in status.topKeywords"
              :key="kw"
              class="rounded-full bg-error-subtle px-2 py-0.5 text-body-xs font-medium text-error-strong"
            >
              {{ kw }}
            </span>
          </div>
        </div>
      </div>
      <button
        class="text-body-xs text-error-strong hover:text-error-strong shrink-0"
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
