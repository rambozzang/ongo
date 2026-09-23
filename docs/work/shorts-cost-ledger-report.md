# C4 쇼츠 실행 원가 원장 구현 보고서

작성일: 2026-09-23  
계약: `docs/work/revenue-path-contract.md` · 배경: `docs/work/shorts-unit-economics.md`

## 구현 결과

쇼츠 파이프라인 실행과 별개인 `ugc_shorts_cost_ledger` 원장을 추가했다. 실제 STT 요청이 시작된 실행에는 서버가 미리 측정해 `PipelineRun.sourceDurationMs`에 보관한 원본 길이를 초 단위로 기록하고, 요청 모델을 `whisper-1`로 명시했다. 각 LLM 단계의 응답 envelope에서 제공자·모델·입력/출력 토큰 사용량을 기록한다. 한 번의 외부 요청마다 행을 남기므로 실행 재시도와 반복 요청도 각각 관측 가능하다.

응답에 사용량 정보가 없을 때 Spring AI가 `EmptyUsage`의 0/0을 제공하는 경우가 있어 이를 누락으로 판별해 DB `NULL`로 저장한다. 실제 요청 정보가 보고된 경우에만 토큰 값을 기록한다. 원장 저장 예외는 로깅하고 삼켜 파이프라인 작업·크레딧 흐름을 실패시키지 않는다. 과거 실행에서 측정된 원본 길이가 없는 경우 길이를 추정하지 않고 `NULL`로 남긴다.

원가는 사용량 데이터만 저장하며 통화 환산·금액 추정은 하지 않는다. 관리자별 원가 합계 조회 API는 계약상 선택 사항이라 추가하지 않았다.

## 변경 파일 및 설계 이유

- `backend/onGo-api/src/main/resources/db/migration/V117__shorts_cost_ledger.sql` — 예약된 단일 마이그레이션. 실행 FK(삭제 시 cascade), 호출 종류·단계·제공자·모델, 초 길이, nullable 토큰 수, 기록 시각과 음수 방지 제약 및 run 인덱스.
- `backend/onGo-domain/src/main/kotlin/com/ongo/domain/ugc/shorts/ShortsCostEntry.kt`, `ShortsCostLedgerRepository.kt` — 프레임워크와 DB에 의존하지 않는 원장 모델·저장 포트.
- `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/ShortsCostLedgerJooqRepository.kt`, `.../Tables.kt` — 기존 jOOQ 방식으로 V117 테이블 저장 및 허용된 테이블 상수 등록.
- `backend/onGo-application/src/main/kotlin/com/ongo/application/ugc/shorts/ShortsCostLedger.kt` — 토큰 메타데이터 해석, 실제 선택 가능한 설정 제공자 기준 분류, 원장 기록 실패 격리.
- `backend/onGo-application/src/main/kotlin/com/ongo/application/ai/SttUseCase.kt` — 파이프라인 경로에만 관측 콜백을 추가하고 `whisper-1`을 명시. 측정 콜백 오류는 전사 결과에 영향을 주지 않음.
- `backend/onGo-application/src/main/kotlin/com/ongo/application/ugc/shorts/stage/{Transcribe,Segment,Hook,Subtitle,Template,Reframe,Validate}StageExecutor.kt` — 전사 원본 길이와 6개 LLM 실행 단계의 제공자 응답 메타데이터 연결.
- `backend/onGo-application/src/test/kotlin/com/ongo/application/ugc/shorts/ShortsCostLedgerTest.kt` — 초 변환, 제공자 fallback, 사용량 미제공의 NULL, 저장 장애 격리 검증.
- `backend/onGo-application/src/test/kotlin/com/ongo/application/ugc/shorts/stage/StageTestFixtures.kt`, `SegmentStageExecutorTest.kt` — 기존 단계 동작/오류 테스트가 `responseEntity` 경로를 같은 결과 값으로 검증하도록 mock envelope 갱신. 테스트 기대값이나 검증을 완화하지 않음.
- `backend/onGo-infrastructure/src/test/kotlin/com/ongo/infrastructure/persistence/jooq/ShortsCostLedgerIT.kt` — Testcontainers PostgreSQL에서 실제 V117 적용과 저장 왕복 검증.

