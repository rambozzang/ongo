# ABTest WIP 활성화 게이트 시나리오

조사일: 2026-08-07
대상: `ABTestController` (`/api/v1/ab-tests`)
목적: `@Profile("wip")`를 제거하기 전에 기본 프로필에서 컨텍스트·DB·API·스케줄러가 실제로 안전한지 확인한다.

이 문서는 계획과 판정 기준만 담는다. 현재 코드는 수정하지 않았고, ABTest 전용 테스트는 아직 0건이다.

## 현재 소스 근거

- 컨트롤러는 `backend/onGo-api/src/main/kotlin/com/ongo/api/abtest/ABTestController.kt:23-28`에서 WIP 프로필과 두 의존성(`ABTestUseCase`, `ABTestStatisticsService`)을 선언한다. 경로는 `:25`의 `/api/v1/ab-tests`다.
- UseCase는 `backend/onGo-application/src/main/kotlin/com/ongo/application/abtest/ABTestUseCase.kt:16-22`에서 WIP 프로필과 세 저장소(`ABTestRepository`, `ABTestVariantRepository`, `VideoRepository`)를 선언한다.
- 통계 서비스는 `ABTestStatisticsService.kt:15-19`에서 두 ABTest 저장소를 사용하고 WIP 프로필이 없다. 통계 계산은 `:27-87`, 표본·카이제곱 계산은 `:93-192`에 있다.
- 자동 평가기는 `ABTestEvaluator.kt:13-19`에서 일반 컴포넌트로 등록되고 매시간 실행된다(`:23-25`). 실행 중 테스트를 조회하고 유의미한 승자를 완료 처리한다(`:28-60`).
- DB 계약은 `backend/onGo-api/src/main/resources/db/migration/V4__analytics_tables.sql:20-48`에 있다. `ab_tests.user_id`는 `users` FK, `ab_test_variants.test_id`는 `ab_tests` FK + `ON DELETE CASCADE`다.
- 저장소 어댑터는 `backend/onGo-infrastructure/src/main/kotlin/com/ongo/infrastructure/persistence/jooq/ABTestJooqRepository.kt:32-105`와 `:108-175`에서 확인된다. `VideoRepository` 어댑터도 `VideoJooqRepository.kt:38-42`에 있다.
- Spring Boot 기본 애플리케이션은 `backend/onGo-api/src/main/kotlin/com/ongo/api/OnGoApplication.kt:9-13`의 `com.ongo` 전체 스캔이다. 현재 인프라 IT의 `InfrastructureTestApplication.kt:13-31`은 persistence/domain만 스캔하므로 ABTest 컨트롤러 노출을 검증하지 않는다.

## 활성화 전 차단 조건

다음 중 하나라도 실패하면 `@Profile("wip")`를 유지한다.

1. 기본 프로필(테스트에서는 `test` 프로필, `wip` 미활성)로 `OnGoApplication` 컨텍스트가 뜨지 않는다.
2. `ABTestRepository`, `ABTestVariantRepository`, `VideoRepository` 중 하나라도 실제 Spring bean으로 연결되지 않는다.
3. Testcontainers PostgreSQL에서 Flyway 적용 후 저장·조회·수정·삭제가 실패한다.
4. UseCase의 사용자 소유권 검사, variant cascade, 상태 전이가 실패한다.
5. 컨트롤러의 인증 사용자 경계와 404/403/검증 오류 응답이 계약과 다르다.
6. 자동 평가기의 DB 오류가 트랜잭션을 오염시키거나 다중 인스턴스 중복 실행 위험이 해결되지 않는다.

### 현재 발견된 스케줄러 차단점

