<template>
  <div class="min-h-full bg-surface-base px-4 py-5 text-content tablet:px-[22px] tablet:py-6">
    <header class="mb-[18px] flex flex-col gap-3 tablet:flex-row tablet:items-end tablet:justify-between">
      <div>
        <p class="font-mono text-[10px] uppercase tracking-[0.16em] text-content-tertiary">{{ t('nav.channels') }}</p>
        <h1 class="mt-1 text-[26px] font-bold tracking-[-0.02em] text-content">{{ t('channels.title') }}</h1>
        <p class="mt-1 text-[12px] text-content-tertiary">{{ t('channels.description') }}</p>
      </div>
      <button type="button" class="btn-primary !min-h-9 !px-3 text-[11px]" @click="openChannelManager">
        <PlusIcon class="h-4 w-4" />
        {{ t('channels.addChannel') }}
      </button>
    </header>

    <div v-if="expiredChannels.length" class="mb-4 flex flex-col gap-2 rounded-lg border border-error-subtle bg-error-subtle px-3 py-2.5 text-[12px] text-error-strong tablet:flex-row tablet:items-center">
      <div class="flex min-w-0 items-start gap-2">
        <ExclamationTriangleIcon class="mt-0.5 h-4 w-4 shrink-0" />
        <span>{{ t('channels.emptyDescription') }} ({{ expiredChannels.length }})</span>
      </div>
      <button type="button" class="shrink-0 self-start rounded-md border border-error-strong px-2 py-1 text-[11px] font-semibold transition-colors duration-150 hover:bg-error-strong hover:text-surface-base tablet:ml-auto tablet:self-auto" @click="openChannelManager">
        {{ t('channels.connect') }}
      </button>
    </div>

    <div
      v-if="channelStore.loadError"
      class="mb-4 flex flex-wrap items-center gap-2 rounded-lg border border-warning-subtle bg-warning-subtle px-3 py-2.5 text-[12px] text-warning-strong"
      role="alert"
    >
      <span class="min-w-0 flex-1">{{ t('channels.loadFailed') }}</span>
      <button
        type="button"
        class="shrink-0 rounded-md border border-warning-strong px-2 py-1 text-[11px] font-semibold transition-colors hover:bg-warning-strong hover:text-surface-base disabled:opacity-50"
        :disabled="channelStore.loading"
        @click="channelStore.fetchChannels"
      >
        {{ t('action.retry') }}
      </button>
    </div>

    <section class="mb-3 flex flex-col gap-3 tablet:flex-row tablet:items-center">
      <div>
        <h2 class="text-[13px] font-bold text-content">{{ t('channels.totalChannels') }} {{ channels.length }}</h2>
        <p class="mt-1 text-[11.5px] text-content-tertiary">{{ t('channels.description') }}</p>
      </div>
      <div class="flex items-center gap-2 tablet:ml-auto">
        <span class="rounded-[5px] bg-success-subtle px-2 py-1 font-mono text-[10px] text-success-strong">{{ healthyCount }} {{ t('channels.healthy') }}</span>
        <span class="rounded-[5px] bg-warning-subtle px-2 py-1 font-mono text-[10px] text-warning-strong">{{ warningCount }} {{ t('channels.warning') }}</span>
        <span class="rounded-[5px] bg-error-subtle px-2 py-1 font-mono text-[10px] text-error-strong">{{ expiredChannels.length }} {{ t('channels.error') }}</span>
      </div>
    </section>

    <div v-if="channelStore.loading" class="grid gap-[11px] tablet:grid-cols-2 desktop:grid-cols-3">
      <div v-for="slot in 3" :key="slot" class="h-[188px] animate-pulse rounded-[11px] border border-line bg-surface-card" />
    </div>
    <div v-else-if="!channels.length" class="rounded-[11px] border border-line bg-surface-card px-5 py-14 text-center">
      <LinkIcon class="mx-auto mb-3 h-8 w-8 text-content-quaternary" />
      <h2 class="text-[13px] font-bold text-content">{{ t('channels.emptyTitle') }}</h2>
      <p class="mx-auto mt-1 max-w-md text-[12px] leading-5 text-content-tertiary">{{ t('channels.emptyDescription') }}</p>
      <button type="button" class="btn-primary mt-4 !min-h-9 text-[11px]" @click="openChannelManager">{{ t('channels.connectChannel') }}</button>
    </div>
    <template v-else>
      <div class="grid gap-[11px] tablet:grid-cols-2 desktop:grid-cols-3">
        <article v-for="channel in channels" :key="channel.id" class="rounded-[11px] border border-line bg-surface-card p-[15px] transition-colors duration-150 hover:border-line-hover">
          <div class="flex items-start gap-2.5">
            <PlatformChip v-if="toRedesignPlatform(channel.platform)" :platform="toRedesignPlatform(channel.platform)!" />
            <div v-else class="flex h-[26px] w-[26px] shrink-0 items-center justify-center rounded-md bg-muted-subtle font-mono text-[9px] text-muted-strong">{{ channel.platform.slice(0, 2) }}</div>
            <div class="min-w-0 flex-1">
              <h2 class="truncate text-[13px] font-bold text-content">{{ channel.channelName }}</h2>
              <p class="mt-0.5 truncate font-mono text-[10.5px] text-content-tertiary">{{ channel.channelUrl || t('channels.connected') }}</p>
            </div>
            <StatusPill :variant="statusVariant(channel)">{{ statusLabel(channel) }}</StatusPill>
          </div>

          <div class="mt-4 grid grid-cols-3 gap-px overflow-hidden rounded-lg border border-line bg-line">
            <div class="bg-surface-input px-2 py-2.5">
              <p class="text-[10px] text-content-tertiary">{{ t('channels.connected') }}</p>
              <p class="mt-1 font-mono text-[13px] text-content">{{ formatNumber(channel.subscriberCount) }}</p>
            </div>
            <div class="bg-surface-input px-2 py-2.5">
              <p class="text-[10px] text-content-tertiary">{{ t('analyticsView.table.views') }}</p>
              <p class="mt-1 font-mono text-[13px] text-content-secondary">—</p>
            </div>
            <div class="bg-surface-input px-2 py-2.5">
              <p class="text-[10px] text-content-tertiary">{{ t('analyticsView.table.video') }}</p>
              <p class="mt-1 font-mono text-[13px] text-content-secondary">—</p>
            </div>
          </div>

          <div class="mt-3 flex items-center gap-2 text-[11px] text-content-tertiary">
            <span class="flex-1 truncate">{{ syncNote(channel) }}</span>
            <button type="button" class="shrink-0 text-content-secondary transition-colors duration-150 hover:text-accent disabled:opacity-50" :disabled="channelStore.isSyncingChannel" @click="syncChannel(channel.id)">
              {{ channelStore.isSyncingChannel ? t('action.loading') : t('channels.syncAll') }}
            </button>
          </div>
        </article>

        <button type="button" class="flex min-h-[188px] items-center justify-center rounded-[11px] border border-dashed border-line-control bg-surface-card px-4 text-[12px] font-semibold text-content-secondary transition-colors duration-150 hover:border-accent hover:text-accent" @click="openChannelManager">
          <PlusIcon class="mr-1.5 h-4 w-4" />
          {{ t('channels.connectChannel') }}
        </button>
      </div>

      <SectionCard :title="t('nav.automation')" :meta="t('channels.emptyDescription')" class="mt-6">
        <div class="flex flex-col items-center justify-center px-4 py-10 text-center">
          <AdjustmentsHorizontalIcon class="mb-2 h-7 w-7 text-content-quaternary" />
          <p class="text-[12px] font-semibold text-content-secondary">{{ t('automation.emptyRulesTitle') }}</p>
          <p class="mt-1 max-w-md text-[11px] text-content-tertiary">{{ t('automation.emptyRulesDesc') }}</p>
          <RouterLink to="/automation" class="mt-3 text-[11px] font-semibold text-accent hover:text-accent-hover">{{ t('automation.newRule') }}</RouterLink>
        </div>
      </SectionCard>
    </template>

    <ConnectChannelModal
      v-model="isConnectModalOpen"
      :connected-platforms="channelStore.connectedPlatforms"
      :max-allowed="channelStore.maxAllowed"
      :current-count="channels.length"
      @connect="connectChannel"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import { AdjustmentsHorizontalIcon, ExclamationTriangleIcon, LinkIcon, PlusIcon } from '@heroicons/vue/24/outline'
