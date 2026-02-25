export type CheckStatus = 'PENDING' | 'CHECKING' | 'PASSED' | 'WARNING' | 'BLOCKED'
export type IssueType = 'MUSIC' | 'IMAGE' | 'BRAND' | 'CONTENT_POLICY' | 'MONETIZATION'
export type IssueSeverity = 'INFO' | 'WARNING' | 'CRITICAL'

export interface CopyrightIssue {
  id: string
  type: IssueType
  severity: IssueSeverity
  title: string
  description: string
  timestamp?: { start: number; end: number }
  suggestion: string
  autoFixAvailable: boolean
}

export interface CopyrightCheckResult {
  id: number
  videoId: number
  videoTitle: string
  status: CheckStatus
  issues: CopyrightIssue[]
  musicDetected: { title: string; artist: string; licensed: boolean; timestamp: { start: number; end: number } }[]
  monetizationEligible: boolean
  platformChecks: {
    platform: string
    status: CheckStatus
    issues: string[]
  }[]
  checkedAt: string
}

export interface RunCheckRequest {
  videoId: number
  platforms: string[]
  checkMusic: boolean
  checkContent: boolean
  checkBrand: boolean
}

export interface RunCheckResponse {
  result: CopyrightCheckResult
  creditsUsed: number
  creditsRemaining: number
}

export interface VideoForCheck {
  id: number
  title: string
  thumbnailUrl: string
  duration: number
  hasBeenChecked: boolean
  lastCheckStatus?: CheckStatus
}
