<template>
  <main class="flex min-h-screen items-center justify-center bg-surface-base px-4 py-8 text-content">
    <section class="w-full max-w-[460px] rounded-2xl border border-line bg-surface-card p-6 shadow-sm tablet:p-8">
      <div v-if="loading" class="space-y-3" aria-live="polite">
        <div class="h-6 w-40 animate-pulse rounded bg-surface-raised" />
        <div class="h-4 w-full animate-pulse rounded bg-surface-raised" />
        <div class="h-24 rounded-xl bg-surface-raised" />
      </div>

      <div v-else-if="errorMessage" class="space-y-4" role="alert">
        <p class="text-sm font-bold text-error-strong">{{ errorMessage }}</p>
        <RouterLink to="/today" class="btn-secondary inline-flex min-h-11 items-center">{{ t('oauthAuthorize.back') }}</RouterLink>
      </div>

      <template v-else-if="authorization">
        <div class="flex items-center gap-3">
          <img v-if="authorization.profilePictureUrl" :src="authorization.profilePictureUrl" :alt="authorization.name" class="h-12 w-12 rounded-xl object-cover">
          <div v-else class="flex h-12 w-12 items-center justify-center rounded-xl bg-accent-dim text-lg font-bold text-accent">on</div>
          <div class="min-w-0">
            <p class="text-[10px] font-semibold uppercase tracking-[0.14em] text-content-tertiary">onGo OAuth</p>
            <h1 class="truncate text-lg font-bold">{{ authorization.name }}</h1>
          </div>
        </div>

        <div class="mt-6 rounded-xl border border-line bg-surface-input p-4">
          <h2 class="text-sm font-bold">{{ t('oauthAuthorize.title') }}</h2>
          <p class="mt-2 text-xs leading-5 text-content-secondary">{{ authorization.description || t('oauthAuthorize.noDescription') }}</p>
          <p class="mt-3 text-[11px] leading-5 text-content-tertiary">{{ t('oauthAuthorize.permission') }}</p>
        </div>

        <p class="mt-4 break-all text-[11px] text-content-tertiary">
          {{ t('oauthAuthorize.redirect') }} <span class="font-mono">{{ authorization.redirectUri }}</span>
        </p>

        <div class="mt-6 grid gap-2 tablet:grid-cols-2">
          <button type="button" class="btn-secondary min-h-11" :disabled="submitting" @click="decide(false)">{{ t('oauthAuthorize.deny') }}</button>
          <button type="button" class="btn-primary min-h-11" :disabled="submitting" @click="decide(true)">{{ submitting ? t('action.loading') : t('oauthAuthorize.allow') }}</button>
        </div>
      </template>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { oauthApi, type PublicOAuthAuthorizationRequest } from '@/api/oauth'

const { t } = useI18n()
const route = useRoute()
const authorization = ref<PublicOAuthAuthorizationRequest | null>(null)
const loading = ref(true)
const submitting = ref(false)
const errorMessage = ref<string | null>(null)
const clientId = typeof route.query.client_id === 'string' ? route.query.client_id : ''
const responseType = typeof route.query.response_type === 'string' ? route.query.response_type : 'code'
const state = typeof route.query.state === 'string' ? route.query.state : undefined

onMounted(async () => {
  if (!clientId) {
    errorMessage.value = t('oauthAuthorize.invalidRequest')
    loading.value = false
    return
  }
  try {
    authorization.value = await oauthApi.getAuthorizationRequest({ clientId, responseType })
  } catch {
    errorMessage.value = t('oauthAuthorize.loadFailed')
  } finally {
    loading.value = false
  }
})

async function decide(approved: boolean) {
  submitting.value = true
  try {
    const result = await oauthApi.decideAuthorization({ clientId, responseType, state, approved })
    window.location.assign(result.redirectUrl)
  } catch {
    errorMessage.value = t('oauthAuthorize.decisionFailed')
    submitting.value = false
  }
}
</script>
