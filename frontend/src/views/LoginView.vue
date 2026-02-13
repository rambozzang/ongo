<template>
  <div class="flex min-h-screen">
    <!-- Left: Branding -->
    <div class="hidden flex-1 items-center justify-center bg-gradient-to-br from-primary-600 to-primary-800 tablet:flex">
      <div class="max-w-lg px-8 text-center text-white">
        <h1 class="mb-6 text-5xl font-bold tracking-tight">onGo</h1>
        <div class="mx-auto mb-8 flex h-48 w-48 items-center justify-center rounded-2xl bg-white/10 backdrop-blur-sm">
          <svg class="h-24 w-24 text-white/80" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 10.5l4.72-4.72a.75.75 0 011.28.53v11.38a.75.75 0 01-1.28.53l-4.72-4.72M4.5 18.75h9a2.25 2.25 0 002.25-2.25v-9a2.25 2.25 0 00-2.25-2.25h-9A2.25 2.25 0 002.25 7.5v9a2.25 2.25 0 002.25 2.25z" />
          </svg>
        </div>
        <p class="text-2xl font-semibold leading-relaxed">
          크리에이터의 시간을<br />되돌려주는 플랫폼
        </p>
        <p class="mt-4 text-base leading-relaxed text-white/75">
          영상 하나로, 모든 플랫폼에.<br />
          AI가 최적화하고, 한 번에 게시합니다.
        </p>
        <div class="mt-8 flex items-center justify-center gap-6 text-white/60">
          <div class="flex items-center gap-2">
            <span class="inline-block h-3 w-3 rounded-full bg-youtube"></span>
            <span class="text-sm">YouTube</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="inline-block h-3 w-3 rounded-full bg-tiktok"></span>
            <span class="text-sm">TikTok</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="inline-block h-3 w-3 rounded-full bg-instagram"></span>
            <span class="text-sm">Instagram</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="inline-block h-3 w-3 rounded-full bg-naver"></span>
            <span class="text-sm">Naver Clip</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Right: Login Form -->
    <div class="flex flex-1 items-center justify-center bg-white dark:bg-gray-800 p-8">
      <div class="w-full max-w-sm">
        <!-- Mobile logo -->
        <div class="mb-10 text-center tablet:hidden">
          <h1 class="text-4xl font-bold text-primary-600">onGo</h1>
          <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">크리에이터 멀티 플랫폼 관리</p>
        </div>

        <div class="mb-2 hidden tablet:block">
          <p class="text-sm font-medium text-primary-600">onGo</p>
        </div>
        <h2 class="mb-2 text-2xl font-bold text-gray-900 dark:text-gray-100">시작하기</h2>
        <p class="mb-8 text-sm text-gray-500 dark:text-gray-400">소셜 계정으로 간편하게 로그인하세요.</p>

        <div class="space-y-3">
          <!-- Google Login Button -->
          <button
            :disabled="isLoading"
            class="flex w-full items-center justify-center gap-3 rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-4 py-3.5 text-sm font-medium text-gray-700 dark:text-gray-300 shadow-sm transition-all hover:bg-gray-50 dark:hover:bg-gray-700 hover:shadow-md disabled:cursor-not-allowed disabled:opacity-50"
            @click="loginWithGoogle"
          >
            <svg class="h-5 w-5" viewBox="0 0 24 24">
              <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4" />
              <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853" />
              <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05" />
              <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335" />
            </svg>
            <span>Google 계정으로 계속하기</span>
          </button>

          <!-- Kakao Login Button -->
          <button
            :disabled="isLoading"
            class="flex w-full items-center justify-center gap-3 rounded-xl bg-[#FEE500] px-4 py-3.5 text-sm font-medium text-[#191919] shadow-sm transition-all hover:bg-[#FADA0A] hover:shadow-md disabled:cursor-not-allowed disabled:opacity-50"
            @click="loginWithKakao"
          >
            <svg class="h-5 w-5" viewBox="0 0 24 24" fill="#191919">
              <path d="M12 3C6.48 3 2 6.36 2 10.44c0 2.62 1.75 4.93 4.38 6.24l-1.12 4.1a.3.3 0 00.45.34l4.76-3.15c.49.06 1 .1 1.53.1 5.52 0 10-3.36 10-7.63S17.52 3 12 3z" />
            </svg>
            <span>카카오 계정으로 계속하기</span>
          </button>
        </div>

        <!-- Loading indicator -->
        <div v-if="isLoading" class="mt-4 text-center">
          <div class="inline-block h-5 w-5 animate-spin rounded-full border-2 border-primary-600 border-t-transparent"></div>
          <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">로그인 처리 중...</p>
        </div>

        <!-- Error message -->
        <div v-if="errorMessage" class="mt-4 rounded-lg bg-red-50 dark:bg-red-900/20 p-3 text-center text-sm text-red-600 dark:text-red-400">
          {{ errorMessage }}
        </div>

        <!-- Dev Login -->
        <div class="mt-6 text-center">
          <button
            :disabled="isLoading"
            class="text-sm font-medium text-primary-600 transition-colors hover:text-primary-700 hover:underline disabled:opacity-50"
            @click="devLogin"
          >
            개발자 로그인
          </button>
          <span class="mx-1 text-gray-300 dark:text-gray-600">|</span>
          <span class="text-xs text-gray-400 dark:text-gray-500">Admin 계정으로 테스트</span>
        </div>

        <!-- Terms -->
        <p class="mt-8 text-center text-xs leading-relaxed text-gray-400 dark:text-gray-500">
          가입 시
          <a href="#" class="text-gray-500 dark:text-gray-400 underline transition-colors hover:text-gray-700 dark:hover:text-gray-300">이용약관</a> 및
          <a href="#" class="text-gray-500 dark:text-gray-400 underline transition-colors hover:text-gray-700 dark:hover:text-gray-300">개인정보처리방침</a>에
          동의합니다.
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const isLoading = ref(false)
const errorMessage = ref('')

