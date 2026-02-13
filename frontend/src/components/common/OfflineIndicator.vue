<template>
  <!-- Offline Banner -->
  <Transition name="slide-down">
    <div
      v-if="!isOnline"
      role="alert"
      aria-live="assertive"
      class="fixed left-0 right-0 top-0 z-[90] bg-amber-500 px-4 py-3 text-center text-white shadow-lg dark:bg-amber-600"
    >
      <div class="flex items-center justify-center gap-2">
        <WifiIcon class="h-5 w-5" />
        <span class="font-medium">
          인터넷 연결이 끊겼습니다. 일부 기능이 제한될 수 있습니다.
        </span>
      </div>
    </div>
  </Transition>

  <!-- Reconnected Banner -->
  <Transition name="slide-down">
    <div
      v-if="showReconnected"
      role="alert"
      aria-live="polite"
      class="fixed left-0 right-0 top-0 z-[90] bg-emerald-500 px-4 py-3 text-center text-white shadow-lg dark:bg-emerald-600"
    >
      <div class="flex items-center justify-center gap-2">
        <CheckCircleIcon class="h-5 w-5" />
        <span class="font-medium">연결이 복원되었습니다</span>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { WifiIcon, CheckCircleIcon } from '@heroicons/vue/24/outline'

const isOnline = ref(navigator.onLine)
const showReconnected = ref(false)
const reconnectedTimeout = ref<ReturnType<typeof setTimeout> | null>(null)

function handleOnline() {
  isOnline.value = true

  // Show reconnected message
  showReconnected.value = true

  // Auto-hide after 3 seconds
  if (reconnectedTimeout.value) {
    clearTimeout(reconnectedTimeout.value)
  }

  reconnectedTimeout.value = setTimeout(() => {
    showReconnected.value = false
    reconnectedTimeout.value = null
  }, 3000)

  console.log('[OfflineIndicator] Connection restored')
}

function handleOffline() {
  isOnline.value = false
  showReconnected.value = false

  // Clear reconnected timeout if exists
  if (reconnectedTimeout.value) {
    clearTimeout(reconnectedTimeout.value)
    reconnectedTimeout.value = null
  }

  console.log('[OfflineIndicator] Connection lost')
}

onMounted(() => {
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
})

onUnmounted(() => {
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)

  if (reconnectedTimeout.value) {
    clearTimeout(reconnectedTimeout.value)
  }
})
</script>

<style scoped>
/* Slide down animation */
.slide-down-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-down-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 1, 1);
}

.slide-down-enter-from {
  transform: translateY(-100%);
  opacity: 0;
}

.slide-down-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}
</style>
