<script setup lang="ts">
import { computed } from 'vue'
import { Doughnut } from 'vue-chartjs'
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from 'chart.js'
import type { TrafficSourceResponse } from '@/types/analytics'

ChartJS.register(ArcElement, Tooltip, Legend)

const props = defineProps<{ data: TrafficSourceResponse | null }>()

const TRAFFIC_LABELS: Record<string, string> = {
  SEARCH: '검색',
  SUGGESTED: '추천 영상',
  EXTERNAL: '외부 링크',
  BROWSE: '탐색',
  CHANNEL: '채널 페이지',
  NOTIFICATION: '알림',
  OTHER: '기타',
}

const COLORS = ['#8b5cf6', '#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#6366f1', '#94a3b8']

/**
 * **재지 않은 것과 0 건은 다르다.**
 *
 * 서버는 수집 경로가 없어도 `{ sources: {}, total: 0 }` 이라는 **성공 응답**을 준다.
 * 예전에는 `data` 가 null 이 아니기만 하면 차트를 그렸고, 빈 도넛이 "유입이 0 건이었다"
 * 는 측정 결과처럼 보였다. `available === false` 면 차트 대신 사유를 보여준다.
 *
 * `available` 이 없는 옛 응답(`undefined`)은 판단할 수 없으므로 예전처럼 그린다.
 */
const unavailable = computed(() => props.data != null && props.data.available === false)

const chartData = computed(() => {
  if (!props.data || unavailable.value) return null
  const entries = Object.entries(props.data.sources)
  // 분포가 비어 있으면 그릴 계열이 없다. 빈 도넛은 0 건으로 읽힌다.
  if (entries.length === 0) return null
  return {
    labels: entries.map(([k]) => TRAFFIC_LABELS[k] || k),
    datasets: [{
      data: entries.map(([, v]) => v),
      backgroundColor: COLORS.slice(0, entries.length),
    }],
  }
})

const chartOptions = {
  responsive: true,
  plugins: {
    legend: { position: 'right' as const },
  },
}
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg p-6 border border-gray-200 dark:border-gray-700">
    <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">트래픽 소스</h3>
    <div v-if="chartData" class="max-w-md mx-auto">
      <Doughnut :data="chartData" :options="chartOptions" />
    </div>
    <!--
      수집 경로가 없는 상태와 "아직 데이터 없음" 을 구분해서 알린다. 서버가 사유
      문장을 주므로 화면에서 문구를 지어내지 않는다.
    -->
    <p v-else-if="unavailable" class="text-center text-gray-400 py-8">
      {{ data?.unavailableReason || '이 지표는 아직 지원하지 않습니다' }}
    </p>
    <p v-else class="text-center text-gray-400 py-8">데이터가 없습니다</p>
  </div>
</template>
