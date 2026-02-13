import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { InboxMessage, InboxFilters, MessageType, MessagePlatform, MessageStatus } from '@/types/inbox'
import { inboxApi } from '@/api/inbox'
import type { InboxMessageResponse } from '@/api/inbox'

function mapApiMessage(msg: InboxMessageResponse): InboxMessage {
  return {
    id: msg.id,
    type: (msg.messageType?.toLowerCase() ?? 'comment') as MessageType,
    platform: (msg.platform ?? 'YOUTUBE') as MessagePlatform,
    senderName: msg.senderName,
    senderAvatar: msg.senderAvatarUrl ?? '',
    content: msg.content,
    videoId: msg.videoId ? String(msg.videoId) : undefined,
    status: (msg.isRead ? 'read' : 'unread') as MessageStatus,
    isStarred: msg.isStarred,
    sentiment: 'neutral',
    createdAt: msg.receivedAt ?? msg.createdAt ?? new Date().toISOString(),
  }
}

export const useInboxStore = defineStore('inbox', () => {
  // State
  const messages = ref<InboxMessage[]>([])
  const filters = ref<InboxFilters>({
    platform: 'ALL',
    type: 'ALL',
    status: 'ALL',
    searchText: '',
  })
  const selectedMessageId = ref<number | null>(null)
  const selectedMessageIds = ref<Set<number>>(new Set())

  const loading = ref(false)

  // Load messages from the real API
  const initMessages = async () => {
    loading.value = true
    try {
      const response = await inboxApi.listMessages({ page: 0, size: 100 })
      messages.value = response.messages.map(mapApiMessage)
    } catch {
      // API failed — show empty list
      messages.value = []
    } finally {
      loading.value = false
    }
  }

  // Getters
  const filteredMessages = computed(() => {
    let result = messages.value

    if (filters.value.platform !== 'ALL') {
      result = result.filter((m) => m.platform === filters.value.platform)
    }

    if (filters.value.type !== 'ALL') {
      result = result.filter((m) => m.type === filters.value.type)
    }

    if (filters.value.status !== 'ALL') {
      result = result.filter((m) => m.status === filters.value.status)
    }

    if (filters.value.searchText.trim()) {
      const search = filters.value.searchText.toLowerCase()
      result = result.filter(
        (m) =>
          m.content.toLowerCase().includes(search) ||
          m.senderName.toLowerCase().includes(search) ||
          m.videoTitle?.toLowerCase().includes(search)
      )
    }

    return result.sort(
      (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    )
  })

  const unreadCount = computed(() => {
    return messages.value.filter((m) => m.status === 'unread').length
  })

  const messagesByPlatform = computed(() => {
    const counts = {
      YOUTUBE: 0,
      TIKTOK: 0,
      INSTAGRAM: 0,
      NAVER_CLIP: 0,
    }
    messages.value.forEach((m) => {
      counts[m.platform]++
    })
    return counts
  })

  const selectedMessage = computed(() => {
    if (!selectedMessageId.value) return null
    return messages.value.find((m) => m.id === selectedMessageId.value) || null
  })

  const starredMessages = computed(() => {
    return messages.value.filter((m) => m.isStarred)
  })

  // Actions
  const markAsRead = async (id: number) => {
    const message = messages.value.find((m) => m.id === id)
    if (message) {
      message.status = 'read'

      try {
        await inboxApi.markAsRead(id)
      } catch {
        // Local state already updated
      }
    }
  }

  const markAsUnread = (id: number) => {
    const message = messages.value.find((m) => m.id === id)
    if (message && message.status !== 'replied') {
      message.status = 'unread'
    }
  }

  const toggleStar = async (id: number) => {
    const message = messages.value.find((m) => m.id === id)
    if (message) {
      message.isStarred = !message.isStarred

      try {
        await inboxApi.toggleStar(id)
      } catch {
        // Local state already updated
      }
    }
  }

  const archiveMessage = (id: number) => {
    const message = messages.value.find((m) => m.id === id)
    if (message) {
      message.status = 'archived'
    }
  }

  const replyToMessage = (id: number, replyContent: string) => {
    const message = messages.value.find((m) => m.id === id)
    if (message) {
      message.status = 'replied'
      message.replyContent = replyContent
      message.repliedAt = new Date().toISOString()
    }
  }

  const bulkArchive = (ids: number[]) => {
    ids.forEach((id) => {
      const message = messages.value.find((m) => m.id === id)
      if (message) {
        message.status = 'archived'
      }
    })
    selectedMessageIds.value.clear()
  }

  const bulkMarkRead = (ids: number[]) => {
    ids.forEach((id) => {
      const message = messages.value.find((m) => m.id === id)
      if (message) {
        message.status = 'read'
      }
    })
    selectedMessageIds.value.clear()
  }

  const setFilters = (newFilters: Partial<InboxFilters>) => {
    filters.value = { ...filters.value, ...newFilters }
  }

  const selectMessage = (id: number | null) => {
    selectedMessageId.value = id
    if (id) {
      markAsRead(id)
    }
  }

  const toggleMessageSelection = (id: number) => {
    if (selectedMessageIds.value.has(id)) {
      selectedMessageIds.value.delete(id)
    } else {
      selectedMessageIds.value.add(id)
    }
  }

  const clearSelection = () => {
    selectedMessageIds.value.clear()
  }

  const markAllAsRead = async () => {
    filteredMessages.value.forEach((m) => {
      if (m.status === 'unread') {
        m.status = 'read'
      }
    })
    try {
      await inboxApi.markAllAsRead()
    } catch {
      // Local state already updated
    }
  }

  // Initialize on store creation
  initMessages()

  return {
    // State
    messages,
    filters,
    selectedMessageId,
    selectedMessageIds,
    loading,
    // Getters
    filteredMessages,
    unreadCount,
    messagesByPlatform,
    selectedMessage,
    starredMessages,
    // Actions
    initMessages,
    markAsRead,
    markAsUnread,
    toggleStar,
    archiveMessage,
    replyToMessage,
    bulkArchive,
    bulkMarkRead,
    setFilters,
    selectMessage,
    toggleMessageSelection,
    clearSelection,
    markAllAsRead,
  }
})
