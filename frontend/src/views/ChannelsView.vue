<template>
  <div>
    <!-- Header -->
    <div class="mb-6 flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-900 dark:text-gray-100">채널 관리</h1>
      <button class="btn-primary inline-flex items-center gap-2" @click="showConnectModal = true">
        <PlusIcon class="h-5 w-5" />
        새 채널 연결
      </button>
    </div>

    <PageGuide title="채널 관리" :items="[
      '새 채널 연결 버튼으로 YouTube·TikTok·Instagram·Naver Clip 등 플랫폼 계정을 OAuth로 연동하세요',
      '상단 요약 카드에서 총 채널 수, 정상·주의·오류 상태 채널 수를 한눈에 확인할 수 있습니다',
      '각 채널 카드의 토큰 상태가 주의(노란색) 또는 오류(빨간색)이면 재연결 버튼으로 토큰을 갱신하세요',
      '모두 동기화 버튼으로 전체 채널의 구독자·콘텐츠 정보를 최신 상태로 업데이트하세요',
      '채널 연동을 해제하면 해당 플랫폼으로의 업로드와 분석이 중단되니 주의하세요',
    ]" />

    <!-- Loading State -->
    <LoadingSpinner v-if="loading" full-page />

    <!-- Empty State -->
    <EmptyState
      v-else-if="channels.length === 0"
      title="연동된 채널이 없어요"
      description="YouTube, TikTok, Instagram, Naver Clip 채널을 연결하면 영상 업로드와 분석을 시작할 수 있어요."
      :icon="LinkIcon"
      @action="showConnectModal = true"
    >
      <template #action>
        <button class="btn-primary inline-flex items-center gap-2" @click="showConnectModal = true">
          <PlusIcon class="h-5 w-5" />
          채널 연동하기
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
              <p class="text-sm text-gray-600 dark:text-gray-400">총 채널</p>
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
              <p class="text-sm text-gray-600 dark:text-gray-400">정상</p>
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
              <p class="text-sm text-gray-600 dark:text-gray-400">주의</p>
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
              <p class="text-sm text-gray-600 dark:text-gray-400">오류</p>
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
          {{ syncingAll ? '모두 동기화 중...' : '모두 동기화' }}
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
      title="채널 연동 해제"
      :message="disconnectMessage"
      confirm-text="연동 해제"
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
import type { Channel, Platform } from '@/types/channel'
import { PLATFORM_CONFIG } from '@/types/channel'
import { buildOAuthUrl } from '@/utils/oauth'

// --- Stores ---
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
  return `${config.label} 채널 "${channelToDisconnect.value.channelName}"의 연동을 해제하시겠습니까?\n\n연동 해제 시 해당 플랫폼 업로드/분석이 중단됩니다.`
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
    notification.success('채널 정보가 동기화되었습니다.')
  } catch {
    notification.error('동기화에 실패했습니다. 다시 시도해주세요.')
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
    notification.success('모든 채널이 동기화되었습니다.')
  } catch {
    notification.error('일부 채널 동기화에 실패했습니다.')
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
    notification.success(`${name} 채널 연동이 해제되었습니다.`)
  } catch {
    notification.error('연동 해제에 실패했습니다. 다시 시도해주세요.')
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
