#!/bin/bash
# ============================================================
# onGo - 배포 스크립트
# 사용법: bash deploy.sh [all|backend|frontend] [--skip-git] [--skip-build]
# ============================================================

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }
step()  { echo -e "${BLUE}[STEP]${NC} $1"; }

# ============================================================
# 경로 설정
# ============================================================
BASE_DIR="/data/ongo"
SRC_DIR="$BASE_DIR/src"
JAR_DIR="$BASE_DIR/jar"
WWW_DIR="$BASE_DIR/www"
LOG_DIR="$BASE_DIR/log"
PID_FILE="$BASE_DIR/app.pid"
ENV_FILE="$BASE_DIR/.env"
DEPLOY_TARGET="${1:-all}"
SKIP_GIT=false
SKIP_BUILD=false
DEPLOY_ARTIFACT=""
SERVER_PORT="${SERVER_PORT:-8070}"
BACKEND_HEALTH_TIMEOUT_SECONDS="${BACKEND_HEALTH_TIMEOUT_SECONDS:-90}"

# ============================================================
# systemd 연동
# ============================================================
#
# 운영 서버에 감시자가 없으면 — `setsid java &` 와 PID 파일만 남으면 — 크래시나 재부팅
# 뒤에 서비스가 스스로 돌아오지 않는다. 판매용 운영 배포는 그 상태를 허용하지 않는다.
#
# **유닛 설치는 root 작업이라 이 스크립트가 하지 않는다.** setup-server.sh 또는 수동
# 설치가 먼저 끝나 있어야 한다. 유닛이 없는 호스트는 JAR을 건드리기 전에 중단한다.
#
# 왜 분기가 반드시 있어야 하는가:
#   유닛을 설치해 놓고 배포가 `start.sh` 를 직접 부르면, 그 프로세스는 유닛의 cgroup
#   **밖**에서 뜬다. systemd 는 서비스가 멈춘 줄 알고 감시하지 않는다. 감시자를 붙였는데
#   정작 배포한 프로세스만 감시 밖에 남는, 안 붙인 것보다 나쁜 상태가 된다.
ONGO_SYSTEMD_UNIT="${ONGO_SYSTEMD_UNIT:-ongo-backend.service}"

# 유닛이 **설치돼 있는가**. enable 여부는 묻지 않는다 — 설치만 돼 있으면
# `systemctl restart` 가 그 cgroup 에서 서비스를 올린다.
ongo_systemd_unit_installed() {
    command -v systemctl >/dev/null 2>&1 || return 1
    systemctl list-unit-files "$ONGO_SYSTEMD_UNIT" 2>/dev/null \
        | grep -q "^${ONGO_SYSTEMD_UNIT}[[:space:]]"
}

# root 면 그대로, 아니면 sudo 를 앞에 붙인다. sudo 조차 없으면 그대로 시도해
# 실패 사유가 그대로 드러나게 둔다 — 조용히 건너뛰면 재시작이 안 된 채 성공으로 보인다.
ongo_sudo() {
    if [ "$(id -u)" -eq 0 ]; then
        "$@"
    elif command -v sudo >/dev/null 2>&1; then
        sudo "$@"
    else
        "$@"
    fi
}

# 무관리 JVM은 판매용 배포 대상이 아니다. 기존 호스트의 긴급 복구처럼 정말 필요한
# 경우에만 호출자가 명시적으로 ALLOW_UNMANAGED_BACKEND=true를 설정한다. 우회 여부를
# 로그에 남겨, systemd 밖에서 실행된 것을 정상 운영으로 오인하지 않게 한다.
require_managed_backend() {
    if [ "${ALLOW_UNMANAGED_BACKEND:-false}" = "true" ]; then
        warn "ALLOW_UNMANAGED_BACKEND=true: systemd 없는 응급 배포를 허용합니다. 재부팅 자동 복구가 보장되지 않습니다."
        return 0
    fi
    if ! ongo_systemd_unit_installed; then
        error "$ONGO_SYSTEMD_UNIT 유닛이 설치되지 않아 배포를 중단합니다."
        error "먼저 sudo bash deploy/oracle/setup-server.sh 를 실행하거나 유닛을 /etc/systemd/system에 설치하세요."
        error "정말 임시 우회해야 하면 ALLOW_UNMANAGED_BACKEND=true 를 명시적으로 설정하세요."
        return 1
    fi
}

