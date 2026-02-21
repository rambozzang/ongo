# Phase 1 MVP Gap 수정 설계

## 배경

Phase 1 MVP 기능 완전성 점검에서 3개의 Gap을 발견했다. 모두 기존 인프라를 활용하여 최소한의 변경으로 수정 가능하다.

---

## Gap 1: 온보딩 플랜 선택 백엔드 연동 누락

### 현황
- `OnboardingView.vue`에서 `selectedPlan` 상태를 로컬에만 저장
- `completeOnboarding()` 호출 시 플랜 정보 미전달
- 백엔드 `initializeSubscription()`이 항상 FREE로 초기화

### 수정
**프론트엔드** (`OnboardingView.vue`):
1. `subscriptionApi` import 추가
2. `completeOnboarding()` 함수에서 `authApi.completeOnboarding()` 이후, `selectedPlan !== 'FREE'`이면 `subscriptionApi.changePlan({ planType: selectedPlan })` 호출
3. 실패 시에도 온보딩 완료 진행 (플랜은 나중에 변경 가능하므로 비차단)

### 영향 파일
- `frontend/src/views/OnboardingView.vue` (수정)

---

## Gap 2: 저크레딧 알림 이벤트 리스너 미구현

### 현황
- `CreditScheduler`가 매시간 `LowCreditAlertEvent` 발행
- 이벤트를 소비하는 리스너 없음 (orphaned event)
- `NotificationType.CREDIT_LOW` enum, `NotificationRepository`, `WebSocketNotificationService` 모두 존재

### 수정
**백엔드** - 새 파일 `LowCreditAlertEventListener.kt` 생성:
1. `@EventListener`로 `LowCreditAlertEvent` 수신
2. `Notification` 생성 (type=CREDIT_LOW, title="AI 크레딧 부족", message="잔여 크레딧: {balance}/{freeMonthly}")
3. `notificationRepository.save()` 호출
4. `webSocketNotificationService.sendToUser()` 호출하여 실시간 알림 전송

### 영향 파일
- `backend/onGo-application/src/main/kotlin/com/ongo/application/credit/LowCreditAlertEventListener.kt` (신규)

---

## Gap 3: 구독자 변화 트렌드 차트 미구현

### 현황
- `TrendData`에 `views` 필드만 있고 `subscribers` 필드 없음
- `TrendPoint` DTO에도 구독자 정보 없음
- 분석 페이지에 조회수 트렌드만 있고 구독자 트렌드 차트 없음

### 수정
**백엔드**:
1. `TrendData` 도메인에 `subscribers: Long` 필드 추가
2. `AnalyticsJooqRepository.getTrendData()`에서 subscribers 조인 추가 (channels 테이블의 subscriber_count 활용)
3. `TrendPoint` DTO에 `totalSubscribers: Long`, `platformSubscribers: Map<String, Long>` 추가
4. `AnalyticsUseCase.getTrends()`에서 구독자 데이터 매핑

**프론트엔드** (`AnalyticsView.vue`):
1. 조회수 트렌드 차트 아래에 구독자 변화 Area Chart 추가
2. 기존 `trendData`에서 `totalSubscribers` 읽어 렌더링
3. 기존 SVG 차트 패턴 동일하게 적용 (영역 채우기 + 반투명)

### 영향 파일
- `backend/onGo-domain/.../analytics/TrendData.kt` (수정)
- `backend/onGo-infrastructure/.../jooq/AnalyticsJooqRepository.kt` (수정)
- `backend/onGo-application/.../analytics/dto/AnalyticsDtos.kt` (수정)
- `backend/onGo-application/.../analytics/AnalyticsUseCase.kt` (수정)
- `frontend/src/views/AnalyticsView.vue` (수정)
- `frontend/src/types/analytics.ts` (수정, 있다면)

---

## 구현 순서

1. **Gap 2** (저크레딧 알림) - 독립적, 새 파일 1개 추가
2. **Gap 1** (온보딩 플랜) - 독립적, 기존 파일 수정
3. **Gap 3** (구독자 차트) - 백엔드→프론트엔드 순차 수정
4. 빌드 검증 (`./gradlew compileKotlin`)
