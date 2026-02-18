<template>
  <div class="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900">
    <div class="w-full max-w-sm text-center">
      <!-- Loading -->
      <div v-if="isProcessing" class="space-y-4">
        <div class="mx-auto h-12 w-12 animate-spin rounded-full border-4 border-primary-200 border-t-primary-600" />
        <p class="text-gray-600 dark:text-gray-300">채널을 연결하고 있습니다…</p>
      </div>

      <!-- Error -->
      <div v-else-if="errorMessage" class="space-y-4">
        <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-red-100 dark:bg-red-900/30">
          <svg class="h-8 w-8 text-red-600 dark:text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </div>
        <p class="text-red-600 dark:text-red-400">{{ errorMessage }}</p>
        <button class="btn-primary mt-4" @click="goBack">돌아가기</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { channelApi } from '@/api/channel'
import { getOAuthRedirectUri } from '@/utils/oauth'
import type { Platform } from '@/types/channel'

const router = useRouter()
const route = useRoute()

const isProcessing = ref(true)
const errorMessage = ref('')
let returnPath = '/channels'

function goBack() {
  router.replace(returnPath)
}

onMounted(async () => {
  const code = route.query.code as string | undefined
  const state = route.query.state as string | undefined

  if (!code || !state) {
    errorMessage.value = '잘못된 콜백 요청입니다. 인가 코드 또는 상태 정보가 누락되었습니다.'
    isProcessing.value = false
    return
  }

  // Parse state: "PLATFORM|/return/path"
  const [platformStr, ...pathParts] = state.split('|')
  const platform = platformStr as Platform
  returnPath = pathParts.join('|') || '/channels'

  try {
    await channelApi.connect(platform, {
      authorizationCode: code,
      redirectUri: getOAuthRedirectUri(),
    })
    router.replace(returnPath)
  } catch {
    errorMessage.value = '채널 연동에 실패했습니다. 다시 시도해주세요.'
    isProcessing.value = false
  }
})
</script>
