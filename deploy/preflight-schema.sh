#!/usr/bin/env bash
#
# 배포 전 DB 스키마 점검 — **읽기 전용, fail-closed**.
#
# ## 왜 필요한가
#
# 새 코드는 `subscriptions.billing_key_encrypted`,
# `subscriptions.pending_billing_cycle`, `subscription_renewal_attempts.payment_id`,
# `ai_pipeline_jobs.refunded_credits` 를 SELECT 하고 INSERT/UPDATE 한다.
# 그 컬럼이 없는 DB 에 배포하면 구독 조회·저장이 전부 실패한다. 기능 토글로도 막을 수
# 없다 — 토글은 정기 청구 실행만 끄고, 컬럼 접근은 구독을 읽는 모든 경로에 있다.
#
# `ai_pipeline_jobs.refunded_credits` 는 파이프라인 정산의 멱등 표식이다. 없으면 조회부터
# 실패해 AI 파이프라인이 뜨지 않고, 억지로 우회하면 같은 환불을 두 번 내보낸다.
#
# `analytics_daily.revenue_status` / `revenue_currency` (V107) 는 광고 수익의 측정 상태와
# 통화다. 없으면 성과 동기화(AnalyticsSyncScheduler)와 수익 조회가 SQL 오류로 실패해
# 대시보드 지표가 통째로 비고, 수익 화면은 "0 원"과 "측정 안 됨"을 구분하지 못한다.
#
# `ai_pipeline_jobs.credit_allocation` (V108) 은 차감 출처 분해다. AiPipelineJooqRepository
# 가 이 컬럼을 SELECT 하고 INSERT 하므로, 없으면 **파이프라인 시작과 조회가 SQL 오류로
# 실패**한다. 억지로 우회해도 정산이 출처를 잃어 구매 크레딧이 무료 크레딧으로 바뀐다 —
# 고객이 돈 주고 산 자산이 월말에 사라진다.
#
# `video_translations.credit_allocation` / `claimed_at` / `attempts` (V109) 는 번역의
# 차감 출처, 워커 선점 표식, 시도 횟수다. `TranslationJooqRepository` 가 셋 다
# SELECT 하고 `claimed_at`·`attempts` 는 UPDATE 한다. 없으면 번역 요청이 SQL 오류로
# 실패하고, 복구 스캐너가 멈춘 행을 되살리지 못해 고객이 크레딧만 잃는다.
# `attempts` 가 없으면 재시도 상한도 판정할 수 없어 죽는 입력이 LLM 을 무한히 태운다.
#
# `ugc_post_metric_snapshots.source` / `unavailable_metrics` (V110) 는 UGC 지표의 출처와
# 미측정 목록이다. `JooqMetricSnapshotRepository` 가 둘 다 SELECT/INSERT 하므로 없으면
# 지표 동기화와 캠페인 분석이 SQL 오류로 실패한다. 우회하면 플랫폼이 주지 않는 0 이
# 다시 측정값으로 합산돼 브랜드 성과와 보상 판단이 왜곡된다.
#
# `content_images.storage_object_key` (V112) 는 게시 이미지의 실제 저장 키다.
# `ContentImageJooqRepository` 가 INSERT 하고 SELECT 하므로 없으면 **이미지 게시가 SQL
# 오류로 실패**한다. 우회해도 탈퇴·삭제 정리가 무엇을 지울지 알 수 없어 고아가 남는다.
#
# `ugc_shorts_run_stages.credit_allocation` / `refunded_credits` (V111) 는 쇼츠 단계의
# 차감 분해와 정산 표식이다. `ShortsRunStageJooqRepository` 가 셋 다 다루므로 없으면
# 단계 저장이 실패하고, 우회하면 환불 근거를 잃어 같은 단계를 두 번 환불한다.
#
# `assets.file_url` 과 `brand_kits` 의 URL 네 컬럼 (V113) 은 **컬럼이 아니라 타입**이
# 바뀐다. VARCHAR(500) 로 남아 있으면 한글 파일명 에셋의 presigned URL(400~530 자)이
# INSERT 에서 `22001 value too long` 으로 잘린다 — 존재 검사로는 잡히지 않아 따로 본다.
#
# 실제로 운영 Flyway 는 V93 이고 이 컬럼이 없다(2026-08-27 읽기 전용 확인). 즉 V94~V113
# 스무 개가 모두 미적용이다. 이 상태에서 배포하면 결제·구독 화면이 통째로 죽는다.
#
# ## fail-closed
#
# **점검하지 못한 상태는 안전하지 않다.** psql 이 없든, 접속이 안 되든, 비밀번호가
# 없든, 결과는 같다 — 스키마를 확인하지 못한 채 새 코드를 올리는 것이다. 그래서
# 모든 불확실은 0 이 아닌 코드로 끝나고, 호출자는 배포를 중단해야 한다.
#
# 성공만 0 이다. 그 밖은 전부 "배포하지 말 것"이다.
#
# ## 무엇을 하지 않는가
#
# **DDL 을 실행하지 않는다.** 마이그레이션은 애플리케이션의 Flyway 가 기동 시 적용하며,
# 이 스크립트는 "지금 배포해도 되는가"만 판정한다. 스키마를 고치는 스크립트가 배포 경로에
# 있으면 롤백할 때 되돌릴 대상이 둘로 갈라진다.
#
# ## 종료 코드
#
#   0 - 배포해도 안전
#   1 - 필요한 스키마가 없음
#   2 - 점검 불가(psql 없음, DB_PASSWORD 없음, 접속 실패)
#
#   **1 과 2 모두 배포를 중단해야 한다.** 구분은 원인을 알리기 위한 것뿐이다.