`ABTestEvaluator.evaluateRunningTests()`는 하나의 `@Transactional` 메서드(`ABTestEvaluator.kt:23-25`) 안에서 사용자별 항목을 순회하고, 각 항목의 예외를 `catch`해 로그만 남긴다(`:32-57`). jOOQ가 Spring 트랜잭션에 참여하는 현재 구조에서는 DB 예외를 삼킨 뒤 트랜잭션이 abort될 수 있다. 그러면 뒤 항목의 작업과 앞서 성공한 작업이 모두 커밋되지 않을 수 있다. 이 동작은 소스만으로 운영 결과를 단정하지 않고, **실제 PostgreSQL IT로 재현한 뒤** 다음 중 하나를 구현해야 한다.

- 항목 단위 `REQUIRES_NEW`/`TransactionTemplate`로 격리하고 바깥 루프는 실패 항목을 기록하며 계속 진행
- 또는 예외를 다시 던져 전체 배치를 원자적으로 실패시키고 재시도

현재 문서 기준으로는 이 차단점이 해소되지 않았으므로 ABTest 활성화 게이트는 아직 통과하지 않은 상태다. 다중 인스턴스에서 `ABTestEvaluator`가 중복 실행될 수 있는지는 배포 토폴로지와 락 정책을 확인해야 하며, 미확인으로 둔다.

## 통과 시나리오

### 1. 컨텍스트·빈 연결

새 테스트 소스셋에서 `@SpringBootTest(classes = [OnGoApplication::class])`, `@ActiveProfiles("test")`로 실행한다. `wip`를 active profile에 넣지 않은 상태에서 다음을 확인한다.

- `ABTestController`, `ABTestUseCase`, `ABTestStatisticsService`, `ABTestEvaluator` bean이 생성된다.
- `ABTestJooqRepository`, `ABTestVariantJooqRepository`, `VideoJooqRepository`가 각 도메인 인터페이스에 유일하게 주입된다.
- Flyway가 V4를 포함해 성공하고, 다른 WIP 컨트롤러의 빈 누락 때문에 전체 컨텍스트가 실패하지 않는다.
- `GET /api/v1/ab-tests` 매핑이 등록된다. 현재 매핑 목록은 `ABTestController.kt:31-154`다.

이 단계는 기존 `InfrastructureTestApplication` IT로 대체하지 않는다. 그 테스트 애플리케이션은 API 스캔을 하지 않기 때문이다.

### 2. 저장소·트랜잭션 IT (Testcontainers PostgreSQL)

최소 fixture로 사용자 1명과 영상 1개를 만들고 V4 스키마에서 다음을 검증한다.

- `ABTestJooqRepository.save/findById/findByUserId/update/delete`가 `ab_tests`에 정확히 반영된다.
- `ABTestVariantJooqRepository.save/findByTestId/deleteByTestId`가 `ab_test_variants`에 반영된다.
- 부모 삭제 후 variant가 `ON DELETE CASCADE`로 사라진다(`V4:36-39`).
- variant 저장 중 예외가 나면 `ABTestUseCase.createTest()`의 부모와 앞서 저장한 variant가 함께 롤백된다. UseCase의 `@Transactional`은 `ABTestUseCase.kt:40-64`다.
- `video_id`가 존재하지 않는 경우 FK 오류가 표면화되고, 잘못된 `user_id`로 타 사용자의 테스트가 조회되지 않는다.

### 3. UseCase 단위 테스트

현재 0건이므로 MockK 테스트를 별도로 추가한다. 단순 메서드 호출뿐 아니라 실패 시 상호작용을 검증한다.

- 생성: 요청 variant 전부 저장, 빈 variant/중복 입력의 정책 확인, variant 저장 실패 시 후속 저장 중단.
- 권한: `get/update/delete/start/stop/pause/complete/applyWinner/getStatistics`에서 다른 사용자 ID가 `ForbiddenException`을 받는지 확인한다. 조회·수정 구현 근거는 `ABTestUseCase.kt:33-38`, `:66-86`, `:88-172`다.
- 상태: start=`RUNNING`, pause=`PAUSED`, stop/complete=`COMPLETED`, 시작·종료 시각 보존을 검증한다.
- 승자: views=0, clicks 동률, variant 1개/0개, 클릭률 최고 variant를 검증한다(`ABTestUseCase.kt:153-172`).
- 통계: variant 0/1개, 표본 부족, 95% 유의미 조건, Wilson 구간·카이제곱 경계값을 검증한다(`ABTestStatisticsService.kt:31-87`, `:140-157`).