# 재시작 한 곳. 기본 경로는 systemd이며, 명시적 응급 우회에서만 종전 stop/start를 쓴다.
restart_backend() {
    if ongo_systemd_unit_installed; then
        info "systemd 유닛으로 재시작합니다: $ONGO_SYSTEMD_UNIT"
        ongo_sudo systemctl restart "$ONGO_SYSTEMD_UNIT"
    else
        warn "systemd 유닛이 없어 스크립트로 응급 재시작합니다."
        bash "$SRC_DIR/deploy/stop.sh" || true
        bash "$SRC_DIR/deploy/start.sh"
    fi
}

# PID 파일만 존재하는 것은 기동 증거가 아니다. `start.sh` 가 PID 를 쓴 직후 JVM 이
# 죽거나, 이전 프로세스가 남아 있어도 파일만 있으면 기존 배포는 성공으로 끝났다.
# 특히 JAR 을 원자 교체한 뒤 재시작이 실제로 일어났는지 확인하지 않으면 디스크의
# 산출물과 실행 중인 JVM 이 서로 다른 버전인 상태를 놓친다.
backend_pid() {
    local pid
    [ -f "$PID_FILE" ] || return 1
    pid=$(cat "$PID_FILE" 2>/dev/null || true)
    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    printf '%s\n' "$pid"
}

backend_process_alive() {
    local pid
    pid=$(backend_pid) || return 1
    ps -p "$pid" -o command= 2>/dev/null | grep -Fq -- "$JAR_DIR/ongo-api.jar"
}

backend_health_ready() {
    command -v curl >/dev/null 2>&1 || return 1
    curl -fsS --max-time 2 "http://127.0.0.1:${SERVER_PORT}/actuator/health" 2>/dev/null \
        | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'
}

# 재시작 직후 프로세스와 애플리케이션 health를 모두 확인한다. 이 게이트를 통과하지
# 못하면 deploy.sh가 성공으로 끝나지 않아, 운영자가 새 JAR 미적용/기동 실패를
# "배포 완료"로 오인하지 않는다.
wait_for_backend_health() {
    local waited=0
    while [ "$waited" -lt "$BACKEND_HEALTH_TIMEOUT_SECONDS" ]; do
        if ! backend_process_alive; then
            error "재시작 후 onGo JVM 프로세스를 찾을 수 없습니다."
            return 1
        fi
        if backend_health_ready; then
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done
    error "Backend health 확인 타임아웃 (${BACKEND_HEALTH_TIMEOUT_SECONDS}초, port=${SERVER_PORT})"
    return 1
}

# Argument Parsing
for arg in "$@"; do
    case $arg in
        --skip-git)
            SKIP_GIT=true
            shift
            ;;
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --artifact=*)
            DEPLOY_ARTIFACT="${arg#--artifact=}"
            shift
            ;;
    esac
done

TIMESTAMP=$(date +%Y%m%d_%H%M%S)

echo "============================================"
echo "  onGo 배포 ($DEPLOY_TARGET)"
echo "  시각: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================"
echo ""

# 디렉토리 존재 확인
if [ ! -d "$SRC_DIR" ]; then
    error "$SRC_DIR 디렉토리가 없습니다."
    exit 1
fi

cd "$SRC_DIR"

# ============================================================
# Git Pull
# ============================================================
if [ "$SKIP_GIT" = false ]; then
    step "최신 소스코드 가져오기 (git pull)..."
    git fetch --all
    BEFORE_HASH=$(git rev-parse HEAD)
    git pull origin main 2>/dev/null || { error "Failed to pull from main branch"; exit 1; }
    AFTER_HASH=$(git rev-parse HEAD)

    if [ "$BEFORE_HASH" = "$AFTER_HASH" ] && [ "$DEPLOY_TARGET" = "all" ]; then
        warn "변경사항이 없습니다. 강제 배포하려면: bash deploy.sh backend"
    fi

    info "현재 커밋: $(git log --oneline -1)"
else
    info "Git Pull 건너뜀 (--skip-git)"
fi
echo ""

