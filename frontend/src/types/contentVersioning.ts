export interface ContentVersion {
  id: number
  contentId: number
  contentTitle: string
  versionNumber: number
  changeType: 'TITLE' | 'THUMBNAIL' | 'DESCRIPTION' | 'TAGS' | 'SUBTITLE' | 'VIDEO'
  changeDescription: string
  previousValue?: string
  newValue?: string
  performanceBefore?: VersionPerformance
  performanceAfter?: VersionPerformance
  createdBy: string
  createdAt: string
}

export interface VersionPerformance {
  views: number
  likes: number
  engagement: number
  ctr: number
}

export interface ContentVersionGroup {
  contentId: number
  contentTitle: string
  platform: string
  totalVersions: number
  latestVersion: number
  versions: ContentVersion[]
  createdAt: string
}

export interface ContentVersioningSummary {
  totalContents: number
  totalVersions: number
  avgVersionsPerContent: number
  mostEditedContent: string
  bestPerformingChange: string
}
