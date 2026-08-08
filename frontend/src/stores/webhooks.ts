import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Webhook, WebhookEvent, WebhookDelivery } from '@/types/webhook'
import { webhookApi } from '@/api/webhooks'
import { useNotificationStore } from '@/stores/notification'
import type { WebhookResponse, WebhookTestResponse } from '@/api/webhooks'

function mapApiWebhook(w: WebhookResponse): Webhook {
  return {
    id: w.id,
    url: w.url,
    events: (w.events ?? []) as WebhookEvent[],
    secret: w.secret ?? undefined,
    isActive: w.isActive,
    lastTriggeredAt: w.lastTriggeredAt ?? undefined,
    failureCount: w.failureCount,
    createdAt: w.createdAt ?? new Date().toISOString(),
    recentDeliveries: (w.recentDeliveries ?? []).map((delivery) => ({
      id: delivery.id,
      webhookId: delivery.webhookId,
      event: delivery.event as WebhookEvent,
      statusCode: delivery.statusCode,
      responseBody: delivery.responseBody ?? undefined,
      sentAt: delivery.sentAt ?? new Date(0).toISOString(),
      duration: 0,
    })),
  }
}

export const useWebhookStore = defineStore('webhooks', () => {
  // --- State ---
  const webhooks = ref<Webhook[]>([])
  const selectedWebhookId = ref<number | null>(null)
  const loading = ref(false)

  // --- Getters ---
  const activeWebhooks = computed<Webhook[]>(() =>
    webhooks.value.filter((w) => w.isActive),
  )

  const selectedWebhook = computed<Webhook | null>(() => {
    if (selectedWebhookId.value === null) return null
    return webhooks.value.find((w) => w.id === selectedWebhookId.value) ?? null
  })

  // --- Actions ---
  async function fetchWebhooks() {
    loading.value = true
    try {
      const data = await webhookApi.list()
      webhooks.value = data.map(mapApiWebhook)
    } catch (e) {

      useNotificationStore().error('웹훅 처리 중 오류가 발생했습니다')
    } finally {
      loading.value = false
    }
  }

  async function createWebhook(data: { url: string; events: WebhookEvent[]; secret?: string }) {
    try {
      const result = await webhookApi.create({
        name: data.url,
        url: data.url,
        events: data.events,
      })
      const newWebhook = mapApiWebhook(result)
      webhooks.value.push(newWebhook)
      return newWebhook
    } catch (e) {
      useNotificationStore().error('웹훅 처리 중 오류가 발생했습니다')
      throw e
    }
  }

  async function updateWebhook(id: number, data: { url?: string; events?: WebhookEvent[] }) {
    try {
      const result = await webhookApi.update(id, {
        url: data.url,
        events: data.events,
      })
      const index = webhooks.value.findIndex((w) => w.id === id)
      if (index !== -1) {
        webhooks.value[index] = { ...webhooks.value[index], ...mapApiWebhook(result) }
      }
    } catch (e) {
      useNotificationStore().error('웹훅 처리 중 오류가 발생했습니다')
      throw e
    }
  }

  async function deleteWebhook(id: number) {
    try {
      await webhookApi.delete(id)
    } catch (e) {
      useNotificationStore().error('웹훅 처리 중 오류가 발생했습니다')
      throw e
    }
    const index = webhooks.value.findIndex((w) => w.id === id)
    if (index !== -1) {
      webhooks.value.splice(index, 1)
      if (selectedWebhookId.value === id) {
        selectedWebhookId.value = null
      }
    }
  }

  async function toggleActive(id: number) {
    const webhook = webhooks.value.find((w) => w.id === id)
    if (webhook) {
      const result = await webhookApi.update(id, { isActive: !webhook.isActive })
      const index = webhooks.value.findIndex((w) => w.id === id)
      if (index !== -1) webhooks.value[index] = mapApiWebhook(result)
    }
  }

  async function testWebhook(id: number): Promise<WebhookTestResponse> {
    const webhook = webhooks.value.find((w) => w.id === id)
    if (!webhook) throw new Error('웹훅을 찾을 수 없습니다')

    try {
      const result = await webhookApi.test(id)
      await fetchWebhooks()
      return result
    } catch (e) {
      useNotificationStore().error('웹훅 처리 중 오류가 발생했습니다')
      throw e
    }
  }

  async function regenerateSecret(id: number): Promise<string> {
    const webhook = webhooks.value.find((w) => w.id === id)
    if (!webhook) throw new Error('웹훅을 찾을 수 없습니다')

    const updated = await webhookApi.rotateSecret(id)
    const index = webhooks.value.findIndex((w) => w.id === id)
    if (index !== -1) webhooks.value[index] = mapApiWebhook(updated)
    return updated.secret ?? ''
  }

  async function retryDelivery(webhookId: number, deliveryId: number): Promise<WebhookDelivery> {
    const webhook = webhooks.value.find((w) => w.id === webhookId)
    if (!webhook) throw new Error('웹훅을 찾을 수 없습니다')

    const originalDelivery = webhook.recentDeliveries?.find((d) => d.id === deliveryId)
    if (!originalDelivery) throw new Error('배달 기록을 찾을 수 없습니다')

    try {
      const result = await webhookApi.retryDelivery(webhookId, deliveryId)
      const retried: WebhookDelivery = {
        id: result.id,
        webhookId: result.webhookId,
        event: result.event as WebhookEvent,
        statusCode: result.statusCode,
        responseBody: result.responseBody ?? undefined,
        sentAt: result.sentAt ?? new Date(0).toISOString(),
        duration: 0,
      }

      if (!webhook.recentDeliveries) {
        webhook.recentDeliveries = []
      }
      webhook.recentDeliveries.unshift(retried)
      webhook.lastTriggeredAt = retried.sentAt

      return retried
    } catch (e) {
      useNotificationStore().error('웹훅 처리 중 오류가 발생했습니다')
      throw e
    }
  }

  return {
    webhooks,
    selectedWebhookId,
    loading,
    activeWebhooks,
    selectedWebhook,
    fetchWebhooks,
    createWebhook,
    updateWebhook,
    deleteWebhook,
    toggleActive,
    testWebhook,
    regenerateSecret,
    retryDelivery,
  }
})
