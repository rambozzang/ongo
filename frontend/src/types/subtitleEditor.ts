export type SubtitleStatus = 'DRAFT' | 'GENERATING' | 'READY' | 'EXPORTED'
export type SubtitleFormat = 'SRT' | 'VTT' | 'ASS' | 'TXT'
export type SubtitleLanguage = 'ko' | 'en' | 'ja' | 'zh' | 'auto'

export interface SubtitleCue {
  id: string
  index: number
  startTime: number
  endTime: number
  text: string
  translatedText?: string
  speaker?: string
  confidence: number
}

export interface SubtitleTrack {
  id: number
  videoId: number
  videoTitle: string
  language: SubtitleLanguage
  status: SubtitleStatus
  cues: SubtitleCue[]
  totalDuration: number
  wordCount: number
  createdAt: string
  updatedAt: string
}

export interface GenerateSubtitleRequest {
  videoId: number
  language: SubtitleLanguage
  translateTo?: SubtitleLanguage
  speakerDiarization: boolean
}

export interface GenerateSubtitleResponse {
  track: SubtitleTrack
  creditsUsed: number
  creditsRemaining: number
}

export interface ExportSubtitleRequest {
  trackId: number
  format: SubtitleFormat
}

export interface UpdateCueRequest {
  trackId: number
  cueId: string
  text?: string
  startTime?: number
  endTime?: number
}

export interface VideoForSubtitle {
  id: number
  title: string
  thumbnailUrl: string
  duration: number
  hasSubtitles: boolean
  subtitleCount: number
}
