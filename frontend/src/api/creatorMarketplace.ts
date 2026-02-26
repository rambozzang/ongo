import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { MarketplaceListing, MarketplaceOrder, MarketplaceSummary } from '@/types/creatorMarketplace'

export const creatorMarketplaceApi = {
  getListings: (serviceType?: string) =>
    apiClient.get<ResData<MarketplaceListing[]>>('/api/v1/creator-marketplace', { params: { serviceType } }).then(unwrapResponse),
  getListing: (id: number) =>
    apiClient.get<ResData<MarketplaceListing>>(`/api/v1/creator-marketplace/${id}`).then(unwrapResponse),
  createListing: (data: Partial<MarketplaceListing>) =>
    apiClient.post<ResData<MarketplaceListing>>('/api/v1/creator-marketplace', data).then(unwrapResponse),
  getOrders: () =>
    apiClient.get<ResData<MarketplaceOrder[]>>('/api/v1/creator-marketplace/orders').then(unwrapResponse),
  placeOrder: (listingId: number) =>
    apiClient.post<ResData<MarketplaceOrder>>(`/api/v1/creator-marketplace/${listingId}/order`).then(unwrapResponse),
  getSummary: () =>
    apiClient.get<ResData<MarketplaceSummary>>('/api/v1/creator-marketplace/summary').then(unwrapResponse),
}

export default creatorMarketplaceApi
