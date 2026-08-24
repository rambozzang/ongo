<template>
  <div class="flex min-h-screen">
    <!-- Left: Branding -->
    <div class="hidden flex-1 items-center justify-center bg-gradient-to-br from-primary-600 to-primary-800 tablet:flex">
      <div class="max-w-lg px-8 text-center text-white">
        <OnGoLogo size="lg" inverse class="mb-6" />
        <div class="mx-auto mb-8 flex h-48 w-48 items-center justify-center rounded-2xl bg-white/10 backdrop-blur-sm">
          <svg class="h-24 w-24 text-white/80" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 10.5l4.72-4.72a.75.75 0 011.28.53v11.38a.75.75 0 01-1.28.53l-4.72-4.72M4.5 18.75h9a2.25 2.25 0 002.25-2.25v-9a2.25 2.25 0 00-2.25-2.25h-9A2.25 2.25 0 002.25 7.5v9a2.25 2.25 0 002.25 2.25z" />
          </svg>
        </div>
        <p class="whitespace-pre-line text-h1 font-semibold leading-relaxed">
          {{ $t('loginView.heroTitle') }}
        </p>
        <p class="mt-4 whitespace-pre-line text-body-lg leading-relaxed text-white/75">
          {{ $t('loginView.heroSubtitle') }}
        </p>
        <div class="mt-8 flex items-center justify-center gap-6 text-white/60">
          <div class="flex items-center gap-2">
            <span class="inline-block h-3 w-3 rounded-full bg-youtube"></span>
            <span class="text-body">YouTube</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="inline-block h-3 w-3 rounded-full bg-tiktok"></span>
            <span class="text-body">TikTok</span>
          </div>
          <div class="flex items-center gap-2">
            <span class="inline-block h-3 w-3 rounded-full bg-instagram"></span>
            <span class="text-body">Instagram</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Right: Login Form -->
    <div class="flex flex-1 items-center justify-center bg-white dark:bg-gray-800 p-8">
      <div class="w-full max-w-sm">
        <!-- Mobile logo.
             좌측 히어로가 tablet 미만에서 렌더되지 않으므로, 가치 제안을 여기서
             한 번 더 말한다. 이게 없으면 모바일 방문자가 보는 설명은 한 줄뿐이다. -->
        <div class="mb-8 text-center tablet:hidden">
          <OnGoLogo size="lg" />
          <p class="mt-2 text-body text-gray-500 dark:text-gray-400">{{ $t('app.description') }}</p>
          <p class="mt-3 whitespace-pre-line text-body-sm leading-relaxed text-gray-500 dark:text-gray-400">
            {{ $t('loginView.heroSubtitle') }}
          </p>
        </div>

        <div class="mb-2 hidden tablet:block">
          <OnGoLogo size="sm" />
        </div>
        <h2 class="mb-2 text-h1 font-bold text-gray-900 dark:text-gray-100">{{ $t('loginView.title') }}</h2>
        <p class="mb-8 text-body text-gray-500 dark:text-gray-400">{{ $t('loginView.subtitle') }}</p>

        <div class="space-y-3">
          <!-- Google Login Button -->
          <button
            :disabled="isLoading"
            class="flex w-full items-center justify-center gap-3 rounded-xl border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-4 py-3.5 text-body font-medium text-gray-700 dark:text-gray-300 shadow-sm transition-all hover:bg-gray-50 dark:hover:bg-gray-700 hover:shadow-md disabled:cursor-not-allowed disabled:opacity-50"
            @click="loginWithGoogle"
          >
            <svg class="h-5 w-5" viewBox="0 0 24 24">
              <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z" fill="#4285F4" />
              <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853" />
              <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05" />
              <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335" />
            </svg>
            <span>{{ $t('loginView.continueWithGoogle') }}</span>
          </button>

          <!-- Kakao Login Button -->
          <button
            :disabled="isLoading"
            class="flex w-full items-center justify-center gap-3 rounded-xl bg-[#FEE500] px-4 py-3.5 text-body font-medium text-[#191919] shadow-sm transition-all hover:bg-[#FADA0A] hover:shadow-md disabled:cursor-not-allowed disabled:opacity-50"
            @click="loginWithKakao"
          >
            <svg class="h-5 w-5" viewBox="0 0 24 24" fill="#191919">
              <path d="M12 3C6.48 3 2 6.36 2 10.44c0 2.62 1.75 4.93 4.38 6.24l-1.12 4.1a.3.3 0 00.45.34l4.76-3.15c.49.06 1 .1 1.53.1 5.52 0 10-3.36 10-7.63S17.52 3 12 3z" />
            </svg>
            <span>{{ $t('loginView.continueWithKakao') }}</span>
          </button>
        </div>

        <!-- Loading indicator -->
        <div v-if="isLoading" class="mt-4 text-center">
          <div class="inline-block h-5 w-5 animate-spin rounded-full border-2 border-primary-600 border-t-transparent"></div>
          <p class="mt-1 text-body-xs text-gray-400 dark:text-gray-500">{{ $t('loginView.processing') }}</p>
        </div>

        <!-- Session expired message -->
        <div v-if="sessionExpired" class="mt-4 rounded-lg bg-warning-subtle p-3 text-center text-body text-warning-strong">
          {{ $t('loginView.sessionExpired') }}
        </div>

        <!-- Error message -->
        <div v-if="errorMessage" class="mt-4 rounded-lg bg-error-subtle p-3 text-center text-body text-error-strong">
          {{ errorMessage }}
        </div>

        <!-- 개발 빌드에서만 노출. 운영 번들에서는 DOM 자체가 제거된다. -->
        <div v-if="isDevBuild" class="mt-6 text-center">
          <button
            :disabled="isLoading"
            class="text-body font-medium text-primary-600 transition-colors hover:text-primary-700 hover:underline disabled:opacity-50"
            @click="devLogin"
          >
            {{ $t('loginView.devLogin') }}
          </button>
          <span class="mx-1 text-gray-300 dark:text-gray-600">|</span>
          <span class="text-body-xs text-gray-400 dark:text-gray-500">{{ $t('loginView.devLoginHint') }}</span>
        </div>

        <!-- 공개 요금 안내.
             로그인 전에는 가격을 볼 수 있는 화면이 없어, 방문자가 얼마인지 모르는 채
             소셜 계정 접근 권한을 먼저 내줘야 했다. CTA **아래**에 두어 초기 화면에서
             로그인 버튼이 접히지 않게 하고, 개인 크리에이터가 실제로 고르는 3개만 보여준다.
             금액은 PLANS 상수를 그대로 쓴다(별도 하드코딩 금지). -->
        <section
          :aria-label="$t('subscription.planComparison')"
          class="mt-8 border-t border-gray-200 pt-6 dark:border-gray-700"
        >
          <h3 class="mb-3 text-body-xs font-semibold uppercase tracking-wide text-gray-400 dark:text-gray-500">
            {{ $t('subscription.planComparison') }}
          </h3>
          <ul class="grid grid-cols-3 gap-2">
            <li
              v-for="plan in publicPlans"
              :key="plan.type"
              class="rounded-lg border border-gray-200 p-2.5 dark:border-gray-700"
            >
              <p class="text-body-xs font-semibold text-gray-900 dark:text-gray-100">{{ plan.name }}</p>
              <p class="mt-0.5 text-body-xs font-bold text-primary-600 dark:text-primary-400">
                {{ priceLabel(plan) }}
              </p>
              <p class="mt-1.5 text-[11px] leading-tight text-gray-500 dark:text-gray-400">
                {{ $t('subscription.connectedChannels') }} {{ plan.maxPlatforms }}
              </p>
              <p class="text-[11px] leading-tight text-gray-500 dark:text-gray-400">
                {{ $t('subscription.monthlyUploads') }} {{ plan.maxUploadsPerMonth }}
              </p>
            </li>
          </ul>
        </section>

        <!-- Terms -->
        <i18n-t
          keypath="loginView.terms"
          tag="p"
          scope="global"
          class="mt-8 text-center text-body-xs leading-relaxed text-gray-400 dark:text-gray-500"
        >
          <template #terms>
            <RouterLink to="/terms" class="text-gray-500 dark:text-gray-400 underline transition-colors hover:text-gray-700 dark:hover:text-gray-300">{{ $t('loginView.termsOfService') }}</RouterLink>
          </template>
          <template #privacy>
            <RouterLink to="/privacy" class="text-gray-500 dark:text-gray-400 underline transition-colors hover:text-gray-700 dark:hover:text-gray-300">{{ $t('loginView.privacyPolicy') }}</RouterLink>
          </template>
        </i18n-t>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/auth'
