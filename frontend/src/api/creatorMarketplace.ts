import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type { MarketplaceListing, MarketplaceOrder, MarketplaceSummary } from '@/types/creatorMarketplace'

export const creatorMarketplaceApi = {
  getListings: (serviceType?: string) =>
    apiClient.get<ResData<MarketplaceListing[]>>('/creator-marketplace', { params: { serviceType } }).then(unwrapResponse),
  getListing: (id: number) =>
    apiClient.get<ResData<MarketplaceListing>>(`/creator-marketplace/${id}`).then(unwrapResponse),
  createListing: (data: Partial<MarketplaceListing>) =>
    apiClient.post<ResData<MarketplaceListing>>('/creator-marketplace', data).then(unwrapResponse),
  getOrders: () =>
    apiClient.get<ResData<MarketplaceOrder[]>>('/creator-marketplace/orders').then(unwrapResponse),
  placeOrder: (listingId: number) =>
    apiClient.post<ResData<MarketplaceOrder>>(`/creator-marketplace/${listingId}/order`).then(unwrapResponse),
  getSummary: () =>
    apiClient.get<ResData<MarketplaceSummary>>('/creator-marketplace/summary').then(unwrapResponse),
}

export default creatorMarketplaceApi