# ============================================================
# 환경변수 선행 검증
# ============================================================
#
# **deploy_backend 의 가장 앞에서 돈다. 빌드·JAR 복사보다 먼저다.**
#
# 예전에는 이 검증이 JAR 복사 뒤에 있었다. 그래서 검증이 실패하면 배포는 중단되는데
# `$JAR_DIR/ongo-api.jar` 는 **이미 새 빌드로 덮어써진 상태**로 남았다. 구 프로세스는
# 열어 둔 inode 를 계속 쓰므로 그대로 돌지만, 디스크의 JAR 과 실행 중인 코드가
# 어긋난다. 그 뒤 무엇이든(OOM·수동 기동·다음 배포) 프로세스를 다시 띄우면 아무도
# 의도하지 않은 시점에 새 코드와 미적용 마이그레이션이 한꺼번에 올라온다.
#
# 실제로 2026-08-16 · 08-24 · 08-27 세 번의 배포가 이 상태로 끝났고, 운영은 08-12
# 빌드를 돌리면서 디스크에는 08-27 JAR 이 놓여 있었다. 아무도 배포 실패를 몰랐다.
#
# 그래서 **디스크를 건드리기 전에** 판정한다. 이 함수가 실패하면 JAR 백업·복사도,
# 서비스 중지도 일어나지 않는다 — 배포만 실패하고 운영은 손대지 않은 상태로 남는다.
#
# 비밀값은 출력하지 않는다. 아래 보고 함수들은 변수 **이름만** 보여준다.
preflight_env() {
    # .env 파일이 없으면 샘플에서 복사
    if [ ! -f "$ENV_FILE" ]; then
        if [ -f "$SRC_DIR/deploy/oracle/.env.production" ]; then
            cp "$SRC_DIR/deploy/oracle/.env.production" "$ENV_FILE"
            info ".env 파일 생성: deploy/oracle/.env.production 복사 완료"
            # Windows 줄바꿈 제거 (안전성 강화)
            sed -i 's/\r$//' "$ENV_FILE"
        else
            warn ".env 파일과 deploy/oracle/.env.production 파일이 모두 없습니다."
        fi
    fi

    # 배포 스크립트가 비밀값을 생성하거나 덮어쓰지 않도록 한다.
    # 개발용 키가 남아 있으면 애플리케이션 기동 전에 명확히 실패시킨다.
    if [ -f "$ENV_FILE" ] && grep -Eq '^PLATFORM_TOKEN_ENCRYPTION_KEY=(change-me|Y2hhbmdlLW1l)' "$ENV_FILE"; then
        error "PLATFORM_TOKEN_ENCRYPTION_KEY가 개발용 기본값입니다. $ENV_FILE 에 고유한 32바이트 Base64 키를 설정하세요."
        exit 1
    fi

    # 필수 환경변수 선행 검증 — **JAR 복사·서비스 중지보다 먼저** 돈다.
    #
    # start.sh 도 같은 검증을 하지만 그건 서비스를 멈춘 뒤에 돈다. 값이 비어 있으면
    # 앱이 내려간 채 다시 뜨지 않는다. 여기서 먼저 걸러야 기존 프로세스를 살린 채
    # 배포만 실패시킬 수 있다(무중단 실패).
    #
    # 서브셸에서 돌려 .env 값이 deploy.sh 환경으로 새지 않게 하고, set -e 영향도 가둔다.
    #
    # .env 로드 실패는 fail-closed 로 처리한다. 무시하면 부모 환경(Jenkins)에 같은 이름의
    # 값이 있을 때 .env 가 깨졌는데도 누락 목록이 비어 그대로 통과한다.
    local ENV_PREFLIGHT_RC=0
    local MISSING_VARS
    local INVALID_SHORT_VARS
    MISSING_VARS="$(
        set +e
        # shellcheck source=deploy/required-env.sh
        source "$SRC_DIR/deploy/required-env.sh" || exit 10
        ongo_load_env_file "$ENV_FILE" >/dev/null 2>&1 || exit $((20 + $?))
        ongo_missing_env_vars
    )" || ENV_PREFLIGHT_RC=$?

    case "$ENV_PREFLIGHT_RC" in
        0) ;;
        10) error "required-env.sh 를 읽을 수 없습니다: $SRC_DIR/deploy/required-env.sh"
            error "기존 서비스는 그대로 실행 중입니다."
            exit 1 ;;
        21) error "$ENV_FILE 파일이 없어 배포를 중단합니다. 기존 서비스는 그대로 실행 중입니다."
            exit 1 ;;
        22) error "$ENV_FILE 을 읽을 수 없습니다(문법 오류). 배포를 중단합니다."
            error "기존 서비스는 그대로 실행 중입니다."
            exit 1 ;;
        *)  error "환경변수 선행 검증이 예상치 못하게 실패했습니다(rc=$ENV_PREFLIGHT_RC)."
            error "기존 서비스는 그대로 실행 중입니다."
            exit 1 ;;
    esac

    if [ -n "$MISSING_VARS" ]; then
        error "필수 환경변수가 없어 배포를 중단합니다. 기존 서비스는 그대로 실행 중입니다."
        (
            source "$SRC_DIR/deploy/required-env.sh"
            ongo_report_missing_env_vars "$MISSING_VARS" "$ENV_FILE"
        )
        exit 1
    fi

    INVALID_SHORT_VARS="$(
        set +e
        # shellcheck source=deploy/required-env.sh
        source "$SRC_DIR/deploy/required-env.sh" || exit 10
        ongo_load_env_file "$ENV_FILE" >/dev/null 2>&1 || exit $((20 + $?))
        ongo_invalid_short_env_vars
    )" || ENV_PREFLIGHT_RC=$?

    if [ "$ENV_PREFLIGHT_RC" -ne 0 ]; then
        error "환경변수 길이 선행 검증에 실패했습니다(rc=$ENV_PREFLIGHT_RC). 기존 서비스는 그대로 실행 중입니다."
        exit 1
    fi
    if [ -n "$INVALID_SHORT_VARS" ]; then
        error "운영 자격 증명이 너무 짧아 배포를 중단합니다. 기존 서비스는 그대로 실행 중입니다."
        (
            source "$SRC_DIR/deploy/required-env.sh"
            ongo_report_invalid_short_env_vars "$INVALID_SHORT_VARS" "$ENV_FILE"
        )
        exit 1
    fi

    preflight_server_port
}