## 검증 — 명령과 실제 결과

1. API 컴파일 및 서비스/실 PostgreSQL 저장 검증:

   ```text
   cd backend
   ./gradlew :onGo-api:compileKotlin \
     :onGo-application:test --tests 'com.ongo.application.ugc.shorts.ShortsCostLedgerTest' \
     :onGo-infrastructure:test --tests 'com.ongo.infrastructure.persistence.jooq.ShortsCostLedgerIT'
   ```

   결과: `BUILD SUCCESSFUL` (17초). API Kotlin 컴파일 성공, 원장 단위 테스트 **2/2 통과**, Testcontainers의 PostgreSQL 16 IT **1/1 통과**. IT에서 Spring Boot/Flyway가 실제 PG에 V117을 적용한 뒤 실행 연결, STT `3600.000`초, 제공자·모델, 토큰 값 및 누락 토큰 `NULL`을 다시 읽어 확인했다.

2. 변경된 쇼츠 단계 회귀 테스트:

   ```text
   ./gradlew :onGo-application:test \
     --tests 'com.ongo.application.ugc.shorts.ShortsCostLedgerTest' \
     --tests 'com.ongo.application.ugc.shorts.stage.*StageExecutorTest'
   ```

   결과: `BUILD SUCCESSFUL` (5초), 원장 단위 테스트와 쇼츠 단계 테스트 **42/42 통과** (원장 2, 단계 40).

3. `git diff --check` — 공백/패치 오류 없음.

## 변이 검증

- 사용량이 없는 응답도 기록된 것으로 간주하도록 `usageReported = true`로 변이: `ShortsCostLedgerTest` 실패(**2개 중 1개 실패**). 입력 토큰이 `NULL`이어야 하는데 0이 들어오는 것을 검출했다. 변이를 원복했다.
- 원장 저장 실패를 삼키지 않고 그대로 전파하도록 `runCatching`을 제거: `ShortsCostLedgerTest` 실패(**2개 중 1개 실패**, `IllegalStateException`). 측정 실패가 실행을 깨뜨리지 않는 계약을 검출했다. 변이를 원복했다.
- 두 변이 원복 후 최종 단위 테스트와 PostgreSQL IT가 모두 통과했다.

## 범위 확인

Codex는 V117만 추가했다. 운영 DB·설정·비밀값과 외부 API/결제에는 접근하지 않았고 커밋하지 않았다. 현재 작업트리의 dev-login·대시보드·갱신 관련 변경은 계약상 Claude 소유라 건드리지 않았다.

## Claude 리뷰 수정 (2026-09-23)

1. **전사 모델이 코드 상수 `whisper-1` 로 고정됐다** — 단위경제 문서의 1순위 권장(설정 한 줄로
   `gpt-4o-mini-transcribe` 전환)이 막히고, 설정을 바꿔도 원장은 옛 이름을 적는다. `SttUseCase` 가
   `spring.ai.openai.audio.transcription.options.model`(기본 `whisper-1`)을 읽어 요청과 원장에 같은 값을
   쓴다. `SttUseCaseTest` 2건(설정값 전달·기본값).
2. **제공자 판정 규칙이 `ChatClientResolver` 를 복사했다** — 선택 규칙이 바뀌면 원장이 조용히 틀린다.
   `ChatClientResolver.resolveProvider` 를 내놓고 원장이 그것을 부른다. 사용 가능한 제공자가 없으면
   (호출 자체가 일어나지 않은 경우) 원장도 쓰지 않는다 — 없는 호출을 QWEN 으로 적던 fallback 을 뺐다.
3. 확인만: LLM 단계는 트랜잭션 밖에서 돌아, 원장 INSERT 실패가 다른 쓰기를 aborted 로 만들지 않는다.
4. `deploy/preflight-schema.sh` 에 V117 과 `ugc_shorts_cost_ledger` 확인을 추가(Claude 소유).
