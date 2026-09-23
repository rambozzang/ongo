# 수익 경로 2차 — 작업 계약 (Claude · Codex)

작성 2026-09-23. 같은 작업트리에서 동시에 일한다. **자기 소유가 아닌 파일은 고치지 않는다.**
커밋은 Claude 만 한다. Codex 는 끝나면 `docs/work/shorts-cost-ledger-report.md` 에 보고를 쓴다.

## 공통 금지
- `설정_작업.txt` 읽기·수정 금지
- 운영 DB·운영 설정·비밀값 접근 금지, 실제 외부 API·결제 호출 금지
- 가짜 성공/목 운영 로직 금지, 기존 테스트 완화·삭제 금지
- git checkout/reset/revert/stash 금지
- 요금·크레딧 단가·자동 갱신 변경 금지
- 같은 작업트리라 Gradle 동시 실행 시 XML/IO 오류가 날 수 있다 — 그러면 다시 돌린다

## Claude 소유
- dev-login 운영 차단: `DevAuthController.kt`, `SecurityConfig` 의 dev 경로, `ProductionConfigurationValidator.kt`,
  `deploy/start.sh`, 관련 테스트
- 대시보드 KPI 이전 기간: `AnalyticsUseCase.kt`, `AnalyticsJooqRepository.getDashboardKpi` 와 그 테스트
- 배포 전 데이터 감사 SQL: `deploy/audit/*`
- `deploy/preflight-schema.sh`(.test.sh) — Codex 가 마이그레이션을 추가하면 Claude 가 반영한다

## Codex 소유 — 쇼츠 실행 원가 기록 (C4)
목적: 쇼츠 1회 실행마다 **실제로 쓴 외부 자원**을 저장해, 가격 결정을 추정이 아니라 실측으로 한다.
- 마이그레이션 **V117** 하나만 (번호 예약됨). 새 테이블이든 기존 run 테이블 컬럼이든 설계는 Codex 판단
- 기록 대상(최소): 음성 인식 원본 길이(초)·모델명, LLM 호출별 제공자·모델·입력/출력 토큰(응답에 사용량이 없으면 NULL — 0 금지)
- 기록 실패가 실행·과금을 깨뜨리면 안 된다(측정은 부수 효과)
- 파일: `ugc/shorts/**`, `ai/SttUseCase.kt`, 필요한 도메인/인프라 repository·Tables.kt 상수, 신규 테스트
- 실 PG IT 1개 이상(`*IT.kt`, Testcontainers) — V117 적용·저장·NULL 보존
- 가능하면 관리자용 조회(run 별 원가 합계) 읽기 API. UI 는 선택

## 인터페이스
- Claude 는 V117 이후 번호를 쓰지 않는다. Codex 는 V117 외 마이그레이션을 만들지 않는다
