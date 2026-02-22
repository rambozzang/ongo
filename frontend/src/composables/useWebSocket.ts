import { ref, type Ref } from 'vue'
import { Client } from '@stomp/stompjs'
// @ts-ignore
import SockJS from 'sockjs-client/dist/sockjs'
import { useNotificationCenterStore } from '@/stores/notificationCenter'
import { useNotificationStore } from '@/stores/notification'
import type { NotificationCategory } from '@/types/notification'

export interface WsMessage {
  type: string
  payload: Record<string, unknown>
  timestamp: number
}

export interface OnlineMember {
  userId: number
  name: string
  connectedAt: number
}

interface UseWebSocketReturn {
  connected: Ref<boolean>
  connect: (userId: number, accessToken: string) => void
  disconnect: () => void
}

const connected = ref(false)
let stompClient: Client | null = null
let reconnectDelay = 1000
const MAX_RECONNECT_DELAY = 30000

function mapWsTypeToNotification(type: string): {
  notifType: string
  category: NotificationCategory
  toastType: 'success' | 'error' | 'warning' | 'info'
} {
  switch (type) {
    case 'UPLOAD_COMPLETE':
      return { notifType: 'upload_success', category: 'upload', toastType: 'success' }
    case 'UPLOAD_FAILED':
      return { notifType: 'upload_failed', category: 'upload', toastType: 'error' }
    case 'CREDIT_LOW':
      return { notifType: 'credit_low', category: 'ai', toastType: 'warning' }
    case 'SCHEDULE_REMINDER':
      return { notifType: 'schedule_reminder', category: 'schedule', toastType: 'info' }
    case 'COMMENT':
      return { notifType: 'comment', category: 'upload', toastType: 'info' }
    default:
      return { notifType: type.toLowerCase(), category: 'upload', toastType: 'info' }
  }
}

export function useWebSocket(): UseWebSocketReturn {
  function connect(userId: number, accessToken: string) {
    if (stompClient?.connected) return

    const wsUrl = `${window.location.protocol === 'https:' ? 'https:' : 'http:'}//${window.location.host}/ws`

    stompClient = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      connectHeaders: {
        Authorization: `Bearer ${accessToken}`,
      },
      reconnectDelay: 0, // 수동 재연결 관리
      onConnect: () => {
        connected.value = true
        reconnectDelay = 1000 // 재연결 성공 시 초기화

        // 사용자 큐 구독
        stompClient?.subscribe(`/queue/user/${userId}`, (frame) => {
          try {
            const message = JSON.parse(frame.body) as WsMessage
            handleMessage(message)
          } catch {
            // 파싱 실패 무시
          }
        })

        // 재연결 시 누락된 알림 동기화
        const notifCenter = useNotificationCenterStore()
        notifCenter.fetchNotifications()
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame.headers['message'])
        connected.value = false
      },
      onWebSocketClose: () => {
        connected.value = false
        scheduleReconnect(userId, accessToken)
      },
    })

    stompClient.activate()
  }

  function handleMessage(message: WsMessage) {
    const { notifType, category, toastType } = mapWsTypeToNotification(message.type)
    const payload = message.payload || {}

    const title = (payload.title as string) || message.type
    const body = (payload.message as string) || ''

    // 1) notificationCenter 스토어에 추가
    const notifCenter = useNotificationCenterStore()
    notifCenter.addNotification({
      type: notifType as any,
      category,
      title,
      message: body,
      isRead: false,
      referenceType: payload.referenceType as string | undefined,
      referenceId: payload.referenceId as number | undefined,
    })

    // 2) 토스트 팝업 표시
    const toastStore = useNotificationStore()
    toastStore.showToast({
      type: toastType,
      title,
      message: body,
      duration: 5000,
    })
  }

  function scheduleReconnect(userId: number, accessToken: string) {
    if (document.hidden) {
      // 탭 비활성 시 재연결 대기 → 탭 활성화 시 재연결
      const onVisible = () => {
        if (!document.hidden) {
          document.removeEventListener('visibilitychange', onVisible)
          connect(userId, accessToken)
        }
      }
      document.addEventListener('visibilitychange', onVisible)
      return
    }

    setTimeout(() => {
      reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY)
      connect(userId, accessToken)
    }, reconnectDelay)
  }

  function disconnect() {
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
    connected.value = false
    reconnectDelay = 1000
  }

  return {
    connected,
    connect,
    disconnect,
  }
}
