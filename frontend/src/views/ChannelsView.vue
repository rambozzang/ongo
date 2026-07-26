<template>
  <div>
    <!-- Header -->
    <PageHeader :title="$t('channels.title')" :description="$t('channels.description')">
      <template #actions>
        <button class="btn-primary inline-flex items-center gap-2" @click="showConnectModal = true">
          <PlusIcon class="h-5 w-5" />
          {{ $t('channels.addChannel') }}
        </button>
      </template>
    </PageHeader>

    <PageGuide :title="$t('channels.pageGuideTitle')" :items="($tm('channels.pageGuide') as string[])" />

    <!-- 로딩 → 빈 상태 → 채널 개요 -->
    <AsyncState
      :loading="loading"
      :empty="channels.length === 0"
      skeleton="card"
      :skeleton-count="3"
      :empty-title="$t('channels.emptyTitle')"
      :empty-description="$t('channels.emptyDescription')"
      :empty-icon="LinkIcon"
    >
      <template #empty-action>
        <button class="btn-primary inline-flex items-center gap-2" @click="showConnectModal = true">
          <PlusIcon class="h-5 w-5" />
          {{ $t('channels.connectChannel') }}
        </button>
      </template>

      <div>
        <!-- Summary Section -->
        <div class="page-grid page-grid--metrics mb-6">
          <div class="card">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-body text-gray-600 dark:text-gray-400">{{ $t('channels.totalChannels') }}</p>
                <p class="mt-1 text-h1 font-bold text-gray-900 dark:text-gray-100">
                  {{ channelStats.total }}
                </p>
              </div>
              <div class="flex h-12 w-12 items-center justify-center rounded-full bg-info-subtle">
                <LinkIcon class="h-6 w-6 text-info-strong" />
              </div>
            </div>
          </div>

          <div class="card">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-body text-gray-600 dark:text-gray-400">{{ $t('channels.healthy') }}</p>
                <p class="mt-1 text-h1 font-bold text-success-strong">
                  {{ channelStats.healthy }}
                </p>
              </div>
              <div class="flex h-12 w-12 items-center justify-center rounded-full bg-success-subtle">
                <CheckCircleIcon class="h-6 w-6 text-success-strong" />
              </div>
            </div>
          </div>

          <div class="card">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-body text-gray-600 dark:text-gray-400">{{ $t('channels.warning') }}</p>
                <p class="mt-1 text-h1 font-bold text-warning-strong">
                  {{ channelStats.warning }}
                </p>
              </div>
              <div class="flex h-12 w-12 items-center justify-center rounded-full bg-warning-subtle">
                <ExclamationTriangleIcon class="h-6 w-6 text-warning-strong" />
              </div>
            </div>
          </div>

          <div class="card">
            <div class="flex items-center justify-between">
              <div>
                <p class="text-body text-gray-600 dark:text-gray-400">{{ $t('channels.error') }}</p>
                <p class="mt-1 text-h1 font-bold text-error-strong">
                  {{ channelStats.error }}
                </p>
              </div>
              <div class="flex h-12 w-12 items-center justify-center rounded-full bg-error-subtle">
                <XCircleIcon class="h-6 w-6 text-error-strong" />
              </div>
            </div>
          </div>
        </div>

        <!-- Sync All Button -->
        <div class="mb-6 flex justify-end">
          <button
            class="btn-secondary inline-flex items-center gap-2"
            :disabled="syncingAll"
            @click="handleSyncAll"
          >
            <ArrowPathIcon
              class="h-5 w-5"
              :class="{ 'animate-spin': syncingAll }"
            />
            {{ syncingAll ? $t('channels.syncingAll') : $t('channels.syncAll') }}
          </button>
        </div>

        <!-- Channel Health Cards Grid -->
        <div class="page-grid page-grid--cards">
          <ChannelHealthCard
            v-for="channel in channels"
            :key="channel.id"
            :channel="channel"
            :token-expires-at="getTokenExpiryDate(channel)"
            @sync="handleSync(channel.id)"
            @reconnect="handleReconnect(channel.platform)"
            @disconnect="openDisconnectModal(channel)"
          />
        </div>
      </div>
    </AsyncState>

    <!-- Connect Channel Modal -->
    <ConnectChannelModal
      v-model="showConnectModal"
      :connected-platforms="connectedPlatforms"
      :max-allowed="maxAllowed"
      :current-count="channels.length"
      @connect="handleConnect"
    />


    <!-- Disconnect Confirmation Modal -->
    <ConfirmModal
      v-model="showDisconnectModal"
      :title="$t('channels.disconnectTitle')"
      :message="disconnectMessage"
      :confirm-text="$t('channels.disconnectConfirm')"
      :danger="true"
      @confirm="handleDisconnect"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { storeToRefs } from 'pinia'
