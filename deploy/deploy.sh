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
# Backend 배포
# ============================================================
deploy_backend() {
    step "Backend 배포 시작..."

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

    cp "$JAR_FILE" "$JAR_DIR/ongo-api.jar"
    info "JAR 복사 완료: $JAR_FILE → $JAR_DIR/ongo-api.jar"

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

    # 필수 환경변수 선행 검증 — 반드시 stop.sh 앞이어야 한다.
    #
    # start.sh 도 같은 검증을 하지만 그건 서비스를 멈춘 뒤에 돈다. 값이 비어 있으면
    # 앱이 내려간 채 다시 뜨지 않는다. 여기서 먼저 걸러야 기존 프로세스를 살린 채
    # 배포만 실패시킬 수 있다(무중단 실패).
    #
    # 서브셸에서 돌려 .env 값이 deploy.sh 환경으로 새지 않게 하고, set -e 영향도 가둔다.
    #
    # .env 로드 실패는 fail-closed 로 처리한다. 무시하면 부모 환경(Jenkins)에 같은 이름의
    # 값이 있을 때 .env 가 깨졌는데도 누락 목록이 비어 그대로 통과한다.
    ENV_PREFLIGHT_RC=0
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

    # DB 스키마 점검 — 읽기 전용, **fail-closed**. 서비스를 멈추기 전에 한다.
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

    # 서비스 재시작
    info "Backend 재시작..."
    bash "$SRC_DIR/deploy/stop.sh" || true
    bash "$SRC_DIR/deploy/start.sh"

    # 상태 확인
    if [ -f "$PID_FILE" ] && ps -p $(cat "$PID_FILE") > /dev/null 2>&1; then
        info "Backend 실행 중 (PID: $(cat $PID_FILE))"
    else
        error "Backend 시작 실패! 로그:"
        tail -20 "$LOG_DIR/backend.log"
        exit 1
    fi

    # 오래된 백업 정리 (최근 3개만 유지)
    ls -t "$JAR_DIR"/ongo-api.jar.backup.* 2>/dev/null | tail -n +4 | xargs rm -f 2>/dev/null || true

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
if [ -f "$PID_FILE" ] && ps -p $(cat "$PID_FILE") > /dev/null 2>&1; then
    echo "  - Backend:  active (PID: $(cat $PID_FILE))"
else
    echo "  - Backend:  inactive"
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
