# 전문 크리에이터 실사용 고도화 — Claude 보고서

작성 2026-10-09 · Claude. Codex 는 캘린더/예약·성과 분석·통합 검증·매뉴얼 담당이다.
이 문서는 Claude 의 선택 범위·진행 상태·변경 파일·검증 결과를 Codex 가 읽을 수 있게 남긴다.

## 상태: 완료 (2026-10-09) — 저장(커밋)·올리기(푸시)·운영 반영 없음, 로컬 구현과 시험까지

## 현 상태 검토 결과 (요약)

- **만들기(ComposeView)** — 이미 상당히 갖춰져 있다: 서버 기능 정보 기반 플랫폼별 글자/태그 한도 검증
  (`validatePublishDrafts`), 플랫폼별 초안, 서버 초안 저장·불러오기, AI 메타데이터 적용, 자막. 이번 범위에서는 손대지 않는다.
- **인박스(InboxView + redesignInbox 스토어)** — 백엔드 기능의 절반도 연결돼 있지 않다:
  - 검색·플랫폼·감정 필터가 서버에 있는데(`GET /comments?searchText&platform&sentiment&page`) 화면은 처음 100개만 받아 클라이언트 정규식으로 분류한다.
  - "일괄 처리" 가 숨김 하나뿐이다. 서버의 일괄 답변·AI 일괄 초안(`/batch/reply`, `/ai-draft`)이 화면에 없다.
  - **일괄 숨김이 일부 실패해도 선택한 전부를 처리 완료로 표시한다**(성공 개수를 무시) — 가짜 성공.
  - 백엔드 일괄 응답이 개수만 준다 — 어떤 댓글이 왜 실패했는지 알 수 없어 실패만 다시 시도할 수 없다.
  - 일괄 요청 개수·동시 실행 상한이 없다 — 수백 건이 플랫폼 API 로 한꺼번에 나갈 수 있다.

## 선택한 범위 — 인박스(댓글) 전문화

1. **백엔드 일괄 처리 신뢰성** — 항목별 결과(성공/실패 코드·사유·재시도 가능 여부), 요청 상한 50건, 중복 제거,
   빈 답변 거부, 동시 실행 상한. 기존 `successCount/failCount/totalRequested` 는 유지(하위 호환).
2. **화면 일괄 처리 3종** — 숨김 / 같은 답변 보내기 / AI 초안(크레딧 사전 표시 → 초안 검토·수정 → 개별·전체 전송).
   항목별 결과를 목록에 표시하고 **실패한 것만 다시 시도**. 성공한 것만 처리 완료로 바꾼다.
3. **서버 검색·필터·더 불러오기** — 검색어(지연 입력), 플랫폼, 감정을 서버 조회로. 페이지 단위 더 불러오기.
4. 단건 답변 실패 시 작성 중인 답변 보존(이미 동작 — 테스트로 고정).

## 파일 소유 (충돌 방지)

Claude: `frontend/src/views/redesign/InboxView.vue`(+테스트), `frontend/src/stores/redesignInbox.ts`(+테스트),
`frontend/src/api/comments.ts`, `frontend/src/types/comment.ts`, i18n 의 `redesign.inbox.*` 키만,
백엔드 `application/comment/CommentBatchUseCase.kt`·`dto/CommentCommandCenterDtos.kt`·`api/comment/CommentController.kt`(일괄 부분)와 테스트.
Codex 소유(CalendarView·PerformanceView·redesign calendar/performance 스토어·analytics/schedule 백엔드·UserManualView)는 건드리지 않는다.
마이그레이션은 추가하지 않는다.

## 진행 기록

### 체크포인트 1 — 백엔드 (완료, 시험 통과)

추가로 발견해 고친 결함:
- **댓글 숨기기가 토글이었다** — 일괄 숨김에 이미 숨긴 댓글이 섞이면 도로 보이게 됐다. `setHidden(userId, id, hidden)` 추가,
  일괄 숨김은 항상 숨김. 단건 `PUT /comments/{id}/hide` 는 `hidden` 쿼리를 받고, 생략하면 예전 토글(댓글 관리 화면 호환).
- **AI 일괄 초안이 소유자를 확인하지 않았다(보안)** — 남의 댓글 ID 로 그 내용·초안을 받을 수 있었다. 본인 댓글만, 25개 상한,
  나오지 않은·중복·빈 초안의 크레딧은 돌려준다(`CreditService.withCreditsRefundingUnused` — 원가 예산·실패 시 전액 환불 포함).
- 일괄 답변/숨김: 항목별 결과(`items[]` — commentId·success·errorCode·message·retryable), 50건 상한, 중복 제거,
  빈 답변·2,000자 초과 거부, 동시 4건 제한. 기존 개수 필드 유지.

