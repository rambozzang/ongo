# 런북 — V53 마이그레이션 드리프트 진단과 보정

작성일: 2026-08-03
대상: `V53__add_analytics_daily_impressions.sql`
긴급도: **배포 차단 사안.** 해소 전까지 V55~V57(UGC 쇼츠 파이프라인)을 배포하지 말 것

## 1. 무슨 일인가

`V53` 은 멱등하지 않다. 컬럼은 `ADD COLUMN IF NOT EXISTS` 로 방어했지만 제약조건은 그냥
`ADD CONSTRAINT` 다. PostgreSQL 에는 `ADD CONSTRAINT IF NOT EXISTS` 가 없다.

따라서 **컬럼과 제약이 이미 존재하는데 Flyway 이력에는 V53 이 없는 환경**에서는 이렇게 깨진다.

```
ERROR: constraint "chk_analytics_impressions" for relation "analytics_daily" already exists
```

Flyway 는 실패 지점 이후로 진행하지 않으므로 **V54~V57 이 전부 막힌다.** 즉 쇼츠 파이프라인
마이그레이션이 적용되지 않고 앱 기동도 실패한다.

로컬 개발 DB 가 정확히 이 상태였다. 누군가 컬럼과 제약을 수동으로 만들었고 V53 은 성공 기록이
한 번도 없었다.

## 2. 왜 V53 파일을 고치지 않는가

이미 적용됐을 수 있는 버전 마이그레이션은 **불변으로 취급한다.**

V53 을 멱등하게 고치면 아직 적용 안 된 드리프트 환경에는 도움이 되지만, **이미 V53 이 성공한
환경에서는 체크섬 불일치로 Flyway validate 가 실패해 기동이 깨진다.** `flyway repair` 는 SQL 을
실행하지 않고 메타데이터만 고치므로, 상태를 모르는 채 일괄 실행하면 실제 스키마 불일치를
덮어버릴 수 있다.

**V53 수정 + repair 는 최후의 선택이다.** 모든 환경(운영·스테이징·로컬)의 적용 상태를 목록화한
뒤에만 검토한다.

## 3. 1단계 — 읽기 전용 진단

**앱을 재기동해 결과를 추측하지 말 것.** 아래를 운영 DB 에 읽기 전용으로 실행한다.
서버 CLI 직접 접속이 금지되어 있으므로 Jenkins 의 일회성 진단 스텝이나 DB 담당자를 통한다.

```sql
-- (1) V53 이력과 체크섬
SELECT version, description, success, checksum, installed_on
FROM flyway_schema_history
WHERE version IN ('52','53','54')
ORDER BY installed_rank;

-- (2) 컬럼 존재 여부와 정의
SELECT column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'analytics_daily'
  AND column_name IN ('impressions','avg_view_duration_seconds')
ORDER BY column_name;

-- (3) 제약 존재 여부와 실제 정의
SELECT conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'analytics_daily'::regclass
  AND conname IN ('chk_analytics_impressions','chk_analytics_avg_view_duration');

-- (4) 실패 기록이 남아 있는지
SELECT version, description, success, installed_on
FROM flyway_schema_history
WHERE success = false;
```

### 기대값 (V53 이 의도한 상태)

| 항목 | 기대값 |
|---|---|
| `impressions` | `integer`, `NOT NULL`, `default 0` |
| `avg_view_duration_seconds` | `integer`, `NOT NULL`, `default 0` |
| `chk_analytics_impressions` | `CHECK ((impressions >= 0))` |
| `chk_analytics_avg_view_duration` | `CHECK ((avg_view_duration_seconds >= 0))` |

## 4. 2단계 — 상태별 판단

| V53 이력 | 스키마 상태 | 처리 |
|---|---|---|
| 있음 (success) | 기대값과 일치 | **조치 없음.** 그대로 V55~V57 배포 |
| 없음 | 컬럼·제약 모두 없음 | **조치 없음.** 기존 V53 이 정상 실행된다 |
| 없음 | 컬럼·제약이 이미 있음 | **5절 보정 후** 기존 V53 실행 ← 로컬이 이 경우였다 |
| 있음 | 스키마 일부 누락 | 새 보정 마이그레이션(V58)으로 처리. V53 은 건드리지 않는다 |
| 있음 | 체크섬 불일치 | **원인 확인 전에는 repair 금지.** 파일이 변경된 경위부터 조사 |

## 5. 3단계 — 드리프트 케이스 보정 (세 번째 행에만 해당)

**제약 이름만 보고 지우지 말 것.** 정의가 기대값과 정확히 일치할 때만 제거한다. 이름은 같은데
정의가 다르면 누군가 의도적으로 다른 규칙을 넣은 것이고, 지우면 그 의도가 사라진다.

Jenkins 의 **pre-migrate 보정 스텝**에서 실행한다.

