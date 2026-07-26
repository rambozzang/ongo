<template>
  <div class="max-w-4xl">
    <button class="mb-4 inline-flex items-center gap-1 text-body text-gray-500 hover:text-gray-700 dark:text-gray-400" @click="router.push(`/ugc/campaigns/${campaignId}`)">
      <ChevronLeftIcon class="h-4 w-4" />{{ $t('ugc.backToCampaign') }}
    </button>

    <PageHeader :title="$t('ugc.rewardsTitle')" :description="$t('ugc.rewardsDescription')">
      <template #actions>
        <button class="btn-secondary inline-flex items-center gap-2" @click="downloadCsv">
          <ArrowDownTrayIcon class="h-5 w-5" />{{ $t('ugc.exportCsv') }}
        </button>
      </template>
    </PageHeader>

    <div v-if="loading" class="py-16 text-center text-body text-gray-400">{{ $t('action.loading') }}</div>

    <template v-else>
      <!-- Analytics summary -->
      <div class="card mb-4">
        <div class="mb-2 flex items-center justify-between">
          <h3 class="text-body font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.performance') }}</h3>
          <span class="text-body-xs text-gray-400">{{ $t('ugc.lastSynced') }}: {{ analytics?.lastSyncedAt?.slice(0, 16)?.replace('T', ' ') || '-' }}</span>
        </div>
        <div class="grid grid-cols-2 gap-4 mobile:grid-cols-4">
          <div><p class="text-body-xs text-gray-400">{{ $t('ugc.views') }}</p><p class="mt-0.5 text-title font-semibold text-gray-900 dark:text-gray-100">{{ (analytics?.totalViews ?? 0).toLocaleString() }}</p></div>
          <div><p class="text-body-xs text-gray-400">{{ $t('ugc.likes') }}</p><p class="mt-0.5 text-title font-semibold text-gray-900 dark:text-gray-100">{{ (analytics?.totalLikes ?? 0).toLocaleString() }}</p></div>
          <div><p class="text-body-xs text-gray-400">{{ $t('ugc.comments') }}</p><p class="mt-0.5 text-title font-semibold text-gray-900 dark:text-gray-100">{{ (analytics?.totalComments ?? 0).toLocaleString() }}</p></div>
          <div><p class="text-body-xs text-gray-400">{{ $t('ugc.shares') }}</p><p class="mt-0.5 text-title font-semibold text-gray-900 dark:text-gray-100">{{ (analytics?.totalShares ?? 0).toLocaleString() }}</p></div>
        </div>
      </div>

      <!-- Budget -->
      <div class="card mb-4 grid grid-cols-3 gap-4 text-center">
        <div><p class="text-body-xs text-gray-400">{{ $t('ugc.budget') }}</p><p class="mt-0.5 font-semibold text-gray-900 dark:text-gray-100">{{ (rewards?.totalBudget ?? 0).toLocaleString() }}</p></div>
        <div><p class="text-body-xs text-gray-400">{{ $t('ugc.confirmed') }}</p><p class="mt-0.5 font-semibold text-gray-900 dark:text-gray-100">{{ (rewards?.settledTotal ?? 0).toLocaleString() }}</p></div>
        <div><p class="text-body-xs text-gray-400">{{ $t('ugc.remaining') }}</p><p :class="['mt-0.5 font-semibold', (rewards?.remaining ?? 0) < 0 ? 'text-error-strong' : 'text-gray-900 dark:text-gray-100']">{{ (rewards?.remaining ?? 0).toLocaleString() }}</p></div>
      </div>

      <!-- Participants rewards -->
      <div v-if="!rewards || rewards.items.length === 0" class="card py-16 text-center text-body text-gray-500 dark:text-gray-400">
        {{ $t('ugc.noParticipants') }}
      </div>
      <div v-else class="space-y-3">
        <div v-for="item in rewards.items" :key="item.participantId" class="card">
          <div class="flex items-center justify-between gap-2">
            <span class="font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.creator') }} #{{ item.creatorId }}</span>
            <span :class="['rounded-full px-2 py-0.5 text-caption', rewardStatusClass(item.status)]">{{ $t(`ugc.rewardStatus.${item.status}`) }}</span>
          </div>
          <div class="mt-3 grid grid-cols-2 gap-3 mobile:grid-cols-4">
            <div>
              <label class="text-body-xs text-gray-400">{{ $t('ugc.baseAmount') }}</label>
              <input v-model.number="item.baseAmount" type="number" min="0" class="input-field mt-1" :disabled="item.status !== 'DRAFT'" />
            </div>
            <div>
              <label class="text-body-xs text-gray-400">{{ $t('ugc.bonusAmount') }}</label>
              <input v-model.number="item.bonusAmount" type="number" min="0" class="input-field mt-1" :disabled="item.status !== 'DRAFT'" />
            </div>
            <div>
              <label class="text-body-xs text-gray-400">{{ $t('ugc.totalAmount') }}</label>
              <p class="mt-2 font-semibold text-gray-900 dark:text-gray-100">{{ (item.baseAmount + item.bonusAmount).toLocaleString() }}</p>
            </div>
            <div class="flex items-end justify-end gap-2">
              <template v-if="item.status === 'DRAFT'">
                <button class="btn-secondary text-body-xs" :disabled="acting === item.participantId" @click="save(item)">{{ $t('ugc.saveDraft') }}</button>
                <button class="btn-primary text-body-xs" :disabled="acting === item.participantId" @click="confirmReward(item)">{{ $t('ugc.confirmReward') }}</button>
              </template>
              <button v-else-if="item.status === 'CONFIRMED'" class="btn-primary text-body-xs" :disabled="acting === item.participantId" @click="markPaid(item)">{{ $t('ugc.markPaid') }}</button>
            </div>
          </div>
        </div>
      </div>

      <!-- Audit log -->
      <div v-if="auditEvents.length" class="card mt-4">
        <h3 class="mb-2 text-body font-semibold text-gray-900 dark:text-gray-100">{{ $t('ugc.auditLog') }}</h3>
        <ul class="space-y-1">
          <li v-for="ev in auditEvents" :key="ev.id" class="flex items-center justify-between text-body">
            <span class="text-gray-700 dark:text-gray-300">{{ ev.action }} <span v-if="ev.detail" class="text-body-xs text-gray-400">({{ ev.detail }})</span></span>
            <span class="text-body-xs text-gray-400">#{{ ev.actorId }} · {{ ev.createdAt?.slice(0, 16)?.replace('T', ' ') }}</span>
          </li>
        </ul>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useWorkspaceStore } from '@/stores/workspace'
