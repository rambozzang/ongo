<template>
  <Teleport to="body">
    <div v-if="modelValue" class="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="credit-modal-title">
      <div class="fixed inset-0 bg-black/60 backdrop-blur-sm" @click="close" aria-hidden="true" />
      <div class="relative w-full max-w-2xl rounded-2xl bg-white/95 dark:bg-gray-800/95 p-8 shadow-2xl border border-gray-200 dark:border-gray-700">
        <!-- Header -->
        <div class="mb-6 flex items-center justify-between">
          <h3 id="credit-modal-title" class="text-2xl font-bold text-gray-900 dark:text-gray-100">
            <SparklesIcon class="mr-2 inline h-7 w-7 text-primary-600" aria-hidden="true" />
            AI 크레딧 충전
          </h3>
          <button class="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 transition-colors" @click="close" aria-label="모달 닫기">
            <XMarkIcon class="h-6 w-6" />
          </button>
        </div>

        <!-- Step 1: Package Selection -->
        <div v-if="currentStep === 1" class="space-y-6">
          <div>
            <h4 class="mb-2 text-lg font-semibold text-gray-800 dark:text-gray-200">크레딧 패키지 선택</h4>
            <p class="mb-4 text-sm text-gray-600 dark:text-gray-400">원하시는 크레딧 패키지를 선택하세요</p>
            <div class="grid grid-cols-1 gap-4 tablet:grid-cols-2">
              <label
                v-for="(pkg, idx) in CREDIT_PACKAGES"
                :key="pkg.name"
                class="relative flex cursor-pointer flex-col rounded-xl border-2 p-5 transition-all hover:shadow-lg"
                :class="getPackageCardClass(pkg)"
              >
                <!-- Badge for special packages -->
                <div v-if="idx === 1" class="absolute -top-3 left-1/2 -translate-x-1/2">
                  <span class="inline-flex items-center rounded-full bg-primary-600 px-3 py-1 text-xs font-bold text-white shadow-lg">
                    인기
                  </span>
                </div>
                <div v-if="idx === 3" class="absolute -top-3 left-1/2 -translate-x-1/2">
                  <span class="inline-flex items-center rounded-full bg-gradient-to-r from-amber-500 to-orange-500 px-3 py-1 text-xs font-bold text-white shadow-lg">
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
                    <p class="text-lg font-bold text-gray-900 dark:text-gray-100">{{ pkg.name }}</p>
                    <p class="mt-1 text-3xl font-extrabold text-primary-600">
                      {{ pkg.credits.toLocaleString() }}
                      <span class="text-sm font-normal text-gray-500 dark:text-gray-400">크레딧</span>
                    </p>
                  </div>
                  <div class="flex h-6 w-6 items-center justify-center rounded-full border-2 transition-all" :class="getRadioClass(pkg)">
                    <div v-if="selectedPackage?.name === pkg.name" class="h-3 w-3 rounded-full bg-primary-600" />
                  </div>
                </div>

                <div class="mt-4 space-y-1.5 text-sm text-gray-600 dark:text-gray-400">
                  <div class="flex items-center justify-between">
                    <span>크레딧당</span>
                    <span class="font-semibold text-gray-700 dark:text-gray-300">₩{{ pkg.pricePerCredit.toLocaleString() }}</span>
                  </div>
                  <div class="flex items-center justify-between">
                    <span>유효기간</span>
                    <span class="font-semibold text-gray-700 dark:text-gray-300">{{ pkg.validDays }}일</span>
                  </div>
                  <div v-if="getSavingsPercentage(pkg) > 0" class="flex items-center justify-between pt-1">
                    <span class="text-green-600 dark:text-green-400 font-medium">절감 효과</span>
                    <span class="text-green-600 dark:text-green-400 font-bold">{{ getSavingsPercentage(pkg) }}% 할인</span>
                  </div>
                </div>

                <div class="mt-4 pt-4 border-t border-gray-200 dark:border-gray-700">
                  <div class="flex items-baseline justify-between">
                    <span class="text-sm text-gray-500 dark:text-gray-400">결제 금액</span>
                    <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">
                      ₩{{ pkg.price.toLocaleString() }}
                    </p>
                  </div>
                </div>
              </label>
            </div>
          </div>
        </div>

        <!-- Step 2: Payment Method Selection -->
        <div v-else-if="currentStep === 2" class="space-y-6">
          <div>
            <h4 class="mb-4 text-lg font-semibold text-gray-800 dark:text-gray-200">결제 수단 선택</h4>
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
            <p class="text-2xl font-bold text-gray-900 dark:text-gray-100">충전 완료!</p>
            <p class="mt-2 text-gray-600 dark:text-gray-400">
              {{ selectedPackage?.credits.toLocaleString() }} 크레딧이 충전되었습니다
            </p>
            <div class="mt-6 w-full rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50 p-4">
              <div class="flex items-center justify-between text-sm">
                <span class="text-gray-600 dark:text-gray-400">충전 패키지</span>
                <span class="font-medium text-gray-900 dark:text-gray-100">{{ selectedPackage?.name }}</span>
              </div>
              <div class="mt-2 flex items-center justify-between text-sm">
                <span class="text-gray-600 dark:text-gray-400">결제 금액</span>
                <span class="font-bold text-gray-900 dark:text-gray-100">₩{{ selectedPackage?.price.toLocaleString() }}</span>
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
import { XMarkIcon, SparklesIcon, CheckCircleIcon, CreditCardIcon, DevicePhoneMobileIcon } from '@heroicons/vue/24/outline'
import { CREDIT_PACKAGES, type CreditPackage } from '@/types/credit'

interface Props {
  modelValue: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'purchase', pkg: CreditPackage): void
}>()

const currentStep = ref(1)
const selectedPackage = ref<CreditPackage | null>(null)
const selectedPaymentMethod = ref<string>('card')
const paymentComplete = ref(false)

// Mock card form
const cardNumber = ref('')
const cardExpiry = ref('')
const cardCvc = ref('')

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
  if (currentStep.value === 1) return selectedPackage.value !== null
  if (currentStep.value === 2) {
    if (selectedPaymentMethod.value === 'card') {
      return cardNumber.value.length === 19 && cardExpiry.value.length === 5 && cardCvc.value.length === 3
    }
    return selectedPaymentMethod.value !== null
  }
  return false
})

function selectPackage(pkg: CreditPackage) {
  selectedPackage.value = pkg
}

function getSavingsPercentage(pkg: CreditPackage): number {
  // Base price per credit (using the smallest package as reference)
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

function nextStep() {
  if (currentStep.value === 2) {
    // Start mock payment processing
    currentStep.value = 3
    setTimeout(() => {
      paymentComplete.value = true
      if (selectedPackage.value) {
        emit('purchase', selectedPackage.value)
      }
    }, 2000)
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
    selectedPackage.value = null
    selectedPaymentMethod.value = 'card'
    paymentComplete.value = false
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