import OnGoLogo from '@/components/brand/OnGoLogo.vue'
import { loginErrorMessage } from '@/utils/loginError'
import { PLANS, type Plan, type PlanType } from '@/types/subscription'

const { t, locale } = useI18n()

/**
 * 로그인 전 공개 요금 안내에 쓸 플랜.
 *
 * Business 는 제외한다. 로그인 화면의 대상은 개인 크리에이터이고, 4개를 나열하면
 * max-w-sm 컬럼에서 카드가 읽을 수 없을 만큼 좁아진다.
 */
const PUBLIC_PLAN_TYPES: PlanType[] = ['FREE', 'STARTER', 'PRO']
const publicPlans = computed(() => PLANS.filter((plan) => PUBLIC_PLAN_TYPES.includes(plan.type)))

/**
 * 통화 표기는 Intl 에 맡긴다. 로케일 파일을 건드리지 않고도 ko/en 양쪽에서
 * ₩9,900 형태가 나오며, 금액 자체는 PLANS 를 그대로 쓴다.
 */
function priceLabel(plan: Plan): string {
  if (plan.price === 0) return t('subscription.statusFree')
  const amount = new Intl.NumberFormat(locale.value, {
    style: 'currency',
    currency: 'KRW',
    maximumFractionDigits: 0,
  }).format(plan.price)
  return `${amount}${t('subscription.perMonth')}`
}
const authStore = useAuthStore()
const isLoading = ref(false)
const errorMessage = ref('')
const sessionExpired = ref(false)
const isDevBuild = import.meta.env.DEV

