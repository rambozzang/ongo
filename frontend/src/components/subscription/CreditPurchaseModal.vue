<template>
  <Teleport to="body">
    <div v-if="modelValue" class="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="credit-modal-title">
      <div class="fixed inset-0 bg-black/60 backdrop-blur-sm" aria-hidden="true" @click="close" />
      <div
        class="relative w-full max-w-2xl rounded-2xl bg-white/95 dark:bg-gray-800/95 p-8 shadow-2xl border border-gray-200 dark:border-gray-700"
        :aria-busy="processing || portoneLoading ? 'true' : undefined"
      >
        <!-- Header -->
        <div class="mb-6 flex items-center justify-between">
          <h3 id="credit-modal-title" class="text-h1 font-bold text-gray-900 dark:text-gray-100">
            <SparklesIcon class="mr-2 inline h-7 w-7 text-primary-600" aria-hidden="true" />
            AI 크레딧 충전
          </h3>
          <button
            type="button"
            class="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 transition-colors disabled:cursor-not-allowed disabled:opacity-50"
            aria-label="모달 닫기"
            :disabled="processing || portoneLoading"
            @click="close"
          >
            <XMarkIcon class="h-6 w-6" />
          </button>
        </div>

        <!-- Step 1: Package Selection -->
        <div v-if="!processing && !paymentComplete" class="space-y-6">
          <div>
            <h4 class="mb-2 text-title font-semibold text-gray-800 dark:text-gray-200">크레딧 패키지 선택</h4>
            <p class="mb-4 text-body text-gray-600 dark:text-gray-400">원하시는 크레딧 패키지를 선택하세요</p>
            <p
              v-if="requiredCredits != null"
              class="mb-4 rounded-lg border border-info-subtle bg-info-subtle px-4 py-3 text-body text-info-strong"
              data-testid="credit-requirement-summary"
            >
              이 기능에는 {{ requiredCredits.toLocaleString() }} 크레딧이 필요합니다.
              <span v-if="currentBalance != null">현재 잔액은 {{ currentBalance.toLocaleString() }} 크레딧입니다.</span>
            </p>
            <p v-if="isLoadingPackages" class="text-body text-gray-500 dark:text-gray-400" role="status">
              패키지를 불러오는 중…
            </p>
            <p v-if="!paymentChecked" class="mb-4 rounded-lg border border-line bg-surface-muted px-4 py-3 text-body text-content-secondary" role="status" aria-live="polite">
              결제 가능 여부를 확인하는 중…
            </p>
            <PaymentUnavailableNotice
              v-else-if="!paymentEnabled"
              class="mb-4"
              :reason="paymentDisabledReason"
              :checking="paymentChecking"
              :check-failed="paymentCheckFailed"
              @recheck="recheckPaymentAvailability"
            />
            <div
              v-else-if="!isLoadingPackages && !packages"
              class="rounded-lg border border-warning-subtle bg-warning-subtle px-4 py-3 text-body text-warning-strong"
              role="status"
              data-testid="credit-packages-error"
            >
              <p>{{ packagesError ?? '크레딧 패키지를 불러오지 못했습니다.' }}</p>
              <button type="button" class="btn-secondary mt-2 text-body-xs" @click="creditStore.fetchPackages()">
                다시 시도
              </button>
            </div>
            <div v-else class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
              <label
                v-for="(pkg, idx) in packages"
                :key="pkg.name"
                class="relative flex cursor-pointer flex-col rounded-xl border-2 p-5 transition-all hover:shadow-lg"
                :class="getPackageCardClass(pkg)"
              >
                <div v-if="idx === 1" class="absolute -top-3 left-1/2 -translate-x-1/2">
                  <span class="inline-flex items-center rounded-full bg-primary-600 px-3 py-1 text-body-xs font-bold text-white shadow-lg">
                    인기
                  </span>
                </div>
                <div v-if="idx === 3" class="absolute -top-3 left-1/2 -translate-x-1/2">
                  <span class="inline-flex items-center rounded-full bg-gradient-to-r from-amber-500 to-orange-500 px-3 py-1 text-body-xs font-bold text-white shadow-lg">
                    최고 가성비
                  </span>
                </div>

                <input
                  type="radio"
                  name="package"
                  :value="pkg.name"
                  :checked="selectedPackage?.name === pkg.name"
                  class="sr-only"
                  @change="selectPackage(pkg)"
                />

                <div class="flex items-start justify-between">
                  <div class="flex-1">
                    <p class="text-title font-bold text-gray-900 dark:text-gray-100">{{ pkg.name }}</p>
                    <p class="mt-1 text-display font-extrabold text-primary-600">
                      {{ pkg.credits.toLocaleString() }}
                      <span class="text-body font-normal text-gray-500 dark:text-gray-400">크레딧</span>
                    </p>
                  </div>
                  <div class="flex h-6 w-6 items-center justify-center rounded-full border-2 transition-all" :class="getRadioClass(pkg)">
                    <div v-if="selectedPackage?.name === pkg.name" class="h-3 w-3 rounded-full bg-primary-600" />
                  </div>
                </div>

                <div class="mt-4 space-y-1.5 text-body text-gray-600 dark:text-gray-400">
                  <div class="flex items-center justify-between">
                    <span>크레딧당</span>
                    <span class="font-semibold text-gray-700 dark:text-gray-300">₩{{ pkg.pricePerCredit.toLocaleString() }}</span>
                  </div>
                  <div class="flex items-center justify-between">
                    <span>유효기간</span>
                    <span class="font-semibold text-gray-700 dark:text-gray-300">{{ pkg.validDays }}일</span>
                  </div>
                  <div v-if="getSavingsPercentage(pkg) > 0" class="flex items-center justify-between pt-1">
                    <span class="text-success-strong font-medium">절감 효과</span>
                    <span class="text-success-strong font-bold">{{ getSavingsPercentage(pkg) }}% 할인</span>
                  </div>
                </div>

                <div class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-700">
                  <div class="flex items-baseline justify-between">
                    <span class="text-body text-gray-500 dark:text-gray-400">결제 금액</span>
                    <p class="text-h1 font-bold text-gray-900 dark:text-gray-100">
                      ₩{{ pkg.price.toLocaleString() }}
                    </p>
                  </div>
                </div>
              </label>
            </div>
          </div>

          <div v-if="paymentError" class="rounded-lg bg-error-subtle p-4" role="alert">
            <p class="text-body text-error-strong">{{ paymentError }}</p>
          </div>
        </div>

        <!-- Processing -->
        <div v-else-if="processing && !paymentComplete" class="flex flex-col items-center justify-center py-12" role="status" aria-live="polite">
          <LoadingSpinner size="lg" class="mb-6" />
          <p class="text-title font-medium text-gray-700 dark:text-gray-300">결제 처리 중...</p>
          <p class="mt-2 text-body text-gray-500 dark:text-gray-400">잠시만 기다려주세요</p>
        </div>

        <!-- Success -->
        <div v-else-if="paymentComplete" class="flex flex-col items-center justify-center py-12">
          <div class="mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-success-subtle">
            <CheckCircleIcon class="h-12 w-12 text-success-strong" />
          </div>
          <p class="text-h1 font-bold text-gray-900 dark:text-gray-100">충전 완료!</p>
          <p class="mt-2 text-gray-600 dark:text-gray-400">
            {{ selectedPackage?.credits.toLocaleString() }} 크레딧이 충전되었습니다
          </p>
          <div class="mt-6 w-full rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50 p-4">
            <div class="flex items-center justify-between text-body">
              <span class="text-gray-600 dark:text-gray-400">충전 패키지</span>
              <span class="font-medium text-gray-900 dark:text-gray-100">{{ selectedPackage?.name }}</span>
            </div>
            <div class="mt-2 flex items-center justify-between text-body">
              <span class="text-gray-600 dark:text-gray-400">결제 금액</span>
              <span class="font-bold text-gray-900 dark:text-gray-100">₩{{ selectedPackage?.price.toLocaleString() }}</span>
            </div>
          </div>
        </div>

        <!-- Navigation Buttons -->
        <div class="mt-8 flex justify-end gap-3">
          <button
            v-if="!processing && !paymentComplete"
            type="button"
            class="btn-secondary"
            :disabled="portoneLoading"
            @click="close"
          >
            취소
          </button>
          <button
            v-if="!processing && !paymentComplete"
            type="button"
            class="btn-primary"
            :disabled="!selectedPackage || !paymentEnabled || !paymentChecked || paymentChecking || portoneLoading"
            @click="startPayment"
          >
            {{ !paymentChecked || paymentChecking ? '결제 확인 중…' : portoneLoading ? '준비 중...' : '결제하기' }}
          </button>
          <button
            v-if="paymentComplete"
            type="button"
            class="btn-primary"
            @click="close"
          >
            확인
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { XMarkIcon, SparklesIcon, CheckCircleIcon } from '@heroicons/vue/24/outline'
import { storeToRefs } from 'pinia'
import { type CreditPackage } from '@/types/credit'
import { useCreditStore } from '@/stores/credit'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PaymentUnavailableNotice from './PaymentUnavailableNotice.vue'
import { usePaymentAvailability } from '@/composables/usePaymentAvailability'
import { usePortOne } from '@/composables/usePortOne'

