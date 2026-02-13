<script setup lang="ts">
import { type WorkflowTriggerType, WORKFLOW_TRIGGER_OPTIONS } from '@/types/automation'

const triggerType = defineModel<WorkflowTriggerType>('triggerType', { required: true })
const config = defineModel<Record<string, unknown>>('config', { required: true })
</script>

<template>
  <div class="space-y-4">
    <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-2">
      <span class="w-2 h-2 rounded-full bg-green-500"></span>
      트리거 설정
    </h3>

    <div>
      <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">트리거 유형</label>
      <select
        :value="triggerType"
        class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:ring-1 focus:ring-primary-500"
        @change="triggerType = ($event.target as HTMLSelectElement).value as WorkflowTriggerType"
      >
        <option v-for="opt in WORKFLOW_TRIGGER_OPTIONS" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>
    </div>

    <!-- Trigger-specific config fields -->
    <div v-if="triggerType === 'ANALYTICS_MILESTONE'" class="space-y-3">
      <div>
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">메트릭</label>
        <select
          :value="(config.metric as string) ?? 'views'"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm"
          @change="config = { ...config, metric: ($event.target as HTMLSelectElement).value }"
        >
          <option value="views">조회수</option>
          <option value="likes">좋아요</option>
          <option value="comments">댓글</option>
          <option value="subscribers">구독자</option>
        </select>
      </div>
      <div>
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">임계값</label>
        <input
          type="number"
          :value="(config.threshold as number) ?? 1000"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm"
          @input="config = { ...config, threshold: Number(($event.target as HTMLInputElement).value) }"
        />
      </div>
    </div>

    <div v-if="triggerType === 'SUBSCRIBER_MILESTONE'" class="space-y-3">
      <div>
        <label class="block text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">구독자 수</label>
        <input
          type="number"
          :value="(config.count as number) ?? 1000"
          class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900 px-3 py-2 text-sm"
          @input="config = { ...config, count: Number(($event.target as HTMLInputElement).value) }"
        />
      </div>
    </div>

    <p class="text-xs text-gray-500 dark:text-gray-400 mt-4">
      이 트리거가 발생하면 아래 조건을 확인한 후 액션을 실행합니다.
    </p>
  </div>
</template>