변경 파일: `CommentBatchUseCase.kt`, `dto/CommentCommandCenterDtos.kt`, `CommentEngagementUseCase.kt`(setHidden),
`CommentController.kt`(hide 의 hidden 쿼리), `GenerateReplyUseCase.kt`(executeBatch), `CreditService.kt`(withCreditsRefundingUnused).
시험: `CommentBatchUseCaseTest`(8), `GenerateReplyBatchTest`(4, 신규), `CreditServiceTest`(+2), `CommentEngagementUseCaseTest`(+1) 통과.

### 체크포인트 2 — 화면 (완료)

인박스(`InboxView.vue`, `stores/redesignInbox.ts`):
- **서버 검색·필터·더 불러오기** — 검색어(입력 멈춘 뒤 300ms), 플랫폼, 부정 탭은 `sentiment=NEGATIVE` 로 서버 재조회
  (탭을 떠나면 조건 해제). 50개씩, 겹치는 댓글은 거른다. DM 은 서버 검색이 없어 같은 조건을 화면에서 적용.
- **조회 순번 가드** — 늦게 온 예전 응답·예전 조건의 다음 페이지가 새 결과를 덮지 않는다.
- **일괄 3종** — 처리 완료(숨김) / 같은 답변 보내기 / AI 초안. 서버 항목별 결과로 **성공한 것만** 처리 완료, 실패는 체크와 사유 유지.
- **실패 다시 시도** — 실패마다 소속 배치(번호·동작·문구)를 저장하고, 재시도는 **마지막 배치의 다시 시도 가능한 실패만
  그 배치의 동작·문구로** 보낸다(Codex 지적: 예전엔 이전 숨김 실패 댓글에 나중 답변 문구가 게시될 수 있었다).
- **AI 초안** — 서버 단가 × 선택 수를 버튼에 표시(단가를 모르면 버튼 잠금), 25개 상한, 검토 패널에서 고친 문구만 보냄,
  실패한 초안은 문구·사유와 함께 남아 다시 보낼 수 있다. 만들기만으로는 아무것도 게시되지 않는다.
- **감정 대소문자 결함** — 서버는 `NEGATIVE` 로 저장하는데 화면은 `negative` 와 비교해 부정 필터·배지가 한 번도 맞지 않았다. 정규화.
- 단건 처리 완료는 `hide(id, true)` 로 명시(토글 아님).
- i18n: `redesign.inbox.*` 키 35개를 ko/en 에 줄 단위로 추가(기존 키·형식 변경 없음).

### 체크포인트 3 — Codex 리뷰 반영 (완료)

1. **재시도가 다른 동작을 보낼 수 있던 결함** — 위 "실패 다시 시도" 로 수정. 회귀 시험 3개(숨김 실패 후 답변 성공 → 재시도 아무것도
   안 보냄 / 혼합 실패 → 마지막 답변 배치 대상만 그 문구로 / 초안 실패 → 그 초안 문구로 단건 재전송).
2. **부분 환불이 원가 상한과 충돌** — 검토 근거: `AiSpendContext` 예산은 `withCreditsRefundingUnused` 시작 시 **받은 크레딧 전체**
   (`budgetKrw(amount)`)로 열리고 가로채기는 그 안에서 실제 원가를 `spentKrw` 에 쌓는다. 그 뒤 미사용분을 다 돌려주면 남은 크레딧 C 에 대해
   `spentKrw > budgetKrw(C)` 가 될 수 있어 `AiUnitEconomics` 의 "크레딧 1개당 원가 ≤ budgetKrwPerCredit(₩2.2)" 계약을 깬다
   (예: 75 받고 72 환불 → 3크레딧 예산 ₩6.6 vs 원가 최대 ₩165). 수정: 블록이 끝날 때의 `spentKrw` 를 읽어
   `mustKeep = creditsFor(spentKrw)`(원가를 덮는 최소 크레딧, 원가 0 이면 0)는 남기고 그 위만 환불. 응답의 사용 크레딧은 실제로 가져간 값
   (`Settled.creditsCharged`). 시험: 원가를 10크레딧어치 썼으면 그만큼 남기고 `budgetKrw(charged) ≥ spent` 확인.
3. **부분 환불 거래 실패 시 사용량 과소 표시** — 환불 트랜잭션이 실제로 커밋된 만큼만 뺀다. 실패하면 받은 전부를 사용량으로 보이고
   결과는 그대로 돌려준다(오류 기록). 시험 추가, 변이(실패해도 뺀 값) 시 실패 확인.
4. **부정 탭 서버 필터·조회 순번 가드** — 위 체크포인트 2 에 반영. 회귀 시험 4개(미수집 부정 댓글을 서버 필터로 찾기·해제 / 화면 필터 간
   이동은 재조회 없음 / 늦게 온 검색 응답 버림 / 예전 조건 다음 페이지 버림).

## 변경 파일 (최종)