### 4. API 통합 테스트

인증 테스트 fixture를 사용해 기본 프로필에서 컨트롤러가 실제로 응답하는지 검증한다.

- `GET /api/v1/ab-tests`, `GET /{id}`, `GET /summary`, `GET /{id}/statistics`, `GET /videos`의 200/빈 상태.
- `POST /`, `PUT /{id}`, `DELETE /{id}`, `POST /{id}/start|stop|pause|complete|apply-winner`의 정상 상태 전이.
- 타 사용자 ID의 접근은 403, 없는 ID는 404, Bean Validation 실패는 프로젝트 표준 4xx다.
- 경로 충돌이 없는지 `/videos`와 `/{id}`를 실제 MockMvc/WebTestClient로 확인한다(`ABTestController.kt:39-46`, `:108-115`).

### 5. 자동 평가기 IT

실제 PostgreSQL에서 실행 중 테스트 3건을 준비한다.

- 유의미하지 않은 항목은 상태와 이벤트가 변하지 않는다.
- 유의미한 항목은 `COMPLETED`, `winner_variant_id`, `ended_at`, `ABTestCompletedEvent`가 함께 반영된다(`ABTestEvaluator.kt:36-52`).
- 한 항목에서 DB 예외를 유발했을 때 위의 차단 정책(항목 격리 또는 전체 재던짐)이 테스트 결과와 일치한다. 예외를 삼키고 뒤 작업이 조용히 사라지는 현행 동작은 통과로 인정하지 않는다.
- 동일 스케줄러가 두 인스턴스에서 실행될 수 있는지는 운영 토폴로지 확인 전까지 **미확인**이다. 필요하면 분산 락/행 잠금 정책을 별도 게이트로 추가한다.

## 활성화 순서와 롤백

1. 위 1~5 단계의 테스트를 먼저 추가하고 `./gradlew test --rerun-tasks`와 관련 IT를 통과시킨다.
2. 테스트 환경에서 `wip` 미활성 기본 프로필로 컨텍스트와 API를 확인한다.
3. 그 뒤에만 `ABTestController.kt:23` 및 `ABTestUseCase.kt:16`의 프로필 게이트 제거를 별도 작은 커밋으로 한다. 이 문서 작성 단계에서는 제거하지 않는다.
4. 배포 후 smoke test로 목록 조회·생성·상태 전이·통계를 확인한다. 오류율/DB 제약 위반/스케줄러 로그를 관찰한다.
5. 컨텍스트 실패나 데이터 정합성 이상이 있으면 프로필 게이트를 되돌려 기본 앱 기동을 우선 복구한다. 기존 WIP 엔드포인트가 기본 프로필에서 404인 상태는 의도된 안전한 롤백 상태다.

## 최종 판정 형식

| 게이트 | 증거 | 상태 |
|---|---|---|
| 기본 프로필 컨텍스트 | `@SpringBootTest(OnGoApplication)` 실측 로그 | 미실행 |
| 모든 생성자 의존성 bean | ABTest/variant/video 어댑터 + Spring bean 목록 | 소스상 가능, 런타임 미확인 |
| DB CRUD·롤백 | PostgreSQL Testcontainers IT | 미작성 |
| UseCase·API 계약 | 단위/통합 테스트 | 0건 |
| Evaluator 예외 격리 | PostgreSQL IT + 정책 구현 | **차단됨** |
| 외부 credential | ABTest 코드 직접 의존 없음 | 미확인(운영 인증/DB는 별도) |

위 표의 `미실행`, `미작성`, `차단됨`이 하나라도 남아 있으면 활성화하지 않는다.
