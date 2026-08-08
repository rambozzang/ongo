import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Channel } from '@/types/channel'
import type { Platform } from '@/types/channel'
import { channelApi } from '@/api/channel'
import { useVideoStore } from '@/stores/video'

export const useChannelStore = defineStore('channel', () => {
  const channels = ref<Channel[]>([])
  const maxAllowed = ref(0)
  const isLoadingChannels = ref(false)
  const isSyncingChannel = ref(false)
  const loadError = ref(false)

  // Backwards-compatible loading ref
  const loading = isLoadingChannels

  const connectedPlatforms = computed<Platform[]>(() =>
    channels.value.map((c) => c.platform),
  )

  async function fetchChannels() {
    isLoadingChannels.value = true
    loadError.value = false
    try {
      const data = await channelApi.list()
      channels.value = data.channels
      maxAllowed.value = data.maxAllowed
    } catch {
      // 기존 데이터는 유지한다. 실패를 빈 상태로 바꾸면 사용자가 연결 해제를 오인한다.
      loadError.value = true
    } finally {
      isLoadingChannels.value = false
    }
  }

  async function disconnectChannel(id: number) {
    await channelApi.disconnect(id)
    channels.value = channels.value.filter((c) => c.id !== id)

    // 관련 스토어 캐시 무효화
    const videoStore = useVideoStore()
    videoStore.invalidateCache()
  }

  async function syncChannel(id: number) {
    isSyncingChannel.value = true
    try {
      const updated = await channelApi.sync(id)
      const index = channels.value.findIndex((c) => c.id === id)
      if (index !== -1) {
        channels.value[index] = updated
      }
    } finally {
      isSyncingChannel.value = false
    }
  }

  return {
    channels,
    maxAllowed,
    loading,
    loadError,
    isLoadingChannels,
    isSyncingChannel,
    connectedPlatforms,
    fetchChannels,
    disconnectChannel,
    syncChannel,
  }
})