interface Props {
  modelValue: boolean
  /** 특정 기능에서 열었을 때 필요한 크레딧. 일반 충전 진입점에서는 생략한다. */
  requiredCredits?: number | null
  /** 필요한 크레딧과 함께 보여 줄 당시 잔액. */
  currentBalance?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  requiredCredits: null,
  currentBalance: null,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'purchase', pkg: CreditPackage): void
}>()

const selectedPackage = ref<CreditPackage | null>(null)
const processing = ref(false)
const paymentComplete = ref(false)
const paymentError = ref('')
/** 늦게 도착한 PG 콜백이 닫힌 모달의 상태를 되살리지 못하게 하는 시도 세대. */
let paymentAttempt = 0

const { loading: portoneLoading, openCreditCheckout } = usePortOne()
const {
  paymentEnabled,
  paymentDisabledReason,
  paymentChecked,
  paymentChecking,
  paymentCheckFailed,
  loadPaymentAvailability,
  recheckPaymentAvailability,
} = usePaymentAvailability()

/*
 * 패키지 목록은 **서버가 준다.** 결제 금액은 서버가 `CreditPackage` enum 에서 계산하므로,
 * 화면이 자기 상수를 그리면 사용자가 본 금액과 청구액이 갈릴 수 있다.
 *
 * 조회 전·실패 시에는 목록을 그리지 않는다 — 오래된 숫자를 대신 보여 주는 것이 빈 화면보다
 * 나쁘다. 살 수 없는 상태를 살 수 있는 것처럼 만들지도 않는다.
 */
