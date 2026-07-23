<template>
  <div class="max-w-3xl">
    <button class="mb-4 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 dark:text-gray-400" @click="router.push('/ugc/campaigns')">
      <ChevronLeftIcon class="h-4 w-4" />{{ $t('ugc.backToList') }}
    </button>

    <div v-if="loading" class="py-16 text-center text-sm text-gray-400">{{ $t('action.loading') }}</div>

    <template v-else-if="campaign">
      <PageHeader :title="campaign.name" :description="campaign.description || $t('ugc.noDescription')">
        <template #title-suffix>
          <span :class="['rounded-full px-2.5 py-0.5 text-xs font-medium', statusClass(campaign.status)]">
            {{ $t(`ugc.status.${campaign.status}`) }}
          </span>
        </template>
        <template #actions>
          <button v-if="campaign.status !== 'DRAFT'" class="btn-secondary" @click="router.push(`/ugc/campaigns/${campaign.id}/applications`)">
            {{ $t('ugc.manageApplications') }}
          </button>
          <button v-if="campaign.status !== 'DRAFT'" class="btn-secondary" @click="router.push(`/ugc/campaigns/${campaign.id}/submissions`)">
            {{ $t('ugc.reviewSubmissions') }}
          </button>
          <button v-if="campaign.status !== 'DRAFT'" class="btn-secondary" @click="router.push(`/ugc/campaigns/${campaign.id}/rewards`)">
            {{ $t('ugc.rewardsSection') }}
          </button>
          <button v-if="campaign.status === 'DRAFT'" class="btn-secondary" @click="router.push(`/ugc/campaigns/${campaign.id}/edit`)">
            {{ $t('ugc.edit') }}
          </button>
          <button v-if="campaign.status === 'DRAFT'" class="btn-primary" @click="ask('publish')">
            {{ $t('ugc.publish') }}
          </button>
          <button v-if="campaign.status === 'RECRUITING' || campaign.status === 'ACTIVE'" class="btn-secondary" @click="ask('pause')">
            {{ $t('ugc.pause') }}
          </button>
          <button v-if="campaign.status === 'RECRUITING' || campaign.status === 'ACTIVE' || campaign.status === 'PAUSED'" class="btn-primary" @click="ask('complete')">
            {{ $t('ugc.complete') }}
          </button>
        </template>
      </PageHeader>

      <!-- Meta -->
      <div class="page-grid page-grid--metrics card mb-4">
        <div>
          <p class="text-xs text-gray-400">{{ $t('ugc.fieldObjective') }}</p>
          <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ campaign.objective }}</p>
        </div>
        <div>
          <p class="text-xs text-gray-400">{{ $t('ugc.budget') }}</p>
          <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ formatMoney(campaign.totalBudget, campaign.currency) }}</p>
        </div>
        <div>
          <p class="text-xs text-gray-400">{{ $t('ugc.reward') }}</p>
          <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ formatMoney(campaign.fixedRewardPerCreator, campaign.currency) }}</p>
        </div>
        <div>
          <p class="text-xs text-gray-400">{{ $t('ugc.period') }}</p>
          <p class="mt-0.5 text-sm font-medium text-gray-900 dark:text-gray-100">{{ formatPeriod(campaign.startAt, campaign.endAt) }}</p>
        </div>
      </div>

      <!-- Playbook -->
      <div class="card">
        <div class="mb-3 flex items-center justify-between">
          <h3 class="font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.playbook') }}</h3>
          <button v-if="!isTerminal" class="btn-secondary text-xs" @click="router.push(`/ugc/campaigns/${campaign.id}/edit`)">
            {{ $t('ugc.edit') }}
          </button>
        </div>
        <div v-if="playbook">
          <p class="font-medium text-gray-900 dark:text-gray-100">{{ playbook.title }}</p>
          <p v-if="playbook.summary" class="mt-1 text-sm text-gray-500 dark:text-gray-400">{{ playbook.summary }}</p>
          <span class="mt-2 inline-block rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-300">{{ playbook.contentType }}</span>
          <ol v-if="playbook.steps.length" class="mt-3 space-y-2">
            <li v-for="(s, i) in playbook.steps" :key="i" class="rounded-lg border border-gray-100 p-3 text-sm dark:border-gray-700">
              <span class="font-medium text-gray-900 dark:text-gray-100">{{ i + 1 }}. {{ s.title }}</span>
              <p v-if="s.instruction" class="mt-1 text-gray-500 dark:text-gray-400">{{ s.instruction }}</p>
            </li>
          </ol>
        </div>
        <p v-else class="text-sm text-gray-400">{{ $t('ugc.noPlaybook') }}</p>
      </div>
    </template>

    <ConfirmModal
      v-model="confirmOpen"
      :title="confirmTitle"
      :message="confirmMessage"
      @confirm="runPending"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useUgcCampaignStore } from '@/stores/ugcCampaign'
import { useNotificationStore } from '@/stores/notification'
import type { CampaignStatus } from '@/api/ugcCampaign'
import PageHeader from '@/components/common/PageHeader.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import { ChevronLeftIcon } from '@heroicons/vue/24/outline'

type PendingAction = 'publish' | 'pause' | 'complete'

const { t } = useI18n({ useScope: 'global' })
const route = useRoute()
const router = useRouter()
const store = useUgcCampaignStore()
const notify = useNotificationStore()

const loading = ref(true)
const confirmOpen = ref(false)
const pending = ref<PendingAction | null>(null)

const campaignId = computed(() => Number(route.params.id))
const campaign = computed(() => store.current?.campaign ?? null)
const playbook = computed(() => store.current?.playbook ?? null)
const isTerminal = computed(() => campaign.value?.status === 'COMPLETED' || campaign.value?.status === 'CANCELLED')

const confirmTitle = computed(() => (pending.value ? t(`ugc.${pending.value}`) : ''))
const confirmMessage = computed(() => (pending.value ? t(`ugc.confirm_${pending.value}`) : ''))

function statusClass(status: CampaignStatus): string {
  switch (status) {
    case 'RECRUITING':
      return 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
    case 'ACTIVE':
      return 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-300'
    case 'PAUSED':
      return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300'
    case 'CANCELLED':
      return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
  }
}

function formatMoney(amount: number, currency: string): string {
  return `${new Intl.NumberFormat('ko-KR').format(amount)} ${currency}`
}

function formatPeriod(startAt: string | null, endAt: string | null): string {
  if (!startAt || !endAt) return '-'
  return `${startAt.slice(0, 10)} ~ ${endAt.slice(0, 10)}`
}

function ask(action: PendingAction) {
  pending.value = action
  confirmOpen.value = true
}

function successKey(action: PendingAction): string {
  if (action === 'publish') return 'ugc.published'
  if (action === 'pause') return 'ugc.paused'
  return 'ugc.completedMsg'
}

async function runPending() {
  const action = pending.value
  if (!action) return
  try {
    if (action === 'publish') await store.publish(campaignId.value)
    else if (action === 'pause') await store.pause(campaignId.value)
    else await store.complete(campaignId.value)
    notify.success(t(successKey(action)))
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.actionFailed'))
  } finally {
    pending.value = null
  }
}

onMounted(async () => {
  try {
    await store.fetchCampaign(campaignId.value)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.loadFailed'))
  } finally {
    loading.value = false
  }
})
</script>
