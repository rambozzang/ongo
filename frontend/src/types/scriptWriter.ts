export type ScriptTone = 'CASUAL' | 'PROFESSIONAL' | 'HUMOROUS' | 'EDUCATIONAL' | 'DRAMATIC' | 'STORYTELLING'
export type ScriptFormat = 'LONG_FORM' | 'SHORT_FORM' | 'TUTORIAL' | 'REVIEW' | 'VLOG' | 'INTERVIEW'
export type ScriptStatus = 'DRAFT' | 'REVIEWING' | 'FINAL' | 'ARCHIVED'

export interface Script {
  id: number
  title: string
  topic: string
  format: ScriptFormat
  tone: ScriptTone
  status: ScriptStatus
  targetDuration: number
  estimatedWordCount: number
  content: ScriptSection[]
  keywords: string[]
  targetAudience: string
  notes?: string
  creditCost: number
  createdAt: string
  updatedAt: string
}

export interface ScriptSection {
  id: number
  type: 'INTRO' | 'BODY' | 'CTA' | 'OUTRO' | 'HOOK' | 'TRANSITION'
  title: string
  content: string
  duration: number
  notes?: string
  orderNumber: number
}

export interface ScriptTemplate {
  id: number
  name: string
  description: string
  format: ScriptFormat
  tone: ScriptTone
  sections: { type: ScriptSection['type']; title: string; placeholder: string }[]
  usageCount: number
}

export interface GenerateScriptRequest {
  topic: string
  format: ScriptFormat
  tone: ScriptTone
  targetDuration: number
  keywords?: string[]
  targetAudience?: string
  additionalNotes?: string
}

export interface ScriptSummary {
  totalScripts: number
  drafts: number
  finals: number
  totalCreditsUsed: number
  avgDuration: number
  favoriteFormat: string
}
