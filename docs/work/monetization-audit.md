# 수익화 감사 — 코드 기준 정적 조사

감사일: 2026-09-23  
범위: 요금제·한도·전환 UX·PortOne·크레딧·AI/쇼츠 과금 경로. 서버 설정·실결제·실제 인프라 원가는 확인하지 않았다. 이 문서는 공유 작업트리의 감사 시점 스냅샷이다. 업로드 구현 파일을 다른 작업자가 동시에 수정 중이므로 특히 업로드 관련 결론은 최종 병합본에서 재확인해야 한다. Gradle/테스트는 실행하지 않았다.

## 돈을 막고 있는 것 Top 5

1. **자동 갱신은 기본 비활성이다 — 반복 매출이 끊길 수 있음.** `BillingScheduler`의 `subscription.renewal.enabled` 기본값은 `false`이고 꺼져 있으면 갱신 대상을 조회조차 하지 않는다. 배포 설정이 이를 명시적으로 켰는지 확인되지 않았으므로, 운영에서 켜져 있지 않으면 첫 결제 이후 자동 청구가 없다. (`backend/onGo-application/src/main/kotlin/com/ongo/application/subscription/BillingScheduler.kt:52-53,136-149`)
2. **현재 표준 업로드 흐름에서 월 업로드 수 한도가 강제되지 않는다.** 요금제에는 Free 5회, Starter 30회, Pro 100회가 있고 구형 스트리밍 경로는 검사하지만, presigned/멀티파트 경로의 시작·확정에서는 스토리지 쿼터만 검사한다. 게시 유스케이스에도 월간 카운트 검사가 없다. 무료 사용자가 새 경로로 업로드하면 월 업로드 과금 차별이 새는 것으로 보인다. (`PlanType.kt:16-19`; `StreamPublishUseCase.kt:102-107`; `UploadVideoUseCase.kt:51-77,91-100,115-153`; `PublishVideoUseCase.kt:61-122`)
3. **무료 요금제에서 반복 예약을 만들 수 있다.** 반복 예약 생성/수정은 소유권·원본·플랫폼·주기를 검사하지만 플랜 유형이나 `scheduleDays`를 검사하지 않는다. Free는 표상 예약 불가인데, recurring API에서는 기능 접근이 열린다. (`PlanType.kt:16-19`; `RecurringScheduleUseCase.kt:39-65,68-107,183-217`)
4. **결제는 코드에 있지만 운영 준비 상태와 갱신은 배포 의존이다.** 서버 결제 readiness가 꺼져 있거나 확인 API에 실패하면 UI가 fail-closed로 결제 버튼을 막는다. readiness 환경 설정 및 PortOne 상점 실설정은 이 조사만으로 확인할 수 없다. (`PortOnePaymentService.kt:557-562`; `usePaymentAvailability.ts:4-14,40-60`). 1번의 갱신 기본값도 함께 운영 확인이 필요하다.
5. **판매 페이지가 서버 제한 중 하나를 숨긴다.** 경쟁 채널은 Free 2 / Starter 5 / Pro 15 / Business 무제한으로 서버 제한되지만, 플랜 응답 DTO에서 해당 필드를 내리지 않고 비교표에도 행이 없다. 한도에 도달해 막힐 때까지 유료 가치가 보이지 않아 업그레이드 동기와 설명력이 약하다. (`PlanType.kt:14,16-19`; `CompetitorUseCase.kt:128-136`; `SubscriptionUseCase.kt:330-338`; `PlanComparisonTable.vue:32-101`)

## 1. 요금제 실제 차이와 노출/강제 일치

| 플랜 | 월/연 가격 | 연결 채널 | 월 업로드 | 예약 일수 | 분석 기간 | 저장공간 | 월 무료 크레딧 | 팀 좌석(추가 인원) | 경쟁 채널 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Free | ₩0 / ₩0 | 1 | 5 | 0 | 7일 | 1GB | 30 | 0 | 2 |
| Starter | ₩9,900 / ₩99,000 | 3 | 30 | 7일 | 30일 | 10GB | 100 | 0 | 5 |
| Pro | ₩19,900 / ₩199,000 | 4 | 100 | 30일 | 365일 | 50GB | 300 | 2 | 15 |
| Business | ₩49,900 / ₩499,000 | 4 | 무제한 | 90일 | 무제한 | 200GB | 1,000 | 10 | 무제한 |

