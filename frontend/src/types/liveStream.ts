export interface LiveStream {
  id: number
  title: string
  description: string
  platform: string
  status: 'SCHEDULED' | 'LIVE' | 'ENDED' | 'CANCELLED'
  scheduledAt: string
  startedAt: string | null
  endedAt: string | null
  viewerCount: number
  peakViewers: number
  chatMessages: number
  streamUrl: string | null
  thumbnailUrl: string | null
}

export interface StreamChat {
  id: number
  streamId: number
  username: string
  message: string
  timestamp: string
  isHighlighted: boolean
  isModerator: boolean
}

export interface LiveStreamSummary {
  totalStreams: number
  liveNow: number
  avgViewers: number
  totalChatMessages: number
  nextScheduled: string | null
}
