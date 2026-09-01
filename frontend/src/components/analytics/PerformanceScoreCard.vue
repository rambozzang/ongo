<template>
  <div class="card">
    <div class="mb-4 flex items-center justify-between">
      <!-- 위 '성과 점수' 카드와 계산 근거가 다르다. 부제로 그 차이를 밝힌다. -->
      <div>
        <h3 class="text-title font-semibold text-gray-900 dark:text-gray-100">AI 성과 점수</h3>
        <p class="text-caption text-gray-500 dark:text-gray-400">
          {{ $t('videoDetail.aiScoreBasis') }}
        </p>
      </div>
      <!--
        비교할 영상이 없으면 배지를 그리지 않는다. `percentileRank` 는 null 일 수 있고,
        그때 `.toFixed()` 를 부르면 런타임 오류가 난다. 0 으로 대체하면 "Top 0%"(최상위)라는
        **없는 사실**을 만든다.
      -->
      <span
        v-if="topPercent !== null"
        class="rounded-full px-2.5 py-0.5 text-body-xs font-semibold"
        :class="percentileBadgeClass"
        data-testid="percentile-badge"
      >
        Top {{ topPercent.toFixed(0) }}%
      </span>
      <span
        v-else-if="score"
        class="text-caption text-gray-500 dark:text-gray-400"
        data-testid="percentile-unavailable"
      >
        {{ $t('videoDetail.percentileUnavailable') }}
      </span>
    </div>

    <!-- 로딩 → 빈 상태 → 성과 점수 -->
    <AsyncState
      :loading="loading"
      :empty="!score"
      skeleton="list"
      :skeleton-count="3"
      :empty-icon="ChartBarIcon"
      :empty-title="emptyTitle"
      empty-variant="compact"
    >
      <template v-if="score">
        <div class="grid gap-6 desktop:grid-cols-2">
          <!-- Overall Score Circle -->
          <div class="flex flex-col items-center justify-center">
            <div class="relative">
              <svg class="h-40 w-40 -rotate-90 transform">
                <circle
                  cx="80" cy="80" r="70"
                  stroke="currentColor" :stroke-width="10" fill="none"
                  class="text-gray-200 dark:text-gray-700"
                />
                <circle
                  cx="80" cy="80" r="70"
                  stroke="currentColor" :stroke-width="10" fill="none"
                  :stroke-dasharray="circumference"
                  :stroke-dashoffset="scoreOffset"
                  :class="scoreColorClass"
                  class="transition-all duration-1000 ease-out"
                  stroke-linecap="round"
                />
              </svg>
              <div class="absolute inset-0 flex flex-col items-center justify-center">
                <!--
                  총점이 없으면 숫자를 그리지 않는다. `Math.round(null)` 은 0 이라,
                  그대로 두면 미측정이 "0점"이 된다.
                -->
                <span
                  v-if="overallScore !== null"
                  class="text-4xl font-bold text-gray-900 dark:text-gray-100"
                  data-testid="overall-score"
                >
                  {{ Math.round(overallScore) }}
                </span>
                <span
                  v-else
                  class="px-2 text-center text-body-xs text-gray-400 dark:text-gray-500"
                  data-testid="overall-unavailable"
                >
                  {{ $t('videoDetail.scoreUnavailable') }}
                </span>
                <div class="flex items-center gap-1">
                  <svg
                    v-if="score.trend === 'up'"
                    class="h-3.5 w-3.5 text-success-strong" viewBox="0 0 20 20" fill="currentColor"
                  >
                    <path fill-rule="evenodd" d="M10 17a.75.75 0 01-.75-.75V5.612L5.29 9.77a.75.75 0 01-1.08-1.04l5.25-5.5a.75.75 0 011.08 0l5.25 5.5a.75.75 0 11-1.08 1.04l-3.96-4.158V16.25A.75.75 0 0110 17z" clip-rule="evenodd" />
                  </svg>
                  <svg
                    v-else-if="score.trend === 'down'"
                    class="h-3.5 w-3.5 text-error-strong" viewBox="0 0 20 20" fill="currentColor"
                  >
                    <path fill-rule="evenodd" d="M10 3a.75.75 0 01.75.75v10.638l3.96-4.158a.75.75 0 111.08 1.04l-5.25 5.5a.75.75 0 01-1.08 0l-5.25-5.5a.75.75 0 111.08-1.04l3.96 4.158V3.75A.75.75 0 0110 3z" clip-rule="evenodd" />
                  </svg>
                  <span class="text-body-xs text-gray-500 dark:text-gray-400" data-testid="trend-label">
                    {{ trendLabel }}
                  </span>
                </div>
              </div>
            </div>
            <!-- 예측할 근거가 없으면 숫자 대신 이유를 말한다. -->
            <p class="mt-2 text-body text-gray-500 dark:text-gray-400" data-testid="prediction">
              {{ predictionLabel }}
            </p>
          </div>

          <!-- Breakdown Bars -->
          <div class="space-y-3">
            <div v-for="item in breakdownItems" :key="item.key" :data-testid="`breakdown-${item.key}`">
              <div class="mb-1 flex items-center justify-between">
                <span class="text-caption text-gray-600 dark:text-gray-400">{{ item.label }}</span>
                <!--
                  계산할 수 없는 축은 숫자를 그리지 않는다. `?? 0` 을 하면 "그 축에서
                  최하위"라는, 측정한 적 없는 판정이 된다.
                -->
                <span
                  v-if="item.value !== null"
                  class="text-body-xs font-semibold text-gray-900 dark:text-gray-100"
                >
                  {{ Math.round(item.value) }}
                </span>
                <span
                  v-else
                  class="text-body-xs text-gray-400 dark:text-gray-500"
                  :title="item.reason"
                  data-testid="breakdown-unavailable"
                >
                  {{ $t('videoDetail.scoreUnavailable') }}
                </span>
              </div>
              <div class="h-2 w-full rounded-full bg-gray-200 dark:bg-gray-700">
                <!-- 미측정 축은 막대 자체를 그리지 않는다. 폭 0 은 "0점"으로 보인다. -->
                <div
                  v-if="item.value !== null"
                  class="h-full rounded-full transition-all duration-700"
                  :class="item.colorClass"
                  :style="{ width: `${item.value}%` }"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- Anomaly Alert -->
        <div
          v-if="score.isAnomaly"
          class="mt-4 flex items-start gap-3 rounded-lg border border-warning bg-warning-subtle p-3"
        >
          <svg class="mt-0.5 h-5 w-5 flex-shrink-0 text-warning-strong" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M8.485 2.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.17 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 2.495zM10 5a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 0110 5zm0 9a1 1 0 100-2 1 1 0 000 2z" clip-rule="evenodd" />
          </svg>
          <div>
            <p class="text-body font-medium text-warning-strong">이상 감지됨</p>
            <p class="mt-0.5 text-body-xs text-warning-strong">
              {{ score.anomalyDescription }}
            </p>
          </div>
        </div>
      </template>
    </AsyncState>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChartBarIcon } from '@heroicons/vue/24/outline'