# 앱이 들을 포트와 nginx 가 넘길 포트가 같은지 본다.
#
# ## 왜 필요한가
#
# `SERVER_PORT` 는 필수 환경변수 목록에 **없다.** 없어도 정상이기 때문이다 —
# 비어 있으면 `application.yml` 의 `server.port: 8070` 이 쓰인다.
#
# 문제는 값이 **있을 때**다. `.env` 의 `SERVER_PORT` 는 export 되어 Spring 의
# relaxed binding 으로 `server.port` 를 덮는다. 반면 nginx 의 `proxy_pass` 는
# 설정 파일에 박혀 있고 배포 스크립트가 건드리지 않는다(운영자 수동 반영).
#
# 그래서 `.env` 에 포트를 하나 적는 것만으로 앱은 8071 에서 멀쩡히 뜨고 nginx 만
# 8070 으로 502 를 낸다. **이때 systemd 는 `active (running)` 이라 정상으로 보인다.**
# 프로세스도 살아 있고 로그도 깨끗해서 원인을 찾는 데 가장 오래 걸리는 형태다.
#
# ## 왜 8070 을 여기 적지 않는가
#
# 적으면 같은 상수가 네 곳(`application.yml`, `start.sh`, nginx, 여기)에 생겨
# 드리프트가 하나 더 는다. nginx 설정에서 **읽어서** 비교한다.
#
# nginx 설정을 못 읽으면 통과시킨다. 이 검사는 배포를 막는 것이 목적이 아니라
# 어긋남을 잡는 것이고, 설정 파일 위치는 호스트마다 다를 수 있다.
preflight_server_port() {
    local nginx_conf="$SRC_DIR/deploy/oracle/nginx-ongo.conf"
    [ -f "$nginx_conf" ] || return 0

    # `grep -o` 로 뽑는다. `sed` 의 `\+` 는 BSD(macOS)에서 안 먹어 빈 값이 되고,
    # 그러면 아래 `return 0` 으로 **조용히 통과**한다 — 검사가 fail-open 이 된다.
    local nginx_port
    nginx_port="$(grep -o 'proxy_pass http://localhost:[0-9][0-9]*' "$nginx_conf" \
        | grep -o '[0-9][0-9]*' | head -1)"

    # 못 뽑으면 통과시키되 침묵하지 않는다. 설정 형식이 바뀌었다는 신호다.
    if [ -z "$nginx_port" ]; then
        warn "nginx 설정에서 upstream 포트를 읽지 못해 포트 정합성 검사를 건너뜁니다: $nginx_conf"
        return 0
    fi

    local configured_port
    configured_port="$(
        set +e
        source "$SRC_DIR/deploy/required-env.sh" 2>/dev/null || exit 0
        ongo_load_env_file "$ENV_FILE" >/dev/null 2>&1 || exit 0
        printf '%s' "${SERVER_PORT:-}"
    )"

    # 비어 있으면 application.yml 기본값을 쓴다 — nginx 와 맞는지는 저장소 테스트가 본다.
    [ -n "$configured_port" ] || return 0

    if [ "$configured_port" != "$nginx_port" ]; then
        error "SERVER_PORT($configured_port) 와 nginx upstream($nginx_port) 이 다릅니다."
        error "이대로 배포하면 앱은 정상 기동하지만 외부 API 는 전부 502 가 됩니다."
        error "$ENV_FILE 의 SERVER_PORT 를 지우거나, nginx 설정의 proxy_pass 를 함께 바꾸세요."
        error "기존 서비스는 그대로 실행 중입니다."
        exit 1
    fi
}

