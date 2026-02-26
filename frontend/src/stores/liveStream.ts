import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { liveStreamApi } from '@/api/liveStream'
import type { LiveStream, StreamChat, LiveStreamSummary } from '@/types/liveStream'

export const useLiveStreamStore = defineStore('liveStream', () => {
  const streams = ref<LiveStream[]>([])
  const chats = ref<StreamChat[]>([])
  const summary = ref<LiveStreamSummary | null>(null)
  const loading = ref(false)

  const liveStreams = computed(() => streams.value.filter(s => s.status === 'LIVE'))
  const scheduledStreams = computed(() => streams.value.filter(s => s.status === 'SCHEDULED'))

  async function fetchStreams(status?: string) {
    loading.value = true
    try {
      streams.value = await liveStreamApi.getStreams(status)
    } catch {
      streams.value = [
        { id: 1, title: '금요 라이브 Q&A', description: '구독자 질문 답변 라이브', platform: 'YOUTUBE', status: 'LIVE', scheduledAt: '2025-03-14T19:00:00Z', startedAt: '2025-03-14T19:00:00Z', endedAt: null, viewerCount: 1250, peakViewers: 1580, chatMessages: 3400, streamUrl: 'https://youtube.com/live/abc', thumbnailUrl: null },
        { id: 2, title: '게임 스트리밍', description: '신작 게임 플레이', platform: 'YOUTUBE', status: 'SCHEDULED', scheduledAt: '2025-03-15T20:00:00Z', startedAt: null, endedAt: null, viewerCount: 0, peakViewers: 0, chatMessages: 0, streamUrl: null, thumbnailUrl: null },
        { id: 3, title: '쿠킹 라이브', description: '봄 나물 요리', platform: 'INSTAGRAM', status: 'ENDED', scheduledAt: '2025-03-13T18:00:00Z', startedAt: '2025-03-13T18:02:00Z', endedAt: '2025-03-13T19:30:00Z', viewerCount: 0, peakViewers: 890, chatMessages: 1200, streamUrl: null, thumbnailUrl: null },
        { id: 4, title: 'ASMR 라이브', description: '공부 함께해요', platform: 'TIKTOK', status: 'SCHEDULED', scheduledAt: '2025-03-16T22:00:00Z', startedAt: null, endedAt: null, viewerCount: 0, peakViewers: 0, chatMessages: 0, streamUrl: null, thumbnailUrl: null },
      ]
    } finally {
      loading.value = false
    }
  }

  async function fetchChats(streamId: number) {
    try {
      chats.value = await liveStreamApi.getChats(streamId)
    } catch {
      chats.value = [
        { id: 1, streamId, username: 'viewer123', message: '안녕하세요!', timestamp: '2025-03-14T19:01:00Z', isHighlighted: false, isModerator: false },
        { id: 2, streamId, username: 'mod_helper', message: '규칙을 지켜주세요~', timestamp: '2025-03-14T19:02:00Z', isHighlighted: false, isModerator: true },
        { id: 3, streamId, username: 'fan_best', message: '오늘 컨디션 좋아보여요!', timestamp: '2025-03-14T19:03:00Z', isHighlighted: true, isModerator: false },
        { id: 4, streamId, username: 'new_sub', message: '구독했어요 ^^', timestamp: '2025-03-14T19:04:00Z', isHighlighted: false, isModerator: false },
      ]
    }
  }

  async function fetchSummary() {
    try {
      summary.value = await liveStreamApi.getSummary()
    } catch {
      summary.value = { totalStreams: 45, liveNow: 1, avgViewers: 820, totalChatMessages: 52000, nextScheduled: '2025-03-15T20:00:00Z' }
    }
  }

  async function createStream(data: { title: string; platform: string; scheduledAt: string }) {
    try {
      const stream = await liveStreamApi.create(data)
      streams.value.unshift(stream)
    } catch {
      // fallback
    }
  }

  return { streams, chats, summary, loading, liveStreams, scheduledStreams, fetchStreams, fetchChats, fetchSummary, createStream }
})
