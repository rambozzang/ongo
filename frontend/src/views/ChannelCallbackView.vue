<template>
  <div class="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900">
    <div class="w-full max-w-sm text-center">
      <!-- Loading -->
      <div v-if="isProcessing" class="space-y-4">
        <div class="mx-auto h-12 w-12 animate-spin rounded-full border-4 border-primary-200 border-t-primary-600" />
        <p class="text-body text-gray-600 dark:text-gray-300">{{ $t('channelCallbackView.connecting') }}</p>
      </div>

      <!-- Error -->
      <div v-else-if="errorMessage" class="space-y-4">
        <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-error-subtle">
          <svg class="h-8 w-8 text-error-strong" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </div>
        <p class="text-body text-error-strong">{{ errorMessage }}</p>

        <!--
          플랜 한도로 거절된 경우에만 업그레이드 경로를 연다.
          사용자는 OAuth 동의까지 마치고 온 상태라, 사유만 보여주고 끝내면
          다음에 무엇을 해야 하는지 알 방법이 없다.
        -->
        <div
          v-if="isPlanLimit"
          class="rounded-lg border border-warning bg-warning-subtle p-4 text-left"
        >
          <p class="text-body-sm text-warning-strong">{{ $t('channelCallbackView.planLimitHint') }}</p>
          <router-link :to="PLAN_UPGRADE_PATH" class="btn-primary mt-3 inline-flex">
            {{ $t('channelCallbackView.planLimitCta') }}
          </router-link>
        </div>

        <button class="btn-primary mt-4" @click="goBack">{{ $t('channelCallbackView.goBack') }}</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import { channelApi } from '@/api/channel'
import { getOAuthRedirectUri } from '@/utils/oauth'
import { PLAN_LIMIT_EXCEEDED, PLAN_UPGRADE_PATH, matchesCode } from '@/composables/usePlanLimit'
import type { Platform } from '@/types/channel'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()

const isProcessing = ref(true)
const errorMessage = ref('')
const isPlanLimit = ref(false)
let returnPath = '/channels'

function goBack() {
  router.replace(returnPath)
}

onMounted(async () => {
  // Vue Router query values can be arrays when a parameter is repeated. Do
  // not cast those values to string and call string methods on attacker-
  // controlled callback input.
  const code = typeof route.query.code === 'string' ? route.query.code : undefined
  const state = typeof route.query.state === 'string' ? route.query.state : undefined

  if (!code || !state) {
    errorMessage.value = t('channelCallbackView.invalidCallback')
    isProcessing.value = false
    return
  }

  // Parse state: "PLATFORM|/return/path|nonce". The nonce prevents forged callbacks.
  const [platformStr, pathPart, nonce, mode] = state.split('|')
  const platform = platformStr as Platform
  returnPath = pathPart && pathPart.startsWith('/') && !pathPart.startsWith('//') ? pathPart : '/channels'

  const supportedPlatforms: Platform[] = [
    'YOUTUBE', 'TIKTOK', 'INSTAGRAM', 'TWITTER', 'FACEBOOK', 'THREADS',
    'PINTEREST', 'LINKEDIN', 'WORDPRESS', 'TUMBLR', 'VIMEO', 'DAILYMOTION',
  ]
  const expectedNonce = sessionStorage.getItem('channel_oauth_state_nonce')
  if (!supportedPlatforms.includes(platform) || !nonce || !expectedNonce || nonce !== expectedNonce) {
    errorMessage.value = t('channelCallbackView.invalidCallback')
    isProcessing.value = false
    sessionStorage.removeItem('channel_oauth_state_nonce')
    return
  }

  try {
    const request: import('@/types/channel').ChannelConnectRequest = {
      authorizationCode: code,
      redirectUri: getOAuthRedirectUri(),
      addAsNew: mode === 'new',
    }
    if (platform === 'TWITTER') {
      request.codeVerifier = sessionStorage.getItem('twitter_code_verifier') || undefined
    }
    await channelApi.connect(platform, request)
    router.replace(returnPath)
  } catch (error) {
    // 이 화면의 안내 문구는 '채널을 더 연결하려면…' 이다. 저장 공간 같은 다른 한도까지
    // 받아들이면 사유와 안내가 어긋나므로 채널 한도 코드만 본다.
    isPlanLimit.value = matchesCode(error, PLAN_LIMIT_EXCEEDED)
    errorMessage.value = error instanceof Error && error.message
      ? error.message
      : t('channelCallbackView.connectError')
    isProcessing.value = false
  } finally {
    sessionStorage.removeItem('twitter_code_verifier')
    sessionStorage.removeItem('channel_oauth_state_nonce')
  }
})
</script>