async function devLogin() {
  isLoading.value = true
  errorMessage.value = ''
  try {
    await authStore.devLogin()
  } catch (e: unknown) {
    errorMessage.value = e instanceof Error ? e.message : '개발자 로그인에 실패했습니다.'
    isLoading.value = false
  }
}

const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID
const KAKAO_CLIENT_ID = import.meta.env.VITE_KAKAO_CLIENT_ID
const REDIRECT_URI = `${window.location.origin}/auth/callback`

function loginWithGoogle() {
  isLoading.value = true
  errorMessage.value = ''
  const params = new URLSearchParams({
    client_id: GOOGLE_CLIENT_ID,
    redirect_uri: `${REDIRECT_URI}/google`,
    response_type: 'code',
    scope: 'openid email profile',
    access_type: 'offline',
    prompt: 'consent',
  })
  window.location.href = `https://accounts.google.com/o/oauth2/v2/auth?${params}`
}

function loginWithKakao() {
  isLoading.value = true
  errorMessage.value = ''
  const params = new URLSearchParams({
    client_id: KAKAO_CLIENT_ID,
    redirect_uri: `${REDIRECT_URI}/kakao`,
    response_type: 'code',
  })
  window.location.href = `https://kauth.kakao.com/oauth/authorize?${params}`
}

// Handle OAuth callback if code is present in URL
async function handleOAuthCallback() {
  const url = new URL(window.location.href)
  const code = url.searchParams.get('code')
  const path = url.pathname

  if (!code) return

  const provider = path.includes('google') ? 'google' : path.includes('kakao') ? 'kakao' : null
  if (!provider) return

  isLoading.value = true
  errorMessage.value = ''

  try {
    await authStore.login(provider, {
      code,
      redirectUri: `${REDIRECT_URI}/${provider}`,
    })
  } catch (e: unknown) {
    errorMessage.value = e instanceof Error ? e.message : '로그인에 실패했습니다. 다시 시도해주세요.'
    isLoading.value = false
  }
}

handleOAuthCallback()
</script>