import { useNotificationStore } from '@/stores/notification'
import { ugcRewardApi, type AuditEventResponse, type CampaignAnalyticsResponse, type ParticipantRewardListResponse, type ParticipantRewardResponse } from '@/api/ugcReward'
import PageHeader from '@/components/common/PageHeader.vue'
import { ChevronLeftIcon, ArrowDownTrayIcon } from '@heroicons/vue/24/outline'

const { t } = useI18n({ useScope: 'global' })
const route = useRoute()
const router = useRouter()
const workspaceStore = useWorkspaceStore()
const notify = useNotificationStore()

const campaignId = Number(route.params.id)
const analytics = ref<CampaignAnalyticsResponse | null>(null)
const rewards = ref<ParticipantRewardListResponse | null>(null)
const auditEvents = ref<AuditEventResponse[]>([])
const loading = ref(true)
const acting = ref<number | null>(null)

async function workspaceId(): Promise<number> {
  const id = await workspaceStore.ensureActiveWorkspace()
  if (id == null) throw new Error(t('ugc.noWorkspace'))
  return id
}

function rewardStatusClass(status: string): string {
  switch (status) {
    case 'CONFIRMED':
      return 'bg-info-subtle text-info-strong'
    case 'PAID_EXTERNALLY':
      return 'bg-success-subtle text-success-strong'
    case 'CANCELLED':
      return 'bg-error-subtle text-error-strong'
    default:
      return 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300'
  }
}

async function fetchAll() {
  loading.value = true
  try {
    const ws = await workspaceId()
    analytics.value = await ugcRewardApi.getAnalytics(ws, campaignId)
    rewards.value = await ugcRewardApi.listParticipants(ws, campaignId)
    auditEvents.value = (await ugcRewardApi.listAuditEvents(ws, campaignId)).items
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function save(item: ParticipantRewardResponse) {
  acting.value = item.participantId
  try {
    const ws = await workspaceId()
    await ugcRewardApi.updateReward(ws, item.participantId, { baseAmount: item.baseAmount, bonusAmount: item.bonusAmount })
    notify.success(t('ugc.draftSaved'))
    rewards.value = await ugcRewardApi.listParticipants(ws, campaignId)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.saveFailed'))
  } finally {
    acting.value = null
  }
}

async function confirmReward(item: ParticipantRewardResponse) {
  acting.value = item.participantId
  try {
    const ws = await workspaceId()
    await ugcRewardApi.updateReward(ws, item.participantId, { baseAmount: item.baseAmount, bonusAmount: item.bonusAmount })
    await ugcRewardApi.confirmReward(ws, item.participantId)
    notify.success(t('ugc.rewardConfirmed'))
    rewards.value = await ugcRewardApi.listParticipants(ws, campaignId)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.actionFailed'))
  } finally {
    acting.value = null
  }
}

async function markPaid(item: ParticipantRewardResponse) {
  acting.value = item.participantId
  try {
    const ws = await workspaceId()
    await ugcRewardApi.markPaid(ws, item.participantId)
    notify.success(t('ugc.markedPaid'))
    rewards.value = await ugcRewardApi.listParticipants(ws, campaignId)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.actionFailed'))
  } finally {
    acting.value = null
  }
}

async function downloadCsv() {
  try {
    const blob = await ugcRewardApi.downloadCsv(await workspaceId(), campaignId)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `campaign-${campaignId}-rewards.csv`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    notify.error(e instanceof Error ? e.message : t('ugc.actionFailed'))
  }
}

onMounted(fetchAll)
</script>