set -uo pipefail

RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 이 버전이 성공으로 남아 있어야 새 코드가 요구하는 스키마가 갖춰진 것이다.
#
# Flyway 는 버전 순으로만 적용하므로 113 이 성공이면 V94~V112 도 이미 지나간 것이다.
# 그래서 각 버전을 따로 확인하지 않고 마지막 하나만 본다.
#
# 새 마이그레이션을 추가하면 **이 값을 함께 올려야 한다.** 그러지 않으면 컬럼이 없는
# 서버가 기동을 통과한 뒤 런타임에 SQL 오류를 낸다 — preflight 가 있으나 마나가 된다.
REQUIRED_FLYWAY_VERSION="113"

# 버전 확인과 별개로 실제 스키마도 본다. 이력만 믿으면 수동으로 컬럼을 지운 경우를
# 놓친다. "테이블:컬럼" 이며 컬럼이 비면 테이블 존재만 본다.
REQUIRED_SCHEMA="subscriptions:billing_key_encrypted subscriptions:pending_billing_cycle subscription_renewal_attempts:payment_id ai_pipeline_jobs:refunded_credits ai_pipeline_jobs:credit_allocation analytics_daily:revenue_status analytics_daily:revenue_currency video_translations:credit_allocation video_translations:claimed_at video_translations:attempts ugc_post_metric_snapshots:source ugc_post_metric_snapshots:unavailable_metrics ugc_shorts_run_stages:credit_allocation ugc_shorts_run_stages:refunded_credits content_images:storage_object_key"

abort_migration_needed() {
    error "배포를 중단합니다. $1"
    error ""
    error "이 상태로 배포하면 구독 조회·저장이 전부 실패해 결제와 구독 화면이 죽습니다."
    error "기능 토글(SUBSCRIPTION_RENEWAL_ENABLED)로는 막을 수 없습니다 —"
    error "토글은 정기 청구 실행만 끄고, 컬럼 접근은 구독을 읽는 모든 경로에 있습니다."
    error ""
    error "조치: V94~V113 을 순서대로 먼저 적용한 뒤 다시 배포하세요."
    error "절차는 docs/operations/SUBSCRIPTION_RENEWAL_ROLLOUT.md 를 따르세요."
    exit 1
}