# ============================================================
# Backend 배포
# ============================================================
deploy_backend() {
    step "Backend 배포 시작..."

    # **디스크를 건드리기 전에 환경을 판정한다.** 실패하면 JAR 복사도 서비스 중지도
    # 일어나지 않는다 — preflight_env 주석 참고.
    preflight_env

    # 재부팅·크래시 후 자동 복구가 없는 호스트에는 JAR을 발행하지 않는다.
    if ! require_managed_backend; then
        error "기존 서비스는 그대로 실행 중입니다."
        exit 1
    fi

    cd "$SRC_DIR/backend"

    # 운영 배포 전 단위/API 테스트와 패키징을 함께 검증한다.
    # 배포 호스트는 Docker를 사용하지 않으므로 Testcontainers IT만 명시적으로 제외한다.
    if [ "$SKIP_BUILD" = false ]; then
        info "Gradle 테스트 및 bootJar 빌드 중..."
        chmod +x gradlew
        # 반드시 clean을 먼저 수행한다. Jenkins workspace가 재사용되면
        # 삭제된 Kotlin 클래스가 build/classes에 남아 bootJar에 다시 들어가
        # 컴파일은 성공하지만 런타임에서 삭제된 Bean을 찾는 문제가 발생한다.
        ./gradlew clean :onGo-common:test :onGo-domain:test :onGo-application:test :onGo-infrastructure:test :onGo-api:test :onGo-api:bootJar --no-daemon -PskipIntegrationTests=true 2>&1 | tail -5
    else
        info "Backend 빌드 건너뜀 (--skip-build)"
    fi

    # JAR 파일 복사
    mkdir -p "$JAR_DIR"
    # Spring Boot 4 names the executable artifact `ongo-api-<version>.jar` by
    # default. Older builds used the optional `*-boot.jar` suffix. Do not use
    # `ls | head` here: with `set -euo pipefail`, a missing first glob exits
    # the deployment before the fallback can run (the service is then never
    # restarted even though the Gradle build succeeded).
    JAR_FILE=""
    if [ -n "$DEPLOY_ARTIFACT" ]; then
        # CI must deploy the exact artifact it just tested. This avoids
        # accidentally selecting an older build from the production checkout.
        if [ ! -f "$DEPLOY_ARTIFACT" ]; then
            error "전달된 배포 JAR을 찾을 수 없습니다: $DEPLOY_ARTIFACT"
            exit 1
        fi
        JAR_FILE="$DEPLOY_ARTIFACT"
        info "CI 빌드 산출물 사용: $JAR_FILE"
    else
        shopt -s nullglob
        JAR_CANDIDATES=(onGo-api/build/libs/ongo-api-*-boot.jar)
        if [ "${#JAR_CANDIDATES[@]}" -eq 0 ]; then
            for candidate in onGo-api/build/libs/ongo-api-*.jar; do
                [[ "$candidate" == *-plain.jar ]] && continue
                JAR_CANDIDATES+=("$candidate")
            done
        fi
        shopt -u nullglob
        if [ "${#JAR_CANDIDATES[@]}" -gt 0 ]; then
            # There is normally one artifact; sort by modification time when a
            # workspace still contains outputs from more than one build.
            JAR_FILE=$(for candidate in "${JAR_CANDIDATES[@]}"; do
                printf '%s\t%s\n' "$(stat -c '%Y' "$candidate" 2>/dev/null || stat -f '%m' "$candidate" 2>/dev/null)" "$candidate"
            done | sort -nr | cut -f2- | sed -n '1p')
        fi
    fi
    if [ -z "$JAR_FILE" ]; then
        error "JAR 파일을 찾을 수 없습니다. 빌드 실패!"
        error "확인 경로: $SRC_DIR/backend/onGo-api/build/libs"
        ls -la onGo-api/build/libs 2>/dev/null || true
        exit 1
    fi

    # 백업
    if [ -f "$JAR_DIR/ongo-api.jar" ]; then
        cp "$JAR_DIR/ongo-api.jar" "$JAR_DIR/ongo-api.jar.backup.$TIMESTAMP"
        info "기존 JAR 백업: ongo-api.jar.backup.$TIMESTAMP"
    fi

    # JAR 교체는 **원자적이어야 한다.**
    #
    # 예전에는 최종 경로에 그대로 `cp` 했다. `cp` 는 같은 inode 를 truncate 하고 다시 쓰므로,
    # **실행 중인 JVM 이 열어 둔 바로 그 파일의 내용이 배포 도중에 바뀐다.** Spring Boot 의
    # 중첩 JAR 로더는 클래스를 지연 로딩하기 때문에, 아직 로드하지 않은 클래스를 읽는 순간
    # 다른 빌드의 바이트를 읽게 된다 — ClassNotFoundException·NoSuchMethodError·ZIP 오류가
    # 예고 없이 난다. 운영에서 실제로 8/12 기동 프로세스가 8/16·8/24·8/27 세 번 덮어써진
    # JAR 을 연 채 돌고 있었다.
    #
    # 그래서 **같은 디렉터리**의 임시 파일에 쓴 뒤 `mv` 로 갈아 끼운다. 같은 파일시스템이라
    # `mv` 는 rename(2) 한 번이고, 그 순간 새 inode 가 그 이름을 갖는다. 실행 중 JVM 은
    # 옛 inode 를 계속 들고 있으므로(파일은 `(deleted)` 로 남아 살아 있다) 내용이 섞이지
    # 않는다. 다음 기동이 새 파일을 연다.
    #
    # 임시 파일 이름에 PID 를 붙여 동시 실행이 서로를 덮지 않게 한다.
    JAR_TMP="$JAR_DIR/.ongo-api.jar.new.$$"
    # 실패하거나 중단되면 임시 파일을 남기지 않는다. 기존 JAR 은 손대지 않았으므로 그대로다.
    trap 'rm -f "$JAR_TMP"' EXIT
    if ! cp "$JAR_FILE" "$JAR_TMP"; then
        error "새 JAR 을 임시 파일로 복사하지 못했습니다. 기존 JAR 을 그대로 둡니다."
        rm -f "$JAR_TMP"
        trap - EXIT
        exit 1
    fi
    # 기존 파일의 권한을 잇는다. 최초 배포처럼 기존 파일이 없으면 읽기 권한만 보장한다.
    # `mv` 는 원본 모드를 그대로 옮기므로 여기서 맞춰야 서비스 계정이 계속 읽을 수 있다.
    if [ -f "$JAR_DIR/ongo-api.jar" ]; then
        chmod --reference="$JAR_DIR/ongo-api.jar" "$JAR_TMP" 2>/dev/null || chmod 644 "$JAR_TMP"
    else
        chmod 644 "$JAR_TMP"
    fi
    if ! mv -f "$JAR_TMP" "$JAR_DIR/ongo-api.jar"; then
        error "새 JAR 을 최종 경로로 교체하지 못했습니다. 기존 JAR 을 그대로 둡니다."
        rm -f "$JAR_TMP"
        trap - EXIT
        exit 1
    fi
    trap - EXIT
    info "JAR 원자 교체 완료: $JAR_FILE → $JAR_DIR/ongo-api.jar"

    # DB 마이그레이션 적용 — 새 JAR 의 Flyway 만 돌린다. **서비스를 멈추기 전에** 한다.
    #
    # 마이그레이션 실행기는 애플리케이션 기동뿐이고, 아래 preflight 는 새 스키마가
    # 이미 있어야 통과한다. 이 단계가 없으면 "preflight 가 막아서 앱이 못 뜨고,
    # 앱이 못 떠서 Flyway 가 안 도는" 교착이 된다.
    #
    # 실패하면 여기서 끝난다. 서비스는 아직 멈추지 않았으므로 계속 실행 중이다.
    info "DB 마이그레이션 적용..."
    if ! bash "$SRC_DIR/deploy/migrate-schema.sh"; then
        error "마이그레이션에 실패해 배포를 중단했습니다. 서비스는 계속 실행 중입니다."
        exit 1
    fi

    # DB 스키마 점검 — 읽기 전용, **fail-closed**. 서비스를 멈추기 전에 한다.
    #
    # 위 마이그레이션이 성공했더라도 **결과를 다시 확인한다.** 적용됐다는 보고와 실제
    # 스키마는 다른 사실이고, 이 게이트가 검증하는 것은 후자다.
    #
    # stop.sh 뒤로 가면 배포가 불가능하다는 사실을 서비스가 이미 내려간 뒤에 알게 된다.
    # 여기서 막으면 운영은 계속 돌고 있는 상태로 끝난다.
    #
    # 0 이 아닌 모든 코드에서 중단한다. "스키마가 없다"(1)와 "점검하지 못했다"(2)는
    # 원인만 다를 뿐 결과가 같다 — 어느 쪽이든 **스키마를 확인하지 못한 채 새 코드를
    # 올리는 것**이고, 그 배포가 안전하다고 말할 근거가 없다.
    info "DB 스키마 점검..."
    if ! bash "$SRC_DIR/deploy/preflight-schema.sh"; then
        error "스키마 점검을 통과하지 못해 배포를 중단했습니다. 서비스는 계속 실행 중입니다."
        exit 1
    fi

    # 오래된 백업 정리 (최근 3개만 유지) — **재시작보다 먼저 한다.**
    #
    # 예전에는 기동 확인 뒤에 있었다. 그런데 이 함수는 재시작 앞뒤로 여러 번 exit 하고
    # 기동 실패도 exit 1 이라, 정리가 **한 번도 도달하지 못했다.** 운영에는 3개만 남아야 할
    # 백업이 6개(각 200MB) 쌓여 있었다. 정리는 서비스 상태와 무관한 디스크 위생이므로
    # 앞으로 옮겨 배포가 어디서 끝나든 실행되게 한다.
    #
    # 이 시점에는 이번 배포의 백업이 이미 만들어져 있으므로 최신 3개 안에 포함된다.
    ls -t "$JAR_DIR"/ongo-api.jar.backup.* 2>/dev/null | tail -n +4 | xargs rm -f 2>/dev/null || true

    # 서비스 재시작 — systemd 유닛이 있으면 그 cgroup 아래에서 올린다(restart_backend 참고).
    # 재시작 전 PID를 기억한다. stop.sh가 실패했는데도 start.sh가 기존 PID 파일을 보고
    # 성공으로 끝나면, health는 UP이어도 새 JAR이 전혀 실행되지 않은 상태가 된다.
    PREVIOUS_BACKEND_PID="$(backend_pid 2>/dev/null || true)"
    info "Backend 재시작..."
    restart_backend

    # 상태 확인 — PID 파일만으로 성공 처리하지 않는다. 실제 JAR 프로세스가 살아 있고
    # 인증 없는 actuator health가 UP이어야 배포를 성공으로 판정한다.
    if wait_for_backend_health; then
        CURRENT_BACKEND_PID="$(backend_pid 2>/dev/null || true)"
        if [ -n "$PREVIOUS_BACKEND_PID" ] && [ "$PREVIOUS_BACKEND_PID" = "$CURRENT_BACKEND_PID" ]; then
            error "Backend가 재기동되지 않았습니다 (기존 PID=${PREVIOUS_BACKEND_PID}). 새 JAR은 실행되지 않았습니다."
            tail -20 "$LOG_DIR/backend.log"
            exit 1
        fi
        info "Backend 실행 및 health 확인 완료 (PID: $CURRENT_BACKEND_PID, port: $SERVER_PORT)"
    else
        error "Backend 시작 실패! 로그:"
        tail -20 "$LOG_DIR/backend.log"
        exit 1
    fi

    echo ""
}