```sql
DO $$
DECLARE
    def_impressions TEXT;
    def_duration    TEXT;
BEGIN
    SELECT pg_get_constraintdef(oid) INTO def_impressions
    FROM pg_constraint
    WHERE conrelid = 'analytics_daily'::regclass AND conname = 'chk_analytics_impressions';

    SELECT pg_get_constraintdef(oid) INTO def_duration
    FROM pg_constraint
    WHERE conrelid = 'analytics_daily'::regclass AND conname = 'chk_analytics_avg_view_duration';

    -- 정의가 V53 의도와 정확히 일치할 때만 제거한다. V53 이 곧바로 동일하게 다시 만든다.
    IF def_impressions = 'CHECK ((impressions >= 0))' THEN
        ALTER TABLE analytics_daily DROP CONSTRAINT chk_analytics_impressions;
        RAISE NOTICE 'chk_analytics_impressions 제거 — V53 이 재생성한다';
    ELSIF def_impressions IS NOT NULL THEN
        RAISE EXCEPTION '제약 정의가 예상과 다르다: %. 수동 확인이 필요하다', def_impressions;
    END IF;

    IF def_duration = 'CHECK ((avg_view_duration_seconds >= 0))' THEN
        ALTER TABLE analytics_daily DROP CONSTRAINT chk_analytics_avg_view_duration;
        RAISE NOTICE 'chk_analytics_avg_view_duration 제거 — V53 이 재생성한다';
    ELSIF def_duration IS NOT NULL THEN
        RAISE EXCEPTION '제약 정의가 예상과 다르다: %. 수동 확인이 필요하다', def_duration;
    END IF;
END $$;
```

컬럼은 건드리지 않는다. V53 의 `ADD COLUMN IF NOT EXISTS` 가 알아서 넘어간다.

## 6. 4단계 — 배포

1. 보정 스텝 실행 결과 확인
2. Flyway 가 V53 → V57 까지 성공했는지 확인
3. 그 다음에만 앱 재기동
4. 재기동은 **push 또는 Jenkins 로만.** CLI 직접 조작 금지

## 7. 하지 말 것

- V53 파일 수정 (체크섬 불일치로 적용된 환경이 깨진다)
- 상태 확인 없이 `flyway repair`
- 정의 확인 없이 `DROP CONSTRAINT`
- 앱을 재기동해보며 상태 추측하기
- 이 사안 해소 전 V55~V57 배포

## 8. 로컬에서 실제로 겪은 사례 (참고)

로컬 dev DB 는 "V53 이력 없음 + 컬럼·제약 있음" 상태였다. 제약 두 개를 제거하고 재기동하니
V53~V57 이 한 번에 적용됐고, 적용 후 스키마가 기대값과 정확히 일치함을 확인했다.

```
impressions               integer NOT NULL DEFAULT 0   CHECK ((impressions >= 0))
avg_view_duration_seconds integer NOT NULL DEFAULT 0   CHECK ((avg_view_duration_seconds >= 0))
```

다만 그때는 제약 **정의를 확인하지 않고** 이름만 보고 제거했다. 로컬이라 위험이 낮았을 뿐,
운영에서는 5절처럼 정의를 검증한 뒤 제거해야 한다.

## 9. 다른 마이그레이션 점검 결과

전체 마이그레이션을 훑어 같은 패턴이 더 있는지 확인했다. **V53 이 유일하다.**

| 파일 | ADD CONSTRAINT | 상태 |
|---|---|---|
| `V38__revenue_alert_unique_and_confidence_fix.sql` | 1건 | `DO $$` 블록으로 감쌈 — **안전** |
| `V43__add_missing_indexes.sql` | 5건 | 전부 `DO $$` 블록으로 감쌈 — **안전** |
| `V53__add_analytics_daily_impressions.sql` | 2건 | **무방비 — 이 문서의 원인** |

즉 이 프로젝트에는 이미 올바른 관용구가 있었고 V53 만 따르지 않았다. 앞으로 작성하는
마이그레이션은 V43 처럼 제약조건도 `DO $$` 블록으로 감싸 멱등하게 만든다.

```sql
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'my_table'::regclass AND conname = 'chk_my_constraint'
    ) THEN
        ALTER TABLE my_table ADD CONSTRAINT chk_my_constraint CHECK (my_col >= 0);
    END IF;
END $$;
```

## 10. 이 문서의 SQL 검증 기록

5절 보정 블록은 임시 DB 에서 두 가지 경우를 실제로 돌려 확인했다.

| 케이스 | 결과 |
|---|---|
| 제약 정의가 기대값과 일치 | 두 제약 모두 제거됨. 이어서 V53 실행 시 정상 적용되고 제약이 의도대로 재생성됨 |
| 제약 정의가 다름 (`CHECK (impressions >= 1)`) | 예외를 던지고 **제약을 보존함**. 실제 정의를 오류 메시지에 담아 출력 |
