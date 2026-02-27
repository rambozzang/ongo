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
        <div v-if="!processing && !paymentComplete" class="space-y-6">
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
                <p>• Paddle 결제 창이 열리며 안전하게 결제됩니다.</p>
                <p>• 업그레이드 시 차액은 일할 계산됩니다.</p>
              </div>
            </div>
          </div>

          <div v-if="paymentError" class="rounded-lg bg-red-50 dark:bg-red-900/20 p-4">
            <p class="text-sm text-red-700 dark:text-red-400">{{ paymentError }}</p>
          </div>
        </div>

        <!-- Processing -->
        <div v-else-if="processing && !paymentComplete" class="flex flex-col items-center justify-center py-12">
          <div class="mb-6 h-16 w-16 animate-spin rounded-full border-4 border-primary-200 border-t-primary-600" />
          <p class="text-lg font-medium text-gray-700 dark:text-gray-300">결제 처리 중...</p>
          <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">잠시만 기다려주세요</p>
        </div>

        <!-- Success -->
        <div v-else-if="paymentComplete" class="flex flex-col items-center justify-center py-12">
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
            :disabled="paddleLoading"
            @click="startPayment"
          >
            {{ paddleLoading ? '준비 중...' : '결제하기' }}
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
import { XMarkIcon, SparklesIcon, CheckCircleIcon, InformationCircleIcon } from '@heroicons/vue/24/outline'
import { PLANS, type PlanType } from '@/types/subscription'
import { usePaddle } from '@/composables/usePaddle'

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

const processing = ref(false)
const paymentComplete = ref(false)
const paymentError = ref('')

const { loading: paddleLoading, openSubscriptionCheckout } = usePaddle()

const targetPlanInfo = computed(() => {
  return PLANS.find((p) => p.type === props.targetPlan) ?? null
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

async function startPayment() {
  paymentError.value = ''
  try {
    await openSubscriptionCheckout(props.targetPlan, {
      onSuccess: () => {
        processing.value = true
        // 웹훅이 DB를 동기화할 시간을 줌
        setTimeout(() => {
          paymentComplete.value = true
          processing.value = false
          emit('confirm')
        }, 1500)
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
