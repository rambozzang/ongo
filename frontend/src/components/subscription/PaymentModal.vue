<template>
  <Teleport to="body">
    <div v-if="modelValue" class="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="payment-modal-title">
      <div class="fixed inset-0 bg-black/60 backdrop-blur-sm" @click="close" aria-hidden="true" />
      <div class="relative w-full max-w-2xl rounded-2xl bg-white/95 dark:bg-gray-800/95 p-8 shadow-2xl border border-gray-200 dark:border-gray-700">
        <!-- Header -->
        <div class="mb-6 flex items-center justify-between">
          <h3 id="payment-modal-title" class="text-2xl font-bold text-gray-900 dark:text-gray-100">플랜 변경</h3>
          <button class="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 transition-colors" @click="close" aria-label="모달 닫기">
            <XMarkIcon class="h-6 w-6" />
          </button>
        </div>

        <!-- Step 1: Plan Summary -->
        <div v-if="currentStep === 1" class="space-y-6">
          <div class="rounded-xl border-2 bg-gradient-to-br p-6" :class="getPlanGradientClass()">
            <div class="flex items-start justify-between">
              <div>
                <p class="text-sm font-medium opacity-80 mb-1">업그레이드 플랜</p>
                <h4 class="text-3xl font-bold mb-3">{{ targetPlanInfo?.name }}</h4>
                <div class="flex items-baseline gap-2">
                  <p class="text-3xl font-bold">
                    {{ price === 0 ? '무료' : '₩' + price.toLocaleString() }}
                  </p>
                  <span v-if="price > 0" class="text-base font-normal opacity-80">/월</span>
                </div>
              </div>
              <div class="flex h-16 w-16 items-center justify-center rounded-full bg-white/20 backdrop-blur-sm">
                <SparklesIcon class="h-8 w-8" aria-hidden="true" />
              </div>
            </div>
          </div>

          <div class="space-y-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50 p-6">
            <h5 class="mb-3 text-sm font-semibold text-gray-700 dark:text-gray-300">플랜 주요 혜택</h5>
            <div v-if="targetPlanInfo" class="space-y-2">
              <div class="flex items-center gap-2 text-sm">
                <CheckCircleIcon class="h-5 w-5 flex-shrink-0 text-green-500" />
                <span class="text-gray-700 dark:text-gray-300">
                  월 {{ targetPlanInfo.maxUploadsPerMonth === -1 ? '무제한' : targetPlanInfo.maxUploadsPerMonth + '회' }} 업로드
                </span>
              </div>
              <div class="flex items-center gap-2 text-sm">
                <CheckCircleIcon class="h-5 w-5 flex-shrink-0 text-green-500" />
                <span class="text-gray-700 dark:text-gray-300">
                  {{ formatStorage(targetPlanInfo.storageMb) }} 저장 공간
                </span>
              </div>
              <div class="flex items-center gap-2 text-sm">
                <CheckCircleIcon class="h-5 w-5 flex-shrink-0 text-green-500" />
                <span class="text-gray-700 dark:text-gray-300">
                  AI 크레딧 월 {{ targetPlanInfo.freeAiCredits.toLocaleString() }}개
                </span>
              </div>
              <div class="flex items-center gap-2 text-sm">
                <CheckCircleIcon class="h-5 w-5 flex-shrink-0 text-green-500" />
                <span class="text-gray-700 dark:text-gray-300">
                  {{ targetPlanInfo.support }} 지원
                </span>
              </div>
            </div>
          </div>

          <div class="rounded-lg border border-blue-200 dark:border-blue-800 bg-blue-50 dark:bg-blue-900/20 p-4">
            <div class="flex gap-3">
              <InformationCircleIcon class="h-5 w-5 flex-shrink-0 text-blue-600 dark:text-blue-400" />
              <div class="text-sm text-blue-800 dark:text-blue-300">
                <p class="font-medium mb-1">결제 안내</p>
                <p>• 업그레이드 시 차액은 일할 계산되어 즉시 청구됩니다.</p>
                <p>• 다음 결제일부터 새로운 요금제가 적용됩니다.</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Step 2: Payment Method Selection -->
        <div v-else-if="currentStep === 2" class="space-y-6">
          <div>
            <h4 class="mb-2 text-lg font-semibold text-gray-800 dark:text-gray-200">결제 수단 선택</h4>
            <p class="mb-4 text-sm text-gray-600 dark:text-gray-400">원하시는 결제 방법을 선택하세요</p>
            <div class="grid grid-cols-1 gap-3">
              <label
                v-for="method in paymentMethods"
                :key="method.id"
                class="flex cursor-pointer items-center justify-between rounded-lg border-2 p-4 transition-all"
                :class="selectedPaymentMethod === method.id ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20' : 'border-gray-200 dark:border-gray-700 hover:border-gray-300 dark:hover:border-gray-600'"
              >
                <div class="flex items-center gap-4">
                  <input
                    type="radio"
                    name="payment-method"
                    :value="method.id"
                    :checked="selectedPaymentMethod === method.id"
                    class="h-4 w-4 text-primary-600"
                    @change="selectedPaymentMethod = method.id"
                  />
                  <div class="flex items-center gap-3">
                    <div class="flex h-12 w-12 items-center justify-center rounded-lg" :class="method.bgColor">
                      <component :is="method.icon" class="h-6 w-6" :class="method.iconColor" />
                    </div>
                    <span class="font-medium text-gray-900 dark:text-gray-100">{{ method.name }}</span>
                  </div>
                </div>
              </label>
            </div>

            <!-- Mock Card Form -->
            <div v-if="selectedPaymentMethod === 'card'" class="mt-6 space-y-4 rounded-lg border border-gray-200 dark:border-gray-700 p-6 bg-gray-50 dark:bg-gray-900/50">
              <h5 class="text-sm font-semibold text-gray-700 dark:text-gray-300">카드 정보</h5>
              <div>
                <label class="mb-1 block text-sm text-gray-600 dark:text-gray-400">카드 번호</label>
                <input
                  v-model="cardNumber"
                  type="text"
                  placeholder="0000 0000 0000 0000"
                  maxlength="19"
                  class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-4 py-2 text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-500/20"
                  @input="formatCardNumber"
                />
              </div>
              <div class="grid grid-cols-2 gap-4">
                <div>
                  <label class="mb-1 block text-sm text-gray-600 dark:text-gray-400">만료일</label>
                  <input
                    v-model="cardExpiry"
                    type="text"
                    placeholder="MM/YY"
                    maxlength="5"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-4 py-2 text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-500/20"
                    @input="formatExpiry"
                  />
                </div>
                <div>
                  <label class="mb-1 block text-sm text-gray-600 dark:text-gray-400">CVC</label>
                  <input
                    v-model="cardCvc"
                    type="text"
                    placeholder="000"
                    maxlength="3"
                    class="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-4 py-2 text-gray-900 dark:text-gray-100 focus:border-primary-500 focus:outline-none focus:ring-2 focus:ring-primary-500/20"
                  />
                </div>
              </div>
            </div>

            <div v-if="paymentError" class="mt-4 rounded-lg bg-red-50 dark:bg-red-900/20 p-4">
              <p class="text-sm text-red-700 dark:text-red-400">{{ paymentError }}</p>
            </div>
          </div>
        </div>

        <!-- Step 3: Processing & Success -->
        <div v-else-if="currentStep === 3" class="space-y-6">
          <div v-if="!paymentComplete" class="flex flex-col items-center justify-center py-12">
            <div class="mb-6 h-16 w-16 animate-spin rounded-full border-4 border-primary-200 border-t-primary-600" />
            <p class="text-lg font-medium text-gray-700 dark:text-gray-300">결제 처리 중...</p>
            <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">잠시만 기다려주세요</p>
          </div>

          <div v-else class="flex flex-col items-center justify-center py-12">
            <div class="mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-green-100 dark:bg-green-900/30">
              <CheckCircleIcon class="h-12 w-12 text-green-600 dark:text-green-400" />
            </div>
            <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">플랜 변경 완료!</p>
            <p class="mt-2 text-gray-600 dark:text-gray-400">
              {{ targetPlanInfo?.name }} 플랜으로 성공적으로 변경되었습니다
            </p>
            <div class="mt-6 w-full rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50 p-4">
              <div class="flex items-center justify-between text-sm">
                <span class="text-gray-600 dark:text-gray-400">변경된 플랜</span>
                <span class="font-medium text-gray-900 dark:text-gray-100">{{ targetPlanInfo?.name }}</span>
              </div>
              <div class="mt-2 flex items-center justify-between text-sm">
                <span class="text-gray-600 dark:text-gray-400">월 결제 금액</span>
                <span class="font-bold text-gray-900 dark:text-gray-100">
                  {{ price === 0 ? '무료' : '₩' + price.toLocaleString() }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Step Indicator -->
        <div v-if="!paymentComplete" class="mt-8 flex items-center justify-center gap-2" role="progressbar" :aria-valuenow="currentStep" aria-valuemin="1" aria-valuemax="3" aria-label="결제 진행 단계">
          <div
            v-for="step in 3"
            :key="step"
            class="h-2 w-16 rounded-full transition-all"
            :class="currentStep >= step ? 'bg-primary-600' : 'bg-gray-200 dark:bg-gray-700'"
            :aria-label="`단계 ${step}`"
          />
        </div>

        <!-- Navigation Buttons -->
        <div class="mt-8 flex justify-end gap-3">
          <button
            v-if="currentStep > 1 && !paymentComplete"
            class="btn-secondary"
            @click="previousStep"
          >
            이전
          </button>
          <button
            v-if="currentStep === 1"
            class="btn-secondary"
            @click="close"
          >
            취소
          </button>
          <button
            v-if="currentStep < 3"
            class="btn-primary"
            :disabled="!canProceed"
            @click="nextStep"
          >
            다음
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
import { ref, computed, watch, onUnmounted } from 'vue'
import { XMarkIcon, SparklesIcon, CheckCircleIcon, InformationCircleIcon, CreditCardIcon, DevicePhoneMobileIcon } from '@heroicons/vue/24/outline'
import { PLANS, type PlanType } from '@/types/subscription'
import { subscriptionApi } from '@/api/subscription'

interface Props {
  modelValue: boolean
  targetPlan: PlanType
  price: number
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
}>()

const currentStep = ref(1)
const selectedPaymentMethod = ref<string>('card')
const paymentComplete = ref(false)
const paymentError = ref('')

// Mock card form
const cardNumber = ref('')
const cardExpiry = ref('')
const cardCvc = ref('')

const targetPlanInfo = computed(() => {
  return PLANS.find((p) => p.type === props.targetPlan) ?? null
})

const paymentMethods = [
  {
    id: 'card',
    name: '신용카드/체크카드',
    icon: CreditCardIcon,
    bgColor: 'bg-blue-100 dark:bg-blue-900/30',
    iconColor: 'text-blue-600 dark:text-blue-400',
  },
  {
    id: 'kakao',
    name: '카카오페이',
    icon: DevicePhoneMobileIcon,
    bgColor: 'bg-yellow-100 dark:bg-yellow-900/30',
    iconColor: 'text-yellow-600 dark:text-yellow-400',
  },
  {
    id: 'naver',
    name: '네이버페이',
    icon: DevicePhoneMobileIcon,
    bgColor: 'bg-green-100 dark:bg-green-900/30',
    iconColor: 'text-green-600 dark:text-green-400',
  },
  {
    id: 'toss',
    name: '토스페이',
    icon: DevicePhoneMobileIcon,
    bgColor: 'bg-blue-100 dark:bg-blue-900/30',
    iconColor: 'text-blue-600 dark:text-blue-400',
  },
]

const canProceed = computed(() => {
  if (currentStep.value === 1) return true
  if (currentStep.value === 2) {
    if (selectedPaymentMethod.value === 'card') {
      return cardNumber.value.length === 19 && cardExpiry.value.length === 5 && cardCvc.value.length === 3
    }
    return selectedPaymentMethod.value !== null
  }
  return false
})

function formatStorage(mb: number): string {
  if (mb >= 1024) return (mb / 1024) + 'GB'
  return mb + 'MB'
}

function getPlanGradientClass(): string {
  const gradientMap: Record<PlanType, string> = {
    FREE: 'from-gray-400 to-gray-600 text-white border-gray-500',
    STARTER: 'from-blue-400 to-blue-600 text-white border-blue-500',
    PRO: 'from-purple-400 to-purple-600 text-white border-purple-500',
    BUSINESS: 'from-amber-400 to-amber-600 text-white border-amber-500',
  }
  return gradientMap[props.targetPlan]
}

function formatCardNumber() {
  cardNumber.value = cardNumber.value
    .replace(/\s/g, '')
    .replace(/(\d{4})/g, '$1 ')
    .trim()
}

function formatExpiry() {
  cardExpiry.value = cardExpiry.value
    .replace(/\D/g, '')
    .replace(/(\d{2})(\d)/, '$1/$2')
}

async function nextStep() {
  if (currentStep.value === 2) {
    currentStep.value = 3
    paymentError.value = ''
    try {
      await subscriptionApi.changePlan({
        targetPlan: props.targetPlan,
      })
      paymentComplete.value = true
      emit('confirm')
    } catch (e: unknown) {
      paymentError.value = e instanceof Error ? e.message : '플랜 변경에 실패했습니다. 다시 시도해주세요.'
      currentStep.value = 2
    }
  } else {
    currentStep.value++
  }
}

function previousStep() {
  if (currentStep.value > 1) {
    currentStep.value--
  }
}

function close() {
  emit('update:modelValue', false)
  // Reset state after animation
  setTimeout(() => {
    currentStep.value = 1
    selectedPaymentMethod.value = 'card'
    paymentComplete.value = false
    paymentError.value = ''
    cardNumber.value = ''
    cardExpiry.value = ''
    cardCvc.value = ''
  }, 300)
}

// Keyboard navigation
function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && !paymentComplete.value) {
    close()
  }
}

// Add/remove keyboard listener when modal opens/closes
watch(() => props.modelValue, (isOpen) => {
  if (isOpen) {
    document.addEventListener('keydown', handleKeydown)
    // Focus trap - prevent scrolling background
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