import { analyticsApi } from '@/api/analytics'
import type { PerformanceScoreResponse } from '@/types/analytics'
import AsyncState from '@/components/common/AsyncState.vue'

const props = defineProps<{
  videoId: number
}>()

const { t } = useI18n()

const score = ref<PerformanceScoreResponse | null>(null)
/** 서버가 알려 준 미수집 사유. 빈 상태 문구를 고르는 데만 쓴다. */
const unavailableReason = ref<string | null>(null)
const loading = ref(false)

/**
 * 빈 상태 문구.
 *
 * "성과 데이터가 없습니다" 하나로 뭉치면 게시된 곳이 없는 영상과 아직 수집 전인 영상이
 * 같아 보인다. 크리에이터가 할 수 있는 일이 다르므로 구분해서 말한다.
 */
const emptyTitle = computed(() => {
  if (unavailableReason.value === 'NO_UPLOADS') return t('videoDetail.aiScoreEmptyNoUploads')
  if (unavailableReason.value === 'NO_ANALYTICS') return t('videoDetail.aiScoreEmptyNoAnalytics')
  return t('videoDetail.aiScoreEmptyDefault')
})

const circumference = 2 * Math.PI * 70

/**
 * 총점. **계산된 축이 하나도 없으면 `null`.**
 *
 * 유한한 숫자만 값으로 다룬다. `NaN`/`Infinity` 가 새어 들어오면 `Math.round` 는
 * 그것을 그대로 통과시키고 화면에 "NaN" 이 찍힌다.
 */
const overallScore = computed<number | null>(() => {
  const value = score.value?.overallScore
  return typeof value === 'number' && Number.isFinite(value) ? value : null
})

const scoreOffset = computed(() => {
  // 점수가 없으면 원호를 비운다. 0 으로 대체하면 "0점" 을 그린 것과 같아 보인다.
  if (overallScore.value === null) return circumference
  return circumference * (1 - overallScore.value / 100)
})

const scoreColorClass = computed(() => {
  const s = overallScore.value
  // 점수가 없을 때 색으로 좋고 나쁨을 주장하지 않는다.
  if (s === null) return 'text-gray-400'
  if (s <= 30) return 'text-error-strong'
  if (s <= 60) return 'text-warning-strong'
  if (s <= 80) return 'text-success-strong'
  return 'text-primary-500'
})

