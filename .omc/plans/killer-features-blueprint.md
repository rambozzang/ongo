# onGo 킬러 기능 기획서 v1.0

> 작성일: 2026-04-06
> 목적: 크리에이터가 "안 쓸 수 없는" 5대 킬러 기능의 구체적 기획

---

## 목차
1. [수익 인텔리전스 + 실시간 알림](#1-수익-인텔리전스--실시간-알림)
2. [AI 브랜드 딜 매칭 마켓플레이스](#2-ai-브랜드-딜-매칭-마켓플레이스)
3. [스마트 댓글 커맨드 센터](#3-스마트-댓글-커맨드-센터)
4. [콘텐츠 퍼포먼스 예측 엔진](#4-콘텐츠-퍼포먼스-예측-엔진)
5. [MCN/에이전시 커맨드 센터](#5-mcn에이전시-커맨드-센터)
6. [구현 순서 로드맵](#구현-순서-로드맵)

---

## 1. 수익 인텔리전스 + 실시간 알림

### 기능명
**Revenue Intelligence** -- "내 콘텐츠가 오늘 얼마를 벌었는지, 어디에 올려야 돈이 되는지 알려주는 수익 두뇌"

### 왜 킬러인가

| 락인 메커니즘 | 설명 |
|---|---|
| **데이터 락인** | 6개월 이상 수익 데이터가 쌓이면 히스토리를 버릴 수 없음. 타 서비스로 이전 불가능한 자산 |
| **수익 직결** | CPM/RPM 비교로 "어디에 올려야 돈이 되는지" 실행 가능한 인사이트 제공 |
| **습관 형성** | 매일 아침 수익 알림 -> 앱을 매일 여는 습관 형성 -> DAU 극대화 |

### 사용자 시나리오

**시나리오 1: 아침 수익 알림**
> 크리에이터 김OO는 매일 아침 9시에 "어제 수익 12,340원 (+23% vs 지난주)" 알림을 받는다. 알림을 탭하면 플랫폼별 수익 비교 화면이 열린다. YouTube CPM이 3,200원인데 TikTok은 800원인 것을 확인하고, 다음 영상은 YouTube Shorts 대신 YouTube 일반 영상으로 올리기로 결정한다.

**시나리오 2: 수익 이상 감지**
> 크리에이터 박OO의 YouTube 수익이 전일 대비 40% 급감했다. onGo가 "YouTube 수익 급감 감지 (-40%)" 긴급 알림을 보낸다. 함께 제공되는 원인 분석에서 "CPM 하락 (시즌 효과 추정)"이라는 AI 분석을 확인하고, 대응 전략(다른 플랫폼 비중 확대)을 세운다.

**시나리오 3: 월간 수익 리포트**
> 매월 1일, "3월 수익 총 정산" 리포트가 자동 생성된다. 플랫폼별 수익, 브랜드 딜 수익, 성장률, 다음 달 예측이 포함된 PDF/이미지를 SNS에 공유할 수 있다.

### 핵심 기능 스펙

#### 화면 1: 수익 대시보드 (기존 RevenueView 강화)
- **일간/주간/월간 수익 카드**: 총 수익, 전기 대비 변화율, 목표 달성률
- **플랫폼별 CPM/RPM 비교 차트**: 바 차트로 한눈에 비교 (기존 `getCpmRpm` 활용)
- **수익 트렌드 라인 차트**: 일별/주별 추이 (기존 `getRevenueTrends` 활용)
- **수익 구성 파이 차트**: 광고 수익 vs 브랜드 딜 vs 슈퍼챗 등 소스별 분류
- **AI 인사이트 카드**: "이번 주 TikTok CPM이 120% 상승했습니다. TikTok에 2개 추가 업로드를 추천합니다"

#### 화면 2: 수익 알림 설정
- **일간 수익 요약 알림**: ON/OFF + 시간 설정 (기본: 매일 09:00)
- **수익 이상 감지 알림**: 임계값 설정 (기본: 전일 대비 -30% 또는 +50%)
- **목표 달성 알림**: 월 수익 목표 설정 -> 달성 시 축하 알림
- **마일스톤 알림**: 첫 100만원, 첫 1000만원 등 달성 시 알림

#### API 스펙 (신규)
```
POST   /api/v1/revenue/alerts/config          - 알림 설정 저장
GET    /api/v1/revenue/alerts/config          - 알림 설정 조회
GET    /api/v1/revenue/insights               - AI 수익 인사이트 조회
POST   /api/v1/revenue/insights/generate      - AI 수익 인사이트 생성 (크레딧 차감)
GET    /api/v1/revenue/report/monthly          - 월간 수익 리포트
POST   /api/v1/revenue/report/monthly/share    - 리포트 이미지 생성 (공유용)
```

### 기존 인프라 활용

| 기존 코드 | 활용 방안 |
|---|---|
| `RevenueUseCase` | `getRevenueSummary`, `getCpmRpm`, `getRevenueTrends`, `getBrandDealRevenue` 그대로 활용 |
| `RevenueForecasterUseCase` | AI 기반 수익 예측 이미 구현됨. 인사이트 생성에 확장 가능 |
| `GenerateRevenueReportUseCase` | AI 수익 리포트 생성 이미 구현됨 |
| `NotificationUseCase` | 알림 발송 인프라 활용. 현재 CRUD 완비 |
| `RevenueJooqRepository` | `getTotalRevenue`, `getPlatformRevenue`, `getCpmRpmByPlatform`, `getDailyRevenue` 실데이터 쿼리 |
| `RevenueView.vue` | 수익 대시보드 화면 존재. 강화만 필요 |
| `RevenueChart.vue`, `RevenuePlatformBreakdown.vue`, `RevenueTable.vue` | 차트 컴포넌트 재활용 |

### 신규 필요 항목

| 구분 | 항목 | 설명 |
|---|---|---|
| **DB** | `revenue_alert_configs` 테이블 | userId, alertType, threshold, schedule, isEnabled |
| **DB** | `revenue_insights` 테이블 | userId, insightType, content, platform, confidence, createdAt |
| **UseCase** | `RevenueAlertUseCase` | 알림 설정 CRUD + 트리거 판단 로직 |
| **UseCase** | `RevenueInsightUseCase` | AI 인사이트 생성 + 캐싱 |
| **Scheduler** | `RevenueAlertScheduler` | @Scheduled로 일일/이상감지 알림 발송 (Spring Events) |
| **Frontend** | 수익 알림 설정 모달 | RevenueView 내 설정 모달 추가 |
| **Frontend** | AI 인사이트 카드 컴포넌트 | 수익 대시보드에 인사이트 섹션 추가 |

### 우선순위: **P0** (최우선)

**이유:**
- 기존 인프라 활용도가 가장 높음 (RevenueUseCase, RevenueForecaster 모두 REAL 상태)
- 구현 난이도 낮으면서 사용자 체감 임팩트 최대
- "매일 수익 확인" 습관 형성 = DAU의 핵심 드라이버
- 수익과 직결되는 기능은 유료 전환율이 가장 높음

### 구현 난이도: **M** (Medium)

- 백엔드: 기존 UseCase 확장 + Scheduler 1개 + UseCase 2개 신규
- 프론트엔드: 기존 화면 강화 + 설정 모달 1개
- AI: 기존 PromptTemplates 패턴 그대로 인사이트 프롬프트 추가

### 예상 임팩트

| 지표 | 예상 효과 |
|---|---|
| **DAU** | +40% (매일 수익 알림 -> 매일 접속) |
| **유료 전환** | +25% (수익 데이터는 Pro/Business 전용) |
| **리텐션** | 3개월 후 이탈률 -30% (데이터 락인) |
| **NPS** | "돈 관련 기능이 가장 유용" 피드백 예상 |

---

## 2. AI 브랜드 딜 매칭 마켓플레이스

### 기능명
**Brand Deal Hub** -- "내 채널에 딱 맞는 브랜드를 AI가 찾아주고, 미디어킷까지 자동 생성해주는 수익화 허브"

### 왜 킬러인가

| 락인 메커니즘 | 설명 |
|---|---|
| **수익 직결** | 크리에이터 수익의 60%+가 브랜드 딜. 매칭 한 건이 수십~수백만원 |
| **네트워크 효과** | 크리에이터가 많을수록 브랜드가 몰리고, 브랜드가 많을수록 크리에이터가 몰림 |
| **데이터 락인** | 딜 히스토리, 성사율, 평균 단가 등이 쌓이면 이직 비용 발생 |

### 사용자 시나리오

**시나리오 1: AI 매칭 추천**
> 뷰티 크리에이터 이OO(구독자 5만)가 "브랜드 매칭" 탭을 연다. AI가 채널 카테고리(뷰티), 구독자 규모, 참여율, 시청자 연령대를 분석하여 "올리브영 신제품 리뷰 캠페인 (예산 150만원, 매칭 점수 92점)"을 추천한다. 클릭 한 번으로 제안서를 보낸다.

**시나리오 2: 자동 미디어킷**
> 크리에이터 정OO가 브랜드에서 "미디어킷 보내주세요"라는 요청을 받는다. onGo의 미디어킷 자동 생성 버튼을 누르면, 최신 채널 통계(구독자, 평균 조회수, 참여율, 시청자 데모그래픽)가 반영된 미디어킷 PDF가 30초 만에 생성된다.

**시나리오 3: 딜 파이프라인 관리**
> 에이전시 매니저 김OO가 소속 크리에이터 10명의 브랜드 딜을 한 화면에서 관리한다. 제안 -> 협상 -> 계약 -> 콘텐츠 제작 -> 게시 -> 정산 단계별로 파이프라인을 추적한다.

### 핵심 기능 스펙

#### 화면 1: 브랜드 매칭 대시보드 (InfluencerMatchView 강화)
- **추천 매칭 목록**: AI 매칭 점수 + 예상 수익 + 브랜드 정보
- **매칭 필터**: 카테고리, 예산 범위, 플랫폼, 콘텐츠 유형
- **매칭 상세**: 브랜드 프로필, 캠페인 요구사항, 예상 ROI

#### 화면 2: 미디어킷 편집기 (기존 MediaKit 강화)
- **자동 채움**: 채널 통계 자동 수집 (Analytics API 활용)
- **템플릿 선택**: 3-5개 디자인 템플릿
- **PDF/이미지 내보내기**: 공유 가능한 링크 생성 (기존 slug 기반 공개 미디어킷 활용)
- **실시간 통계 반영**: 접속할 때마다 최신 수치로 자동 업데이트

#### 화면 3: 딜 파이프라인 (기존 BrandDealView 강화)
- **칸반 보드**: PROPOSAL -> NEGOTIATION -> CONTRACTED -> PRODUCING -> PUBLISHED -> SETTLED
- **딜 상세**: 브랜드명, 금액, 마감일, 딜리버러블, 진행 상태
- **수익 추적**: 딜별 예상 수익 vs 실수익 비교

#### API 스펙 (신규)
```
POST   /api/v1/brand-deals/match              - AI 매칭 실행 (크레딧 차감)
GET    /api/v1/brand-deals/match/recommended   - 추천 매칭 목록
POST   /api/v1/brand-deals/media-kit/generate  - AI 미디어킷 자동 생성
GET    /api/v1/brand-deals/media-kit/preview    - 미디어킷 미리보기
GET    /api/v1/brand-deals/pipeline             - 딜 파이프라인 (칸반 데이터)
PUT    /api/v1/brand-deals/{id}/stage           - 딜 스테이지 변경
GET    /api/v1/brand-deals/stats                - 딜 통계 (성사율, 평균 단가 등)
```

### 기존 인프라 활용

| 기존 코드 | 활용 방안 |
|---|---|
| `BrandDealUseCase` | CRUD 완비 (create/update/delete/get). 파이프라인 확장 가능 |
| `BrandDeal` 도메인 | brandName, dealValue, status, deadline, deliverables 필드 존재 |
| `MediaKit` 도메인 | displayName, bio, categories, socialLinks, rateCard, isPublic, slug 완비 |
| `saveOrUpdateMediaKit` | 미디어킷 저장/수정 로직 구현 완료 |
| `getPublicMediaKit(slug)` | 공개 미디어킷 slug 기반 조회 구현 완료 |
| `InfluencerMatchUseCase` | `findMatches`, `sendCollabRequest`, `listCollabs` CRUD 존재. **매칭 알고리즘만 STUB** |
| `InfluencerProfile` 도메인 | channelName, subscriberCount, avgViews, engagementRate, categories, matchScore 필드 존재 |
| `AnalyticsUseCase` | 채널 통계 (구독자, 조회수, 참여율, 데모그래픽) 조회 가능 -> 미디어킷 자동 채움에 활용 |

### 신규 필요 항목

| 구분 | 항목 | 설명 |
|---|---|---|
| **UseCase** | `BrandMatchAlgorithmUseCase` | AI 기반 매칭 점수 계산 (채널 카테고리 x 브랜드 카테고리 x 구독자 규모 x 참여율 x 시청자 데모그래픽) |
| **UseCase** | `MediaKitGeneratorUseCase` | Analytics 데이터 자동 수집 -> 미디어킷 자동 생성 |
| **DB** | `brand_campaigns` 테이블 | 브랜드 측 캠페인 등록 (추후 브랜드 측 가입 시) |
| **DB** | `brand_deal_stages` 칼럼 추가 | 기존 brand_deals 테이블에 stage(PROPOSAL~SETTLED) 칼럼 |
| **AI** | 매칭 프롬프트 | 크리에이터 프로필 + 브랜드 요구사항 -> 매칭 점수 + 이유 |
| **AI** | 미디어킷 프롬프트 | 채널 데이터 -> bio/카테고리/강점 자동 작성 |
| **Frontend** | 칸반 보드 컴포넌트 | 드래그앤드롭 파이프라인 UI |
| **Frontend** | 미디어킷 에디터 컴포넌트 | 템플릿 기반 편집 UI |

### 우선순위: **P1** (높음)

**이유:**
- 수익 직결 기능이지만, 양면 마켓 특성상 브랜드 측 공급이 초기에 부족할 수 있음
- InfluencerMatch가 유일한 STUB -> 기술 부채 해소 필요
- 미디어킷 자동 생성은 단독으로도 가치 있어서 먼저 출시 가능

### 구현 난이도: **L** (Large)

- AI 매칭 알고리즘 설계 + 테스트 (정확도 검증 필요)
- 칸반 보드 UI (드래그앤드롭)
- 브랜드 측 온보딩 플로우 (Phase 2)

### 예상 임팩트

| 지표 | 예상 효과 |
|---|---|
| **ARPU** | +60% (브랜드 딜 중개 수수료 또는 프리미엄 기능) |
| **사용자 획득** | "미디어킷 무료 생성" -> 바이럴 유입 채널 |
| **리텐션** | 딜 히스토리 축적 -> 장기 락인 |
| **B2B 전환** | 에이전시가 소속 크리에이터 딜 관리에 채택 |

---

## 3. 스마트 댓글 커맨드 센터

### 기능명
**Comment Command Center** -- "5개 플랫폼 댓글을 하나의 인박스에서 관리하고, AI가 답변까지 대신해주는 참여율 부스터"

### 왜 킬러인가

| 락인 메커니즘 | 설명 |
|---|---|
| **시간 절약** | 크리에이터 일일 평균 30-60분 댓글 관리 시간 -> 5분으로 단축 |
| **수익 직결** | 참여율(댓글 답변률) 높을수록 알고리즘 노출 증가 -> 조회수/수익 증가 |
| **데이터 락인** | 감정 분석 히스토리, 답변 패턴, 위기 감지 이력 축적 |

### 사용자 시나리오

**시나리오 1: 통합 인박스**
> 크리에이터 최OO가 아침에 onGo를 열면, 어젯밤 들어온 YouTube 23개, TikTok 45개, Instagram 12개 댓글이 하나의 인박스에 시간순으로 정렬되어 있다. 부정 댓글(빨간색)을 먼저 처리하고, 긍정 댓글에는 AI 추천 답변을 한 번에 전송한다.

**시나리오 2: 위기 감지**
> 크리에이터 한OO의 최근 영상에 부정 댓글이 평소 대비 300% 급증했다. onGo가 "부정 댓글 급증 감지" 알림을 보내고, 주요 불만 키워드("광고 표시 안 함", "실망")를 요약해준다. 크리에이터가 즉시 해명 댓글을 고정한다.

**시나리오 3: AI 자동 답변**
> 크리에이터 윤OO가 SmartReply 규칙을 설정한다: "질문 댓글에 친근한 톤으로 자동 답변, 하루 최대 30개". AI가 "이 영상에서 사용한 카메라 뭐예요?" 같은 반복 질문에 자동으로 "안녕하세요! SONY A7IV 사용하고 있어요 :)" 같은 맞춤 답변을 생성한다.

### 핵심 기능 스펙

#### 화면 1: 통합 댓글 인박스 (기존 CommentsView 강화)
- **통합 피드**: 전체 플랫폼 댓글 시간순/감정순/미답변순 정렬
- **감정 배지**: 긍정(초록)/중립(회색)/부정(빨강) 시각화 (기존 sentiment 필드 활용)
- **빠른 액션**: 답변/숨기기/고정/삭제 원클릭 (기존 CommentEngagementUseCase 활용)
- **AI 답변 추천**: 댓글 선택 시 AI가 3개 답변 후보 생성
- **일괄 처리**: 체크박스로 여러 댓글 선택 -> 일괄 답변/숨기기

#### 화면 2: 감정 분석 대시보드
- **감정 트렌드 차트**: 일별 긍정/중립/부정 비율 추이 (기존 `getSentimentTrend` 활용)
- **위기 감지 카드**: 부정 댓글 급증 시 경고 표시
- **키워드 워드 클라우드**: 자주 언급되는 키워드 시각화
- **영상별 감정 비교**: 어떤 영상이 반응이 좋은지/나쁜지

#### 화면 3: SmartReply 설정 (기존 SmartReplyView 강화)
- **규칙 관리**: 트리거 키워드, 감정, 톤, 자동 전송 여부 (기존 SmartReplyUseCase CRUD 활용)
- **AI 학습 피드백**: 사용자가 AI 답변을 수정하면 그 패턴을 학습
- **통계 대시보드**: 자동 답변 수, 평균 응답 시간, 만족도 (현재 **전부 0 반환** -> 실제 로직 구현 필요)

#### API 스펙 (신규)
```
POST   /api/v1/comments/ai-reply/generate     - AI 답변 후보 생성 (크레딧 차감)
POST   /api/v1/comments/batch/reply            - 일괄 답변 전송
POST   /api/v1/comments/batch/hide             - 일괄 숨기기
GET    /api/v1/comments/crisis-detection        - 위기 감지 상태 조회
GET    /api/v1/comments/keyword-cloud           - 키워드 빈도 분석
GET    /api/v1/smart-reply/stats/real           - SmartReply 실제 통계
```

### 기존 인프라 활용

| 기존 코드 | 활용 방안 |
|---|---|
| `CommentUseCase` | 댓글 목록 조회, 필터링, 감정 통계, 감정 트렌드 **모두 REAL** |
| `CommentEngagementUseCase` | 답변/삭제/숨기기/고정 + 플랫폼 API 연동 **REAL** |
| `CommentSyncUseCase` | 플랫폼별 댓글 동기화 **REAL** |
| `SmartReplyUseCase` | 규칙 CRUD, 설정 CRUD **REAL**. 통계만 STUB |
| `PlatformCommentPort` | YouTube/TikTok/Instagram/NaverClip 댓글 API 연동 |
| `CommentsView.vue` | 댓글 목록 + 필터 + 감정 분석 화면 존재 |
| `SmartReplyView.vue` | 규칙 관리 + 설정 화면 존재 |

### 신규 필요 항목

| 구분 | 항목 | 설명 |
|---|---|---|
| **UseCase** | `CommentAiReplyUseCase` | AI 답변 후보 생성 (ChatClient 활용, 크레딧 차감) |
| **UseCase** | `CommentBatchUseCase` | 일괄 답변/숨기기 처리 |
| **UseCase** | `CrisisDetectionUseCase` | 부정 댓글 급증 감지 + 키워드 추출 |
| **로직** | `SmartReplyStatsCalculator` | SmartReply 통계 실제 계산 로직 (현재 0 반환 수정) |
| **DB** | `comment_ai_replies` 테이블 | AI 생성 답변 이력 (학습용) |
| **Scheduler** | `CrisisDetectionScheduler` | 주기적 부정 댓글 모니터링 |
| **Frontend** | AI 답변 추천 패널 | 댓글 선택 시 슬라이드아웃 패널 |
| **Frontend** | 키워드 워드 클라우드 | d3.js 또는 vue-wordcloud 활용 |

### 우선순위: **P0** (최우선)

**이유:**
- 기존 인프라 활용도 최고 (Comment, SmartReply 대부분 REAL)
- "시간 절약" 가치가 가장 체감되는 기능 (매일 30분+ 절약)
- SmartReply 통계 0 반환 버그 수정은 어차피 해야 할 기술 부채
- 댓글 참여율은 모든 플랫폼 알고리즘의 핵심 지표 -> 조회수 직결

### 구현 난이도: **M** (Medium)

- 대부분 기존 UseCase 확장 + UI 개선
- AI 답변 생성은 기존 ChatClient 패턴 그대로
- 위기 감지는 단순 통계 비교 (이전 기간 vs 현재 기간)

### 예상 임팩트

| 지표 | 예상 효과 |
|---|---|
| **시간 절약** | 크리에이터당 일 30분+ 절약 |
| **참여율** | 댓글 답변률 +200% -> 플랫폼 알고리즘 노출 증가 |
| **유료 전환** | 댓글 관리 = Pro/Business 전용 -> 전환 드라이버 |
| **리텐션** | 매일 댓글 확인 습관 -> DAU 증가 |

---

## 4. 콘텐츠 퍼포먼스 예측 엔진

### 기능명
**Performance Predictor** -- "업로드 전에 예상 조회수를 알려주고, 제목/태그를 최적화해주는 AI 코치"

### 왜 킬러인가

| 락인 메커니즘 | 설명 |
|---|---|
| **데이터 락인** | 과거 데이터가 많을수록 예측 정확도 증가 -> 6개월 이상 사용 시 대체 불가 |
| **수익 직결** | 조회수 예측 -> 최적 제목/태그 선택 -> 조회수 증가 -> 수익 증가 |
| **의사결정 지원** | "이 영상 올려야 할까?" 판단의 근거 제공 |

### 사용자 시나리오

**시나리오 1: 업로드 전 예측**
> 크리에이터 강OO가 새 영상 업로드 화면에서 제목 "아이폰 17 Pro 1주일 사용기"와 태그를 입력한다. AI가 "예상 조회수: 15,000-22,000 (신뢰도 78%)"를 표시한다. "리뷰" 대신 "사용기"라는 단어가 조회수를 12% 낮춘다는 제안을 받고, 제목을 "아이폰 17 Pro 솔직 리뷰"로 변경하자 예측이 "18,000-28,000"으로 올라간다.

**시나리오 2: A/B 제목 테스트**
> 크리에이터 송OO가 제목 후보 3개를 입력한다. AI가 각각의 예상 CTR과 조회수를 비교해준다. 가장 높은 예측 점수의 제목을 선택한다.

**시나리오 3: 콘텐츠 전략 코칭**
> 매주 "이번 주 콘텐츠 코칭" 리포트가 생성된다. "게임 카테고리 영상의 평균 조회수가 먹방 대비 3배입니다. 게임 영상 비중을 늘려보세요." 같은 데이터 기반 전략 제안을 받는다.

### 핵심 기능 스펙

#### 화면 1: 예측 패널 (업로드 화면 통합)
- **실시간 예측 미터**: 제목/태그 입력 시 실시간으로 예상 조회수 범위 표시
- **최적화 제안**: "이 단어를 바꾸면 +X% 예상" 제안 카드
- **과거 유사 콘텐츠**: 같은 카테고리/태그의 과거 영상 성과 비교
- **최적 업로드 시간**: 기존 `getOptimalPublishTimes` 결과 표시

#### 화면 2: A/B 테스트 도구
- **제목 비교**: 최대 5개 제목 후보의 예상 CTR 비교
- **태그 조합 비교**: 태그 세트별 예상 도달 범위 비교
- **썸네일 A/B** (Phase 2): 썸네일 이미지별 예상 CTR

#### 화면 3: 콘텐츠 전략 대시보드
- **카테고리별 성과**: 어떤 주제의 영상이 잘 되는지 (기존 `getTagPerformance` 활용)
- **트렌드 예측**: 현재 상승 중인 키워드/카테고리
- **주간 코칭 리포트**: AI 생성 전략 제안

#### API 스펙 (신규)
```
POST   /api/v1/predictions/performance         - 콘텐츠 퍼포먼스 예측 (크레딧 차감)
POST   /api/v1/predictions/title-compare        - 제목 A/B 비교 (크레딧 차감)
POST   /api/v1/predictions/optimize             - 제목/태그 최적화 제안
GET    /api/v1/predictions/history               - 예측 이력 (실제 vs 예측 비교)
GET    /api/v1/predictions/coaching              - 주간 코칭 리포트
POST   /api/v1/predictions/coaching/generate     - 코칭 리포트 생성 (크레딧 차감)
```

### 기존 인프라 활용

| 기존 코드 | 활용 방안 |
|---|---|
| `AnalyticsUseCase` | `getOptimalPublishTimes` (최적 시간), `getTagPerformance` (태그 성과), `getCrossPlatformComparison` (플랫폼 비교) 전부 REAL |
| `CrossAnalyticsUseCase` | 크로스 플랫폼 분석 데이터 |
| `HashtagAnalyticsUseCase` | 해시태그 성과 분석 |
| `AnalyticsRepository` | `getTrendData`, `getTopVideos`, `findDailyAnalyticsByChannelIds` 실데이터 쿼리 |
| AI `ChatClientResolver` | Structured Output 패턴으로 예측 결과 파싱 |
| `CreditService` | 크레딧 차감/검증 로직 완비 |
| 기존 AI UseCase 패턴 | `RevenueForecasterUseCase`의 AI 호출 + 크레딧 차감 + 에러 환불 패턴 그대로 적용 |

### 신규 필요 항목

| 구분 | 항목 | 설명 |
|---|---|---|
| **UseCase** | `PerformancePredictionUseCase` | 과거 데이터 기반 + AI 예측 (조회수, CTR, 참여율) |
| **UseCase** | `TitleOptimizationUseCase` | 제목 A/B 비교 + 최적화 제안 |
| **UseCase** | `ContentCoachingUseCase` | 주간 코칭 리포트 생성 |
| **DB** | `performance_predictions` 테이블 | 예측 이력 (예측값 vs 실제값 추적) |
| **DB** | `coaching_reports` 테이블 | 코칭 리포트 캐시 |
| **AI** | 예측 프롬프트 | 과거 성과 데이터 + 제목/태그 -> 예상 조회수/CTR |
| **AI** | 코칭 프롬프트 | 카테고리별 성과 + 트렌드 -> 전략 제안 |
| **Scheduler** | `PredictionAccuracyTracker` | 7일/30일 후 예측 정확도 자동 추적 |
| **Frontend** | 예측 미터 컴포넌트 | 게이지 형태의 조회수 예측 시각화 |
| **Frontend** | A/B 비교 UI | 제목 후보 나란히 비교하는 카드 레이아웃 |

### 우선순위: **P1** (높음)

**이유:**
- 데이터 락인 효과가 가장 강력 (6개월 이상 데이터 축적 시 대체 불가)
- 하지만 초기에 데이터가 부족하면 예측 정확도가 낮아 신뢰를 잃을 위험
- 수익 인텔리전스(P0)로 먼저 데이터를 쌓은 후 출시가 적절

### 구현 난이도: **L** (Large)

- AI 예측 모델 정확도 검증 (가장 어려운 부분)
- 예측 vs 실제 추적 파이프라인 구축
- 업로드 화면에 실시간 예측 통합 (UX 복잡도)

### 예상 임팩트

| 지표 | 예상 효과 |
|---|---|
| **조회수** | 사용자 평균 조회수 +20-30% (최적화된 제목/태그) |
| **리텐션** | 6개월 이상 이탈률 -50% (데이터 락인) |
| **유료 전환** | 예측 기능 = Pro/Business 전용 |
| **차별화** | 경쟁 서비스 대비 가장 강력한 차별화 포인트 |

---

## 5. MCN/에이전시 커맨드 센터

### 기능명
**Agency Command Center** -- "소속 크리에이터 전체를 한 화면에서 관리하는 에이전시 전용 통합 관제탑"

### 왜 킬러인가

| 락인 메커니즘 | 설명 |
|---|---|
| **네트워크 효과** | 에이전시 1곳 채택 = 소속 크리에이터 10-100명 동시 유입 |
| **B2B 수익** | 에이전시는 Business 요금제(49,900원) 고객 -> ARPU 극대화 |
| **전환 비용** | 워크플로우/권한/히스토리 이전이 극도로 어려움 -> 장기 계약 |

### 사용자 시나리오

**시나리오 1: 크리에이터 성과 모니터링**
> MCN 대표 김OO가 소속 크리에이터 30명의 이번 주 성과를 한 화면에서 본다. 조회수 Top 5, 성장률 Top 5, 위기 크리에이터(부정 댓글 급증) 목록이 자동 정렬된다. 위기 크리에이터에게 즉시 메시지를 보낸다.

**시나리오 2: 콘텐츠 승인 워크플로우**
> 에이전시 소속 크리에이터 이OO가 새 영상을 업로드하면, 매니저에게 "검수 요청" 알림이 간다. 매니저가 제목/태그/설명을 확인하고 "승인" 또는 "수정 요청"을 보낸다. 승인 후 자동으로 예약 게시된다.

**시나리오 3: 클라이언트 포털**
> 에이전시 김OO가 광고주(브랜드) 전용 포털을 생성한다. 광고주는 읽기 전용 링크로 캠페인에 참여한 크리에이터의 성과(조회수, 참여율, CPV)를 실시간으로 확인한다.

### 핵심 기능 스펙

#### 화면 1: 에이전시 대시보드 (기존 AgencyView 강화)
- **크리에이터 성과 테이블**: 이름, 플랫폼별 구독자, 이번 주 조회수, 성장률, 상태
- **KPI 요약 카드**: 총 관리 크리에이터, 총 조회수, 총 수익, 활성 딜 수 (기존 AgencyKpiCard 활용)
- **위기 알림 피드**: 부정 댓글 급증, 수익 급감, 채널 정지 등 (기존 AgencyActivityFeed 활용)
- **크리에이터 비교**: 소속 크리에이터 간 성과 비교 차트

#### 화면 2: 콘텐츠 승인 워크플로우
- **대기 중 콘텐츠 목록**: 검수 대기, 수정 요청, 승인 완료 필터
- **프리뷰**: 제목, 설명, 태그, 썸네일 미리보기
- **코멘트/피드백**: 크리에이터-매니저 간 코멘트 스레드
- **일괄 승인**: 체크박스로 여러 콘텐츠 일괄 승인

#### 화면 3: 클라이언트 포털 (기존 Portal 기능 강화)
- **캠페인 성과 리포트**: 참여 크리에이터별 조회수, CPV, 참여율
- **자동 리포트 생성**: 주간/월간 캠페인 리포트 PDF
- **권한 관리**: 열람 가능 데이터 범위 설정 (기존 permissions 필드 활용)

#### API 스펙 (신규)
```
GET    /api/v1/agency/{wsId}/dashboard          - 에이전시 대시보드 집계
GET    /api/v1/agency/{wsId}/creators/ranking    - 크리에이터 성과 순위
GET    /api/v1/agency/{wsId}/alerts              - 에이전시 위기 알림
POST   /api/v1/agency/{wsId}/content-review      - 콘텐츠 검수 요청 생성
PUT    /api/v1/agency/{wsId}/content-review/{id}  - 검수 상태 변경 (승인/반려)
GET    /api/v1/agency/{wsId}/content-review       - 검수 대기 목록
GET    /api/v1/portal/{token}/campaign-report     - 클라이언트 포털 캠페인 리포트
POST   /api/v1/portal/{token}/report/generate     - 리포트 PDF 생성
```

### 기존 인프라 활용

| 기존 코드 | 활용 방안 |
|---|---|
| `AgencyUseCase` | 워크스페이스 CRUD, 크리에이터 추가/제거, 포털 관리 **REAL** |
| `AgencyWorkspace` 도메인 | ownerUserId, name, description, logoUrl |
| `AgencyCreator` 도메인 | workspaceId, userId, role |
| `ClientPortal` 도메인 | accessToken, permissions, expiresAt 완비 |
| `AgencyKpiCard.vue` | KPI 카드 컴포넌트 존재 |
| `AgencyActivityFeed.vue` | 활동 피드 컴포넌트 존재 |
| `AgencyView.vue` | 에이전시 관리 화면 존재 |
| Team/Permission 시스템 | 25개 권한 시스템 REAL |

### 신규 필요 항목

| 구분 | 항목 | 설명 |
|---|---|---|
| **UseCase** | `AgencyDashboardUseCase` | 소속 크리에이터 전체 집계 (조회수, 수익, 성장률) |
| **UseCase** | `ContentReviewUseCase` | 콘텐츠 검수 워크플로우 (요청/승인/반려) |
| **UseCase** | `CampaignReportUseCase` | 캠페인 성과 리포트 생성 |
| **DB** | `content_reviews` 테이블 | videoId, reviewerId, status, feedback, reviewedAt |
| **DB** | `agency_alerts` 테이블 | workspaceId, alertType, creatorId, message, severity |
| **Frontend** | 크리에이터 랭킹 테이블 | 정렬 가능한 성과 테이블 |
| **Frontend** | 콘텐츠 검수 화면 | 프리뷰 + 코멘트 + 승인/반려 UI |
| **Frontend** | 캠페인 리포트 뷰 | 클라이언트 포털용 리포트 화면 |

### 우선순위: **P2** (중간)

**이유:**
- B2B 기능은 개인 크리에이터 기반이 먼저 확보된 후 효과적
- 에이전시 대상 영업/온보딩 프로세스가 별도로 필요
- 기존 AgencyUseCase CRUD가 REAL이므로 기초는 탄탄

### 구현 난이도: **XL** (Extra Large)

- 콘텐츠 승인 워크플로우 (상태 머신 + 알림 + 코멘트)
- 크리에이터 N명의 데이터 집계 (성능 최적화 필요)
- 클라이언트 포털 리포트 생성 (PDF)
- 권한 체계 확장 (기존 25개 + 새로운 워크플로우 권한)

### 예상 임팩트

| 지표 | 예상 효과 |
|---|---|
| **사용자 획득** | 에이전시 1곳 = 10-100명 크리에이터 동시 유입 |
| **ARPU** | Business 요금제(49,900원) 전환 |
| **리텐션** | B2B 고객 연간 이탈률 < 5% (전환 비용 높음) |
| **수익** | 에이전시 10곳 = 월 약 500만원 + 소속 크리에이터 구독 수익 |

---

## 구현 순서 로드맵

### Phase 1: 습관 형성 + 시간 절약 (8-10주)

```
[Week 1-5]  P0-A: 수익 인텔리전스 + 실시간 알림
            - 수익 알림 설정/스케줄러
            - AI 수익 인사이트
            - 기존 RevenueView 강화

[Week 3-8]  P0-B: 스마트 댓글 커맨드 센터
            - AI 답변 추천 + 일괄 처리
            - 위기 감지
            - SmartReply 통계 실제 구현
            - 기존 CommentsView 강화
```

**Phase 1 목표:** "매일 앱을 열게 만드는" 두 가지 이유 확보
- 아침: 수익 알림 확인 -> 수익 대시보드
- 오전: 댓글 알림 확인 -> 댓글 인박스에서 AI 답변

**출시 기준:**
- 수익 알림 정상 발송 + AI 인사이트 생성 + CPM/RPM 비교 동작
- AI 댓글 답변 3개 후보 생성 + 일괄 처리 + 위기 감지 알림

---

### Phase 2: 수익 극대화 (6-8주)

```
[Week 9-14]  P1-A: AI 브랜드 딜 매칭 마켓플레이스
             - AI 매칭 알고리즘 구현 (InfluencerMatch STUB 해소)
             - 미디어킷 자동 생성
             - 딜 파이프라인 칸반 보드

[Week 11-16] P1-B: 콘텐츠 퍼포먼스 예측 엔진
             - 업로드 시 실시간 예측
             - 제목 A/B 비교
             - 예측 정확도 추적
```

**Phase 2 목표:** "이 서비스 때문에 돈을 더 벌 수 있다"는 증거 확보
- 브랜드 딜 매칭으로 실제 수익 발생 사례
- 퍼포먼스 예측으로 조회수 증가 사례

**출시 기준:**
- AI 매칭 점수 생성 + 미디어킷 PDF 내보내기 + 칸반 보드 동작
- 예측 정확도 70% 이상 (동일 카테고리 과거 데이터 기준)

---

### Phase 3: B2B 확장 (8-12주)

```
[Week 17-28] P2: MCN/에이전시 커맨드 센터
             - 에이전시 대시보드 (크리에이터 집계)
             - 콘텐츠 승인 워크플로우
             - 클라이언트 포털 캠페인 리포트
```

**Phase 3 목표:** B2B 수익원 확보 + 네트워크 효과 시작
- 에이전시 파일럿 고객 3곳 확보
- 소속 크리에이터 자동 유입

**출시 기준:**
- 에이전시 대시보드에서 소속 크리에이터 성과 일괄 조회
- 콘텐츠 검수 워크플로우 정상 동작
- 클라이언트 포털 리포트 PDF 생성

---

### 전체 타임라인 요약

| Phase | 기간 | 기능 | 핵심 가치 | 난이도 |
|---|---|---|---|---|
| **Phase 1** | Week 1-8 | 수익 인텔리전스 + 댓글 커맨드 센터 | 습관 형성 + 시간 절약 | M + M |
| **Phase 2** | Week 9-16 | 브랜드 딜 매칭 + 퍼포먼스 예측 | 수익 극대화 | L + L |
| **Phase 3** | Week 17-28 | 에이전시 커맨드 센터 | B2B 확장 | XL |

### 왜 이 순서인가

1. **Phase 1 (수익 + 댓글)이 먼저인 이유:**
   - 기존 인프라 활용도 최고 -> 빠른 출시 가능
   - "매일 쓰는 습관"을 먼저 만들어야 이후 기능이 의미 있음
   - DAU가 확보되어야 Phase 2의 데이터 기반 기능이 정확해짐

2. **Phase 2 (브랜드 딜 + 예측)가 두 번째인 이유:**
   - Phase 1에서 쌓인 수익/분석 데이터가 Phase 2의 원료
   - 사용자 기반이 확보된 후 마켓플레이스가 의미 있음
   - 예측 모델의 정확도는 데이터 양에 비례

3. **Phase 3 (에이전시)가 마지막인 이유:**
   - B2C 기반이 탄탄해야 B2B 영업이 가능
   - 구현 복잡도가 가장 높아 안정적 기반 필요
   - 에이전시는 "이미 잘 되는 서비스"에 합류하려 함

---

### 성공 지표 (KPI)

| 지표 | Phase 1 목표 | Phase 2 목표 | Phase 3 목표 |
|---|---|---|---|
| **DAU** | +40% | +20% | +15% |
| **유료 전환율** | 15% -> 20% | 20% -> 28% | 28% -> 32% |
| **월 이탈률** | 8% -> 5% | 5% -> 3% | 3% -> 2% |
| **ARPU** | +15% | +40% | +60% |
| **NPS** | 40+ | 50+ | 55+ |
