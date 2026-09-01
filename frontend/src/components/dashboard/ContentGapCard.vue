<template>
  <div class="card overflow-hidden">
    <!-- Header -->
    <div class="flex items-center justify-between border-b border-gray-100 p-4 dark:border-gray-700">
      <div class="flex items-center gap-2">
        <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-success-subtle">
          <LightBulbIcon class="h-4 w-4 text-success-strong" />
        </div>
        <div>
          <h3 class="text-body font-semibold text-gray-900 dark:text-gray-100">콘텐츠 갭 분석</h3>
          <p class="text-body-xs text-gray-500 dark:text-gray-400">
            <span v-if="creditCost != null">{{ creditCost }} 크레딧</span>
            <span v-else-if="pricingLoading">비용 확인 중...</span>
            <span v-else>비용을 확인할 수 없습니다</span>
          </p>
        </div>
      </div>
    </div>

    <!-- 로딩 → 에러 → (초기 안내 | 결과) -->
    <AsyncState
      :loading="loading"
      :error="error"
      skeleton="list"
      :skeleton-count="2"
      class="p-4"
      @retry="analyze"
    >
      <!-- 크레딧 부족 안내 + 실제 충전 CTA (result 유무와 무관하게 최우선) -->
      <div
        v-if="creditBlocked"
        class="mb-3 rounded-lg border border-warning bg-warning-subtle px-3 py-2"
      >
        <p class="text-[11px] leading-5 text-warning-strong">{{ $t('dashboard.contentGap.creditBlocked') }}</p>
        <button
          type="button"
          data-testid="contentgap-credit-cta"
          class="btn-primary mt-2 inline-flex w-full justify-center !text-[12px]"
          @click="showCreditModal = true"
        >
          {{ $t('dashboard.contentGap.chargeCredits') }}
        </button>
      </div>

      <!-- Initial State (Not Analyzed) -->
      <div v-if="!result" class="text-center">
        <LightBulbIcon class="mx-auto h-8 w-8 text-gray-300 dark:text-gray-600" />
        <p class="mt-2 text-body text-gray-500 dark:text-gray-400">콘텐츠 기회를 발견하세요</p>
        <p v-if="pricingError" class="mt-2 text-body-xs text-error-strong">{{ pricingError }}</p>
        <p v-else-if="pricingLoading" class="mt-2 text-body-xs text-gray-500 dark:text-gray-400">
          분석 비용을 확인하는 중입니다.
        </p>
        <button
          v-if="!creditBlocked"
          type="button"
          :disabled="pricingLoading || (creditCost == null && !pricingError)"
          class="btn-primary btn-press mt-3 inline-flex items-center gap-1.5 text-body"
          @click="pricingError ? retryPricing() : analyze()"
        >
          <SparklesIcon class="h-4 w-4" />
          <span v-if="pricingLoading">비용 확인 중...</span>
          <span v-else-if="pricingError">비용 다시 확인</span>
          <span v-else>분석 시작</span>
        </button>
      </div>

      <!-- Results -->
      <div v-else class="space-y-3">
        <!-- Opportunities -->
        <div v-if="result.opportunities.length > 0">
          <p class="mb-2 text-caption text-success-strong">기회 발견</p>
          <div class="space-y-2">
            <div
              v-for="(opp, idx) in result.opportunities.slice(0, 3)"
              :key="idx"
              class="rounded-lg border border-success bg-success-subtle p-2.5"
            >
              <div class="flex items-start justify-between">
                <p class="text-caption text-gray-900 dark:text-gray-100">{{ opp.topic }}</p>
                <span
                  class="flex-shrink-0 rounded-full px-1.5 py-0.5 text-[10px] font-medium"
                  :class="demandBadge(opp.estimatedDemand)"
                >
                  {{ opp.estimatedDemand }}
                </span>
              </div>
              <p class="mt-1 text-[11px] text-gray-600 dark:text-gray-400">{{ opp.suggestedAngle }}</p>
            </div>
          </div>
        </div>

        <!-- Oversaturated -->
        <div v-if="result.oversaturated.length > 0">
          <p class="mb-2 text-caption text-error-strong">과포화 주제</p>
          <div class="space-y-2">
            <div
              v-for="(topic, idx) in result.oversaturated.slice(0, 2)"
              :key="idx"
              class="rounded-lg border border-error bg-error-subtle p-2.5"
            >
              <p class="text-caption text-gray-900 dark:text-gray-100">{{ topic.topic }}</p>
              <p class="mt-1 text-[11px] text-gray-600 dark:text-gray-400">{{ topic.recommendation }}</p>
            </div>
          </div>
        </div>

        <!-- Re-analyze button -->
        <button
          v-if="!creditBlocked"
          type="button"
          :disabled="pricingLoading || (creditCost == null && !pricingError)"
          class="w-full rounded-lg border border-gray-200 py-2 text-body-xs text-gray-500 hover:bg-gray-50 dark:border-gray-700 dark:text-gray-400 dark:hover:bg-gray-800"
          @click="pricingError ? retryPricing() : analyze()"
        >
          다시 분석
        </button>
      </div>
    </AsyncState>
    <CreditPurchaseModal v-model="showCreditModal" @purchase="onCreditPurchase" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { LightBulbIcon, SparklesIcon } from '@heroicons/vue/24/outline'
