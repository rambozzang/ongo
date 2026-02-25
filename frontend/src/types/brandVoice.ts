export type VoiceTone = 'CASUAL' | 'PROFESSIONAL' | 'HUMOROUS' | 'EDUCATIONAL' | 'INSPIRATIONAL'
export type VoiceTrainStatus = 'IDLE' | 'TRAINING' | 'READY' | 'FAILED'

export interface BrandVoiceProfile {
  id: number
  name: string
  tone: VoiceTone
  trainStatus: VoiceTrainStatus
  sampleCount: number
  vocabulary: string[]
  avoidWords: string[]
  emojiUsage: 'NONE' | 'MINIMAL' | 'MODERATE' | 'HEAVY'
  avgSentenceLength: number
  signaturePhrase?: string
  createdAt: string
  updatedAt: string
}

export interface TrainVoiceRequest {
  name: string
  sampleTexts: string[]
  tone: VoiceTone
  avoidWords?: string[]
}

export interface GenerateWithVoiceRequest {
  profileId: number
  prompt: string
  platform: string
  maxLength?: number
  includeHashtags?: boolean
}

export interface GenerateWithVoiceResponse {
  text: string
  hashtags: string[]
  confidenceScore: number
  creditsUsed: number
  creditsRemaining: number
}

export interface VoiceAnalysis {
  detectedTone: VoiceTone
  avgSentenceLength: number
  topWords: { word: string; count: number }[]
  emojiFrequency: number
  formalityScore: number
  readabilityScore: number
}
