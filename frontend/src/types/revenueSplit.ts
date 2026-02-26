export interface RevenueSplit {
  id: number
  title: string
  description: string
  totalAmount: number
  currency: string
  status: RevenueSplitStatus
  period: string
  members: SplitMember[]
  createdAt: string
  updatedAt: string
}

export type RevenueSplitStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'DISTRIBUTED' | 'DISPUTED'

export interface SplitMember {
  id: number
  name: string
  email: string
  role: string
  percentage: number
  amount: number
  paymentStatus: 'PENDING' | 'PAID' | 'FAILED'
  paidAt?: string
}

export interface RevenueSplitSummary {
  totalSplits: number
  pendingAmount: number
  distributedAmount: number
  totalMembers: number
  averageSplit: number
}

export interface CreateRevenueSplitRequest {
  title: string
  description: string
  totalAmount: number
  currency: string
  period: string
  members: CreateSplitMemberRequest[]
}

export interface CreateSplitMemberRequest {
  name: string
  email: string
  role: string
  percentage: number
}
