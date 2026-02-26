export interface MarketplaceListing {
  id: number
  creatorId: number
  creatorName: string
  serviceType: 'EDITING' | 'THUMBNAIL' | 'SCRIPT' | 'VOICEOVER' | 'CONSULTING' | 'COLLAB'
  title: string
  description: string
  price: number
  currency: string
  rating: number
  reviewCount: number
  deliveryDays: number
  isActive: boolean
  createdAt: string
}

export interface MarketplaceOrder {
  id: number
  listingId: number
  buyerId: number
  buyerName: string
  sellerId: number
  sellerName: string
  status: 'PENDING' | 'ACCEPTED' | 'IN_PROGRESS' | 'DELIVERED' | 'COMPLETED' | 'CANCELLED'
  totalPrice: number
  orderDate: string
  deliveryDate: string | null
}

export interface MarketplaceSummary {
  totalListings: number
  activeOrders: number
  totalRevenue: number
  avgRating: number
  topServiceType: string
}
