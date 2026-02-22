<template>
  <Transition name="slide-up">
    <div
      v-if="showBanner"
      role="banner"
      class="fixed bottom-16 left-0 right-0 z-[80] mx-4 mb-2 rounded-xl border border-gray-200 bg-white p-4 shadow-lg tablet:bottom-0 tablet:mx-auto tablet:max-w-md dark:border-gray-700 dark:bg-gray-800"
    >
      <div class="flex items-start gap-3">
        <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary-100 dark:bg-primary-900/30">
          <DevicePhoneMobileIcon class="h-6 w-6 text-primary-600 dark:text-primary-400" />
        </div>

        <div class="min-w-0 flex-1">
          <p class="font-semibold text-gray-900 dark:text-gray-100">
            {{ isIos ? '홈 화면에 추가' : 'onGo 앱 설치하기' }}
          </p>
          <p class="mt-0.5 text-sm text-gray-600 dark:text-gray-400">
            <template v-if="isIos">
              Safari 메뉴에서
              <ArrowUpOnSquareIcon class="inline h-4 w-4 align-text-bottom" />
              버튼을 누르고 "홈 화면에 추가"를 선택하세요.
            </template>
            <template v-else>
              홈 화면에 추가하여 더 빠르게 이용하세요.
            </template>
          </p>

          <div v-if="!isIos" class="mt-3 flex gap-2">
            <button
              class="rounded-lg bg-primary-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-primary-700 dark:bg-primary-500 dark:hover:bg-primary-600"
              @click="install"
            >
              설치
            </button>
            <button
              class="rounded-lg px-4 py-1.5 text-sm font-medium text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
              @click="dismiss"
            >
              닫기
            </button>
          </div>
          <div v-else class="mt-3">
            <button
              class="rounded-lg px-4 py-1.5 text-sm font-medium text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-700"
              @click="dismiss"
            >
              닫기
            </button>
          </div>
        </div>

        <button
          class="shrink-0 text-gray-400 hover:text-gray-600 dark:text-gray-500 dark:hover:text-gray-300"
          aria-label="닫기"
          @click="dismiss"
        >
          <XMarkIcon class="h-5 w-5" />
        </button>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import {
  DevicePhoneMobileIcon,
  XMarkIcon,
  ArrowUpOnSquareIcon,
} from '@heroicons/vue/24/outline'

const DISMISS_KEY = 'pwa-install-dismissed'

const showBanner = ref(false)
const deferredPrompt = ref<BeforeInstallPromptEvent | null>(null)

const isIos = ref(false)

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>
}

function checkIos(): boolean {
  const ua = navigator.userAgent
  return /iPad|iPhone|iPod/.test(ua) && !(window as unknown as { MSStream?: unknown }).MSStream
}

function isStandalone(): boolean {
  return (
    window.matchMedia('(display-mode: standalone)').matches ||
    (navigator as unknown as { standalone?: boolean }).standalone === true
  )
}

function isDismissed(): boolean {
  return localStorage.getItem(DISMISS_KEY) === 'true'
}

function handleBeforeInstallPrompt(e: Event) {
  e.preventDefault()
  deferredPrompt.value = e as BeforeInstallPromptEvent

  if (!isDismissed() && !isStandalone()) {
    showBanner.value = true
  }
}

async function install() {
  if (!deferredPrompt.value) return

  await deferredPrompt.value.prompt()
  const { outcome } = await deferredPrompt.value.userChoice

  if (outcome === 'accepted') {
    showBanner.value = false
  }
  deferredPrompt.value = null
}

function dismiss() {
  showBanner.value = false
  localStorage.setItem(DISMISS_KEY, 'true')
}

onMounted(() => {
  isIos.value = checkIos()

  if (isStandalone() || isDismissed()) return

  // iOS Safari — no beforeinstallprompt, show manual guide
  if (isIos.value) {
    const isSafari = /Safari/.test(navigator.userAgent) && !/CriOS|FxiOS/.test(navigator.userAgent)
    if (isSafari) {
      showBanner.value = true
    }
    return
  }

  window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt)
})

onUnmounted(() => {
  window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt)
})
</script>

<style scoped>
.slide-up-enter-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-up-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 1, 1);
}

.slide-up-enter-from {
  transform: translateY(100%);
  opacity: 0;
}

.slide-up-leave-to {
  transform: translateY(100%);
  opacity: 0;
}
</style>
