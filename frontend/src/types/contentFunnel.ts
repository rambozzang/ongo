export interface FunnelStage {
  name: string
  count: number
  rate: number
  dropOff: number
}

export interface VideoFunnel {
  id: number
  videoTitle: string
  platform: string
  stages: FunnelStage[]
  overallConversion: number
  analyzedAt: string
}

export interface FunnelComparison {
  videoA: VideoFunnel
  videoB: VideoFunnel
  stageWinners: { stage: string; winner: 'A' | 'B' | 'TIE' }[]
}

export interface FunnelSummary {
  totalVideos: number
  avgConversion: number
  bestConversionVideo: string
  worstDropOffStage: string
}
