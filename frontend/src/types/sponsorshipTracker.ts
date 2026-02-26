export type SponsorshipStatus = 'INQUIRY' | 'NEGOTIATION' | 'CONTRACTED' | 'IN_PROGRESS' | 'DELIVERED' | 'PAID' | 'CANCELLED'
export type DeliverableType = 'DEDICATED_VIDEO' | 'INTEGRATED' | 'SHORT_FORM' | 'STORY' | 'POST' | 'LIVE_STREAM'
export type PaymentStatus = 'PENDING' | 'INVOICED' | 'PAID' | 'OVERDUE'

export interface Sponsorship {
  id: number
  brandName: string
  brandLogo?: string
  contactName: string
  contactEmail: string
  status: SponsorshipStatus
  dealValue: number
  currency: string
  startDate: string
  endDate: string
  deliverables: Deliverable[]
  notes?: string
  contractUrl?: string
  paymentStatus: PaymentStatus
  paidAmount: number
  createdAt: string
  updatedAt: string
}

export interface Deliverable {
  id: number
  sponsorshipId: number
  type: DeliverableType
  title: string
  description?: string
  dueDate: string
  isCompleted: boolean
  videoId?: string
  platform: string
  completedAt?: string
}

export interface SponsorshipSummary {
  totalDeals: number
  activeDeals: number
  totalRevenue: number
  pendingPayments: number
  completionRate: number
  avgDealValue: number
  upcomingDeadlines: Deliverable[]
  revenueByMonth: { month: string; revenue: number }[]
}

export interface CreateSponsorshipRequest {
  brandName: string
  contactName: string
  contactEmail: string
  dealValue: number
  currency?: string
  startDate: string
  endDate: string
  notes?: string
}

export interface CreateDeliverableRequest {
  sponsorshipId: number
  type: DeliverableType
  title: string
  description?: string
  dueDate: string
  platform: string
}