백엔드
- `onGo-application/.../comment/CommentBatchUseCase.kt` — 항목별 결과, 50건·동시 4건 상한, 중복 제거, 빈/2,000자 초과 거부, 숨김은 명시값
- `onGo-application/.../comment/dto/CommentCommandCenterDtos.kt` — `BatchItemResult`, `BatchResultResponse.items`
- `onGo-application/.../comment/CommentEngagementUseCase.kt` — `setHidden(userId, id, hidden?)`
- `onGo-api/.../comment/CommentController.kt` — `PUT /comments/{id}/hide?hidden=` (생략 시 예전 토글)
- `onGo-application/.../ai/GenerateReplyUseCase.kt` — AI 일괄 초안: 소유자 확인(보안), 25개 상한, 요청 순서 유지, 중복·빈 초안 제거, 실제 사용량 응답
- `onGo-application/.../credit/CreditService.kt` — `withCreditsRefundingUnused` + `Settled`

프런트
- `src/stores/redesignInbox.ts`, `src/views/redesign/InboxView.vue`, `src/api/comments.ts`(`hide(id, hidden?)`), `src/types/comment.ts`
- `src/locales/ko/common.json`, `src/locales/en/common.json` — `redesign.inbox.*` 추가만
- 시험: `src/stores/redesignInbox.test.ts`(15), `src/views/redesign/InboxView.test.ts`(6)

손대지 않은 것: ComposeView(Codex 가 이탈 시 편집 손실 방지 담당), manualSections.ts·UserManualView(Codex), 캘린더·성과 쪽 전부. 마이그레이션 없음.

## 검증

- 프런트: 인박스 스토어·화면 시험 21개 통과, `vue-tsc` 통과, 변경 파일 eslint 통과.
- 백엔드 대상 시험(Codex 최종 직렬 실행용 클래스 목록):
  - `com.ongo.application.comment.CommentBatchUseCaseTest` (8)
  - `com.ongo.application.comment.CommentEngagementUseCaseTest` (setHidden 포함)
  - `com.ongo.application.ai.GenerateReplyBatchTest` (5, 신규)
  - `com.ongo.application.credit.CreditServiceTest` (26 — 부분 환불·원가 최소 유지·환불 실패 포함)
  - 모두 통과. 백엔드 전체 시험은 Codex 가 양측 작업 후 직렬 실행.
- 변이 검증(넣고 → 시험 실패 확인 → 원본 바이트 동일 복원):
  일괄 결과 무시 / AI 초안 소유자 확인 제거 / 재시도 배치 소속 조건 제거 / 환불 실패를 환불로 계산 / 조회 순번 가드·서버 감정 필터 제거.

## 남은 일 · 참고

- 매뉴얼: 인박스 일괄 처리·실패 재시도·AI 초안 검토, 부분 환불(원가 최소 크레딧 유지) 계약 — Codex 가 manualSections.ts 의 한국어/영어에 반영 완료.
- 저장된 답변은 여전히 고정 문구 4개다(사용자 정의 저장은 새 테이블이 필요해 이번 범위에서 제외).

## Codex 최종 통합 검증

양측 최종 수정 이후 프런트 155개 파일/1,321개 시험, 백엔드 application 2,108개/API 175개 시험 모두 통과.
프로덕션 빌드 통과, eslint 오류 0개. 상세 범위와 외부 서비스 검증 한계는 [Codex 보고서](creator-pro-codex-report.md)에 기록했다.

## 최종 확인 (Codex 통합 검증 요청에 대한 답)

- Claude 쪽 수정은 **모두 끝났다.** 이 시점 이후 Claude 는 Gradle·프런트 시험을 새로 실행하지 않는다(동시 실행 XML 충돌 방지).
  백엔드 전체(application/API)와 프런트 build·전체 시험은 Codex 가 직렬로 실행한다.
- **refundTx 실패 시 `creditsCharged` 반영 — 완료.** `CreditService.withCreditsRefundingUnused`(141~153행):
  `refunded = runCatching { refundTx.execute { refundAllocation(allocation, unused) } }.fold(onSuccess = { unused }, onFailure = { 로그; 0 })`,
  반환은 `Settled(result, amount - refunded)`. 즉 **실제로 커밋된 환불분만** 사용량에서 뺀다. 환불이 실패하면 받은 크레딧 전부가
  사용량으로 보이고, 결과는 버리지 않는다.
  - 시험: `CreditServiceTest` 의 `withCreditsRefundingUnused 는 환불 거래가 실패하면 받은 크레딧 전부를 사용량으로 보인다`(508행).
  - 변이 확인: 실패 분기를 `unused` 로 바꾸면 이 시험이 실패했고, 원본은 바이트 동일하게 복원했다(마지막 Claude 실행: 26/26 통과).
- Codex 최종 실행에 포함돼야 할 Claude 대상 시험 클래스:
  `com.ongo.application.comment.CommentBatchUseCaseTest`, `com.ongo.application.comment.CommentEngagementUseCaseTest`,
  `com.ongo.application.ai.GenerateReplyBatchTest`, `com.ongo.application.credit.CreditServiceTest`,
  프런트 `src/stores/redesignInbox.test.ts`, `src/views/redesign/InboxView.test.ts`.
