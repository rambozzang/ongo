<template>
  <Teleport to="body">
    <div v-if="modelValue" class="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="payment-modal-title">
      <div class="fixed inset-0 bg-black/60 backdrop-blur-sm" aria-hidden="true" @click="close" />
      <div class="relative w-full max-w-2xl rounded-2xl bg-white/95 dark:bg-gray-800/95 p-8 shadow-2xl border border-gray-200 dark:border-gray-700">
        <!-- Header -->
        <div class="mb-6 flex items-center justify-between">
          <h3 id="payment-modal-title" class="text-h1 font-bold text-gray-900 dark:text-gray-100">플랜 변경</h3>
          <button class="text-gray-400 dark:text-gray-500 hover:text-gray-600 dark:hover:text-gray-300 transition-colors" aria-label="모달 닫기" @click="close">
            <XMarkIcon class="h-6 w-6" />
          </button>
        </div>

        <!-- Step 1: Plan Summary -->
        <div v-if="!processing && !paymentComplete" class="space-y-6">
          <div class="rounded-xl border-2 bg-gradient-to-br p-6" :class="getPlanGradientClass()">
            <div class="flex items-start justify-between">
              <div>
                <p class="text-body font-medium opacity-80 mb-1">업그레이드 플랜</p>
                <h4 class="text-display font-bold mb-3">{{ targetPlanInfo?.name }}</h4>
                <div class="flex items-baseline gap-2">
                  <p class="text-display font-bold">
                    {{ price === 0 ? '무료' : '₩' + price.toLocaleString() }}
                  </p>
                  <span v-if="price > 0" class="text-body-lg font-normal opacity-80">/월</span>
                </div>
              </div>
              <div class="flex h-16 w-16 items-center justify-center rounded-full bg-white/20 backdrop-blur-sm">
                <SparklesIcon class="h-8 w-8" aria-hidden="true" />
              </div>
            </div>
          </div>

          <div class="space-y-3 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50 p-6">
            <h5 class="mb-3 text-body font-semibold text-gray-700 dark:text-gray-300">플랜 주요 혜택</h5>
            <div v-if="targetPlanInfo" class="space-y-2">
              <div class="flex items-center gap-2 text-body">
                <CheckCircleIcon class="h-5 w-5 flex-shrink-0 text-success" />
                <span class="text-gray-700 dark:text-gray-300">
                  월 {{ targetPlanInfo.maxUploadsPerMonth === -1 ? '무제한' : targetPlanInfo.maxUploadsPerMonth + '회' }} 업로드
                </span>
              </div>
              <div class="flex items-center gap-2 text-body">
                <CheckCircleIcon class="h-5 w-5 flex-shrink-0 text-success" />
                <span class="text-gray-700 dark:text-gray-300">
                  {{ formatStorage(targetPlanInfo.storageMb) }} 저장 공간
                </span>
              </div>
              <div class="flex items-center gap-2 text-body">
                <CheckCircleIcon class="h-5 w-5 flex-shrink-0 text-success" />
                <span class="text-gray-700 dark:text-gray-300">
                  AI 크레딧 월 {{ targetPlanInfo.freeAiCredits.toLocaleString() }}개
                </span>
              </div>
              <div class="flex items-center gap-2 text-body">
                <CheckCircleIcon class="h-5 w-5 flex-shrink-0 text-success" />
                <span class="text-gray-700 dark:text-gray-300">
                  {{ targetPlanInfo.support }} 지원
                </span>
              </div>
            </div>
          </div>

          <div class="rounded-lg border border-info bg-info-subtle p-4">
            <div class="flex gap-3">
              <InformationCircleIcon class="h-5 w-5 flex-shrink-0 text-info-strong" />
              <div class="text-body text-info-strong">
                <p class="font-medium mb-1">결제 안내</p>
                <p>• 포트원 결제 창이 열리며 안전하게 결제됩니다.</p>
                <p>• 업그레이드 시 차액은 일할 계산됩니다.</p>
              </div>
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
          <p class="text-h1 font-bold text-gray-900 dark:text-gray-100">플랜 변경 완료!</p>
          <p class="mt-2 text-gray-600 dark:text-gray-400">
            {{ targetPlanInfo?.name }} 플랜으로 성공적으로 변경되었습니다
          </p>
          <div class="mt-6 w-full rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/50 p-4">
            <div class="flex items-center justify-between text-body">
              <span class="text-gray-600 dark:text-gray-400">변경된 플랜</span>
              <span class="font-medium text-gray-900 dark:text-gray-100">{{ targetPlanInfo?.name }}</span>
            </div>
            <div class="mt-2 flex items-center justify-between text-body">
              <span class="text-gray-600 dark:text-gray-400">월 결제 금액</span>
              <span class="font-bold text-gray-900 dark:text-gray-100">
                {{ price === 0 ? '무료' : '₩' + price.toLocaleString() }}
              </span>
            </div>
          </div>
        </div>

        <!--
          정기결제 동의. 유료 플랜에만 뜬다 — 무료 플랜은 결제 자체가 없다.

          결제 전에 카드 등록 창이 한 번 더 뜬다는 사실을 미리 알린다. 예고 없이 창이
          두 번 뜨면 사용자는 결제가 두 번 되는 줄 안다.
        -->
        <label
          v-if="requiresBillingConsent && !processing && !paymentComplete"
          class="mt-6 flex items-start gap-2 text-body text-gray-600 dark:text-gray-400"
        >
          <input
            v-model="billingConsent"
            type="checkbox"
            class="mt-1"
            data-testid="billing-consent"
          />
          <span>
            매월 자동으로 결제되는 데 동의합니다. 결제 진행 시 카드 등록 창이 먼저 열리며,
            등록한 수단은 다음 결제일에 자동으로 청구됩니다. 구독 화면에서 언제든 해지할 수 있습니다.
          </span>
        </label>

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
            :disabled="portoneLoading || (requiresBillingConsent && !billingConsent)"
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
import { ref, computed, watch, onUnmounted } from 'vue'
import { XMarkIcon, SparklesIcon, CheckCircleIcon, InformationCircleIcon } from '@heroicons/vue/24/outline'
import { PLANS, type PlanType } from '@/types/subscription'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { usePortOne } from '@/composables/usePortOne'

