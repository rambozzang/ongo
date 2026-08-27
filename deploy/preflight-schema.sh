#!/usr/bin/env bash
#
# 배포 전 DB 스키마 점검 — **읽기 전용, fail-closed**.
#
# ## 왜 필요한가
#
# 새 코드는 `subscriptions.billing_key_encrypted` 를 SELECT 하고 INSERT/UPDATE 한다.
# 그 컬럼이 없는 DB 에 배포하면 구독 조회·저장이 전부 실패한다. 기능 토글로도 막을 수
# 없다 — 토글은 정기 청구 실행만 끄고, 컬럼 접근은 구독을 읽는 모든 경로에 있다.
#
# 실제로 운영 Flyway 는 V93 이고 이 컬럼이 없다(2026-08-27 읽기 전용 확인). 이 상태에서
# 배포하면 결제·구독 화면이 통째로 죽는다.
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
# Flyway 는 버전 순으로만 적용하므로 103 이 성공이면 V94~V102 도 이미 지나간 것이다.
# 그래서 열 개를 각각 확인하지 않고 마지막 하나만 본다.
REQUIRED_FLYWAY_VERSION="103"

# 버전 확인과 별개로 실제 스키마도 본다. 이력만 믿으면 수동으로 컬럼을 지운 경우를
# 놓친다. "테이블:컬럼" 이며 컬럼이 비면 테이블 존재만 본다.
REQUIRED_SCHEMA="subscriptions:billing_key_encrypted subscription_renewal_attempts:"

abort_migration_needed() {
    error "배포를 중단합니다. $1"
    error ""
    error "이 상태로 배포하면 구독 조회·저장이 전부 실패해 결제와 구독 화면이 죽습니다."
    error "기능 토글(SUBSCRIPTION_RENEWAL_ENABLED)로는 막을 수 없습니다 —"
    error "토글은 정기 청구 실행만 끄고, 컬럼 접근은 구독을 읽는 모든 경로에 있습니다."
    error ""
    error "조치: V94~V103 을 순서대로 먼저 적용한 뒤 다시 배포하세요."
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

# 1) 마이그레이션 이력
APPLIED="$(query "SELECT 1 FROM flyway_schema_history WHERE version = '$REQUIRED_FLYWAY_VERSION' AND success LIMIT 1")"
if [ "$APPLIED" != "1" ]; then
    abort_migration_needed "V$REQUIRED_FLYWAY_VERSION 이 적용되지 않았습니다(현재: ${CURRENT_VERSION:-알 수 없음}). V94~V103 선행 적용이 필요합니다."
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

info "DB 스키마 점검 통과 — V$REQUIRED_FLYWAY_VERSION 적용 확인, 필요한 테이블·컬럼 존재."
exit 0
