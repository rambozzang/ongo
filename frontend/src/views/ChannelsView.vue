<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex flex-col gap-4 tablet:flex-row tablet:items-center tablet:justify-between">
      <div>
        <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">{{ $t('channels.title') }}</h1>
        <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">
          {{ $t('channels.description') }}
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button class="btn-primary inline-flex items-center gap-2" @click="showConnectModal = true">
          <PlusIcon class="h-5 w-5" />
          {{ $t('channels.addChannel') }}
        </button>
      </div>
    </div>

    <PageGuide :title="$t('channels.pageGuideTitle')" :items="($tm('channels.pageGuide') as string[])" />

    <!-- Loading State -->
    <LoadingSpinner v-if="loading" full-page />

    <!-- Empty State -->
    <EmptyState
      v-else-if="channels.length === 0"
      :title="$t('channels.emptyTitle')"
      :description="$t('channels.emptyDescription')"
      :icon="LinkIcon"
      @action="showConnectModal = true"
    >
      <template #action>
        <button class="btn-primary inline-flex items-center gap-2" @click="showConnectModal = true">
          <PlusIcon class="h-5 w-5" />
          {{ $t('channels.connectChannel') }}
        </button>
      </template>
    </EmptyState>

    <!-- Channels Overview -->
    <div v-else>
      <!-- Summary Section -->
      <div class="mb-6 grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-4">
        <div class="card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('channels.totalChannels') }}</p>
              <p class="mt-1 text-2xl font-bold text-gray-900 dark:text-gray-100">
                {{ channelStats.total }}
              </p>
            </div>
            <div class="flex h-12 w-12 items-center justify-center rounded-full bg-blue-100 dark:bg-blue-900/30">
              <LinkIcon class="h-6 w-6 text-blue-600 dark:text-blue-400" />
            </div>
          </div>
        </div>

        <div class="card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('channels.healthy') }}</p>
              <p class="mt-1 text-2xl font-bold text-green-600 dark:text-green-400">
                {{ channelStats.healthy }}
              </p>
            </div>
            <div class="flex h-12 w-12 items-center justify-center rounded-full bg-green-100 dark:bg-green-900/30">
              <CheckCircleIcon class="h-6 w-6 text-green-600 dark:text-green-400" />
            </div>
          </div>
        </div>

        <div class="card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('channels.warning') }}</p>
              <p class="mt-1 text-2xl font-bold text-yellow-600 dark:text-yellow-400">
                {{ channelStats.warning }}
              </p>
            </div>
            <div class="flex h-12 w-12 items-center justify-center rounded-full bg-yellow-100 dark:bg-yellow-900/30">
              <ExclamationTriangleIcon class="h-6 w-6 text-yellow-600 dark:text-yellow-400" />
            </div>
          </div>
        </div>

        <div class="card">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm text-gray-600 dark:text-gray-400">{{ $t('channels.error') }}</p>
              <p class="mt-1 text-2xl font-bold text-red-600 dark:text-red-400">
                {{ channelStats.error }}
              </p>
            </div>
            <div class="flex h-12 w-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-900/30">
              <XCircleIcon class="h-6 w-6 text-red-600 dark:text-red-400" />
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
      <div class="grid grid-cols-1 gap-4 tablet:grid-cols-2 desktop:grid-cols-3">
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

    <!-- Connect Channel Modal -->
    <ConnectChannelModal
      v-model="showConnectModal"
      :connected-platforms="connectedPlatforms"
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
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import ConfirmModal from '@/components/common/ConfirmModal.vue'
import ChannelHealthCard from '@/components/channel/ChannelHealthCard.vue'
import ConnectChannelModal from '@/components/channel/ConnectChannelModal.vue'
import PageGuide from '@/components/common/PageGuide.vue'
import { useChannelStore } from '@/stores/channel'
import { useNotification } from '@/composables/useNotification'
import { useI18n } from 'vue-i18n'
import type { Channel, Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'
import { buildOAuthUrl } from '@/utils/oauth'

// --- Stores ---
const { t } = useI18n()
const channelStore = useChannelStore()
const notification = useNotification()
const { channels, loading, connectedPlatforms } = storeToRefs(channelStore)

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
function getTokenExpiryDate(_channel: Channel): string | null {
  // Token expiry is not currently available from the API.
  // The ChannelHealthCard infers status from channel.tokenStatus instead.
  return null
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

function handleReconnect(platform: Platform) {
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

function handleConnect(platform: Platform) {
  window.location.href = buildOAuthUrl(platform, '/channels')
}

// --- Lifecycle ---
onMounted(() => {
  channelStore.fetchChannels()
})
</script>