import type { Channel, Platform } from '@/types/channel'
import PlatformChip from '@/components/redesign/PlatformChip.vue'
import SectionCard from '@/components/redesign/SectionCard.vue'
import StatusPill from '@/components/redesign/StatusPill.vue'
import { useChannelStore } from '@/stores/channel'
import ConnectChannelModal from '@/components/channel/ConnectChannelModal.vue'
import { buildOAuthUrl, generateOAuthStateNonce, generatePKCE } from '@/utils/oauth'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const channelStore = useChannelStore()
const { channels } = storeToRefs(channelStore)
const notify = useNotificationStore()
const isConnectModalOpen = ref(false)
const PLATFORM_CODES: Partial<Record<Platform, 'YT' | 'IG' | 'TT' | 'FB' | 'NV' | 'TH' | 'TW'>> = {
  YOUTUBE: 'YT',
  INSTAGRAM: 'IG',
  TIKTOK: 'TT',
  FACEBOOK: 'FB',
  NAVER_CLIP: 'NV',
  THREADS: 'TH',
  TWITTER: 'TW',
}

const expiredChannels = computed(() => channels.value.filter((channel) => ['EXPIRED', 'DISCONNECTED'].includes(channel.tokenStatus)))
const warningCount = computed(() => channels.value.filter((channel) => channel.tokenStatus === 'EXPIRING_SOON').length)
const healthyCount = computed(() => channels.value.filter((channel) => channel.tokenStatus === 'ACTIVE').length)

