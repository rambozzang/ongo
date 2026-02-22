export interface BrandDeal {
  id: number
  brandName: string
  contactName?: string
  contactEmail?: string
  dealValue?: number
  currency: string
  status: string
  deadline?: string
  deliverables: string[]
  notes?: string
  createdAt?: string
}

export interface MediaKit {
  id: number
  displayName: string
  bio?: string
  categories: string[]
  socialLinks: Record<string, string>
  rateCard: Record<string, any>
  isPublic: boolean
  slug?: string
  createdAt?: string
}

export type DealStatus = 'INQUIRY' | 'NEGOTIATION' | 'CONTRACTED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
