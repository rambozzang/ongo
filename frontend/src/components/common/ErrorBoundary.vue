<template>
  <div>
    <slot v-if="!hasError" :key="resetKey" />

    <div v-else class="flex min-h-[60vh] items-center justify-center p-6">
      <div class="card max-w-lg w-full text-center">
        <!-- Error Icon -->
        <div class="mb-6 flex justify-center">
          <div class="rounded-full bg-red-100 p-4 dark:bg-red-900/30">
            <ExclamationTriangleIcon class="h-12 w-12 text-red-600 dark:text-red-500" />
          </div>
        </div>

        <!-- Title -->
        <h2 class="mb-3 text-2xl font-bold text-gray-900 dark:text-gray-100">
          문제가 발생했습니다
        </h2>

        <!-- Description -->
        <p class="mb-2 text-gray-600 dark:text-gray-400">
          {{ errorMessage }}
        </p>
        <p class="mb-8 text-sm text-gray-500 dark:text-gray-500">
          문제가 지속되면 고객센터에 문의해주세요.
        </p>

        <!-- Error Details (Dev Only) -->
        <div
          v-if="showDetails && errorDetails"
          class="mb-6 rounded-lg bg-gray-100 p-4 text-left dark:bg-gray-800"
        >
          <button
            class="mb-2 flex w-full items-center justify-between text-sm font-medium text-gray-700 dark:text-gray-300"
            @click="detailsExpanded = !detailsExpanded"
          >
            <span>오류 상세 정보</span>
            <ChevronDownIcon
              class="h-4 w-4 transition-transform"
              :class="{ 'rotate-180': detailsExpanded }"
            />
          </button>
          <div v-if="detailsExpanded" class="mt-2">
            <pre class="overflow-x-auto text-xs text-gray-600 dark:text-gray-400">{{ errorDetails }}</pre>
          </div>
        </div>

        <!-- Action Buttons -->
        <div class="flex flex-col gap-3 sm:flex-row sm:justify-center">
          <button class="btn-primary" @click="handleRetry">
            <ArrowPathIcon class="mr-2 h-5 w-5" />
            다시 시도
          </button>
          <button class="btn-secondary" @click="handleGoHome">
            <HomeIcon class="mr-2 h-5 w-5" />
            홈으로 이동
          </button>
        </div>

        <!-- Report Link -->
        <div class="mt-6">
          <button
            class="text-sm text-primary-600 hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
            @click="handleReport"
          >
            문제 신고하기
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onErrorCaptured, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import {
  ExclamationTriangleIcon,
  ArrowPathIcon,
  HomeIcon,
  ChevronDownIcon,
} from '@heroicons/vue/24/outline'

const router = useRouter()
const route = useRoute()
const notification = useNotificationStore()

const hasError = ref(false)
const errorMessage = ref('')
const errorDetails = ref('')
const detailsExpanded = ref(false)
const showDetails = ref(import.meta.env.DEV)
const resetKey = ref(0)

// Reset error state on route change
watch(
  () => route.path,
  () => {
    if (hasError.value) {
      hasError.value = false
      errorMessage.value = ''
      errorDetails.value = ''
      detailsExpanded.value = false
      resetKey.value++
    }
  }
)

onErrorCaptured((err: Error, instance, info) => {
  hasError.value = true

  // User-friendly error message
  errorMessage.value = getUserFriendlyMessage(err)

  // Detailed error for development
  errorDetails.value = `Error: ${err.message}\n\nComponent: ${instance?.$options.name || 'Unknown'}\nInfo: ${info}\n\nStack:\n${err.stack || 'No stack trace available'}`

  // Log to console for debugging
  console.error('[ErrorBoundary] Caught error:', {
    error: err,
    component: instance?.$options.name,
    info,
    stack: err.stack,
  })

  // Prevent error from propagating further
  return false
})

function getUserFriendlyMessage(error: Error): string {
  const message = error.message.toLowerCase()

  // Common error patterns
  if (message.includes('network') || message.includes('fetch')) {
    return '네트워크 연결에 문제가 있습니다.'
  }
  if (message.includes('timeout')) {
    return '요청 시간이 초과되었습니다.'
  }
  if (message.includes('permission') || message.includes('forbidden')) {
    return '접근 권한이 없습니다.'
  }
  if (message.includes('not found')) {
    return '요청한 리소스를 찾을 수 없습니다.'
  }

  // Generic message
  return '예기치 않은 오류가 발생했습니다.'
}

function handleRetry() {
  hasError.value = false
  errorMessage.value = ''
  errorDetails.value = ''
  detailsExpanded.value = false
  resetKey.value++

  notification.info('컴포넌트를 다시 로드합니다.')
}

function handleGoHome() {
  hasError.value = false
  router.push('/')
}

function handleReport() {
  // Mock report functionality
  notification.info('문제 신고 기능은 곧 제공될 예정입니다.')

  // In production, this would open a modal or send error to logging service
  console.log('[ErrorBoundary] Report requested:', {
    message: errorMessage.value,
    details: errorDetails.value,
    timestamp: new Date().toISOString(),
    userAgent: navigator.userAgent,
    url: window.location.href,
  })
}
</script>
