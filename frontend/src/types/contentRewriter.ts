export type OutputFormat = 'SHORT_VIDEO' | 'TWEET' | 'BLOG_POST' | 'CAPTION' | 'NEWSLETTER' | 'THREAD'
export type RewriteStatus = 'IDLE' | 'REWRITING' | 'DONE' | 'FAILED'

export interface RewriteSource {
  id: number
  title: string
  type: 'VIDEO_SCRIPT' | 'BLOG' | 'TRANSCRIPT' | 'RAW_TEXT'
  content: string
  wordCount: number
  createdAt: string
}

export interface RewriteResult {
  id: number
  sourceId: number
  format: OutputFormat
  platform: string
  text: string
  hashtags: string[]
  characterCount: number
  estimatedEngagement: number
  createdAt: string
}

export interface RewriteRequest {
  sourceText: string
  sourceType: RewriteSource['type']
  outputFormats: OutputFormat[]
  targetPlatforms: string[]
  tone?: string
  maxLength?: number
}

export interface RewriteResponse {
  results: RewriteResult[]
  creditsUsed: number
  creditsRemaining: number
}