근거: 값은 공통 `PlanType` 정의이며 `priceFor`가 월/연 가격을 선택한다. (`backend/onGo-common/src/main/kotlin/com/ongo/common/enums/PlanType.kt:3-28`). 프런트엔드 요금제 상수와 계약 테스트도 가격/한도를 맞춰 비교한다. (`frontend/src/types/subscription.ts:1-13,40-127`; `frontend/src/types/planPricingContract.test.ts:7-25,91-120`). 로그인한 구독 화면은 백엔드의 계획 응답을 사용한다. (`SubscriptionUseCase.kt:270-283`; `frontend/src/stores/subscription.ts:42-63`)

- **저장공간:** 업로드 시작 때 사용자별 행 잠금 후 현재 사용량+추가 크기를 비교한다. 업로드 확정 시 스토리지 실제 크기로 재검증하므로 백엔드 강제가 확인된다. (`backend/onGo-application/src/main/kotlin/com/ongo/application/storage/StorageQuotaUseCase.kt:25-47`; `UploadVideoUseCase.kt:51-61,80-100`)
- **연결 채널:** 서버가 신규 연결 시 `maxPlatforms`를 검사한다. 다만 카운트 대상은 서로 다른 플랫폼 종류가 아니라 연결된 채널 행/계정 수다. 판매 화면의 “연동 채널 수”와 라벨을 통일하는 편이 정확하다. (`backend/onGo-application/src/main/kotlin/com/ongo/application/channel/ChannelUseCase.kt:75-84,153-161`; `PlanComparisonTable.vue:53-60`)
- **월 업로드:** 구형 multipart 요청 경로에만 검사 코드가 확인된다. 현재 presigned/멀티파트 기본 흐름은 스토리지 한도는 검사하지만 월 업로드 카운트/플랜 제한은 확인되지 않았다. 실제 라우팅의 최종 진입점과 동시 수정 중인 최종 코드를 재검증해야 한다. (`StreamPublishUseCase.kt:95-107`; `UploadVideoUseCase.kt:51-77,91-100,115-153`)
- **예약:** 단건 예약은 Free를 막고 플랜의 예약 가능 일수도 검사한다. (`ScheduleUseCase.kt:231-238`). 반복 예약은 플랜/기간 한도 검사가 없다(Top 5 #3). (`RecurringScheduleUseCase.kt:39-65,68-107`)
- **분석 기간:** 비교표는 “분석 데이터 보관” 기간을 플랜 차이로 표시하지만, 조사한 분석 API는 `days` 요청값을 받아 바로 조회 유스케이스/저장소로 전달하며 플랜별 기간 제한을 검사하지 않는다. 단, 실제 DB 보존 정책 자체는 이 코드만으로 단정할 수 없으므로 “조회 API에서 표시 기간을 강제하지 않음”으로 한정한다. (`PlanType.kt:9-10`; `PlanComparisonTable.vue:73-80`; `AnalyticsController.kt:41-65,78-85`; `AnalyticsUseCase.kt:29-50`)
- **팀 좌석:** 초대 시 `maxTeamMembers`를 서버에서 적용한다. Free/Starter는 추가 멤버 0명, Pro 2명, Business 10명이다. 비교표는 0을 “1명”으로 표시해 소유자 포함 총 인원으로 표현한다. (`TeamUseCase.kt:102-133`; `PlanComparisonTable.vue:94-101`)
- **댓글 관리:** 백엔드에서 Pro/Business만 허용하는 플랜 게이트가 있다. (`CommentUseCase.kt:118-123`). 구독 스토어도 댓글 관리 가능 여부를 Pro/Business에 연결한다. (`frontend/src/stores/subscription.ts:56-60`)
- **경쟁 채널:** 서버 한도는 있으나 판매용 플랜 응답/비교표에 노출되지 않는다(Top 5 #5). `COMPETITOR_LIMIT`가 발생했을 때 업그레이드 경로를 UI에서 일관되게 안내하는지도 명확하지 않다. (`CompetitorUseCase.kt:128-136`; `SubscriptionUseCase.kt:330-338`; `PlanComparisonTable.vue:32-101`)
- **지원 수준:** 프런트에 플랜별 지원 문구가 있지만 코드 조사만으로 고객지원 SLA/실제 운영 프로세스가 이행되는지 확인할 수 없다. 판매 시 보장 가능한 응답시간과 지원 범위를 운영 정책으로 확정해야 한다. (`frontend/src/types/subscription.ts:40-127`; `frontend/src/stores/subscription.ts:56-63`)

## 2. 전환 지점과 오류 UX

- `PLAN_LIMIT_EXCEEDED`, `STORAGE_QUOTA_EXCEEDED`, 크레딧 부족 `CREDIT_INSUFFICIENT`는 서버/프런트에서 안정 코드로 분리된다. 프런트 helper는 화면별로 허용 코드를 명시하게 해, 결제로 풀리지 않는 오류에 잘못 결제를 권하지 않도록 설계됐다. (`backend/onGo-common/src/main/kotlin/com/ongo/common/exception/PlanLimitExceededException.kt:3-7`; `StorageQuotaExceededException.kt:3-10`; `InsufficientCreditException.kt:3-6`; `frontend/src/composables/usePlanLimit.ts:14-37,54-64`)
- 업로드/게시 화면에서 플랜 또는 저장공간 오류면 인라인 업그레이드 링크가 `/subscription`으로 향한다. 크레딧 부족은 별도 크레딧 구매 모달을 연다. 즉 오류에서 링크/버튼 1회, 구독 화면에서 플랜 선택, 결제 모달 확인이 필요하다. (`frontend/src/views/redesign/ComposeView.vue:570-594,1286-1292,1335-1339,1711-1719`; `usePlanLimit.ts:66-67`)
- AI 화면은 `CREDIT_INSUFFICIENT`를 분기해 충전 CTA를 노출하고 클릭하면 구매 모달을 연다. 해당 구현 경로의 동작 계약은 AI/영상 화면 테스트에 있다. (`frontend/src/views/AiView.creditInsufficiency.test.ts:123-160`; `frontend/src/views/VideosView.seoCredit.test.ts:109-164`; 테스트는 실행하지 않음.)
- 쇼츠 파이프라인은 작업 생성 전 총 필요 크레딧을 계산하고 부족하면 별도 코드 `SHORTS_INSUFFICIENT_CREDIT_FOR_RUN`을 반환한다. 이 코드는 일반 작업 도중 잔액 부족과 구분된다. (`ShortsPipelineCreditRequirements.kt:18-29,66-75`; `frontend/src/composables/usePlanLimit.ts:30-37`; `frontend/src/views/ugc/ShortsPipelineView.vue:597`)
- 구매 여정은 플랜 선택 후 checkout 흐름으로 이동한다. 구독 결제는 billing key 발급/서버 등록을 먼저 완료해야 PortOne 결제창을 열며, 발급 실패·취소면 대기 결제를 재조정한다. 모바일도 IFRAME 발급을 시도하고 리디렉션 URL에 billing key가 노출되지 않게 막는다. (`frontend/src/composables/usePortOne.ts:152-199,202-270`). 결제 사용 가능 여부 조회 실패/미설정은 fail-closed이므로, 사용자는 재확인 또는 지원 문의 안내가 필요하다. (`usePaymentAvailability.ts:4-24,40-60`; `PortOnePaymentService.kt:557-562`)

## 3. 결제부터 권한/영수증/해지까지

1. **가격 선택/의도 생성:** 서버는 선택한 플랜/주기의 금액으로 구독 checkout intent를 만들고 준비상태를 먼저 확인한다. 프런트 구독 화면은 결제가 가능할 때만 결제 모달을 연다. (`PortOnePaymentService.kt:333-369,557-562`; `frontend/src/views/SubscriptionView.vue:888-927`; `frontend/src/composables/usePaymentAvailability.ts:40-60`)
2. **PortOne 승인:** 구독은 billing key 등록 후 `requestPayment`; 크레딧 단건 구매는 checkout intent 후 바로 `requestPayment`를 호출한다. (`frontend/src/composables/usePortOne.ts:202-270,276-298`)
3. **검증/멱등성:** 웹훅 서명을 검증하고 처리 대상 이벤트를 멱등 처리한다. 완료 처리에서는 PortOne에서 결제 상태·금액·통화를 다시 확인하고 완료된 결제는 재처리하지 않는다. (`PortOnePaymentService.kt:83-120,435-468`)
4. **권한 반영:** 구독이면 플랜/기간/다음 청구일과 사용자 플랜, 플랜 entitlement 크레딧을 반영한다. 크레딧 결제면 구매 크레딧을 원장에 적립한다. (`PortOnePaymentService.kt:469-493,565-569,571-677`)
5. **영수증/내역:** 검증된 결제 응답의 `receiptUrl`을 결제 행에 저장한다. 프런트 내역은 receipt URL이 있을 때 영수증 링크를 표시한다. (`PortOnePaymentService.kt:461-468`; `frontend/src/views/SubscriptionView.vue:476-485`)
6. **해지/다운그레이드:** 프런트 해지 요청은 구독 API를 호출하고 PortOne 경로에서는 구독 상태를 `CANCELLED`로 기록하며 예약 다운그레이드를 지운다. 결제 기간과 실제 다음 청구 중단 동작은 renewal scheduler/provider 경로와 설정에 의존하므로 운영 시나리오로 확인해야 한다. (`SubscriptionUseCase.kt:158-177`; `BillingScheduler.kt:136-165`; `SubscriptionController.kt:92-96`; `SubscriptionView.vue:968-975`)

**자동 갱신 주의:** 갱신 스케줄러 기본 설정이 false다. 운영값 확인 전에는 “정기구독 자동갱신이 정상”이라고 판매 약속할 수 없다. 또한 코드에서 가격/상태가 맞는지 테스트하는 것은 실제 PortOne 상점·웹훅·카드 결제 성공을 증명하지 않는다. (`BillingScheduler.kt:52-53,136-149`)

**무료 체험:** Free 사용자만 Starter 7일 체험 가능, 한 계정 1회이며 체험 플랜 entitlement도 적용한다. 자동 전환/만료는 스케줄러 처리에 의존한다. 시작 로직은 확인했지만 운영 스케줄 활성/실제 만료 처리는 환경에서 별도 확인이 필요하다. (`SubscriptionUseCase.kt:181-227`; `SubscriptionController.kt:117-124`)

**단건 크레딧:** 4종 패키지 checkout이 있고, 완료 시 해당 구매 크레딧을 적립한다. (`CreditPackage.kt:3-13`; `PortOnePaymentService.kt:411-433,565-569`; `usePortOne.ts:276-298`)

## 4. 무료 누수 또는 유료 표기/동작 위험

- **무료 누수로 확인:** 신규 업로드 경로의 월간 업로드 카운트 미검사(Top 5 #2), 반복 예약 플랜 미검사(Top 5 #3), 분석 기간 미검사. 이들은 판매표/플랜 정의의 유료 차이를 서버에서 동일하게 집행하도록 맞춰야 한다. (`PlanType.kt:8-10`; 관련 구현은 1절 참조)
- **유료 가치가 화면에서 숨겨짐:** 경쟁 채널 수는 백엔드가 막지만 요금제 응답 및 비교표에 없다(Top 5 #5). (`CompetitorUseCase.kt:128-136`; `SubscriptionUseCase.kt:330-338`; `PlanComparisonTable.vue:32-101`)
- **Pro/Business 차별 부족:** `maxPlatforms`는 두 플랜 모두 4다. 다른 혜택으로 가격 상승을 설명해야 하며, 단순 채널 수만 보면 차이가 없다. (`PlanType.kt:18-19`)
- **0-credit 감정 분석:** `SENTIMENT_ANALYSIS`의 단가는 0이다. Pro/Business 댓글 기능에 포함된 원가 보조로 의도했을 수 있으나, 실제 LLM 비용이 발생하는 분석 호출이라면 사용량/비용 상한 또는 포함량을 정해야 한다. 의도는 코드만으로 확정할 수 없다. (`AiFeature.kt:17`; `CommentUseCase.kt:118-123`)
- **결제 상태/운영 설정:** readiness가 꺼지면 결제가 차단되고, 갱신 기본 설정은 꺼져 있다. 배포 환경값을 검사하지 않았으므로 “현재 판매 가능” 여부는 결론 낼 수 없다. (`PortOnePaymentService.kt:557-562`; `BillingScheduler.kt:52-53,136-149`)

## 5. 코드상 가격 단가와 원가 판단

- **크레딧 판매가:** Starter 500/₩4,900(30일), Basic 1,200/₩9,900(60일), Pro 3,000/₩19,900(90일), Business 10,000/₩49,900(180일). 크레딧당 명목 매출은 각각 약 ₩9.80, ₩8.25, ₩6.63, ₩4.99다(부가세·수수료·환불 제외, 단순 나눗셈). (`backend/onGo-common/src/main/kotlin/com/ongo/common/enums/CreditPackage.kt:3-13`)
- **AI 가격표:** 예: 메타 생성 5, 해시태그 3, STT 10, 성과 리포트 8, 댓글 답변 2 크레딧이며 쇼츠 단계는 reframe 3/segment 8/subtitle 5/hook 5/template 3/validate 3이다. (`AiFeature.kt:7-38`; `ShortsPipelineCreditRequirements.kt:20-29`)
- **쇼츠 STT는 길이 비례:** 시작한 10분 단위마다 STT 10크레딧을 부과한다. 쇼츠 파이프라인을 한 번 완주하는 기타 AI 단계 합은 27크레딧이므로 10분 이하 영상은 총 37크레딧, 3시간 상한은 STT 180+기타 27=207크레딧이다. 이는 정해진 크레딧 매출 환산치로 약 ₩184~₩2,029(패키지별 최저·최고 단가 기준)이나, 실제 원가/마진과 동일하지 않다. (`ShortsPipelineCreditRequirements.kt:44,66-75,77-85`; `ShortsPipelineUseCase.kt:78-86`; `AiFeature.kt:9,33-38`; `CreditPackage.kt:9-12`)
- **크레딧 차감/복구:** 차감 서비스와 예외 발생 시 복구 경로가 있으며 플랜 무료 크레딧이 구매 크레딧보다 먼저 사용된다. 구매분은 패키지 만료일을 가진다. (`backend/onGo-application/src/main/kotlin/com/ongo/application/credit/CreditService.kt:73-95,98-157,597-609`)
- **원가/손익 결론은 코드만으로 불가:** STT는 OpenAI transcription 호출을 확인할 수 있지만 모델별 실제 단가·토큰 사용량·현재 공급자 요금은 저장돼 있지 않다. 저장비, egress, 렌더링 CPU/GPU 비용과 수익 매핑도 확인할 수 없다. 그러므로 “특정 기능이 손해”라고 확정할 근거는 없다. Business 크레딧 묶음이 크레딧 단가를 가장 낮추므로 실제 API/렌더 비용을 측정해 크레딧별 원가와 gross margin을 계산해야 한다. (`SttUseCase.kt:101-132`; `AiConfig.kt:40-45`; `CreditPackage.kt:9-12`; `ShortsPipelineCreditRequirements.kt:16-20`)

## 판매 전 우선 액션

1. 업로드 진입점 전부에 월 업로드 cap을 적용하고 동시 업로드/재시도 정의를 확정한다.
2. recurring schedule과 분석 기간을 플랜 entitlement에 연결하거나, 그 차이를 요금표에서 제거한다.
3. PortOne readiness, webhook 수신, 실결제/환불/해지, 자동 갱신 scheduler를 운영 설정으로 검증한다. `subscription.renewal.enabled`는 명시값 확인 없이는 켜졌다고 가정하지 않는다.
4. 경쟁 채널 수를 비교표/API에 공개하고 cap 도달 시 업그레이드 CTA가 이어지는지 확인한다.
5. 실제 LLM/STT/저장/전송/렌더 원가를 기능별로 수집하고 패키지별 공헌이익 및 무료 크레딧 비용을 산출한다.

이 보고서는 정적 코드 조사 결과이며 운영 결제 성공, 설정 활성화, 실제 인프라 비용 또는 전체 테스트 통과를 의미하지 않는다.