function toRedesignPlatform(platform: Platform) {
  return PLATFORM_CODES[platform]
}
function statusVariant(channel: Channel): 'success' | 'warning' | 'error' | 'muted' {
  if (channel.tokenStatus === 'ACTIVE') return 'success'
  if (channel.tokenStatus === 'EXPIRING_SOON') return 'warning'
  if (['EXPIRED', 'DISCONNECTED'].includes(channel.tokenStatus)) return 'error'
  return 'muted'
}
function statusLabel(channel: Channel): string {
  if (channel.tokenStatus === 'ACTIVE') return t('channels.connected')
  if (channel.tokenStatus === 'EXPIRING_SOON') return t('channels.warning')
  return t('channels.error')
}
function formatNumber(value: number): string {
  return new Intl.NumberFormat(locale.value, { notation: 'compact', maximumFractionDigits: 1 }).format(value)
}
function syncNote(channel: Channel): string {
  if (!channel.lastSyncedAt) return t('channels.connecting')
  const date = new Date(channel.lastSyncedAt)
  if (Number.isNaN(date.getTime())) return channel.lastSyncedAt
  return `${t('channels.syncAll')} · ${new Intl.DateTimeFormat(locale.value, { dateStyle: 'medium', timeStyle: 'short' }).format(date)}`
}
function openChannelManager() {
  isConnectModalOpen.value = true
}

async function connectChannel(platform: Platform) {
  try {
    // X는 OAuth 2.0 PKCE가 필수다. verifier는 콜백에서 토큰 교환에 사용한다.
    const challenge = platform === 'TWITTER'
      ? (await generatePKCE('twitter_code_verifier')).challenge
      : undefined
    const stateNonce = generateOAuthStateNonce()
    window.location.href = buildOAuthUrl(platform, '/channels-v2', challenge, stateNonce)
  } catch (error) {
    isConnectModalOpen.value = false
    notify.error(error instanceof Error ? error.message : t('channels.connectFailed'))
  }
}

async function syncChannel(id: number) {
  try {
    await channelStore.syncChannel(id)
    notify.success(t('channels.syncSuccess'))
  } catch (error) {
    notify.error(error instanceof Error ? error.message : t('channels.syncFailed'))
  }
}

onMounted(() => {
  void channelStore.fetchChannels()
  if (route.query.connect === '1') {
    isConnectModalOpen.value = true
    // 뒤로 가기 시 모달이 다시 열리지 않도록 한 번 소비한다.
    void router.replace({ query: { ...route.query, connect: undefined } })
  }
})
</script>
