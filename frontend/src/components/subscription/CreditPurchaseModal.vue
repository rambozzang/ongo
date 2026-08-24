<template>
  <Teleport to="body">
    <div v-if="modelValue" class="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="credit-modal-title">
      <div class="fixed inset-0 bg-black/60 backdrop-blur-sm" aria-hidden="true" @click="close" />
      <div class="relative w-full max-w-2xl rounded-2xl bg-white/95 dark:bg-gray-800/95 p-8 shadow-2xl border border-gray-200 dark:border-gray-700">
        <!-- Header -->
        <div class="mb-6 flex items-center justify-between">
          <h3 id="credit-modal-title" class="text-h1 font-bold text-gray-900 dark:text-gray-100">
            <SparklesIcon class="mr-2 inline h-7 w-7 text-primary-600" aria-hidden="true" />
            AI 크레딧 충전
          </h3>
          <button class="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 transition-colors" aria-label="모달 닫기" @click="close">
            <XMarkIcon class="h-6 w-6" />
          </button>
        </div>

        <!-- Step 1: Package Selection -->
        <div v-if="!processing && !paymentComplete" class="space-y-6">
          <div>
            <h4 class="mb-2 text-title font-semibold text-gray-800 dark:text-gray-200">크레딧 패키지 선택</h4>
            <p class="mb-4 text-body text-gray-600 dark:text-gray-400">원하시는 크레딧 패키지를 선택하세요</p>
            <div class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
              <label
                v-for="(pkg, idx) in CREDIT_PACKAGES"
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

          <div v-if="paymentError" class="rounded-lg bg-error-subtle p-4">
            <p class="text-body text-error-strong">{{ paymentError }}</p>
          </div>
        </div>

        <!-- Processing -->
        <div v-else-if="processing && !paymentComplete" class="flex flex-col items-center justify-center py-12">
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
            class="btn-secondary"
            @click="close"
          >
            취소
          </button>
          <button
            v-if="!processing && !paymentComplete"
            class="btn-primary"
            :disabled="!selectedPackage || portoneLoading"
            @click="startPayment"
          >
            {{ portoneLoading ? '준비 중...' : '결제하기' }}
          </button>
          <button
            v-if="paymentComplete"
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
import { ref, watch, onUnmounted } from 'vue'
import { XMarkIcon, SparklesIcon, CheckCircleIcon } from '@heroicons/vue/24/outline'
import { CREDIT_PACKAGES, type CreditPackage } from '@/types/credit'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { usePortOne } from '@/composables/usePortOne'

interface Props {
  modelValue: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'purchase', pkg: CreditPackage): void
}>()

const selectedPackage = ref<CreditPackage | null>(null)
const processing = ref(false)
const paymentComplete = ref(false)
const paymentError = ref('')

const { loading: portoneLoading, openCreditCheckout } = usePortOne()

function selectPackage(pkg: CreditPackage) {
  selectedPackage.value = pkg
}

function getSavingsPercentage(pkg: CreditPackage): number {
  const basePrice = CREDIT_PACKAGES[0].pricePerCredit
  if (pkg.pricePerCredit >= basePrice) return 0
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
  if (!selectedPackage.value) return
  paymentError.value = ''

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
        processing.value = false
        paymentComplete.value = true
        if (selectedPackage.value) {
          emit('purchase', selectedPackage.value)
        }
      },
      onClose: () => {
        processing.value = false
      },
    })
  } catch (e: unknown) {
    paymentError.value = e instanceof Error ? e.message : '결제 준비에 실패했습니다. 다시 시도해주세요.'
  }
}

function close() {
  emit('update:modelValue', false)
  setTimeout(() => {
    selectedPackage.value = null
    processing.value = false
    paymentComplete.value = false
    paymentError.value = ''
  }, 300)
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && !paymentComplete.value && !processing.value) {
    close()
  }
}

watch(() => props.modelValue, (isOpen) => {
  if (isOpen) {
    document.addEventListener('keydown', handleKeydown)
    document.body.style.overflow = 'hidden'
  } else {
    document.removeEventListener('keydown', handleKeydown)
    document.body.style.overflow = ''
  }
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  document.body.style.overflow = ''
})
</script>
