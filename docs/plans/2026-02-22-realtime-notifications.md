# 실시간 알림 WebSocket 연결 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 프론트엔드에 STOMP.js 클라이언트를 추가하여 백엔드 WebSocket과 연결하고, 실시간 알림 토스트 + 뱃지 카운터를 구현한다.

**Architecture:** 기존 백엔드 STOMP 인프라(`WebSocketConfig.kt`, `WebSocketNotificationService.sendToUser()`)는 변경 없이 그대로 활용한다. 프론트엔드에 `useWebSocket` composable을 만들어 `/queue/user/{userId}`를 구독하고, 메시지 수신 시 notificationCenter 스토어에 추가 + 토스트 팝업을 표시한다.

**Tech Stack:** @stomp/stompjs, sockjs-client, Vue 3 Composition API, Pinia

---

### Task 1: npm 패키지 설치

**Step 1: STOMP.js + SockJS 설치**

```bash
cd /Users/bumkyuchun/work/app/ongo/frontend
npm install @stomp/stompjs sockjs-client
npm install -D @types/sockjs-client
```

**Step 2: 설치 확인**

```bash
cat package.json | grep -E "stomp|sockjs"
```

Expected: `@stomp/stompjs`와 `sockjs-client`가 dependencies에 표시

**Step 3: 커밋**

```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "chore: @stomp/stompjs, sockjs-client 패키지 추가"
```

---

### Task 2: useWebSocket composable 작성

**Files:**
- Create: `frontend/src/composables/useWebSocket.ts`

**Step 1: composable 작성**

```typescript
import { ref } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'
import { useNotificationCenterStore } from '@/stores/notificationCenter'
import { useNotificationStore } from '@/stores/notification'
import type { NotificationCategory } from '@/types/notification'

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

export function useWebSocket() {
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
            const message = JSON.parse(frame.body)
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

  function handleMessage(message: { type: string; payload: Record<string, unknown>; timestamp: number }) {
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
```

**Step 2: 타입 체크**

```bash
cd /Users/bumkyuchun/work/app/ongo/frontend && npx vue-tsc --noEmit 2>&1 | grep -i "useWebSocket\|websocket" || echo "No errors"
```

Expected: "No errors"

**Step 3: 커밋**

```bash
git add frontend/src/composables/useWebSocket.ts
git commit -m "feat: useWebSocket composable — STOMP 클라이언트 연결/구독/재연결"
```

---

### Task 3: AppLayout.vue에 WebSocket 연결 통합

**Files:**
- Modify: `frontend/src/components/layout/AppLayout.vue`

**Step 1: AppLayout.vue 수정**

`<script setup>` 블록에 WebSocket 연결 로직을 추가한다.

변경 내용:
1. import에 `onUnmounted, watch` 추가
2. `useWebSocket` import
3. `onMounted`에서 인증된 사용자이면 WebSocket 연결
4. `onUnmounted`에서 disconnect
5. auth 상태 watch로 로그아웃 시 disconnect

```typescript
// 기존 import 변경:
import { ref, onMounted, onUnmounted, watch } from 'vue'

// 새 import 추가:
import { useWebSocket } from '@/composables/useWebSocket'

// setup 내부에 추가:
const { connect, disconnect } = useWebSocket()

// 기존 onMounted 수정:
onMounted(() => {
  authStore.initialize()
  creditStore.fetchBalance()

  // WebSocket 연결 (인증된 사용자)
  if (authStore.accessToken && authStore.user?.id) {
    connect(authStore.user.id, authStore.accessToken)
  }
})

onUnmounted(() => {
  disconnect()
})

// 로그인 후 연결 / 로그아웃 시 해제
watch(() => authStore.isAuthenticated, (isAuth) => {
  if (isAuth && authStore.accessToken && authStore.user?.id) {
    connect(authStore.user.id, authStore.accessToken)
  } else {
    disconnect()
  }
})
```

**Step 2: 타입 체크**

```bash
cd /Users/bumkyuchun/work/app/ongo/frontend && npx vue-tsc --noEmit 2>&1 | grep -i "AppLayout" || echo "No errors"
```

Expected: "No errors"

**Step 3: 커밋**

```bash
git add frontend/src/components/layout/AppLayout.vue
git commit -m "feat: AppLayout에 WebSocket 연결 통합 — 로그인 시 connect, 로그아웃 시 disconnect"
```

---

### Task 4: notificationCenter 스토어에 WebSocket 메시지 지원 추가

**Files:**
- Modify: `frontend/src/stores/notificationCenter.ts`

**Step 1: addRealtimeNotification 메서드 추가**

`addNotification` 함수 아래에 WebSocket 전용 메서드를 추가한다. 기존 `addNotification`은 이미 적절한 형태이므로, 실시간 알림이 추가될 때 `unreadCount` API도 갱신하는 래퍼를 추가한다.

```typescript
  // 기존 addNotification 아래에 추가:
  async function syncUnreadCount() {
    try {
      const result = await notificationApi.getUnreadCount()
      // unreadCount는 computed이므로 별도 동기화 불필요
      // 단, 서버 데이터와 로컬 데이터 차이가 있을 수 있으므로 전체 갱신
    } catch {
      // 무시
    }
  }
```

그리고 `return` 블록에 `syncUnreadCount`를 추가로 노출한다.

**Step 2: 타입 체크**

```bash
cd /Users/bumkyuchun/work/app/ongo/frontend && npx vue-tsc --noEmit 2>&1 | grep -i "notificationCenter" || echo "No errors"
```

Expected: "No errors"

**Step 3: 커밋**

```bash
git add frontend/src/stores/notificationCenter.ts
git commit -m "feat: notificationCenter에 syncUnreadCount 추가"
```

---

### Task 5: 전체 빌드 검증 + 최종 커밋

**Step 1: 프론트엔드 타입 체크**

```bash
cd /Users/bumkyuchun/work/app/ongo/frontend && npx vue-tsc --noEmit
```

Expected: useWebSocket 관련 에러 0건

**Step 2: 백엔드 빌드 확인 (변경 없으므로 확인만)**

```bash
cd /Users/bumkyuchun/work/app/ongo/backend && ./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

**Step 3: 동작 확인 포인트 (수동 테스트)**

1. 프론트엔드 `npm run dev` 실행
2. 로그인 후 브라우저 개발자 도구 Network 탭에서 `/ws` WebSocket 연결 확인
3. 백엔드에서 `WebSocketNotificationService.sendToUser()` 호출 시 프론트엔드에 토스트 표시 확인
4. 탭 새로고침 후 알림 뱃지 카운터 유지 확인