# ============================================================
# Frontend 배포
# ============================================================
deploy_frontend() {
    step "Frontend 배포 시작..."

    cd "$SRC_DIR/frontend"

    # 의존성 설치 + 빌드
    if [ "$SKIP_BUILD" = false ]; then
        info "npm 의존성 설치..."
        npm ci --silent 2>&1 | tail -3

        info "Vite 빌드 중..."
        VITE_API_BASE_URL=/api/v1 npm run build 2>&1 | tail -5
    else
        info "Frontend 빌드 건너뜀 (--skip-build)"
    fi

    if [ ! -d "dist" ]; then
        error "빌드 결과물(dist)이 없습니다. 빌드 실패!"
        exit 1
    fi

    # hidden sourcemap도 정적 파일 서버에 남으면 직접 URL로 내려받을 수 있다.
    # 현재 배포에는 오류 추적 업로드 경로가 없으므로 공개 산출물에서 제거한다.
    find dist -type f -name '*.map' -delete

    # 백업
    if [ -d "$WWW_DIR" ]; then
        mv "$WWW_DIR" "$WWW_DIR.backup.$TIMESTAMP"
        info "기존 프론트엔드 백업: $WWW_DIR.backup.$TIMESTAMP"
    fi

    # 배포
    mkdir -p "$WWW_DIR"
    cp -r dist/* "$WWW_DIR/"

    # Keep the immediately previous release's hashed chunks for one deploy
    # cycle. Tabs that are open during a deployment can finish loading their
    # old module graph; current files always win because cp -n never
    # overwrites the new build. The nginx /assets/ 404 rule remains the final
    # fallback once older backups are retired.
    PREVIOUS_ASSETS="$WWW_DIR.backup.$TIMESTAMP/assets"
    if [ -d "$PREVIOUS_ASSETS" ]; then
        mkdir -p "$WWW_DIR/assets"
        cp -rn "$PREVIOUS_ASSETS"/. "$WWW_DIR/assets/"
        info "이전 릴리스의 hashed assets를 한 사이클 유지했습니다."
    fi

    # SELinux 컨텍스트 적용 (Nginx가 파일을 읽을 수 있도록)
    restorecon -Rv "$WWW_DIR" > /dev/null 2>&1 || true
    info "Frontend 배포 완료: $WWW_DIR"

    # Nginx 재로드
    # sudo nginx -t && sudo systemctl reload nginx

    # 오래된 백업 정리 (최근 3개만 유지)
    ls -dt "$WWW_DIR".backup.* 2>/dev/null | tail -n +4 | xargs rm -rf 2>/dev/null || true

    echo ""
}

# ============================================================
# 배포 실행
# ============================================================
case "$DEPLOY_TARGET" in
    all)
        deploy_backend
        deploy_frontend
        ;;
    backend)
        deploy_backend
        ;;
    frontend)
        deploy_frontend
        ;;
    *)
        error "알 수 없는 타겟: $DEPLOY_TARGET"
        echo "사용법: bash deploy.sh [all|backend|frontend]"
        exit 1
        ;;
esac

# ============================================================
# 배포 결과 요약
# ============================================================
echo "============================================"
echo "  배포 완료!"
echo "============================================"
echo ""
echo "서비스 상태:"
CURRENT_BACKEND_PID=$(backend_pid || true)
if ongo_systemd_unit_installed && \
   systemctl is-active --quiet "$ONGO_SYSTEMD_UNIT" 2>/dev/null && \
   backend_process_alive && backend_health_ready; then
    echo "  - Backend:  active (systemd, PID: ${CURRENT_BACKEND_PID:-unknown})"
elif [ "${ALLOW_UNMANAGED_BACKEND:-false}" = "true" ] && \
     backend_process_alive && backend_health_ready; then
    echo "  - Backend:  active (EMERGENCY UNMANAGED, PID: ${CURRENT_BACKEND_PID:-unknown})"
else
    echo "  - Backend:  not ready (systemd unit/HTTP health 확인 필요)"
fi
# echo "  - Nginx:    $(systemctl is-active nginx 2>/dev/null || echo 'unknown')"
# echo ""
echo "접속 URL:"
DOMAIN="ongo.codelabtiger.com"
echo "  - 웹 UI:    https://${DOMAIN}"
echo "  - API:      https://${DOMAIN}/api"
echo ""
echo "로그 확인:"
echo "  - Backend:  tail -f $LOG_DIR/backend.log"
echo "  - Nginx:    sudo tail -f /var/log/nginx/ongo-*.log"
echo ""
