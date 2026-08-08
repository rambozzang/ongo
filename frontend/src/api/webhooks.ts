import apiClient, { unwrapResponse } from './client'
import type { ResData } from '@/types/api'

export interface WebhookResponse {
  id: number
  name: string
  url: string
  events: string[]
  secret: string | null
  isActive: boolean
  lastTriggeredAt: string | null
  lastStatusCode: number | null
  failureCount: number
  createdAt: string | null
  updatedAt: string | null
  recentDeliveries: WebhookDeliveryResponse[]
}

export interface WebhookDeliveryResponse {
  id: number
  webhookId: number
  eventKey: string
  event: string
  status: string
  statusCode: number | null
  responseBody: string | null
  sentAt: string | null
  attemptCount: number
  nextAttemptAt: string | null
  lastError: string | null
}

export interface CreateWebhookRequest {
  name: string
  url: string
  events?: string[]
}

export interface UpdateWebhookRequest {
  name?: string
  url?: string
  events?: string[]
  isActive?: boolean
}

export interface WebhookTestResponse {
  success: boolean
  statusCode: number | null
  message: string
}

export const webhookApi = {
  list() {
    return apiClient
      .get<ResData<WebhookResponse[]>>('/webhooks')
      .then(unwrapResponse)
  },

  create(request: CreateWebhookRequest) {
    return apiClient
      .post<ResData<WebhookResponse>>('/webhooks', request)
      .then(unwrapResponse)
  },

  update(id: number, request: UpdateWebhookRequest) {
    return apiClient
      .put<ResData<WebhookResponse>>(`/webhooks/${id}`, request)
      .then(unwrapResponse)
  },

  delete(id: number) {
    return apiClient
      .delete<ResData<void>>(`/webhooks/${id}`)
      .then(unwrapResponse)
  },

  test(id: number) {
    return apiClient
      .post<ResData<WebhookTestResponse>>(`/webhooks/${id}/test`)
      .then(unwrapResponse)
  },

  rotateSecret(id: number) {
    return apiClient
      .post<ResData<WebhookResponse>>(`/webhooks/${id}/rotate-secret`)
      .then(unwrapResponse)
  },

  retryDelivery(webhookId: number, deliveryId: number) {
    return apiClient
      .post<ResData<WebhookDeliveryResponse>>(`/webhooks/${webhookId}/deliveries/${deliveryId}/retry`)
      .then(unwrapResponse)
  },
}