const creditStore = useCreditStore()
const { packages, isLoadingPackages, packagesError } = storeToRefs(creditStore)

function selectPackage(pkg: CreditPackage) {
  selectedPackage.value = pkg
}

function getSavingsPercentage(pkg: CreditPackage): number {
  // 기준가도 서버 목록의 첫 패키지에서 읽는다. 상수를 남겨 두면 그 하나가 드리프트한다.
  const basePrice = packages.value?.[0]?.pricePerCredit
  if (basePrice == null || pkg.pricePerCredit >= basePrice) return 0
  return Math.round(((basePrice - pkg.pricePerCredit) / basePrice) * 100)
}

function getPackageCardClass(pkg: CreditPackage): string {
  if (selectedPackage.value?.name === pkg.name) {
    return 'border-primary-500 bg-primary-50 dark:bg-primary-900/20 shadow-lg'
  }
  return 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'
}

function getRadioClass(pkg: CreditPackage): string {
  if (selectedPackage.value?.name === pkg.name) {
    return 'border-primary-600'
  }
  return 'border-gray-300 dark:border-gray-600'
}

async function startPayment() {
  if (
    !selectedPackage.value ||
    !paymentEnabled.value ||
    !paymentChecked.value ||
    paymentChecking.value ||
    processing.value ||
    portoneLoading.value
  ) return
  paymentError.value = ''
  processing.value = true
  const attempt = ++paymentAttempt

  try {
    // 표시명이 아니라 enum 키를 보낸다. 서버는 STARTER/BASIC/PRO/BUSINESS 만 인식하며,
    // 한글 표시명을 보내면 가격 ID 조회가 실패해 결제창 자체가 열리지 않는다.
    await openCreditCheckout(selectedPackage.value.key, {
      /*
       * onSuccess 는 `complete` 응답을 받은 **뒤에** 불린다(usePortOne.completeResult 가
       * await 한다). 서버는 그 호출 안에서 PG 에 재조회해 검증하고 크레딧까지 지급한다.
       * 즉 이 시점의 잔액은 이미 확정이다.
       *
       * 예전에는 여기서 1.5 초를 기다렸다. 기다릴 대상이 없는 지연이라 오래된 잔액을
       * 더 오래 보여줄 뿐이었고, 그 사이 배경 클릭으로 모달을 닫으면 타이머가 나중에
       * paymentComplete 를 되살려 다음에 열 때 이전 결제의 "충전 완료!"가 떴다.
       */
      onSuccess: () => {
        if (attempt !== paymentAttempt || !props.modelValue) return
        processing.value = false
        paymentComplete.value = true
        if (selectedPackage.value) {
          emit('purchase', selectedPackage.value)
        }
      },
      onClose: () => {
        if (attempt !== paymentAttempt) return
        processing.value = false
      },
    })
  } catch (e: unknown) {
    if (attempt !== paymentAttempt || !props.modelValue) return
    processing.value = false
    paymentError.value = e instanceof Error ? e.message : '결제 준비에 실패했습니다. 다시 시도해주세요.'
  }
}

function close() {
  if (processing.value || portoneLoading.value) return
  resetState()
  emit('update:modelValue', false)
}

function resetState() {
  paymentAttempt += 1
  selectedPackage.value = null
  processing.value = false
  paymentComplete.value = false
  paymentError.value = ''
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && !paymentComplete.value && !processing.value && !portoneLoading.value) {
    close()
  }
}

watch(() => props.modelValue, (isOpen) => {
  if (isOpen) {
    resetState()
    // 열 때마다 확인한다. 세션 중 가격이 바뀌어도 화면이 옛 값을 들고 있지 않게 한다.
    void creditStore.fetchPackages()
    void loadPaymentAvailability()
    document.addEventListener('keydown', handleKeydown)
    document.body.style.overflow = 'hidden'
  } else {
    document.removeEventListener('keydown', handleKeydown)
    document.body.style.overflow = ''
  }
})

/*
 * 열린 채로 마운트되는 경우도 있다. 위 watch 는 값이 **바뀔 때만** 도므로, 그때는
 * 목록을 한 번도 받지 못해 살 수 있는 패키지가 없는 것처럼 보인다.
 */
onMounted(() => {
  if (props.modelValue) {
    void creditStore.fetchPackages()
    void loadPaymentAvailability()
  }
})

onUnmounted(() => {
  paymentAttempt += 1
  document.removeEventListener('keydown', handleKeydown)
  document.body.style.overflow = ''
})
</script>
