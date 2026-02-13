<template>
  <div v-if="anomalies.length > 0" class="card">
    <div class="mb-4 flex items-center justify-between">
      <h3 class="text-lg font-semibold text-gray-900 dark:text-gray-100">이상 감지 알림</h3>
      <span class="rounded-full bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-700 dark:bg-red-900/30 dark:text-red-400">
        {{ anomalies.length }}건
      </span>
    </div>

    <div class="space-y-3">
      <div
        v-for="anomaly in anomalies"
        :key="`${anomaly.videoId}-${anomaly.anomalyType}`"
        class="flex items-start gap-3 rounded-lg border p-3 transition-colors"
        :class="severityBorderClass(anomaly.severity)"
      >
        <!-- Severity Icon -->
        <div
          class="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full"
          :class="severityBgClass(anomaly.severity)"
        >
          <svg v-if="anomaly.severity === 'critical'" class="h-4 w-4 text-white" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M8.485 2.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.17 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 2.495zM10 5a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 0110 5zm0 9a1 1 0 100-2 1 1 0 000 2z" clip-rule="evenodd" />
          </svg>
          <svg v-else-if="anomaly.severity === 'warning'" class="h-4 w-4 text-white" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a.75.75 0 000 1.5h.253a.25.25 0 01.244.304l-.459 2.066A1.75 1.75 0 0010.747 15H11a.75.75 0 000-1.5h-.253a.25.25 0 01-.244-.304l.459-2.066A1.75 1.75 0 009.253 9H9z" clip-rule="evenodd" />
          </svg>
          <svg v-else class="h-4 w-4 text-white" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a.75.75 0 000 1.5h.253a.25.25 0 01.244.304l-.459 2.066A1.75 1.75 0 0010.747 15H11a.75.75 0 000-1.5h-.253a.25.25 0 01-.244-.304l.459-2.066A1.75 1.75 0 009.253 9H9z" clip-rule="evenodd" />
          </svg>
        </div>

        <!-- Content -->
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <p class="text-sm font-medium text-gray-900 dark:text-gray-100">
              {{ anomaly.videoTitle ?? `영상 #${anomaly.videoId}` }}
            </p>
            <span
              class="rounded px-1.5 py-0.5 text-xs font-medium"
              :class="anomalyTypeBadgeClass(anomaly.anomalyType)"
            >
              {{ anomalyTypeLabel(anomaly.anomalyType) }}
            </span>
          </div>
          <p class="mt-0.5 text-xs text-gray-600 dark:text-gray-400">
            {{ anomaly.description }}
          </p>
          <div class="mt-2 flex gap-2">
            <button
              class="rounded-md px-2 py-1 text-xs font-medium text-primary-600 hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-900/20"
              @click="$emit('analyze', anomaly.videoId)"
            >
              분석하기
            </button>
            <button
              class="rounded-md px-2 py-1 text-xs font-medium text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
              @click="dismiss(anomaly)"
            >
              닫기
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { analyticsApi } from '@/api/analytics'
import type { AnomalyItem, AnomalyType } from '@/types/analytics'

defineEmits<{
  (e: 'analyze', videoId: number): void
}>()

const anomalies = ref<AnomalyItem[]>([])

function severityBorderClass(severity: string): string {
  switch (severity) {
    case 'critical': return 'border-red-200 bg-red-50/50 dark:border-red-900/30 dark:bg-red-900/10'
    case 'warning': return 'border-yellow-200 bg-yellow-50/50 dark:border-yellow-900/30 dark:bg-yellow-900/10'
    default: return 'border-blue-200 bg-blue-50/50 dark:border-blue-900/30 dark:bg-blue-900/10'
  }
}

function severityBgClass(severity: string): string {
  switch (severity) {
    case 'critical': return 'bg-red-500'
    case 'warning': return 'bg-yellow-500'
    default: return 'bg-blue-500'
  }
}

function anomalyTypeBadgeClass(type: AnomalyType): string {
  switch (type) {
    case 'VIRAL_SPIKE': return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
    case 'ENGAGEMENT_SURGE': return 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400'
    case 'UNUSUAL_DROP': return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
    case 'SHARE_SPIKE': return 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400'
  }
}

function anomalyTypeLabel(type: AnomalyType): string {
  switch (type) {
    case 'VIRAL_SPIKE': return '바이럴'
    case 'ENGAGEMENT_SURGE': return '참여 급등'
    case 'UNUSUAL_DROP': return '하락'
    case 'SHARE_SPIKE': return '공유 급등'
  }
}

function dismiss(anomaly: AnomalyItem) {
  anomalies.value = anomalies.value.filter(
    a => !(a.videoId === anomaly.videoId && a.anomalyType === anomaly.anomalyType)
  )
}

onMounted(async () => {
  try {
    const result = await analyticsApi.anomalies()
    anomalies.value = result.anomalies
  } catch {
    anomalies.value = []
  }
})
</script>
