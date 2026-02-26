export type BrandColor = 'RED' | 'BLUE' | 'GREEN' | 'PURPLE' | 'ORANGE' | 'PINK' | 'TEAL' | 'YELLOW'

export interface Brand {
  id: number
  name: string
  logoUrl?: string
  color: BrandColor
  category: string
  assignedEditors: string[]
  totalScheduled: number
  totalPublished: number
  isActive: boolean
  createdAt: string
}

export interface BrandScheduleItem {
  id: number
  brandId: number
  brandName: string
  brandColor: BrandColor
  title: string
  platform: string
  scheduledAt: string
  status: 'DRAFT' | 'SCHEDULED' | 'PUBLISHING' | 'PUBLISHED' | 'FAILED'
  assignedTo?: string
  videoId?: string
  notes?: string
}

export interface CalendarConflict {
  date: string
  items: BrandScheduleItem[]
  severity: 'LOW' | 'MEDIUM' | 'HIGH'
  message: string
}

export interface BrandPerformanceReport {
  brandId: number
  brandName: string
  period: string
  totalUploads: number
  totalViews: number
  totalEngagement: number
  avgCtr: number
  revenueGenerated: number
  topContent: { title: string; views: number; platform: string }[]
}

export interface MultiBrandSummary {
  totalBrands: number
  activeBrands: number
  totalScheduledThisWeek: number
  totalPublishedThisMonth: number
  conflicts: CalendarConflict[]
  brandPerformances: BrandPerformanceReport[]
}

export interface CreateBrandRequest {
  name: string
  color: BrandColor
  category: string
  assignedEditors?: string[]
}

export interface CreateScheduleRequest {
  brandId: number
  title: string
  platform: string
  scheduledAt: string
  assignedTo?: string
  notes?: string
}
