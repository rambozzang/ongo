-- V114: 코드가 쓰는 enum 값을 DB 타입에 맞춘다
--
-- ## 무엇이 깨져 있었나
--
-- `NotificationJooqRepository` 는 값을 `'X'::notification_type` 으로 캐스팅해서 넣는다
-- (`Tables.enumValue`). PostgreSQL enum 에 없는 값이면 INSERT 가 예외로 죽는다.
-- 그런데 Kotlin enum 에만 있고 DB 타입에는 없는 값이 셋 있었다.
--
-- ### 1. notification_type += CHANNEL_TOKEN_EXPIRED
--
-- 가장 비싼 쪽이다. `ChannelScheduler.checkExpiringTokens` 는 토큰 갱신이 영구 실패하면
--
--   (1) 채널을 EXPIRED 로 바꾸고 — **이 시점부터 그 채널 게시가 멈춘다**
--   (2) 사용자에게 재연결하라는 알림을 저장한다
--
-- 순서로 처리한다. (2)가 예외를 던지므로 **채널은 끊기고 사용자는 영원히 모른다.**
-- 게다가 그 save 는 `catch` 블록 안에 있고 `expiringSoon.forEach` 바깥에는 try 가 없다.
-- 예외가 루프를 뚫고 나가 **그 주기의 나머지 채널은 갱신 시도조차 못 한다** — 하나가
-- 만료되면 뒤따르는 채널들도 같이 만료되는 연쇄가 된다.
--
-- ### 2. notification_type += REVENUE_ALERT
--
-- `RevenueAlertScheduler` 가 수익 알림을 저장할 때 같은 방식으로 죽는다.
--
-- ### 3. subscription_status += SUSPENDED
--
-- `AdminUseCase` 가 구독을 정지할 때 쓴다. 지금은 관리자가 정지 버튼을 누르면 실패한다.
--
-- ## 왜 여태 안 드러났나
--
-- 셋 다 **평상시에는 지나지 않는 경로**다. 토큰 영구 만료·수익 알림·관리자 정지는
-- 정상 흐름에서 밟히지 않는다. 그래서 "문제가 생겼을 때 알려주는 장치" 만 골라서
-- 고장 나 있었다.
--
-- ## 왜 `IF NOT EXISTS` 인가
--
-- V34 가 같은 방식으로 TRIALING·PAUSED 를 넣었다. 이미 손으로 값을 넣어 둔 환경에서도
-- 재실행이 안전해야 한다.
--
-- PostgreSQL 12+ 는 `ALTER TYPE ... ADD VALUE` 를 트랜잭션 안에서 허용한다(대상은 PG 16).
-- 제약은 "같은 트랜잭션에서 그 값을 **사용**할 수 없다" 는 것인데, 여기서는 추가만 한다.

-- notifications.type 이 이 타입이다 (V1__init_schema.sql:366)
ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'CHANNEL_TOKEN_EXPIRED';
ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'REVENUE_ALERT';

-- subscriptions.status 가 이 타입이다
ALTER TYPE subscription_status ADD VALUE IF NOT EXISTS 'SUSPENDED';