abort_cannot_check() {
    error "배포를 중단합니다. DB 스키마를 점검하지 못했습니다: $1"
    error ""
    error "점검하지 못한 상태는 안전하지 않습니다. 스키마를 확인하지 못한 채 새 코드를"
    error "올리면 구독 조회·저장이 실패해도 배포 시점에는 알 수 없습니다."
    error ""
    error "조치: 위 원인을 해결한 뒤 다시 배포하세요."
    error "직접 확인하려면: SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;"
    exit 2
}

if ! command -v psql >/dev/null 2>&1; then
    abort_cannot_check "psql 을 찾을 수 없습니다"
fi

# 애플리케이션과 **같은 기본값**을 쓴다(application.yml spring.datasource.url).
# 여기서만 다른 기본을 쓰면 점검한 DB 와 애플리케이션이 붙는 DB 가 갈라진다.
: "${DB_URL:=jdbc:postgresql://localhost:54332/ongo?stringtype=unspecified}"
# application.yml 의 ${DB_USERNAME:ongo} 와 같다.
: "${DB_USERNAME:=ongo}"
# 비밀번호는 기본값이 없다(application.yml 도 ${DB_PASSWORD} 로 필수다).
: "${DB_PASSWORD:=}"

if [ -z "$DB_PASSWORD" ]; then
    abort_cannot_check "DB_PASSWORD 가 비어 있습니다"
fi

# DB_URL 은 JDBC 형식이라 psql 이 그대로 못 읽는다. jdbc: 접두사만 떼어 낸다.
PSQL_URL="${DB_URL#jdbc:}"
# 접속 문자열에 자격증명을 넣지 않는다. 비밀번호는 PGPASSWORD 로만 넘긴다.
export PGPASSWORD="$DB_PASSWORD"

query() {
    psql "$PSQL_URL" -U "$DB_USERNAME" -t -A -c "$1" 2>/dev/null
}

if ! query "SELECT 1" >/dev/null; then
    abort_cannot_check "DB 에 접속하지 못했습니다"
fi

CURRENT_VERSION="$(query "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")"
[ -n "$CURRENT_VERSION" ] && info "현재 Flyway 버전: $CURRENT_VERSION"

# 0) 실패한 마이그레이션 — **부분 적용 감지**
#
# 마이그레이션이 도중에 깨지면 `success = false` 행이 남는다. Flyway 는 그 상태에서
# 다음 기동 때 `repair` 없이는 진행하지 않으므로, 배포해도 애플리케이션이 뜨지 않는다.
# 더 나쁜 것은 그 실패가 **DDL 일부만 반영된 상태**일 수 있다는 점이다. 버전 확인만
# 하면 이 상황을 놓친다 — 마지막 성공 버전이 요구 버전이어도 그 뒤에 깨진 것이 있으면
# 스키마는 반쯤 바뀐 채다.
FAILED_MIGRATIONS="$(query "SELECT string_agg(version, ', ' ORDER BY installed_rank) FROM flyway_schema_history WHERE NOT success")"
if [ -n "$FAILED_MIGRATIONS" ]; then
    error "배포를 중단합니다. 실패한 마이그레이션이 이력에 남아 있습니다: $FAILED_MIGRATIONS"
    error ""
    error "스키마가 부분만 반영됐을 수 있고, Flyway 는 이 상태에서 다음 마이그레이션을"
    error "진행하지 않습니다. 배포해도 애플리케이션이 기동하지 않습니다."
    error ""
    error "조치: 해당 버전이 실제로 무엇을 남겼는지 확인한 뒤 정리하고(flyway repair 등)"
    error "다시 배포하세요. 원인 확인 없이 repair 만 하면 반쯤 반영된 스키마가 그대로 남습니다."
    exit 1
