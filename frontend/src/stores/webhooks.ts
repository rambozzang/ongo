import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Webhook, WebhookEvent, WebhookDelivery } from '@/types/webhook'
import { webhookApi } from '@/api/webhooks'
import { useNotificationStore } from '@/stores/notification'
import type { WebhookResponse } from '@/api/webhooks'

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
    recentDeliveries: [],
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
      // Fallback to local
      const newId = Math.max(...webhooks.value.map((w) => w.id), 0) + 1
      const newWebhook: Webhook = {
        id: newId,
        url: data.url,
        events: data.events,
        secret: data.secret || generateSecret(),
        isActive: true,
        failureCount: 0,
        createdAt: new Date().toISOString(),
        recentDeliveries: [],
      }
      webhooks.value.push(newWebhook)
      return newWebhook
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
      const index = webhooks.value.findIndex((w) => w.id === id)
      if (index !== -1) {
        if (data.url !== undefined) webhooks.value[index].url = data.url
        if (data.events !== undefined) webhooks.value[index].events = data.events
      }
    }
  }

  async function deleteWebhook(id: number) {
    try {
      await webhookApi.delete(id)
    } catch (e) {

      useNotificationStore().error('웹훅 처리 중 오류가 발생했습니다')
    }
    const index = webhooks.value.findIndex((w) => w.id === id)
    if (index !== -1) {
      webhooks.value.splice(index, 1)
      if (selectedWebhookId.value === id) {
        selectedWebhookId.value = null
      }
    }
  }

  function toggleActive(id: number) {
    const webhook = webhooks.value.find((w) => w.id === id)
    if (webhook) {
      webhook.isActive = !webhook.isActive
    }
  }

  async function testWebhook(id: number): Promise<WebhookDelivery> {
    const webhook = webhooks.value.find((w) => w.id === id)
    if (!webhook) throw new Error('웹훅을 찾을 수 없습니다')

    try {
      const result = await webhookApi.test(id)
      const delivery: WebhookDelivery = {
        id: Date.now(),
        webhookId: id,
        event: webhook.events[0] || 'video.uploaded',
        statusCode: result.statusCode ?? 200,
        responseBody: result.message,
        sentAt: new Date().toISOString(),
        duration: 0,
      }
      if (!webhook.recentDeliveries) webhook.recentDeliveries = []
      webhook.recentDeliveries.unshift(delivery)
      webhook.lastTriggeredAt = delivery.sentAt
      return delivery
    } catch (e) {

      useNotificationStore().error('웹훅 처리 중 오류가 발생했습니다')
      // Fallback to simulated
      const delivery: WebhookDelivery = {
        id: Date.now(),
        webhookId: id,
        event: webhook.events[0] || 'video.uploaded',
        statusCode: 500,
        responseBody: '{"error":"Test failed"}',
        sentAt: new Date().toISOString(),
        duration: 0,
      }
      if (!webhook.recentDeliveries) webhook.recentDeliveries = []
      webhook.recentDeliveries.unshift(delivery)
      webhook.failureCount++
      return delivery
    }
  }

  function regenerateSecret(id: number): string {
    const webhook = webhooks.value.find((w) => w.id === id)
    if (!webhook) throw new Error('웹훅을 찾을 수 없습니다')

    const newSecret = generateSecret()
    webhook.secret = newSecret
    return newSecret
  }

  async function retryDelivery(webhookId: number, deliveryId: number): Promise<WebhookDelivery> {
    const webhook = webhooks.value.find((w) => w.id === webhookId)
    if (!webhook) throw new Error('웹훅을 찾을 수 없습니다')

    const originalDelivery = webhook.recentDeliveries?.find((d) => d.id === deliveryId)
    if (!originalDelivery) throw new Error('배달 기록을 찾을 수 없습니다')

    try {
      const result = await webhookApi.test(webhookId)
      const retried: WebhookDelivery = {
        id: Date.now(),
        webhookId,
        event: originalDelivery.event,
        statusCode: result.statusCode ?? 200,
        responseBody: result.message ?? '{"ok":true}',
        sentAt: new Date().toISOString(),
        duration: 0,
      }

      if (!webhook.recentDeliveries) {
        webhook.recentDeliveries = []
      }
      webhook.recentDeliveries.unshift(retried)
      webhook.lastTriggeredAt = retried.sentAt

      if ((result.statusCode ?? 200) < 400 && webhook.failureCount > 0) {
        webhook.failureCount--
      }

      return retried
    } catch (e) {

      useNotificationStore().error('웹훅 처리 중 오류가 발생했습니다')
      const retried: WebhookDelivery = {
        id: Date.now(),
        webhookId,
        event: originalDelivery.event,
        statusCode: 500,
        responseBody: '{"error":"Retry failed"}',
        sentAt: new Date().toISOString(),
        duration: 0,
      }

      if (!webhook.recentDeliveries) {
        webhook.recentDeliveries = []
      }
      webhook.recentDeliveries.unshift(retried)
      webhook.failureCount++

      return retried
    }
  }

  // --- Helpers ---
  function generateSecret(): string {
    const array = new Uint8Array(20)
    crypto.getRandomValues(array)
    const hex = Array.from(array, (b) => b.toString(36)).join('').slice(0, 20)
    return `whsec_${hex}`
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
