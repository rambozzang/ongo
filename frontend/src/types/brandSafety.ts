export interface BrandSafetyCheck {
  id: number
  contentTitle: string
  contentType: string
  platform: string
  status: 'PENDING' | 'SAFE' | 'WARNING' | 'VIOLATION'
  overallScore: number
  checks: SafetyCheckItem[]
  checkedAt: string
  createdAt: string
}

export interface SafetyCheckItem {
  id: number
  category: string
  name: string
  status: 'PASS' | 'WARN' | 'FAIL'
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  description: string
  recommendation?: string
}

export interface BrandSafetyRule {
  id: number
  name: string
  category: string
  description: string
  isEnabled: boolean
  severity: string
  createdAt: string
}

export interface BrandSafetySummary {
  totalChecks: number
  safeCount: number
  warningCount: number
  violationCount: number
  avgScore: number
}

export interface RunSafetyCheckRequest {
  contentId: number
  contentTitle: string
  contentType: string
  platform: string
}