fi

# 1) 마이그레이션 이력
APPLIED="$(query "SELECT 1 FROM flyway_schema_history WHERE version = '$REQUIRED_FLYWAY_VERSION' AND success LIMIT 1")"
if [ "$APPLIED" != "1" ]; then
    abort_migration_needed "V$REQUIRED_FLYWAY_VERSION 이 적용되지 않았습니다(현재: ${CURRENT_VERSION:-알 수 없음}). V94~V113 선행 적용이 필요합니다."
fi

# 2) 실제 스키마 — 이력만 믿으면 수동으로 지운 컬럼을 놓친다.
MISSING=""
for entry in $REQUIRED_SCHEMA; do
    table="${entry%%:*}"
    column="${entry#*:}"

    if [ -z "$column" ]; then
        found="$(query "SELECT 1 FROM information_schema.tables WHERE table_name = '$table' LIMIT 1")"
        [ "$found" = "1" ] || MISSING="$MISSING $table"
    else
        found="$(query "SELECT 1 FROM information_schema.columns WHERE table_name = '$table' AND column_name = '$column' LIMIT 1")"
        [ "$found" = "1" ] || MISSING="$MISSING $table.$column"
    fi
done

if [ -n "$MISSING" ]; then
    abort_migration_needed "새 코드가 요구하는 스키마가 없습니다:$MISSING"
fi

# 3) 컬럼 타입 — V113 은 컬럼을 더하지 않고 **길이 제한을 뗀다**.
#
# 위 존재 검사로는 잡히지 않는다. VARCHAR(500) 로 남아 있으면 한글 파일명 에셋의
# presigned URL(서명 쿼리만 ~314 자, 호스트·UUID ~127 자, 한글은 글자당 9 자로 인코딩)이
# 500 자를 넘겨 INSERT 가 `22001 value too long` 으로 실패한다. 사용자에게는 원인 없는
# 업로드 오류로만 보인다.
#
# `character_maximum_length IS NULL` 이 곧 "길이 제한이 없다"(TEXT)는 뜻이다.
REQUIRED_TEXT_COLUMNS="assets:file_url brand_kits:logo_url brand_kits:intro_template_url brand_kits:outro_template_url brand_kits:watermark_url"
NARROW=""
for entry in $REQUIRED_TEXT_COLUMNS; do
    table="${entry%%:*}"
    column="${entry#*:}"
    unlimited="$(query "SELECT 1 FROM information_schema.columns WHERE table_name = '$table' AND column_name = '$column' AND character_maximum_length IS NULL LIMIT 1")"
    [ "$unlimited" = "1" ] || NARROW="$NARROW $table.$column"
done

if [ -n "$NARROW" ]; then
    abort_migration_needed "URL 컬럼에 아직 길이 제한이 남아 있습니다(V113 미적용):$NARROW"
fi

# 4) enum 값 — V100 은 컬럼이 아니라 `credit_tx_type` 에 값을 더한다.
#
# 위 컬럼 검사로는 잡히지 않는다. 이 값이 없으면 크레딧 회수가 **마지막 단계에서**
# 깨진다. `CreditJooqRepository.saveTransaction` 이 타입 이름을 enum 으로 캐스트하므로
# 삽입이 DB 에서 실패하고, 그 경로는 결제 취소·환불에서 실제로 호출된다.
REVOKE_ENUM="$(query "SELECT 1 FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid WHERE t.typname = 'credit_tx_type' AND e.enumlabel = 'REVOKE' LIMIT 1")"
if [ "$REVOKE_ENUM" != "1" ]; then
    abort_migration_needed "credit_tx_type enum 에 'REVOKE' 값이 없습니다(V100 미적용). 크레딧 회수가 실패합니다."
fi

info "DB 스키마 점검 통과 — V$REQUIRED_FLYWAY_VERSION 적용 확인, 필요한 테이블·컬럼 존재."
exit 0
