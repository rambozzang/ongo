#!/usr/bin/env bash
#
# 배포 전 DB 마이그레이션 적용 — **새 JAR 의 Spring Flyway 만 실행한다**.
#
# ## 왜 필요한가
#
# 마이그레이션 실행기는 애플리케이션 기동 시 Spring Flyway 하나뿐이다(Flyway Gradle
# 플러그인도, 별도 CLI 도 없다). 그런데 `deploy.sh` 는 `preflight-schema.sh` 로
# **새 스키마가 이미 있어야만** 서비스를 재기동한다. 그래서 다음 교착이 생긴다.
#
#   운영이 V93 → preflight 가 배포를 막음 → 앱이 뜨지 않음 → Flyway 가 돌지 않음
#   → 영원히 V93
#
# 이 스크립트가 그 고리를 끊는다. **preflight 앞에서** 새 JAR 을 마이그레이션 전용으로
# 한 번 띄워 Flyway 만 적용하고 곧바로 내린다. 그 뒤 preflight 가 결과를 검증하고,
# 통과해야만 실제 서비스 재기동으로 넘어간다.
#
# ## 무엇을 끄는가
#
# 마이그레이션 전용 실행은 **서비스가 아니다.** 운영 서비스가 아직 살아 있는 상태에서
# 두 번째 인스턴스를 띄우는 것이므로 다음을 반드시 끈다.
#
#   ongo.scheduling.enabled=false     @Scheduled 전부 정지. 켜두면 결제 청구·웹훅 재시도·
#                                     게시 워커가 살아 있는 서비스와 **동시에** 돈다.
#   spring.main.web-application-type=none
#                                     웹 서버를 띄우지 않는다. 컨텍스트가 준비되면 그대로
#                                     끝나며, 운영 포트를 빼앗지 않는다.
#   server.port=0                     혹시 웹 스택이 살아나도 운영 포트와 충돌하지 않는다.
#                                     (위 설정과 중복이지만 포트 충돌은 서비스를 죽인다)
#
# ## 성공 판정
#
# **Spring 의 기동 완료 문구를 본 것만 성공이다.** 종료 코드 0 만으로는 부족하다 —
# 컨텍스트가 뜨기도 전에 조용히 끝난 경우와 구분되지 않는다. 마커를 못 보면 실패다.
#
# ## 종료 코드
#
#   0 - 마이그레이션 적용 완료(기동 완료 문구 확인)
#   1 - 마이그레이션 실패 / 조기 종료 / 필수 조건 미충족
#   2 - 제한 시간 초과
#
#   **0 이 아니면 배포를 중단해야 한다.** 어느 경우든 서비스는 멈추지 않은 상태다.

set -uo pipefail

APP_NAME="ongo-migrate"
BASE_DIR="${ONGO_BASE_DIR:-/data/ongo}"
JAR_PATH="${ONGO_JAR_PATH:-$BASE_DIR/jar/ongo-api.jar}"
LOG_DIR="${ONGO_LOG_DIR:-$BASE_DIR/log}"
ENV_FILE="${ONGO_ENV_FILE:-$BASE_DIR/.env}"
LOG_FILE="$LOG_DIR/migrate.log"

# 마이그레이션은 큰 테이블에서 오래 걸린다. V107 은 analytics_daily 파티션 전체에
# ACCESS EXCLUSIVE 락을 잡는다. 그렇다고 무한정 기다리면 배포가 멈춘 채 방치된다.
MIGRATE_TIMEOUT_SECONDS="${MIGRATE_TIMEOUT_SECONDS:-600}"

# Spring 이 컨텍스트를 다 띄웠을 때만 남기는 문구. start.sh 와 같은 기준을 쓴다.
STARTED_MARKER="Started .* in .* seconds"

RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 마이그레이션 JVM 의 PID. 트랩과 정리 함수가 함께 읽으므로 한 곳에서만 채운다.
MIGRATE_PID=""

# 우리가 띄운 프로세스가 **아직 그 프로세스인지** 확인하고 종료한다.
#
# PID 는 재사용된다. 이미 죽은 PID 를 그대로 kill 하면 그 사이 같은 번호를 받은 남의
# 프로세스를 죽인다 — 운영 서비스일 수도 있다. 그래서 명령줄에 우리 JAR 이 있는지
# 확인한 뒤에만 신호를 보낸다.
is_our_process() {
    local pid="$1"
    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    kill -0 "$pid" 2>/dev/null || return 1
    ps -p "$pid" -o command= 2>/dev/null | grep -q 'ongo-migrate-marker'
}

stop_migration_jvm() {
    local pid="$1"
    is_our_process "$pid" || return 0

    kill -TERM "$pid" 2>/dev/null || true
    local waited=0
    while [ "$waited" -lt 30 ]; do
        is_our_process "$pid" || return 0
        sleep 1
        waited=$((waited + 1))
    done

    warn "마이그레이션 JVM 이 정상 종료되지 않아 강제 종료합니다 (PID: $pid)"
    kill -KILL "$pid" 2>/dev/null || true
}

# 스크립트가 어떤 이유로 끝나든(정상·오류·중단) 우리가 띄운 JVM 을 남기지 않는다.
# 남으면 서비스와 동시에 두 인스턴스가 DB 를 잡는다.
cleanup() {
    [ -n "$MIGRATE_PID" ] && stop_migration_jvm "$MIGRATE_PID"
}
trap cleanup EXIT
trap 'error "중단 신호를 받았습니다."; exit 1' INT TERM

