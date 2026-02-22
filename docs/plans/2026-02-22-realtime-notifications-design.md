# 실시간 알림 WebSocket 연결 설계

## 목표

프론트엔드에 STOMP.js 클라이언트를 추가하여 기존 백엔드 WebSocket 인프라(`WebSocketConfig.kt`, `WebSocketNotificationService.kt`)와 연결하고, 실시간 알림 토스트 팝업 + 뱃지 카운터 업데이트를 구현한다.

## 아키텍처

```
[백엔드 이벤트] → WebSocketNotificationService.sendToUser(userId, type, payload)
                          ↓
              STOMP /queue/user/{userId}
                          ↓
        [프론트엔드 useWebSocket composable]
                    ↓              ↓
         notificationCenter    notification (toast)
         (알림 추가 + 뱃지)    (토스트 팝업 5초)
```

## 기존 인프라 (변경 없음)

### 백엔드
- `WebSocketConfig.kt`: STOMP + SockJS, endpoint `/ws`, broker `/topic`, `/queue`
- `WebSocketNotificationService.kt`: `sendToUser(userId, type, payload)` → `/queue/user/{userId}`
- 메시지 포맷: `{ "type": "CREDIT_LOW", "payload": {...}, "timestamp": 1708000000 }`

### 프론트엔드 (기존)
- `notificationCenter.ts`: Pinia 스토어, `addNotification()`, `fetchNotifications()`, `unreadCount` computed
- `notification.ts`: 토스트 스토어, `info()`, `success()`, `error()` 등
- `TopBar.vue`: 알림 벨 아이콘 + 뱃지 카운터 (notificationCenter.unreadCount 반응)

## 변경 사항

### 1. 새 파일: `frontend/src/composables/useWebSocket.ts`

STOMP 클라이언트 연결 관리 composable:
- `connect(userId, accessToken)`: SockJS → STOMP 연결, `/queue/user/{userId}` 구독
- `disconnect()`: 연결 해제, 구독 취소
- `onMessage(frame)`: 메시지 타입별 분기 처리
  - 알림 타입 → `notificationCenter.addNotification()` + `toast.info()`
  - 기타 → 무시 (향후 확장)
- 재연결: exponential backoff (1초→2초→4초→...최대 30초)
- 재연결 성공 시: `notificationCenter.fetchNotifications()` 호출로 누락 알림 동기화

### 2. 수정: `frontend/src/components/layout/AppLayout.vue`

- `onMounted`: 인증된 사용자이면 `useWebSocket().connect(userId, accessToken)` 호출
- `onUnmounted`: `disconnect()` 호출
- auth 스토어의 로그아웃 감지 → `disconnect()`

### 3. 수정: `frontend/src/stores/notificationCenter.ts`

- `addNotification()`에서 WebSocket 메시지 포맷을 Notification 타입으로 매핑하는 헬퍼 추가
- `addRealtimeNotification(wsMessage)`: WebSocket 메시지 → Notification 변환 후 추가

## 메시지 타입 매핑

| 백엔드 type | 프론트 NotificationType | category | 토스트 타입 |
|-------------|------------------------|----------|------------|
| UPLOAD_COMPLETE | upload_success | upload | success |
| UPLOAD_FAILED | upload_failed | upload | error |
| CREDIT_LOW | credit_low | ai | warning |
| SCHEDULE_REMINDER | schedule_reminder | schedule | info |
| COMMENT | comment | upload | info |
| SYSTEM | system | upload | info |

## 재연결 전략

1. 연결 끊김 감지
2. Exponential backoff: 1s → 2s → 4s → 8s → 16s → 30s (최대)
3. 재연결 성공 시 `fetchNotifications()` API 호출로 누락분 동기화
4. 브라우저 탭 비활성 시 재연결 시도 중단, 활성화 시 재개

## 의존성

- `@stomp/stompjs` (STOMP 클라이언트)
- `sockjs-client` (SockJS 폴백, 백엔드 `.withSockJS()` 대응)

## 제약사항

- 백엔드 WebSocket 인프라 변경 없음
- 인증은 STOMP connect 시 헤더로 accessToken 전달 (현재 백엔드에 인증 핸들러 없으므로 헤더만 전달, 향후 `ChannelInterceptor` 추가 가능)