import {
  PlusIcon,
  ArrowPathIcon,
  LinkIcon,
  CheckCircleIcon,
  ExclamationTriangleIcon,
  XCircleIcon,
} from '@heroicons/vue/24/outline'
import AsyncState from '@/components/common/AsyncState.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import ChannelHealthCard from '@/components/channel/ChannelHealthCard.vue'
import ConnectChannelModal from '@/components/channel/ConnectChannelModal.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { useChannelStore } from '@/stores/channel'
import { useNotification } from '@/composables/useNotification'
import { useI18n } from 'vue-i18n'
import type { Channel, Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'
import { buildOAuthUrl, generatePKCE } from '@/utils/oauth'

// --- Stores ---
const { t } = useI18n()
const channelStore = useChannelStore()
const notification = useNotification()
const { channels, loading, connectedPlatforms, maxAllowed } = storeToRefs(channelStore)

// --- State ---
const showConnectModal = ref(false)
const showDisconnectModal = ref(false)
const channelToDisconnect = ref<Channel | null>(null)
const syncingChannelIds = reactive(new Set<number>())
const syncingAll = ref(false)

// --- Computed ---
const channelStats = computed(() => {
  const stats = {
    total: channels.value.length,
    healthy: 0,
    warning: 0,
    error: 0,
  }

  channels.value.forEach((channel) => {
    if (channel.tokenStatus === 'EXPIRED') {
      stats.error++
    } else if (channel.tokenStatus === 'EXPIRING_SOON') {
      stats.warning++
    } else {
      stats.healthy++
    }
  })

  return stats
})

const disconnectMessage = computed(() => {
  if (!channelToDisconnect.value) return ''
  const config = PLATFORM_CONFIG[channelToDisconnect.value.platform]
  return t('channels.disconnectMessage', { platform: config.label, name: channelToDisconnect.value.channelName })
})

// --- Helpers ---
function getTokenExpiryDate(channel: Channel): string | null {
  return channel.tokenExpiresAt ?? null
}

// --- Actions ---
async function handleSync(channelId: number) {
  syncingChannelIds.add(channelId)
  try {
    await channelStore.syncChannel(channelId)
    notification.success(t('channels.syncSuccess'))
  } catch {
    notification.error(t('channels.syncError'))
  } finally {
    syncingChannelIds.delete(channelId)
  }
}

async function handleSyncAll() {
  syncingAll.value = true
  try {
    // Sync all channels
    await Promise.all(
      channels.value.map((channel) => channelStore.syncChannel(channel.id))
    )
    notification.success(t('channels.syncAllSuccess'))
  } catch {
    notification.error(t('channels.syncAllError'))
  } finally {
    syncingAll.value = false
  }
}

async function handleReconnect(platform: Platform) {
  if (platform === 'TWITTER') {
    const { challenge } = await generatePKCE('twitter_code_verifier')
    window.location.href = buildOAuthUrl(platform, '/channels', challenge)
    return
  }
  window.location.href = buildOAuthUrl(platform, '/channels')
}

function openDisconnectModal(channel: Channel) {
  channelToDisconnect.value = channel
  showDisconnectModal.value = true
}

async function handleDisconnect() {
  if (!channelToDisconnect.value) return
  const name = channelToDisconnect.value.channelName
  try {
    await channelStore.disconnectChannel(channelToDisconnect.value.id)
    notification.success(t('channels.disconnectSuccess', { name }))
  } catch {
    notification.error(t('channels.disconnectError'))
  } finally {
    channelToDisconnect.value = null
  }
}

async function handleConnect(platform: Platform) {
  if (platform === 'TWITTER') {
    const { challenge } = await generatePKCE('twitter_code_verifier')
    window.location.href = buildOAuthUrl(platform, '/channels', challenge)
    return
  }
  window.location.href = buildOAuthUrl(platform, '/channels')
}

// --- Lifecycle ---
onMounted(() => {
  channelStore.fetchChannels()
})
</script>