interface Props {
  modelValue: boolean
  targetPlan: PlanType
  price: number
  /** 화면에서 고른 결제 주기. 넘기지 않으면 월간으로 결제된다. */
  billingCycle?: 'MONTHLY' | 'YEARLY'
}

const props = withDefaults(defineProps<Props>(), {
  billingCycle: 'MONTHLY',
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm'): void
}>()

const processing = ref(false)
const paymentComplete = ref(false)
const paymentError = ref('')

const { loading: portoneLoading, openSubscriptionCheckout } = usePortOne()

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

/** Axios 가 붙이는 전송 계층 문구. 사용자에게 의미가 없다 (utils/loginError.ts 와 같은 판단). */
const TRANSPORT_ERROR = /^Request failed with status code \d+$/

/**
 * 결제 실패 사유를 사용자용 문구로 바꾼다.
 *
 * 서버는 거절 사유를 ResData.message 에 한국어로 담아 보낸다(예: 중복 구독 결제 거부).
 * 그런데 client.ts 의 응답 인터셉터는 401 만 가로채고 나머지는 원본 AxiosError 를 그대로
 * reject 하므로, 400 은 `Request failed with status code 400` 이라는 문구로만 도착했다.
 * 결제를 마친 사용자가 온보딩에서 이 화면에 막혔을 때 원인을 알 수 없던 이유다.
 *
 * 그래서 응답 본문의 message 를 먼저 쓰고, 없을 때만 예외 메시지를 쓴다.
 * 전역 인터셉터는 건드리지 않는다 — 다른 화면의 에러 처리까지 바꾸지 않기 위해서다.
 */
function paymentErrorMessage(e: unknown): string {
  const serverMessage = (e as { response?: { data?: { message?: unknown } } })?.response?.data?.message
  if (typeof serverMessage === 'string' && serverMessage.trim()) return serverMessage.trim()

  if (e instanceof Error) {
    const message = e.message.trim()
    if (message && message !== 'Network Error' && !TRANSPORT_ERROR.test(message)) return message
  }
  return '결제 준비에 실패했습니다. 다시 시도해주세요.'
}

/** 유료 플랜만 정기결제 수단이 필요하다. 무료 전환은 결제 자체가 없다. */
const requiresBillingConsent = computed(() => props.price > 0)

const billingConsent = ref(false)

async function startPayment() {
  paymentError.value = ''
  // 버튼도 잠기지만, 여기서 한 번 더 막는다 — 동의 없이 카드 등록 창을 열지 않는다.
  if (requiresBillingConsent.value && !billingConsent.value) return
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
    }, props.billingCycle)
  } catch (e: unknown) {
    paymentError.value = paymentErrorMessage(e)
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
    /*
     * 열릴 때마다 직전 결제의 흔적을 지운다.
     *
     * 상태를 되돌리는 곳이 close() 뿐이었는데, 부모가 v-model 을 직접 내리는 경로
     * (온보딩·구독 화면의 결제 성공 처리)에서는 close() 가 호출되지 않는다. 그래서 다음에
     * 열면 완료 화면이 그대로 떠 결제 버튼이 없고, 정상적인 상위 플랜 업그레이드가 막혔다.
     */
    processing.value = false
    paymentComplete.value = false
    paymentError.value = ''
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
