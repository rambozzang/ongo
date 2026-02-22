<template>
  <div>
    <!-- Loading State -->
    <div v-if="isLoading" class="flex items-center justify-center py-8">
      <div class="text-center">
        <div class="mb-3 inline-block h-8 w-8 animate-spin rounded-full border-4 border-gray-300 border-t-primary-600 dark:border-gray-700 dark:border-t-primary-400" />
        <p class="text-sm text-gray-600 dark:text-gray-400">처리 중...</p>
      </div>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="rounded-lg border border-red-200 bg-red-50 p-6 dark:border-red-900/50 dark:bg-red-900/20">
      <div class="mb-4 flex items-start gap-3">
        <ExclamationCircleIcon class="h-6 w-6 shrink-0 text-red-600 dark:text-red-500" />
        <div class="flex-1">
          <h3 class="mb-1 font-semibold text-red-900 dark:text-red-200">
            작업 실패
          </h3>
          <p class="text-sm text-red-700 dark:text-red-300">
            {{ errorMessage }}
          </p>
          <p v-if="retryCount > 0" class="mt-1 text-xs text-red-600 dark:text-red-400">
            재시도 횟수: {{ retryCount }} / {{ maxRetries }}
          </p>
        </div>
      </div>

      <div class="flex gap-2">
        <button
          class="btn-primary text-sm"
          :disabled="retryCount >= maxRetries"
          @click="handleRetry"
        >
          <ArrowPathIcon class="mr-1.5 h-4 w-4" />
          다시 시도
          <span v-if="retryCount >= maxRetries">(한도 초과)</span>
        </button>
        <button
          v-if="retryCount >= maxRetries"
          class="btn-secondary text-sm"
          @click="handleReset"
        >
          초기화
        </button>
      </div>
    </div>

    <!-- Success State (optional slot) -->
    <slot v-else-if="success" name="success" />

    <!-- Initial State -->
    <slot v-else />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { ExclamationCircleIcon, ArrowPathIcon } from '@heroicons/vue/24/outline'
import { useErrorHandler } from '@/composables/useErrorHandler'

interface Props {
  action: () => Promise<void>
  maxRetries?: number
  retryDelay?: number
}

const props = withDefaults(defineProps<Props>(), {
  maxRetries: 3,
  retryDelay: 1000,
})

const emit = defineEmits<{
  success: []
  error: [error: unknown]
}>()

const { handleApiError } = useErrorHandler()

const isLoading = ref(false)
const success = ref(false)
const error = ref<unknown>(null)
const errorMessage = ref('')
const retryCount = ref(0)

const canRetry = computed(() => retryCount.value < props.maxRetries)

async function executeAction() {
  isLoading.value = true
  error.value = null
  errorMessage.value = ''

  try {
    await props.action()
    success.value = true
    isLoading.value = false
    emit('success')
  } catch (err) {
    error.value = err
    isLoading.value = false

    // Get user-friendly error message
    const handledError = handleApiError(err, { silent: true })
    errorMessage.value = handledError.message

    emit('error', err)
  }
}

let retryTimeoutId: ReturnType<typeof setTimeout> | null = null
let retryCancelled = false

async function handleRetry() {
  if (!canRetry.value) {
    return
  }

  retryCount.value++

  // Exponential backoff: 1s, 2s, 4s, 8s...
  const delay = props.retryDelay * Math.pow(2, retryCount.value - 1)

  await new Promise<void>((resolve) => {
    retryTimeoutId = setTimeout(() => resolve(), delay)
  })
  if (!retryCancelled) {
    await executeAction()
  }
}

function handleReset() {
  retryCount.value = 0
  error.value = null
  errorMessage.value = ''
  success.value = false
}

// Expose methods for parent components
defineExpose({
  execute: executeAction,
  reset: handleReset,
  retry: handleRetry,
})

onUnmounted(() => {
  retryCancelled = true
  if (retryTimeoutId) clearTimeout(retryTimeoutId)
})
</script>
