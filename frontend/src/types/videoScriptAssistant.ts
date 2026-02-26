export interface VideoScript {
  id: number
  title: string
  videoId: number | null
  videoTitle: string | null
  content: string
  tone: 'CASUAL' | 'PROFESSIONAL' | 'HUMOROUS' | 'EDUCATIONAL' | 'DRAMATIC'
  targetLength: number
  wordCount: number
  hookLine: string | null
  ctaText: string | null
  status: 'DRAFT' | 'GENERATING' | 'COMPLETED' | 'APPLIED'
  createdAt: string
  updatedAt: string
}

export interface ScriptSuggestion {
  id: number
  scriptId: number
  sectionType: 'HOOK' | 'INTRO' | 'BODY' | 'TRANSITION' | 'CTA' | 'OUTRO'
  originalText: string
  suggestedText: string
  reason: string
  isApplied: boolean
  createdAt: string
}

export interface VideoScriptAssistantSummary {
  totalScripts: number
  completedScripts: number
  avgWordCount: number
  topTone: string
  suggestionsApplied: number
}
