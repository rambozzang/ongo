<script setup lang="ts">
import { computed } from 'vue'
import type { RevenueInsight } from '@/types/revenue'
import { PLATFORM_CONFIG } from '@/types/channel'
import {
  ArrowTrendingUpIcon,
  ExclamationTriangleIcon,
  LightBulbIcon,
  ChartBarIcon,
  SparklesIcon,
} from '@heroicons/vue/24/outline'

const props = defineProps<{
  insight: RevenueInsight
}>()

// 인사이트 타입별 아이콘 매핑
const iconMap = {
  REVENUE_TREND: ArrowTrendingUpIcon,
  PLATFORM_PERFORMANCE: ChartBarIcon,
  ANOMALY: ExclamationTriangleIcon,
  OPPORTUNITY: LightBulbIcon,
  FORECAST: SparklesIcon,
}

// 인사이트 타입별 배경 색상 — 상태색이 아니라 타입 구분용 카테고리 팔레트다.
// blue/purple이 모두 `info`로 합쳐져 PLATFORM_PERFORMANCE와 FORECAST를 구분할 수
// 없게 되므로 시맨틱 토큰으로 옮기지 않는다.
const bgColorMap: Record<string, string> = {
  REVENUE_TREND: 'bg-primary-50 text-primary-600 dark:bg-primary-900/20 dark:text-primary-400',
  PLATFORM_PERFORMANCE: 'bg-blue-50 text-blue-600 dark:bg-blue-900/20 dark:text-blue-400',
  ANOMALY: 'bg-red-50 text-red-600 dark:bg-red-900/20 dark:text-red-400',
  OPPORTUNITY: 'bg-green-50 text-green-600 dark:bg-green-900/20 dark:text-green-400',
  FORECAST: 'bg-purple-50 text-purple-600 dark:bg-purple-900/20 dark:text-purple-400',
}

// 신뢰도(0~1 숫자) → 레이블 변환
function confidenceLabel(confidence: number | null): 'HIGH' | 'MEDIUM' | 'LOW' {
  if (confidence == null) return 'MEDIUM'
  if (confidence >= 0.7) return 'HIGH'
  if (confidence >= 0.4) return 'MEDIUM'
  return 'LOW'
}

// 신뢰도 배지 색상
const confidenceBadgeClass: Record<string, string> = {
  HIGH: 'bg-success-subtle text-success-strong',
  MEDIUM: 'bg-warning-subtle text-warning-strong',
  LOW: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400',
}

// 백엔드 content(JSON 문자열) 파싱 — summary, detail, changePercent 추출
const parsedContent = computed<{ summary?: string; detail?: string; changePercent?: number }>(() => {
  try {
    return JSON.parse(props.insight.content)
  } catch {
    return { summary: props.insight.content }
  }
})

const icon = iconMap[props.insight.insightType] ?? SparklesIcon
const iconClass = bgColorMap[props.insight.insightType] ?? bgColorMap.REVENUE_TREND
const confidenceKey = computed(() => confidenceLabel(props.insight.confidence))
const badgeClass = computed(() => confidenceBadgeClass[confidenceKey.value] ?? confidenceBadgeClass.MEDIUM)

function getPlatformLabel(platform?: string | null): string {
  if (!platform) return ''
  return PLATFORM_CONFIG[platform as keyof typeof PLATFORM_CONFIG]?.label ?? platform
}
</script>

<template>
  <div class="card flex gap-4">
    <!-- 타입 아이콘 -->
    <div
      class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg"
      :class="iconClass"
    >
      <component :is="icon" class="h-5 w-5" />
    </div>

    <!-- 내용 -->
    <div class="min-w-0 flex-1">
      <div class="mb-1 flex flex-wrap items-center gap-2">
        <span class="text-body font-medium text-gray-900 dark:text-gray-100">
          {{ parsedContent.summary ?? insight.content }}
        </span>
        <!-- 플랫폼 태그 -->
        <span
          v-if="insight.platform"
          class="inline-flex items-center rounded-full bg-gray-100 px-2 py-0.5 text-body-xs text-gray-600 dark:bg-gray-700 dark:text-gray-400"
        >
          {{ getPlatformLabel(insight.platform) }}
        </span>
      </div>
      <p v-if="parsedContent.detail" class="text-body text-gray-500 dark:text-gray-400">
        {{ parsedContent.detail }}
      </p>
      <!-- 변동률 표시 -->
      <span
        v-if="parsedContent.changePercent !== undefined"
        class="mt-1 inline-block text-body-xs font-medium"
        :class="parsedContent.changePercent >= 0 ? 'text-success-strong' : 'text-error-strong'"
      >
        {{ parsedContent.changePercent >= 0 ? '+' : '' }}{{ parsedContent.changePercent.toFixed(1) }}%
      </span>
    </div>

    <!-- 신뢰도 배지 -->
    <div class="shrink-0">
      <span
        class="inline-flex items-center rounded-full px-2.5 py-1 text-body-xs font-medium"
        :class="badgeClass"
      >
        {{ $t(`revenue.insights.confidence.${confidenceKey}`) }}
      </span>
    </div>
  </div>
</template>
