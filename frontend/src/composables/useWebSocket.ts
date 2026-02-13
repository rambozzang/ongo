import { ref, onUnmounted, type Ref } from 'vue'

export interface WsMessage {
  type: string
  payload: unknown
  timestamp: number
}

export interface OnlineMember {
  userId: number
  name: string
  connectedAt: number
}

interface UseWebSocketReturn {
  connected: Ref<boolean>
  messages: Ref<WsMessage[]>
  onlineMembers: Ref<OnlineMember[]>
  connect: (userId: number, teamId?: number) => void
  disconnect: () => void
  send: (destination: string, body: unknown) => void
}

/**
 * WebSocket composable using native WebSocket + simple STOMP-like protocol.
 * Connects to /ws endpoint with SockJS fallback URL.
 */
export function useWebSocket(): UseWebSocketReturn {
  const connected = ref(false)
  const messages = ref<WsMessage[]>([])
  const onlineMembers = ref<OnlineMember[]>([])

  let ws: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let currentUserId: number | null = null
  let currentTeamId: number | null = null

  function getWsUrl(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const host = import.meta.env.VITE_WS_HOST || window.location.host
    return `${protocol}//${host}/ws/websocket`
  }

  function connect(userId: number, teamId?: number) {
    currentUserId = userId
    currentTeamId = teamId ?? null

    if (ws && ws.readyState === WebSocket.OPEN) {
      return
    }

    try {
      ws = new WebSocket(getWsUrl())

      ws.onopen = () => {
        connected.value = true

        // Send STOMP CONNECT frame
        sendFrame('CONNECT', { 'accept-version': '1.2', 'heart-beat': '10000,10000' })

        // Subscribe to user queue
        sendFrame('SUBSCRIBE', {
          id: `sub-user-${userId}`,
          destination: `/queue/user/${userId}`,
        })

        // Subscribe to team topic if teamId provided
        if (teamId) {
          sendFrame('SUBSCRIBE', {
            id: `sub-team-${teamId}`,
            destination: `/topic/team/${teamId}`,
          })
        }

        // Start heartbeat
        heartbeatTimer = setInterval(() => {
          if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send('\n') // STOMP heartbeat
          }
        }, 10000)
      }

      ws.onmessage = (event) => {
        const data = event.data as string
        if (data === '\n') return // heartbeat

        // Parse STOMP frame
        const frame = parseStompFrame(data)
        if (frame.command === 'MESSAGE' && frame.body) {
          try {
            const msg = JSON.parse(frame.body) as WsMessage
            messages.value = [msg, ...messages.value.slice(0, 99)]

            // Handle presence updates
            if (msg.type === 'PRESENCE_UPDATE') {
              const members = msg.payload as OnlineMember[]
              onlineMembers.value = members
            }
          } catch {
            // ignore parse errors
          }
        }
      }

      ws.onclose = () => {
        connected.value = false
        cleanup()
        // Auto-reconnect after 3 seconds
        reconnectTimer = setTimeout(() => {
          if (currentUserId !== null) {
            connect(currentUserId, currentTeamId ?? undefined)
          }
        }, 3000)
      }

      ws.onerror = () => {
        connected.value = false
      }
    } catch {
      connected.value = false
    }
  }

  function disconnect() {
    currentUserId = null
    currentTeamId = null
    if (ws) {
      sendFrame('DISCONNECT', {})
      ws.close()
      ws = null
    }
    cleanup()
    connected.value = false
  }

  function send(destination: string, body: unknown) {
    sendFrame('SEND', { destination }, JSON.stringify(body))
  }

  function sendFrame(command: string, headers: Record<string, string>, body?: string) {
    if (!ws || ws.readyState !== WebSocket.OPEN) return

    let frame = command + '\n'
    for (const [key, value] of Object.entries(headers)) {
      frame += `${key}:${value}\n`
    }
    frame += '\n'
    if (body) {
      frame += body
    }
    frame += '\0'

    ws.send(frame)
  }

  function parseStompFrame(data: string): { command: string; headers: Record<string, string>; body: string } {
    const lines = data.split('\n')
    const command = lines[0] || ''
    const headers: Record<string, string> = {}
    let bodyStart = 1

    for (let i = 1; i < lines.length; i++) {
      const line = lines[i]
      if (line === '') {
        bodyStart = i + 1
        break
      }
      const colonIdx = line.indexOf(':')
      if (colonIdx > 0) {
        headers[line.substring(0, colonIdx)] = line.substring(colonIdx + 1)
      }
    }

    const body = lines.slice(bodyStart).join('\n').replace(/\0$/, '')
    return { command, headers, body }
  }

  function cleanup() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  onUnmounted(() => {
    disconnect()
  })

  return {
    connected,
    messages,
    onlineMembers,
    connect,
    disconnect,
    send,
  }
}