fail_tail() {
    error "$1"
    if [ -f "$LOG_FILE" ]; then
        error "최신 로그 50줄:"
        tail -n 50 "$LOG_FILE" >&2
    fi
}

# ---- 사전 조건 ----

if ! command -v java >/dev/null 2>&1; then
    error "java 를 찾을 수 없습니다. 마이그레이션을 실행할 수 없습니다."
    exit 1
fi

if [ ! -f "$JAR_PATH" ]; then
    error "마이그레이션에 사용할 JAR 이 없습니다: $JAR_PATH"
    exit 1
fi

# 환경변수는 **운영 서비스와 똑같이** 로드한다. 여기서만 다른 값을 쓰면 마이그레이션이
# 붙는 DB 와 서비스가 붙는 DB 가 갈라진다.
# shellcheck source=deploy/required-env.sh
if ! source "$SCRIPT_DIR/required-env.sh"; then
    error "required-env.sh 를 읽을 수 없습니다: $SCRIPT_DIR/required-env.sh"
    exit 1
fi

ongo_load_env_file "$ENV_FILE"
case $? in
    0) ;;
    1) error "환경변수 파일이 없습니다: $ENV_FILE"; exit 1 ;;
    *) error "$ENV_FILE 을 읽을 수 없습니다(문법 오류)."; exit 1 ;;
esac

MISSING_VARS="$(ongo_missing_env_vars)"
if [ -n "$MISSING_VARS" ]; then
    ongo_report_missing_env_vars "$MISSING_VARS" "$ENV_FILE"
    exit 1
fi

mkdir -p "$LOG_DIR"
# 이전 실행의 성공 문구를 이번 성공으로 오인하지 않도록 비운다.
: > "$LOG_FILE"

info "DB 마이그레이션 적용 시작 (JAR: $JAR_PATH, 제한 시간: ${MIGRATE_TIMEOUT_SECONDS}s)"
info "스케줄러·웹서버를 끈 마이그레이션 전용 실행입니다. 기존 서비스는 계속 실행 중입니다."

# `-Dongo-migrate-marker` 는 동작에 영향을 주지 않는 표식이다. `ps` 로 이 프로세스를
# 운영 서비스와 확실히 구분하기 위한 것이며, PID 재사용 오인 종료를 막는다.
java \
    -Dongo-migrate-marker=true \
    -Dspring.profiles.active=prod \
    -Dongo.scheduling.enabled=false \
    -Dspring.main.web-application-type=none \
    -Dserver.port=0 \
    -Dfile.encoding=UTF-8 \
    -Djava.awt.headless=true \
    -jar "$JAR_PATH" \
    > "$LOG_FILE" 2>&1 &
MIGRATE_PID=$!

info "마이그레이션 JVM 기동 (PID: $MIGRATE_PID)"

# ---- 완료 대기 ----
#
# 세 가지로 끝난다: 마커 확인(성공) / 프로세스 조기 종료(실패) / 제한 시간 초과(실패).
STARTED=false
EARLY_EXIT=false
WAITED=0

while [ "$WAITED" -lt "$MIGRATE_TIMEOUT_SECONDS" ]; do
    if grep -qE "$STARTED_MARKER" "$LOG_FILE" 2>/dev/null; then
        STARTED=true
        break
    fi

    if ! is_our_process "$MIGRATE_PID"; then
        # 프로세스가 사라졌다. 마커를 마지막으로 한 번 더 본다 — 컨텍스트가 준비된 뒤
        # 스스로 종료하는 경우(web-application-type=none)가 정상 경로다.
        if grep -qE "$STARTED_MARKER" "$LOG_FILE" 2>/dev/null; then
            STARTED=true
        else
            EARLY_EXIT=true
        fi
        break
    fi

    sleep 1
    WAITED=$((WAITED + 1))
    if [ $((WAITED % 30)) -eq 0 ]; then
        info "... 마이그레이션 진행 중 (${WAITED}/${MIGRATE_TIMEOUT_SECONDS}s)"
    fi
done

# ---- 판정 ----
#
# **마커를 본 것만 성공이다.** 종료 코드 0 은 컨텍스트가 뜨기도 전에 조용히 끝난
# 경우와 구분되지 않는다.

if [ "$EARLY_EXIT" = true ]; then
    fail_tail "마이그레이션 JVM 이 기동을 마치기 전에 종료됐습니다. 마이그레이션이 적용되지 않았습니다."
    exit 1
fi

if [ "$STARTED" != true ]; then
    fail_tail "마이그레이션이 제한 시간(${MIGRATE_TIMEOUT_SECONDS}s) 안에 끝나지 않았습니다."
    error "긴 마이그레이션이라면 MIGRATE_TIMEOUT_SECONDS 를 늘려 다시 시도하세요."
    error "적용이 도중에 멈췄을 수 있으므로 flyway_schema_history 를 먼저 확인하세요."
    exit 2
fi

info "마이그레이션 적용 완료 — 기동 완료를 확인했습니다."

# 여기서 명시적으로 내린다. `web-application-type=none` 이면 보통 스스로 끝나지만,
# 비데몬 스레드가 남으면 JVM 이 계속 살아 있어 배포가 멈춘 것처럼 보인다.
stop_migration_jvm "$MIGRATE_PID"
MIGRATE_PID=""

info "마이그레이션 전용 JVM 종료. preflight 검증으로 넘어갑니다."
exit 0
