<template>
  <div class="min-h-full max-w-xl space-y-5 py-8 text-content">
    <div v-if="loading" class="py-16 text-center text-body text-gray-400">{{ $t('action.loading') }}</div>

    <div v-else-if="error" class="card py-12 text-center">
      <p class="text-body text-error-strong">{{ error }}</p>
      <button class="btn-secondary mt-4" @click="router.push('/creator/campaigns')">{{ $t('ugc.myCampaigns') }}</button>
    </div>

    <div v-else-if="campaign" class="card space-y-5">
      <div>
        <span class="rounded-full bg-primary-50 px-2.5 py-0.5 text-caption text-primary-700 dark:bg-primary-900/30 dark:text-primary-300">
          {{ $t('ugc.inviteBadge') }}
        </span>
        <h1 class="mt-2 text-h1 font-bold text-gray-900 dark:text-gray-100">{{ campaign.name }}</h1>
        <p v-if="campaign.description" class="mt-2 text-body text-gray-600 dark:text-gray-300">{{ campaign.description }}</p>
      </div>

      <dl class="grid grid-cols-2 gap-4 border-y border-gray-100 py-4 text-body dark:border-gray-700">
        <div>
          <dt class="text-body-xs text-gray-400">{{ $t('ugc.reward') }}</dt>
          <dd class="mt-0.5 font-semibold text-gray-900 dark:text-gray-100">{{ formatMoney(campaign.fixedRewardPerCreator, campaign.currency) }}</dd>
        </div>
        <div>
          <dt class="text-body-xs text-gray-400">{{ $t('ugc.period') }}</dt>
          <dd class="mt-0.5 font-medium text-gray-900 dark:text-gray-100">{{ formatPeriod(campaign.startAt, campaign.endAt) }}</dd>
        </div>
      </dl>

      <div v-if="campaign.playbookTitle">
        <h2 class="text-body font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.playbook') }}</h2>
        <p class="mt-1 font-medium text-gray-800 dark:text-gray-200">{{ campaign.playbookTitle }}</p>
        <p v-if="campaign.playbookSummary" class="mt-1 text-body text-gray-500 dark:text-gray-400">{{ campaign.playbookSummary }}</p>
      </div>

      <!-- Already applied -->
      <div v-if="campaign.alreadyApplied" class="rounded-lg bg-success-subtle p-4 text-center text-body text-success-strong">
        {{ $t('ugc.alreadyApplied') }}
      </div>

      <!-- Apply form -->
      <template v-else>
        <div>
          <label class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.applyMessage') }}</label>
          <textarea v-model="message" rows="3" class="input-field" :placeholder="$t('ugc.applyMessagePlaceholder')" />
        </div>
        <div>
          <label class="mb-1.5 block text-body font-medium text-gray-700 dark:text-gray-300">{{ $t('ugc.portfolioUrl') }}</label>
          <input v-model="portfolioUrl" type="url" class="input-field" placeholder="https://" />
        </div>
        <button class="btn-primary w-full" :disabled="applying" @click="submit">
          {{ applying ? $t('ugc.saving') : $t('ugc.applyNow') }}
        </button>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import { ugcParticipationApi, type PublicCampaignResponse } from '@/api/ugcParticipation'

const { t } = useI18n({ useScope: 'global' })
const route = useRoute()
const router = useRouter()
const notify = useNotificationStore()

const token = String(route.params.token)
const campaign = ref<PublicCampaignResponse | null>(null)
const loading = ref(true)
const error = ref('')
const applying = ref(false)
const message = ref('')
const portfolioUrl = ref('')

function formatMoney(amount: number, currency: string): string {
  return `${new Intl.NumberFormat('ko-KR').format(amount)} ${currency}`
}

function formatPeriod(startAt: string | null, endAt: string | null): string {
  if (!startAt || !endAt) return '-'
  return `${startAt.slice(0, 10)} ~ ${endAt.slice(0, 10)}`
}

async function submit() {
  applying.value = true
  try {
    await ugcParticipationApi.apply(token, {
      message: message.value.trim() || null,
      portfolioUrl: portfolioUrl.value.trim() || null,
    })
    notify.success(t('ugc.applyDone'))
    router.push('/creator/campaigns')
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.applyFailed'))
  } finally {
    applying.value = false
  }
}

onMounted(async () => {
  try {
    campaign.value = await ugcParticipationApi.viewInvite(token)
  } catch (e) {
    error.value = e instanceof Error ? e.message : t('ugc.inviteInvalid')
  } finally {
    loading.value = false
  }
})
</script>
