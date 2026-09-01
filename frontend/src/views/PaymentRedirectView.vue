<template>
  <div class="flex min-h-screen items-center justify-center bg-gray-50 p-4 dark:bg-gray-900">
    <div class="w-full max-w-sm text-center">
      <!-- 검증 중 -->
      <div v-if="isProcessing" class="space-y-4">
        <div class="mx-auto h-12 w-12 animate-spin rounded-full border-4 border-primary-200 border-t-primary-600" />
        <p class="text-body text-gray-600 dark:text-gray-300">{{ $t('paymentRedirect.verifying') }}</p>
      </div>

      <!-- 성공: 서버가 확정한 뒤에만 여기 온다 -->
      <div v-else-if="isSuccess" class="space-y-4" data-testid="payment-redirect-success">
        <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-success-subtle">
          <CheckCircleIcon class="h-8 w-8 text-success-strong" />
        </div>
        <p class="text-body text-gray-700 dark:text-gray-200">{{ $t('paymentRedirect.success') }}</p>
        <button class="btn-primary w-full" @click="goToSubscription">
          {{ $t('paymentRedirect.goToSubscription') }}
        </button>
      </div>

      <!-- 실패·검증 불가 -->
      <div v-else class="space-y-4" data-testid="payment-redirect-error">
        <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-error-subtle">
          <ExclamationTriangleIcon class="h-8 w-8 text-error-strong" />
        </div>
        <p class="text-body text-error-strong">{{ errorMessage }}</p>
        <p v-if="isPending" class="text-body-sm text-gray-500 dark:text-gray-400">
          {{ $t('paymentRedirect.pendingHint') }}
        </p>
        <button class="btn-primary w-full" @click="goToSubscription">
          {{ $t('paymentRedirect.goToSubscription') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { CheckCircleIcon, ExclamationTriangleIcon } from '@heroicons/vue/24/outline'
import { portoneApi } from '@/api/portone'
import { useAuthStore } from '@/stores/auth'

/**
 * 모바일 결제가 PG 에서 돌아오는 지점.
 *
 * ## 쿼리를 믿지 않는다
 *
 * 이 URL 은 사용자가 직접 열 수 있고 값도 바꿀 수 있다. 그래서 `code` 가 없다고 해서
 * 성공으로 보지 않고, **항상 서버에 물어본다.** 성공 여부는 `complete`/`reconcile` 의
 * 응답만이 정한다 — 쿼리 파라미터로 유료 기능을 열어주면 결제 없이 구독이 열린다.
 *
 * ## 왜 실패에도 서버를 부르는가
 *
 * PG 가 실패를 알려도 승인만 나고 응답이 끊긴 경우가 있다. 그대로 닫으면 돈은 빠졌는데
 * 아무도 정리하지 않는 PENDING 결제가 남는다. `reconcile` 이 PG 를 재조회해 실제 상태로
 * 맞춘다.
 */
const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const isProcessing = ref(true)
const isSuccess = ref(false)
const isPending = ref(false)
const errorMessage = ref('')

const SUBSCRIPTION_PATH = '/subscription'

function goToSubscription() {
  router.replace(SUBSCRIPTION_PATH)
}

/**
 * 서버가 결제를 확정했을 때만 부른다.
 *
 * ## 왜 프로필을 다시 읽는가
 *
 * 결제가 끝나면 서버는 구독과 `users.plan_type` 을 함께 올린다. 그런데 이 화면은 결제
 * **전에** 로드된 세션을 그대로 들고 있어 `authStore.user.planType` 이 아직 이전 플랜이다.
 * 다시 읽지 않으면 방금 결제한 사용자가 **세션 내내** 상단바에서 옛 플랜을 본다 —
 * 권한은 열렸는데 화면은 안 열린 것처럼 보이고, 그 상태로 문의나 환불로 이어진다.
 *
 * 같은 이유로 `OnboardingView.handlePlanPaymentSuccess` 가 이미 이렇게 한다.
 *
 * ## 실패해도 결제 결과를 뒤집지 않는다
 *
 * 이건 **결제 성공 뒤의 후속 작업**이다. 갱신이 실패해도 되돌릴 결제가 아니므로 성공
 * 표시를 취소하지 않는다. `fetchProfile` 은 지금 내부에서 예외를 삼키지만, 구현이 바뀌어
 * reject 하더라도 흐름이 끊기지 않도록 여기서도 막는다.
 */
async function markSuccess() {
  isSuccess.value = true
  try {
    await authStore.fetchProfile()
  } catch {
    // 다음 진입 때 다시 읽힌다. 결제 결과와는 무관하다.
  }
}

/**
 * Vue Router 의 쿼리 값은 같은 키가 반복되면 배열이 된다. 그대로 문자열로 다루면
 * 조작된 입력에 문자열 메서드를 부르게 된다. 문자열일 때만 받는다.
 */
function queryString(key: string): string | undefined {
  const value = route.query[key]
  return typeof value === 'string' && value.length > 0 ? value : undefined
}

onMounted(async () => {
  const paymentId = queryString('paymentId')
  const code = queryString('code')

  // 식별자가 없으면 확인할 대상 자체가 없다. 결제 완료로 취급하지 않는다.
  if (!paymentId) {
    isProcessing.value = false
    errorMessage.value = t('paymentRedirect.missingPaymentId')
    return
  }

  try {
    if (code) {
      // PG 가 실패를 알렸다. 그래도 승인만 나고 끊겼을 수 있어 서버가 재조회한다.
      const result = await portoneApi.reconcile(paymentId)
      isProcessing.value = false
      if (result.status === 'COMPLETED' || result.status === 'PAID') {
        await markSuccess()
        return
      }
      if (result.status === 'PENDING') {
        isPending.value = true
        errorMessage.value = t('paymentRedirect.pending')
        return
      }
      // 서버가 확정한 실패 사유만 보여준다. PG 가 준 message 는 신뢰하지 않는다.
      errorMessage.value = t('paymentRedirect.failed')
      return
    }

    // 성공처럼 보여도 서버 검증을 거친다.
    await portoneApi.complete(paymentId)
    isProcessing.value = false
    await markSuccess()
  } catch {
    // complete 가 실패하면 확정되지 않은 것이다. 성공으로 넘기지 않는다.
    isProcessing.value = false
    errorMessage.value = t('paymentRedirect.verifyFailed')
  }
})
</script>