import { aiApi } from '@/api/ai'
import type { ContentGapResponse } from '@/types/ai'
import AsyncState from '@/components/common/AsyncState.vue'
import { CREDIT_INSUFFICIENT, matchesCode } from '@/composables/usePlanLimit'
import { useCreditStore } from '@/stores/credit'
import CreditPurchaseModal from '@/components/subscription/CreditPurchaseModal.vue'
import { useAiFeaturePricing } from '@/composables/useAiFeaturePricing'

const result = ref<ContentGapResponse | null>(null)
const loading = ref(false)
const error = ref('')
const creditBlocked = ref(false)
const showCreditModal = ref(false)
const creditStore = useCreditStore()
const {
  loading: pricingLoading,
  error: pricingError,
  load: loadPricing,
  costOf,
} = useAiFeaturePricing()

const creditCost = computed(() => costOf('CONTENT_GAP_ANALYSIS'))

onMounted(() => {
  void loadPricing()
})

function demandBadge(demand: string) {
  switch (demand) {
    case 'HIGH':
      return 'bg-success-subtle text-success-strong'
    case 'MEDIUM':
      return 'bg-warning-subtle text-warning-strong'
    case 'LOW':
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
  }
}

async function analyze() {
  // 실제 차감 단가를 모르면 잘못된 비용 안내 상태에서 유료 요청을 열지 않는다.
  if (creditCost.value == null) return
  loading.value = true
  error.value = ''
  creditBlocked.value = false
  try {
    result.value = await aiApi.contentGapAnalysis({ includeCompetitors: true })
  } catch (e: unknown) {
    // 잔액 부족은 안정 코드로만 판단한다. 문구/상태코드/일반 Error 문자열로는 충전 CTA 를 띄우지 않는다.
    // credit 차단 시에는 error 를 비워 AsyncState 의 에러 슬롯(중복 표시)이 뜨지 않게 한다.
    if (matchesCode(e, CREDIT_INSUFFICIENT)) {
      creditBlocked.value = true
    } else {
      error.value = e instanceof Error ? e.message : '분석에 실패했습니다'
    }
  } finally {
    loading.value = false
  }
}

function onCreditPurchase() {
  void creditStore.fetchBalance()
  creditBlocked.value = false
  error.value = ''
  // 자동 재실행 금지: 사용자가 분석 시작/다시 분석 버튼을 다시 누른다.
}

function retryPricing() {
  void loadPricing()
}
</script>
