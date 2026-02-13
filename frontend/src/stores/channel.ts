import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Channel } from '@/types/channel'
import type { Platform } from '@/types/channel'
import { channelApi } from '@/api/channel'

export const useChannelStore = defineStore('channel', () => {
  const channels = ref<Channel[]>([])
  const isLoadingChannels = ref(false)
  const isSyncingChannel = ref(false)

  // Backwards-compatible loading ref
  const loading = isLoadingChannels

  const connectedPlatforms = computed<Platform[]>(() =>
    channels.value.map((c) => c.platform),
  )

  async function fetchChannels() {
    isLoadingChannels.value = true
    try {
      channels.value = await channelApi.list()
    } catch {
      // Guest mode or API error - continue with empty channels
      channels.value = []
    } finally {
      isLoadingChannels.value = false
    }
  }

  async function disconnectChannel(id: number) {
    await channelApi.disconnect(id)
    channels.value = channels.value.filter((c) => c.id !== id)
  }

  async function syncChannel(id: number) {
    isSyncingChannel.value = true
    try {
      const updated = await channelApi.sync(id)
      const index = channels.value.findIndex((c) => c.id === id)
      if (index !== -1) {
        channels.value[index] = updated
      }
    } catch {
      // sync failed silently
    } finally {
      isSyncingChannel.value = false
    }
  }

  return {
    channels,
    loading,
    isLoadingChannels,
    isSyncingChannel,
    connectedPlatforms,
    fetchChannels,
    disconnectChannel,
    syncChannel,
  }
})
