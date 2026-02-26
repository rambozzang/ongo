import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'
import type {
  Sponsorship,
  SponsorshipSummary,
  Deliverable,
  CreateSponsorshipRequest,
  CreateDeliverableRequest,
} from '@/types/sponsorshipTracker'

export const sponsorshipTrackerApi = {
  getSponsorships: (status?: string) =>
    apiClient.get<ResData<Sponsorship[]>>('/sponsorships', { params: { status } }).then(unwrapResponse),

  getSponsorship: (id: number) =>
    apiClient.get<ResData<Sponsorship>>(`/sponsorships/${id}`).then(unwrapResponse),

  createSponsorship: (request: CreateSponsorshipRequest) =>
    apiClient.post<ResData<Sponsorship>>('/sponsorships', request).then(unwrapResponse),

  updateSponsorship: (id: number, data: Partial<Sponsorship>) =>
    apiClient.put<ResData<Sponsorship>>(`/sponsorships/${id}`, data).then(unwrapResponse),

  deleteSponsorship: (id: number) =>
    apiClient.delete<ResData<void>>(`/sponsorships/${id}`).then(unwrapResponse),

  getSummary: () =>
    apiClient.get<ResData<SponsorshipSummary>>('/sponsorships/summary').then(unwrapResponse),

  createDeliverable: (request: CreateDeliverableRequest) =>
    apiClient.post<ResData<Deliverable>>('/sponsorships/deliverables', request).then(unwrapResponse),

  completeDeliverable: (id: number) =>
    apiClient.put<ResData<Deliverable>>(`/sponsorships/deliverables/${id}/complete`).then(unwrapResponse),

  deleteDeliverable: (id: number) =>
    apiClient.delete<ResData<void>>(`/sponsorships/deliverables/${id}`).then(unwrapResponse),
}