/**
 * 상위 몇 %인가. **낮을수록 좋다.** 비교할 영상이 없으면 null.
 *
 * 서버가 `null` 을 줄 수 있으므로 숫자일 때만 값으로 다룬다. 필드가 없는 옛 응답
 * (`undefined`)도 같은 취급이다 — 없는 순위를 0 으로 채우면 "Top 0%"가 된다.
 */
const topPercent = computed<number | null>(() => {
  const value = score.value?.percentileRank
  return typeof value === 'number' ? value : null
})

/**
 * 배지 색.
 *
 * 값의 방향이 뒤집혔다(예전엔 높을수록 좋음). 색 기준을 그대로 두면 **최고 성과에
 * 회색을, 최저에 초록을** 칠하게 된다.
 */
const percentileBadgeClass = computed(() => {
  const p = topPercent.value
  if (p === null) return ''
  if (p <= 20) return 'bg-success-subtle text-success-strong'
  if (p <= 50) return 'bg-info-subtle text-info-strong'
  return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
})

/**
 * 추세 라벨. **판단할 기간이 없으면 "안정" 이라고 말하지 않는다.**
 *
 * 예전에는 `default: '안정'` 이라 서버가 무엇을 주든 세 라벨 중 하나가 나왔다. 서버가
 * 4일 미만이면 `"stable"` 을 채워 보냈으므로, 게시 직후 영상은 전부 "안정"으로 보였다.
 */
const trendLabel = computed(() => {
  switch (score.value?.trend) {
    case 'up': return '상승세'
    case 'down': return '하락세'
    case 'stable': return '안정'
    default: return t('videoDetail.trendUnavailable')
  }
})

/**
 * 7일 예상 조회수. **회귀선을 그을 근거가 없으면 숫자를 그리지 않는다.**
 *
 * 예전 서버는 관측일이 하루뿐이면 그 하루의 조회수 합계를 "예측"으로 돌려줬고,
 * 이 줄은 그것을 "7일 예상 조회수: 500" 으로 그렸다.
 */
const predictionLabel = computed(() => {
  const value = score.value?.prediction7d
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return `7일 예상 조회수: ${t('videoDetail.scoreUnavailable')}`
  }
  return `7일 예상 조회수: ${formatNumber(value)}`
})

const breakdownItems = computed(() => {
  if (!score.value) return []
  const b = score.value.breakdown
  const reasons = score.value.unavailableMetrics ?? {}
  const axes = [
    { key: 'viewVelocity', label: '조회수 속도 (30%)', colorClass: 'bg-blue-500' },
    { key: 'engagement', label: '참여율 (25%)', colorClass: 'bg-green-500' },
    { key: 'watchTime', label: '시청 시간 (20%)', colorClass: 'bg-purple-500' },
    { key: 'conversion', label: '구독자 전환 (15%)', colorClass: 'bg-orange-500' },
    { key: 'share', label: '공유율 (10%)', colorClass: 'bg-pink-500' },
  ]
  return axes.map((axis) => {
    const raw = b?.[axis.key]
    // `?? 0` 을 하지 않는다. 미측정을 0 으로 그리면 "그 축에서 최하위"가 된다.
    const value = typeof raw === 'number' && Number.isFinite(raw) ? raw : null
    return { ...axis, value, reason: reasons[axis.key] ?? '' }
  })
})

function formatNumber(n: number): string {
  if (n >= 10_000) return `${(n / 10_000).toFixed(1)}만`
  if (n >= 1_000) return `${(n / 1_000).toFixed(1)}천`
  return n.toLocaleString('ko-KR')
}

async function fetchScore() {
  loading.value = true
  try {
    const response = await analyticsApi.performanceScore(props.videoId)
    /*
     * **측정 데이터가 없으면 점수가 없는 것으로 다룬다.**
     *
     * 서버는 집계가 없어도 200 에 0 을 채워 내려준다(`dataAvailable = false`). 그것을
     * 그대로 그리면 "0점 / 7일 예상 조회수 0회 / 안정적 추세"가 되어, 아직 측정되지
     * 않은 영상이 성과가 나쁜 영상처럼 보인다.
     *
     * `dataAvailable` 이 `undefined` 인 옛 응답은 판단 불가라 그대로 보여준다 —
     * 숨기면 실제 점수를 감출 수 있다.
     */
    if (response.dataAvailable === false) {
      score.value = null
      unavailableReason.value = response.unavailableReason ?? null
    } else {
      score.value = response
      unavailableReason.value = null
    }
  } catch {
    score.value = null
    unavailableReason.value = null
  } finally {
    loading.value = false
  }
}

onMounted(fetchScore)
watch(() => props.videoId, fetchScore)
</script>