// 세션 만료로 리다이렉트된 경우 안내 메시지 표시
if (sessionStorage.getItem('sessionExpired')) {
  sessionExpired.value = true
  sessionStorage.removeItem('sessionExpired')
}

async function devLogin() {
  isLoading.value = true
  errorMessage.value = ''
  try {
    await authStore.devLogin()
  } catch (e: unknown) {
    errorMessage.value = loginErrorMessage(e, t('loginView.devLoginError'))
    isLoading.value = false
  }
}

const REDIRECT_URI = `${window.location.origin}/auth/callback`

async function startSocialLogin(provider: 'google' | 'kakao') {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const { authorizationUrl, state } = await authApi.authorizationUrl(provider, `${REDIRECT_URI}/${provider}`)
    sessionStorage.setItem(`oauth_state:${provider}`, state)
    window.location.href = authorizationUrl
  } catch (e: unknown) {
    errorMessage.value = loginErrorMessage(e, t('loginView.oauthPrepareError'))
    isLoading.value = false
  }
}

function loginWithGoogle() {
  return startSocialLogin('google')
}

function loginWithKakao() {
  return startSocialLogin('kakao')
}

// Handle OAuth callback if code is present in URL
async function handleOAuthCallback() {
  const url = new URL(window.location.href)
  const code = url.searchParams.get('code')
  const state = url.searchParams.get('state')
  const path = url.pathname
  const provider = path.includes('google') ? 'google' : path.includes('kakao') ? 'kakao' : null
  const providerError = url.searchParams.get('error')

  if (!provider) return

  if (providerError) {
    errorMessage.value = providerError === 'access_denied'
      ? t('loginView.oauthCancelled')
      : t('loginView.loginError')
    return
  }

  if (!code) return

  // A state is one-time and browser-session bound. Check it before sending the
  // code to the API so a refreshed/old callback cannot produce a confusing
  // server error (and so two provider tabs do not overwrite each other).
  const expectedState = sessionStorage.getItem(`oauth_state:${provider}`)
  const clearCallbackUrl = () => {
    window.history.replaceState({}, document.title, '/login')
  }
  if (!state || !expectedState || state !== expectedState) {
    errorMessage.value = t('loginView.invalidOAuthState')
    clearCallbackUrl()
    return
  }

  sessionStorage.removeItem(`oauth_state:${provider}`)

  isLoading.value = true
  errorMessage.value = ''

  try {
    await authStore.login(provider, {
      code,
      redirectUri: `${REDIRECT_URI}/${provider}`,
      state: state ?? '',
    })
  } catch (e: unknown) {
    errorMessage.value = loginErrorMessage(e, t('loginView.loginError'))
    isLoading.value = false
    // The backend consumes state before exchanging the provider code. Remove
    // the callback parameters after any failure so reload cannot replay them.
    clearCallbackUrl()
  }
}

handleOAuthCallback()
</script>
