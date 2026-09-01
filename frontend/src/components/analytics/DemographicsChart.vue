<script setup lang="ts">
import { computed } from 'vue'
import { Bar, Doughnut } from 'vue-chartjs'
import { Chart as ChartJS, CategoryScale, LinearScale, BarElement, ArcElement, Tooltip, Legend } from 'chart.js'
import type { DemographicsResponse } from '@/types/analytics'

ChartJS.register(CategoryScale, LinearScale, BarElement, ArcElement, Tooltip, Legend)

const props = defineProps<{ data: DemographicsResponse | null }>()

const GENDER_LABELS: Record<string, string> = { male: '남성', female: '여성', other: '기타' }
const GENDER_COLORS = ['#3b82f6', '#ec4899', '#94a3b8']

/**
 * **재지 않은 것과 0 명은 다르다.**
 *
 * 서버는 수집 경로가 없어도 빈 분포를 담은 **성공 응답**을 준다. 예전에는 `data` 가
 * null 이 아니기만 하면 3단 그리드를 그렸고, 빈 막대·빈 도넛이 "그런 시청자가 없었다"
 * 는 측정 결과처럼 보였다. `available === false` 면 차트 대신 사유를 보여준다.
 *
 * `available` 이 없는 옛 응답(`undefined`)은 판단할 수 없으므로 예전처럼 그린다.
 */
const unavailable = computed(() => props.data != null && props.data.available === false)

const ageChartData = computed(() => {
  if (!props.data || unavailable.value) return null
  const entries = Object.entries(props.data.ageDistribution).sort(([a], [b]) => a.localeCompare(b))
  // 분포가 비어 있으면 그릴 계열이 없다. 빈 막대는 0% 로 읽힌다.
  if (entries.length === 0) return null
  return {
    labels: entries.map(([k]) => k),
    datasets: [{ label: '비율 (%)', data: entries.map(([, v]) => v), backgroundColor: '#8b5cf6' }],
  }
})

const genderChartData = computed(() => {
  if (!props.data || unavailable.value) return null
  const entries = Object.entries(props.data.genderDistribution)
  if (entries.length === 0) return null
  return {
    labels: entries.map(([k]) => GENDER_LABELS[k] || k),
    datasets: [{ data: entries.map(([, v]) => v), backgroundColor: GENDER_COLORS.slice(0, entries.length) }],
  }
})

const topCountries = computed(() => {
  if (!props.data || unavailable.value) return []
  return Object.entries(props.data.topCountries).sort(([, a], [, b]) => b - a).slice(0, 5)
})
</script>

<template>
  <div class="bg-white dark:bg-gray-800 rounded-lg p-6 border border-gray-200 dark:border-gray-700">
    <h3 class="text-lg font-semibold text-gray-900 dark:text-white mb-4">시청자 인구통계</h3>
    <!--
      수집 경로가 없는 상태와 "아직 데이터 없음" 을 구분해서 알린다. 서버가 사유
      문장을 주므로 화면에서 문구를 지어내지 않는다.
    -->
    <p v-if="unavailable" class="text-center text-gray-400 py-8">
      {{ props.data?.unavailableReason || '이 지표는 아직 지원하지 않습니다' }}
    </p>
    <div v-else-if="props.data" class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <div>
        <h4 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">연령대</h4>
        <Bar v-if="ageChartData" :data="ageChartData" :options="{ responsive: true, plugins: { legend: { display: false } } }" />
      </div>
      <div>
        <h4 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">성별</h4>
        <Doughnut v-if="genderChartData" :data="genderChartData" :options="{ responsive: true }" />
      </div>
      <div>
        <h4 class="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">상위 국가</h4>
        <ul class="space-y-2">
          <li v-for="[country, count] in topCountries" :key="country" class="flex justify-between text-sm">
            <span class="text-gray-700 dark:text-gray-300">{{ country }}</span>
            <span class="font-medium text-gray-900 dark:text-white">{{ count.toLocaleString() }}</span>
          </li>
        </ul>
        <p v-if="topCountries.length === 0" class="text-gray-400 text-sm">데이터 없음</p>
      </div>
    </div>
    <p v-else class="text-center text-gray-400 py-8">데이터가 없습니다</p>
  </div>
</template>
